-- Run after all migrations in a disposable Supabase test database. All fixtures roll back.
begin;

insert into auth.users (id, email, raw_user_meta_data)
values
    ('bc584e28-fb0a-4ce9-8fa4-851ea0df9111', 'share-owner-584e@example.test',
        '{"username":"share_owner_584e","display_name":"Share Owner"}'),
    ('bc584e28-fb0a-4ce9-8fa4-851ea0df9222', 'share-recipient-584e@example.test',
        '{"username":"share_recipient_584e","display_name":"Recipient"}'),
    ('bc584e28-fb0a-4ce9-8fa4-851ea0df9333', 'share-third-584e@example.test',
        '{"username":"share_third_584e","display_name":"Another Recipient"}');

insert into public.workout_plans (id, owner_id, name, exercises, workout_completed, is_preset)
values
    ('f377dba4-e623-4024-9971-bb3ad59a9111', 'bc584e28-fb0a-4ce9-8fa4-851ea0df9111',
        repeat('x', 50), '[{"exercise_id":"1","name":"Squat","body_part":"UPPER_LEGS","sets":3,"reps":6,"weight":100},
        {"exercise_id":"2","name":"Lunge","body_part":"UPPER_LEGS","sets":2,"reps":8}]', true, false),
    ('f377dba4-e623-4024-9971-bb3ad59a9222', 'bc584e28-fb0a-4ce9-8fa4-851ea0df9111',
        'Private Plan', '[{"exercise_id":"3","name":"Curl","body_part":"UPPER_ARMS","sets":2,"reps":8}]', false, false),
    ('f377dba4-e623-4024-9971-bb3ad59a9333', 'bc584e28-fb0a-4ce9-8fa4-851ea0df9111',
        'Starter', '[{"exercise_id":"1","name":"Squat","body_part":"UPPER_LEGS","sets":3,"reps":6}]', false, true),
    ('f377dba4-e623-4024-9971-bb3ad59a9444', 'bc584e28-fb0a-4ce9-8fa4-851ea0df9222',
        repeat('X', 50), '[{"exercise_id":"1","name":"Squat","body_part":"UPPER_LEGS","sets":3,"reps":6}]', false, false),
    ('f377dba4-e623-4024-9971-bb3ad59a9555', 'bc584e28-fb0a-4ce9-8fa4-851ea0df9222',
        repeat('X', 43) || ' (COPY)',
        '[{"exercise_id":"1","name":"Squat","body_part":"UPPER_LEGS","sets":3,"reps":6}]', false, false);

-- This assertion helper lives only inside the transaction.
create function pg_temp.check_workout_share(ok boolean, failure text)
returns void language plpgsql as $$
begin
    if ok is distinct from true then
        raise exception '%', failure;
    end if;
end;
$$;

set local role authenticated;
set local request.jwt.claim.sub = 'bc584e28-fb0a-4ce9-8fa4-851ea0df9111';

do $$
declare
    shared_plan constant uuid := 'f377dba4-e623-4024-9971-bb3ad59a9111';
    starter constant uuid := 'f377dba4-e623-4024-9971-bb3ad59a9333';
    generated jsonb := public.generate_workout_plan_share(shared_plan);
    replaced jsonb;
    plans jsonb;
begin
    perform pg_temp.check_workout_share(generated ->> 'status' = 'generated', 'Owner should generate a code');
    perform pg_temp.check_workout_share((generated ->> 'expires_at')::timestamptz between
        clock_timestamp() + interval '167 hours 59 minutes' and clock_timestamp() + interval '168 hours',
        'Code should be valid for seven days');
    perform pg_temp.check_workout_share(public.generate_workout_plan_share(starter) ->> 'status' = 'unavailable',
        'An owned starter plan cannot be shared');
    replaced := public.generate_workout_plan_share(shared_plan);
    perform pg_temp.check_workout_share(replaced ->> 'code' <> generated ->> 'code', 'Regeneration rotates the code');
    perform pg_temp.check_workout_share(public.preview_shared_workout_plan(generated ->> 'code') ->> 'status'
        = 'unavailable', 'Rotation must immediately invalidate the old code');
    plans := public.list_owned_workout_plans();
    perform pg_temp.check_workout_share(jsonb_array_length(plans) = 2
        and plans -> 0 ->> 'name' = 'Private Plan'
        and plans -> 0 -> 'share_expires_at' = 'null'::jsonb
        and plans -> 1 ->> 'share_expires_at' = replaced ->> 'expires_at',
        'List should return only owned non-starters, in name order, with active expiry metadata');
    perform set_config('tests.workout_share_code', replaced ->> 'code', true);
end;
$$;

