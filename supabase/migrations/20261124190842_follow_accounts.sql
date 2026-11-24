begin;

create table public.follows (
    follower_id uuid not null default auth.uid() references public.profiles (id) on delete cascade,
    followed_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (follower_id, followed_id),
    constraint follows_no_self_follow check (follower_id <> followed_id)
);

-- The primary key covers lookups by follower. This index covers deletion of a followed profile.
create index follows_followed_id_idx on public.follows (followed_id);

alter table public.follows enable row level security;
revoke all on table public.follows from public, anon, authenticated, service_role;
grant select on table public.follows to authenticated;
-- Post only followed_id. The database assigns the signed-in user as the follower.
grant insert (followed_id) on table public.follows to authenticated;

create policy follows_read_own on public.follows
    for select to authenticated
    using (follower_id = (select auth.uid()));

create policy follows_insert_own on public.follows
    for insert to authenticated
    with check (follower_id = (select auth.uid()));

-- Only the invoker wrappers below are exposed through the Data API.
create schema follow_private;
revoke all on schema follow_private from public, anon, authenticated, service_role;
grant usage on schema follow_private to authenticated;

create function follow_private.find_follow_profile(target_username text)
returns table (id uuid, username text, display_name text)
language sql
stable
security definer
set search_path = ''
as $$
    select profile.id, profile.username, profile.display_name
    from public.profiles as profile
    where (select auth.uid()) is not null
        and profile.username = target_username collate pg_catalog."C";
$$;

create function follow_private.list_following()
returns table (id uuid, username text, display_name text)
language sql
stable
security definer
set search_path = ''
as $$
    select profile.id, profile.username, profile.display_name
    from public.follows as bookmark
    join public.profiles as profile on profile.id = bookmark.followed_id
    where bookmark.follower_id = (select auth.uid())
    order by bookmark.created_at desc, bookmark.followed_id;
$$;

create function public.find_follow_profile(target_username text)
returns table (id uuid, username text, display_name text)
language sql
stable
security invoker
set search_path = ''
as $$
    select result.id, result.username, result.display_name
    from follow_private.find_follow_profile(target_username) as result;
$$;

create function public.list_following()
returns table (id uuid, username text, display_name text)
language sql
stable
security invoker
set search_path = ''
as $$
    select result.id, result.username, result.display_name
    from follow_private.list_following() as result;
$$;

revoke all on function follow_private.find_follow_profile(text), follow_private.list_following(),
    public.find_follow_profile(text), public.list_following() from public, anon, authenticated, service_role;
grant execute on function follow_private.find_follow_profile(text), follow_private.list_following(),
    public.find_follow_profile(text), public.list_following() to authenticated;

commit;
