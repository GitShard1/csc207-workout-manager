begin;

-- Keep codes, their hashes, and redemption receipts off the Data API.
create schema workout_share_private;
revoke all on schema workout_share_private from public, anon, authenticated, service_role;
grant usage on schema workout_share_private to authenticated;

create table workout_share_private.shares (
    plan_id uuid primary key references public.workout_plans (id) on delete cascade,
    code_digest bytea not null unique,
    expires_at timestamptz not null
);

create table workout_share_private.redemptions (
    recipient_id uuid not null references public.profiles (id) on delete cascade,
    request_id uuid not null,
    code_digest bytea not null,
    -- Retain the receipt if the recipient deletes the copy so retry cannot recreate it.
    copied_plan_id uuid not null,
    primary key (recipient_id, request_id)
);

alter table workout_share_private.shares enable row level security;
alter table workout_share_private.redemptions enable row level security;
revoke all on workout_share_private.shares, workout_share_private.redemptions
    from public, anon, authenticated, service_role;

create function workout_share_private.code_digest(share_code text)
returns bytea
language sql
immutable
set search_path = ''
as $$
    select pg_catalog.sha256(pg_catalog.convert_to(pg_catalog.lower(normalized.code), 'UTF8'))
    from (values (pg_catalog.btrim(share_code, E' \t\n\r\f\v'))) as normalized(code)
    where normalized.code collate pg_catalog."C"
        ~ '^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-4[0-9A-Fa-f]{3}-[89ABab][0-9A-Fa-f]{3}-[0-9A-Fa-f]{12}$';
$$;

create function workout_share_private.plan_revision(plan_name text, exercises jsonb)
returns text
language sql
immutable
set search_path = ''
as $$
    select pg_catalog.encode(pg_catalog.sha256(pg_catalog.convert_to(
        pg_catalog.jsonb_build_array(plan_name, exercises)::text, 'UTF8')), 'hex');
$$;

create function workout_share_private.list_owned_workout_plans()
returns jsonb
language sql
security definer
set search_path = ''
as $$
    select coalesce(pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object(
        'id', plan.id, 'name', plan.name, 'share_expires_at', share.expires_at)
        order by pg_catalog.lower(plan.name), plan.id), '[]'::jsonb)
    from public.workout_plans as plan
    left join workout_share_private.shares as share
        on share.plan_id = plan.id and share.expires_at > pg_catalog.statement_timestamp()
    where plan.owner_id = (select auth.uid()) and not plan.is_preset;
$$;

