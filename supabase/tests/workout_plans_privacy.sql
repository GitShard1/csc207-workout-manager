-- Run this file as a single script against Supabase after applying the migrations.
-- A failed check raises an error. Fixtures and plans roll back.
begin;

insert into auth.users (id, email, raw_user_meta_data)
values
    ('3f6d2a91-5b0e-4c1a-9e57-0d7f4b8a2c61', 'plan-sql-owner-7d2e1@example.test',
        '{"username":"plan_test_owner_7d2e1","display_name":"Plan Owner"}'),
    ('a8c4e2b7-1d93-4f6a-b0e5-7c2d9f1e3a48', 'plan-sql-other-7d2e1@example.test',
        '{"username":"plan_test_other_7d2e1","display_name":"Other Owner"}');

-- A starter plan, added the way the team would add one: outside the app.
insert into public.workout_plans (id, owner_id, name, exercises, is_preset)
values ('5e1b7c3d-9a24-4f8e-b6d0-2c4a8e6f1b93', '3f6d2a91-5b0e-4c1a-9e57-0d7f4b8a2c61', 'Starter Full Body',
    '[{"exercise_id":"0043","name":"Barbell Squat","sets":3,"reps":10,"weight":null}]', true);

set local role authenticated;
set local request.jwt.claim.sub = '3f6d2a91-5b0e-4c1a-9e57-0d7f4b8a2c61';

do $$
declare
    owner_uuid constant uuid := '3f6d2a91-5b0e-4c1a-9e57-0d7f4b8a2c61';
    other_uuid constant uuid := 'a8c4e2b7-1d93-4f6a-b0e5-7c2d9f1e3a48';
    plan_uuid constant uuid := 'c7e9a1f3-2b5d-4e8a-9c06-1f3b5d7e9a24';
    starter_uuid constant uuid := '5e1b7c3d-9a24-4f8e-b6d0-2c4a8e6f1b93';
    one_exercise constant jsonb :=
        '[{"exercise_id":"0025","name":"Barbell Bench Press","sets":3,"reps":8,"weight":60}]';
begin
    insert into public.workout_plans (id, name, exercises) values (plan_uuid, 'Push Day', one_exercise);

    if not exists (
        select 1 from public.workout_plans as plan
        where plan.id = plan_uuid and plan.owner_id = owner_uuid and not plan.is_preset
    ) then
        raise exception 'A new plan should belong to the signed-in account and not be a starter plan';
    end if;

    begin
        insert into public.workout_plans (id, name, exercises) values (gen_random_uuid(), 'PUSH DAY', one_exercise);
        raise exception 'A second plan with the same name, ignoring case, should fail with a unique violation';
    exception when unique_violation then
        null;
    end;

    begin
        insert into public.workout_plans (id, name, exercises)
        values (gen_random_uuid(), repeat('a', 51), one_exercise);
        raise exception 'A name over 50 characters should fail with a check violation';
    exception when check_violation then
        null;
    end;

    begin
        insert into public.workout_plans (id, name, exercises) values (gen_random_uuid(), 'Empty', '[]');
        raise exception 'A plan without exercises should fail with a check violation';
    exception when check_violation then
        null;
    end;

    begin
        insert into public.workout_plans (id, name, exercises, is_preset)
        values (gen_random_uuid(), 'My Starter', one_exercise, true);
        raise exception 'An account should be unable to create a starter plan';
    exception when insufficient_privilege then
        null;
    end;

    begin
        insert into public.workout_plans (id, owner_id, name, exercises)
        values (gen_random_uuid(), other_uuid, 'Not Mine', one_exercise);
        raise exception 'An account should be unable to create a plan for another account';
    exception when insufficient_privilege then
        null;
    end;

    if not public.plan_name_taken('push day') then
        raise exception 'The name check should ignore case';
    end if;

    if public.plan_name_taken('Leg Day') then
        raise exception 'An unused name should not be taken';
    end if;

    update public.workout_plans set name = 'Push Day A' where id = plan_uuid;
    if not found then
        raise exception 'The owner should be able to rename their plan';
    end if;

    update public.workout_plans set name = 'Changed Starter' where id = starter_uuid;
    if found then
        raise exception 'Starter plans should not be editable, even by the account they are stored under';
    end if;

    delete from public.workout_plans where id = starter_uuid;
    if found then
        raise exception 'Starter plans should not be deletable';
    end if;
end
$$;

set local request.jwt.claim.sub = 'a8c4e2b7-1d93-4f6a-b0e5-7c2d9f1e3a48';

do $$
declare
    plan_uuid constant uuid := 'c7e9a1f3-2b5d-4e8a-9c06-1f3b5d7e9a24';
    starter_uuid constant uuid := '5e1b7c3d-9a24-4f8e-b6d0-2c4a8e6f1b93';
begin
    if exists (select 1 from public.workout_plans as plan where plan.id = plan_uuid) then
        raise exception 'Another account should not see the owner''s plan';
    end if;

    if not exists (select 1 from public.workout_plans as plan where plan.id = starter_uuid) then
        raise exception 'Every signed-in account should see starter plans';
    end if;

    if public.plan_name_taken('Push Day A') then
        raise exception 'The name check should only look at the signed-in account''s plans';
    end if;

    update public.workout_plans set name = 'Not Yours' where id = plan_uuid;
    if found then
        raise exception 'Another account should be unable to change the owner''s plan';
    end if;

    delete from public.workout_plans where id = plan_uuid;
    if found then
        raise exception 'Another account should be unable to delete the owner''s plan';
    end if;

    -- Different accounts may use the same plan name.
    insert into public.workout_plans (id, name, exercises)
    values (gen_random_uuid(), 'Push Day A',
        '[{"exercise_id":"0025","name":"Barbell Bench Press","sets":3,"reps":8,"weight":60}]');
end
$$;

set local request.jwt.claim.sub = '3f6d2a91-5b0e-4c1a-9e57-0d7f4b8a2c61';

do $$
declare
    plan_uuid constant uuid := 'c7e9a1f3-2b5d-4e8a-9c06-1f3b5d7e9a24';
begin
    delete from public.workout_plans where id = plan_uuid;
    if not found then
        raise exception 'The owner should be able to delete their own plan';
    end if;

    delete from public.workout_plans where id = plan_uuid;
    if found then
        raise exception 'Deleting an absent plan again should delete zero rows';
    end if;
end
$$;

set local role anon;
set local request.jwt.claim.sub = '';

do $$
begin
    begin
        perform * from public.workout_plans;
        raise exception 'Reading plans should require sign-in';
    exception when insufficient_privilege then
        null;
    end;

    begin
        perform public.plan_name_taken('Push Day');
        raise exception 'The name check should require sign-in';
    exception when insufficient_privilege then
        null;
    end;
end
$$;

rollback;