set local request.jwt.claim.sub = 'bc584e28-fb0a-4ce9-8fa4-851ea0df9222';

do $$
declare
    shared_plan constant uuid := 'f377dba4-e623-4024-9971-bb3ad59a9111';
    unused_plan constant uuid := 'f377dba4-e623-4024-9971-bb3ad59a9222';
    recipient constant uuid := 'bc584e28-fb0a-4ce9-8fa4-851ea0df9222';
    source_owner constant uuid := 'bc584e28-fb0a-4ce9-8fa4-851ea0df9111';
    request constant uuid := '2d794720-fe15-4bac-a5f4-21eb476d9111';
    code text := current_setting('tests.workout_share_code');
    preview jsonb := public.preview_shared_workout_plan(E'\t' || upper(code) || E'\r\n');
    saved jsonb;
    copied_id uuid;
begin
    perform pg_temp.check_workout_share(public.generate_workout_plan_share(shared_plan) ->> 'status' = 'unavailable'
        and public.revoke_workout_plan_share(shared_plan) ->> 'status' = 'unavailable',
        'Non-owner cannot generate or revoke a code');
    perform pg_temp.check_workout_share(not exists (
        select 1 from public.workout_plans where id in (shared_plan, unused_plan)),
        'Knowing the IDs does not grant direct access to either owner plan');
    perform pg_temp.check_workout_share(public.preview_shared_workout_plan('00000000-0000-4000-8000-000000000000')
        = public.preview_shared_workout_plan('invalid'),
        'Unknown and malformed codes should return the same unavailable result');
    perform pg_temp.check_workout_share(preview ->> 'status' = 'available'
        and preview ->> 'name' = repeat('x', 50)
        and preview = jsonb_build_object('status', 'available', 'name', repeat('x', 50),
            'revision', preview ->> 'revision', 'exercises',
            '[{"name":"Squat","sets":3,"reps":6,"weight":100},{"name":"Lunge","sets":2,"reps":8,"weight":null}]'::jsonb)
        and (preview ->> 'revision') ~ '^[0-9a-f]{64}$',
        'Preview must return exactly the expected fields in exercise order');
    perform pg_temp.check_workout_share(public.copy_shared_workout_plan(code, 'wrong revision', request)
        ->> 'status' = 'stale', 'A mismatched revision cannot save a copy');
    saved := public.copy_shared_workout_plan(code, preview ->> 'revision', request);
    copied_id := (saved ->> 'id')::uuid;
    perform pg_temp.check_workout_share(saved ->> 'status' = 'saved'
        and saved ->> 'name' = repeat('x', 41) || ' (copy 2)',
        'Copies preserve the original name if possible and disambiguate case-insensitive collisions within 50 characters');
    perform pg_temp.check_workout_share(exists (select 1 from public.workout_plans
        where id = copied_id and owner_id = recipient and copied_from_user_id = source_owner
            and not workout_completed and not is_preset
            and exercises = '[{"exercise_id":"1","name":"Squat","body_part":"UPPER_LEGS","sets":3,"reps":6,"weight":100},
                {"exercise_id":"2","name":"Lunge","body_part":"UPPER_LEGS","sets":2,"reps":8}]'::jsonb),
        'Copy must preserve every exercise field, reset flags, record provenance, and belong to the recipient');
    perform pg_temp.check_workout_share(public.copy_shared_workout_plan(code, preview ->> 'revision', request) = saved,
        'A retry must return the same copy');
    update public.workout_plans set name = 'Recipient copy' where id = copied_id;
    perform pg_temp.check_workout_share(public.preview_shared_workout_plan(code) ->> 'name' = repeat('x', 50),
        'Editing the copy must leave the shared source intact');
    perform set_config('tests.workout_share_preview', preview ->> 'revision', true);
    perform set_config('tests.workout_share_copy', copied_id::text, true);

    begin
        perform * from workout_share_private.shares;
        raise exception 'Clients must not be able to list private shares';
    exception when insufficient_privilege then
        null;
    end;
    begin
        perform * from workout_share_private.redemptions;
        raise exception 'Clients must not be able to list private receipts';
    exception when insufficient_privilege then
        null;
    end;
end;
$$;

set local request.jwt.claim.sub = 'bc584e28-fb0a-4ce9-8fa4-851ea0df9333';

do $$
declare
    code text := current_setting('tests.workout_share_code');
    revision text := current_setting('tests.workout_share_preview');
    request constant uuid := '2d794720-fe15-4bac-a5f4-21eb476d9111';