create function workout_share_private.generate_workout_plan_share(plan_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    code text;
    expiration timestamptz;
begin
    -- All changes to a share acquire the source-plan lock first, as does redemption.
    perform 1 from public.workout_plans as plan
    where plan.id = $1 and plan.owner_id = (select auth.uid()) and not plan.is_preset
    for no key update;
    if not found then
        return '{"status":"unavailable"}'::jsonb;
    end if;

    code := pg_catalog.gen_random_uuid()::text;
    expiration := pg_catalog.clock_timestamp() + interval '168 hours';
    insert into workout_share_private.shares as share (plan_id, code_digest, expires_at)
    values ($1, workout_share_private.code_digest(code), expiration)
    on conflict on constraint shares_pkey do update
    set code_digest = excluded.code_digest, expires_at = excluded.expires_at;

    return pg_catalog.jsonb_build_object('status', 'generated', 'code', code, 'expires_at', expiration);
end;
$$;

create function workout_share_private.revoke_workout_plan_share(plan_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
begin
    perform 1 from public.workout_plans as plan
    where plan.id = $1 and plan.owner_id = (select auth.uid()) and not plan.is_preset
    for no key update;
    if not found then
        return '{"status":"unavailable"}'::jsonb;
    end if;

    delete from workout_share_private.shares as share where share.plan_id = $1;
    return '{"status":"revoked"}'::jsonb;
end;
$$;

create function workout_share_private.preview_shared_workout_plan(share_code text)
returns jsonb
language sql
security definer
set search_path = ''
as $$
    select coalesce((
        select pg_catalog.jsonb_build_object(
            'status', 'available',
            'name', plan.name,
            'revision', workout_share_private.plan_revision(plan.name, plan.exercises),
            'exercises', (
                select pg_catalog.jsonb_agg(pg_catalog.jsonb_build_object(
                    'name', exercise.value -> 'name',
                    'sets', exercise.value -> 'sets',
                    'reps', exercise.value -> 'reps',
                    'weight', exercise.value -> 'weight')
                    order by exercise.position)
                from pg_catalog.jsonb_array_elements(plan.exercises) with ordinality as exercise(value, position)
            )
        )
        from workout_share_private.shares as share
        join public.workout_plans as plan on plan.id = share.plan_id
        where (select auth.uid()) is not null
            and share.code_digest = workout_share_private.code_digest($1)
            and share.expires_at > pg_catalog.statement_timestamp()
    ), '{"status":"unavailable"}'::jsonb);
$$;

create function workout_share_private.copy_shared_workout_plan(
    share_code text, preview_revision text, request_id uuid)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    recipient uuid := auth.uid();
    digest bytea := workout_share_private.code_digest($1);
    source_id uuid;
    source_plan public.workout_plans%rowtype;
    receipt workout_share_private.redemptions%rowtype;
    copied_id uuid := pg_catalog.gen_random_uuid();
    copied_name text;
    suffix text := '';
    copy_number integer := 0;
begin
    if recipient is null or digest is null or $3 is null then
        return '{"status":"unavailable"}'::jsonb;
    end if;

    -- Concurrent retries with the same account and request ID see the first committed receipt.
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(recipient::text || ':' || $3::text, 0));
    select redemption.* into receipt
    from workout_share_private.redemptions as redemption
    where redemption.recipient_id = recipient and redemption.request_id = $3;
    if found then
        if receipt.code_digest <> digest then
            return '{"status":"unavailable"}'::jsonb;
        end if;
        select plan.name into copied_name
        from public.workout_plans as plan
        where plan.id = receipt.copied_plan_id and plan.owner_id = recipient;
        if not found then
            return '{"status":"deleted"}'::jsonb;
        end if;
        return pg_catalog.jsonb_build_object('status', 'saved', 'id', receipt.copied_plan_id, 'name', copied_name);
    end if;

    select share.plan_id into source_id
    from workout_share_private.shares as share
    where share.code_digest = digest and share.expires_at > pg_catalog.clock_timestamp();
    if not found then
        return '{"status":"unavailable"}'::jsonb;
    end if;

    select plan.* into source_plan
    from public.workout_plans as plan
    where plan.id = source_id and not plan.is_preset
    for share;
    if not found or not exists (
        select 1 from workout_share_private.shares as share
        where share.plan_id = source_id and share.code_digest = digest
            and share.expires_at > pg_catalog.clock_timestamp()
    ) then
        return '{"status":"unavailable"}'::jsonb;
    end if;

    if $2 is distinct from workout_share_private.plan_revision(source_plan.name, source_plan.exercises) then
        return '{"status":"stale"}'::jsonb;
    end if;

    loop
        copied_name := pg_catalog.left(source_plan.name, 50 - pg_catalog.length(suffix)) || suffix;
        insert into public.workout_plans (id, owner_id, name, exercises, copied_from_user_id)
        values (copied_id, recipient, copied_name, source_plan.exercises, source_plan.owner_id)
        on conflict (owner_id, pg_catalog.lower(name)) do nothing;
        exit when found;

        copy_number := copy_number + 1;
        suffix := case when copy_number = 1 then ' (copy)' else ' (copy ' || copy_number || ')' end;
    end loop;

    insert into workout_share_private.redemptions (recipient_id, request_id, code_digest, copied_plan_id)
    values (recipient, $3, digest, copied_id);
    return pg_catalog.jsonb_build_object('status', 'saved', 'id', copied_id, 'name', copied_name);
end;
$$;

-- Public wrappers run with invoker rights. Only the private helpers can cross plan RLS for a valid code.
create function public.list_owned_workout_plans()
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select workout_share_private.list_owned_workout_plans(); $$;

create function public.generate_workout_plan_share(plan_id uuid)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select workout_share_private.generate_workout_plan_share($1); $$;

create function public.revoke_workout_plan_share(plan_id uuid)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select workout_share_private.revoke_workout_plan_share($1); $$;

create function public.preview_shared_workout_plan(share_code text)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select workout_share_private.preview_shared_workout_plan($1); $$;

create function public.copy_shared_workout_plan(share_code text, preview_revision text, request_id uuid)
returns jsonb
language sql
security invoker
set search_path = ''
as $$ select workout_share_private.copy_shared_workout_plan($1, $2, $3); $$;

revoke execute on all functions in schema workout_share_private from public, anon, authenticated, service_role;
grant execute on function workout_share_private.list_owned_workout_plans(),
    workout_share_private.generate_workout_plan_share(uuid), workout_share_private.revoke_workout_plan_share(uuid),
    workout_share_private.preview_shared_workout_plan(text),
    workout_share_private.copy_shared_workout_plan(text, text, uuid) to authenticated;

revoke all on function public.list_owned_workout_plans(),
    public.generate_workout_plan_share(uuid), public.revoke_workout_plan_share(uuid),
    public.preview_shared_workout_plan(text), public.copy_shared_workout_plan(text, text, uuid)
    from public, anon, authenticated, service_role;
grant execute on function public.list_owned_workout_plans(),
    public.generate_workout_plan_share(uuid), public.revoke_workout_plan_share(uuid),
    public.preview_shared_workout_plan(text), public.copy_shared_workout_plan(text, text, uuid) to authenticated;

commit;
