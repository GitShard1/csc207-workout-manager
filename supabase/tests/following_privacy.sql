-- Run this file as a single script against Supabase after applying the migrations.
-- A failed check raises an error. Both fixtures and bookmarks roll back.
begin;

insert into auth.users (id, email, raw_user_meta_data)
values
    ('6bed0f17-a7a8-42de-9d8c-8502edfb0410', 'follow-sql-owner-5c8ad@example.test',
        '{"username":"follow_test_owner_5c8ad","display_name":"Test Owner"}'),
    ('f0b69f26-f36f-4b36-9dd5-5c4ec93d9770', 'follow-sql-target-5c8ad@example.test',
        '{"username":"follow_test_target_5c8ad","display_name":"Test Target"}'),
    ('ba5735f1-0982-417a-a0eb-fafcd0f2de98', 'follow-sql-other-5c8ad@example.test',
        '{"username":"follow_test_other_5c8ad","display_name":"Other Owner"}');

set local role authenticated;
set local request.jwt.claim.sub = '6bed0f17-a7a8-42de-9d8c-8502edfb0410';

do $$
declare
    owner_id constant uuid := '6bed0f17-a7a8-42de-9d8c-8502edfb0410';
    target_id constant uuid := 'f0b69f26-f36f-4b36-9dd5-5c4ec93d9770';
begin
    if not exists (
        select 1
        from public.find_follow_profile('follow_test_target_5c8ad')
        where id = target_id and username = 'follow_test_target_5c8ad' and display_name = 'Test Target'
    ) then
        raise exception 'A signed-in user should be able to look up a profile by username';
    end if;

    if exists (select 1 from public.find_follow_profile('FOLLOW_TEST_TARGET_5C8AD')) then
        raise exception 'A lookup should match the exact username';
    end if;

    insert into public.follows (followed_id) values (target_id);

    begin
        insert into public.follows (followed_id) values (target_id);
        raise exception 'A duplicate follow should fail with a unique violation';
    exception when unique_violation then
        null;
    end;

    begin
        insert into public.follows (followed_id) values (owner_id);
        raise exception 'A self-follow should fail with a check violation';
    exception when check_violation then
        null;
    end;

    if (select array_agg(id) from public.list_following()) is distinct from array[target_id] then
        raise exception 'The owner should see exactly one bookmark';
    end if;

    if not exists (
        select 1 from public.follows where follower_id = owner_id and followed_id = target_id
    ) then
        raise exception 'Supabase should derive the owner from the signed-in account';
    end if;
end
$$;

set local request.jwt.claim.sub = 'f0b69f26-f36f-4b36-9dd5-5c4ec93d9770';

do $$
declare
    owner_id constant uuid := '6bed0f17-a7a8-42de-9d8c-8502edfb0410';
    target_id constant uuid := 'f0b69f26-f36f-4b36-9dd5-5c4ec93d9770';
begin
    if exists (select 1 from public.follows) or exists (select 1 from public.list_following()) then
        raise exception 'The second account should start with an empty list';
    end if;

    begin
        insert into public.follows (follower_id, followed_id) values (owner_id, target_id);
        raise exception 'The second account should be unable to insert a bookmark for the first account';
    exception when insufficient_privilege then
        null;
    end;

    insert into public.follows (followed_id) values (owner_id);

    if (select array_agg(id) from public.list_following()) is distinct from array[owner_id] then
        raise exception 'The second account should only get its own bookmark from the list RPC';
    end if;

    if (select array_agg(follower_id) from public.follows) is distinct from array[target_id] then
        raise exception 'The second account should only see its own table row';
    end if;

    delete from public.follows where follower_id = owner_id and followed_id = target_id;
    if found then
        raise exception 'The second account should be unable to delete the first account''s bookmark';
    end if;
end
$$;

set local request.jwt.claim.sub = 'ba5735f1-0982-417a-a0eb-fafcd0f2de98';
insert into public.follows (followed_id) values ('f0b69f26-f36f-4b36-9dd5-5c4ec93d9770');

set local request.jwt.claim.sub = '6bed0f17-a7a8-42de-9d8c-8502edfb0410';

do $$
declare
    owner_id constant uuid := '6bed0f17-a7a8-42de-9d8c-8502edfb0410';
    target_id constant uuid := 'f0b69f26-f36f-4b36-9dd5-5c4ec93d9770';
    other_id constant uuid := 'ba5735f1-0982-417a-a0eb-fafcd0f2de98';
begin
    insert into public.follows (followed_id) values (other_id);

    delete from public.follows where follower_id = owner_id and followed_id = target_id;
    if not found then
        raise exception 'The owner should be able to delete their own bookmark';
    end if;

    delete from public.follows where follower_id = owner_id and followed_id = target_id;
    if found then
        raise exception 'Retrying an absent bookmark should delete zero rows';
    end if;

    if (select array_agg(id) from public.list_following()) is distinct from array[other_id] then
        raise exception 'The selected bookmark should be gone and the owner''s other bookmark should remain';
    end if;

    insert into public.follows (followed_id) values (target_id);
    if not exists (select 1 from public.list_following() where id = target_id) then
        raise exception 'The owner should be able to follow the same account again';
    end if;
end
$$;

set local request.jwt.claim.sub = 'ba5735f1-0982-417a-a0eb-fafcd0f2de98';

do $$
begin
    if (select array_agg(id) from public.list_following()) is distinct from
        array['f0b69f26-f36f-4b36-9dd5-5c4ec93d9770'::uuid] then
        raise exception 'Another account''s bookmark for the same target should survive';
    end if;
end
$$;

set local role anon;
set local request.jwt.claim.sub = '';

do $$
begin
    begin
        perform * from public.find_follow_profile('follow_test_target_5c8ad');
        raise exception 'Profile lookup should require sign-in';
    exception when insufficient_privilege then
        null;
    end;

    begin
        perform * from public.list_following();
        raise exception 'The Following list should require sign-in';
    exception when insufficient_privilege then
        null;
    end;

    begin
        delete from public.follows;
        raise exception 'Deleting a bookmark should require sign-in';
    exception when insufficient_privilege then
        null;
    end;
end
$$;

rollback;