begin
    perform pg_temp.check_workout_share(public.list_owned_workout_plans() = '[]'::jsonb,
        'The third account begins with no plans');
    perform pg_temp.check_workout_share(public.preview_shared_workout_plan(code) ->> 'status' = 'available',
        'An unrelated signed-in account can preview a forwarded code');
    perform pg_temp.check_workout_share(public.copy_shared_workout_plan(code, revision, request) ->> 'name' = repeat('x', 50),
        'An unrelated signed-in account can save with the original name using its own request namespace');
end;
$$;

set local request.jwt.claim.sub = 'bc584e28-fb0a-4ce9-8fa4-851ea0df9111';

do $$
begin
    update public.workout_plans set name = 'Updated source' where id = 'f377dba4-e623-4024-9971-bb3ad59a9111';
end;
$$;

set local request.jwt.claim.sub = 'bc584e28-fb0a-4ce9-8fa4-851ea0df9222';

do $$
begin
    perform pg_temp.check_workout_share(public.copy_shared_workout_plan(current_setting('tests.workout_share_code'),
        current_setting('tests.workout_share_preview'), gen_random_uuid()) ->> 'status' = 'stale',
        'A source edit between preview and save requires a fresh preview');
end;
$$;

reset role;
update workout_share_private.shares set expires_at = clock_timestamp() - interval '1 second'
where plan_id = 'f377dba4-e623-4024-9971-bb3ad59a9111';

set local role authenticated;

do $$
declare
    code text := current_setting('tests.workout_share_code');
    revision text := current_setting('tests.workout_share_preview');
    request constant uuid := '2d794720-fe15-4bac-a5f4-21eb476d9111';
begin
    perform pg_temp.check_workout_share(public.preview_shared_workout_plan(code) ->> 'status' = 'unavailable'
        and public.copy_shared_workout_plan(code, revision, gen_random_uuid()) ->> 'status' = 'unavailable',
        'Expired codes cannot be previewed or newly redeemed');
    perform pg_temp.check_workout_share(public.copy_shared_workout_plan(code, revision, request) ->> 'name'
        = 'Recipient copy', 'A retry after expiry should still return the existing copy');
end;
$$;

set local request.jwt.claim.sub = 'bc584e28-fb0a-4ce9-8fa4-851ea0df9111';

do $$
declare
    shared_plan constant uuid := 'f377dba4-e623-4024-9971-bb3ad59a9111';
    generated jsonb := public.generate_workout_plan_share(shared_plan);
    code text := generated ->> 'code';
begin
    perform pg_temp.check_workout_share(public.revoke_workout_plan_share(shared_plan) ->> 'status' = 'revoked'
        and public.preview_shared_workout_plan(code) ->> 'status' = 'unavailable',
        'A revoked code is unavailable');
    generated := public.generate_workout_plan_share(shared_plan);
    code := generated ->> 'code';
    delete from public.workout_plans where id = shared_plan;
    perform pg_temp.check_workout_share(public.preview_shared_workout_plan(code) ->> 'status' = 'unavailable',
        'Deleting the source invalidates its active share');
end;
$$;

set local request.jwt.claim.sub = 'bc584e28-fb0a-4ce9-8fa4-851ea0df9222';

do $$
declare
    code text := current_setting('tests.workout_share_code');
    revision text := current_setting('tests.workout_share_preview');
    request constant uuid := '2d794720-fe15-4bac-a5f4-21eb476d9111';
    copied_id uuid := current_setting('tests.workout_share_copy')::uuid;
begin
    perform pg_temp.check_workout_share(exists (
        select 1 from public.workout_plans where id = copied_id and name = 'Recipient copy'),
        'The copy outlives the source');
    delete from public.workout_plans where id = copied_id;
    perform pg_temp.check_workout_share(public.copy_shared_workout_plan(code, revision, request) ->> 'status' = 'deleted',
        'Retry cannot resurrect a deleted copy');
end;
$$;

set local request.jwt.claim.sub = '';

do $$
begin
    perform pg_temp.check_workout_share(public.list_owned_workout_plans() = '[]'::jsonb
        and public.preview_shared_workout_plan(current_setting('tests.workout_share_code')) ->> 'status' = 'unavailable',
        'The private functions still require a JWT subject');
end;
$$;

set local role anon;

do $$
begin
    perform pg_temp.check_workout_share(not has_function_privilege('public.list_owned_workout_plans()', 'execute')
        and not has_function_privilege('public.generate_workout_plan_share(uuid)', 'execute')
        and not has_function_privilege('public.revoke_workout_plan_share(uuid)', 'execute')
        and not has_function_privilege('public.preview_shared_workout_plan(text)', 'execute')
        and not has_function_privilege('public.copy_shared_workout_plan(text,text,uuid)', 'execute'),
        'Anon must have no execute privilege on any share RPC');
end;
$$;

rollback;
