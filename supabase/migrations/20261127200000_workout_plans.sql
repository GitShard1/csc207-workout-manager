begin;

-- One row per plan. exercises holds the plan's exercises in order, each a JSON object with exercise_id, name,
-- description, body_part, target, secondary_muscles, equipment, mechanic, instructions, sets, reps and weight
-- (weight may be null). It is a copy, so loading a plan never needs the exercise library.
-- Workouts keep their own copy of the plan's name, so nothing that references plans should cascade into workouts.
create table public.workout_plans (
    id uuid primary key,
    owner_id uuid not null default auth.uid() references public.profiles (id) on delete cascade,
    name text not null,
    exercises jsonb not null,
    workout_completed boolean not null default false,
    is_preset boolean not null default false,
    copied_from_user_id uuid references public.profiles (id) on delete set null,
    created_at timestamptz not null default now(),
    constraint workout_plans_name_length check (char_length(name) between 1 and 50),
    constraint workout_plans_has_exercises check (
        case when jsonb_typeof(exercises) = 'array' then jsonb_array_length(exercises) >= 1 else false end),
    -- Starter plans are written by hand, and the app can't open a plan whose exercises lack these fields.
    constraint workout_plans_exercises_complete check (not jsonb_path_exists(exercises,
        '$[*] ? (!(exists(@.exercise_id) && exists(@.name) && exists(@.body_part) && exists(@.sets) && exists(@.reps)))'))
);

-- Plan names are unique per account, ignoring case. This index also covers lookups by owner.
create unique index workout_plans_owner_name_key on public.workout_plans (owner_id, lower(name));

alter table public.workout_plans enable row level security;
revoke all on table public.workout_plans from public, anon, authenticated, service_role;
grant select, delete on table public.workout_plans to authenticated;
-- The database assigns the owner and keeps is_preset false, so accounts can't create plans for others or starter plans.
grant insert (id, name, exercises, workout_completed, copied_from_user_id) on table public.workout_plans
    to authenticated;
grant update (name, exercises, workout_completed) on table public.workout_plans to authenticated;

create policy workout_plans_read_own_or_starter on public.workout_plans
    for select to authenticated
    using (owner_id = (select auth.uid()) or is_preset);

create policy workout_plans_insert_own on public.workout_plans
    for insert to authenticated
    with check (owner_id = (select auth.uid()) and not is_preset);

create policy workout_plans_update_own on public.workout_plans
    for update to authenticated
    using (owner_id = (select auth.uid()) and not is_preset)
    with check (owner_id = (select auth.uid()) and not is_preset);

create policy workout_plans_delete_own on public.workout_plans
    for delete to authenticated
    using (owner_id = (select auth.uid()) and not is_preset);

create function public.plan_name_taken(plan_name text)
returns boolean
language sql
stable
security invoker
set search_path = ''
as $$
    select exists (
        select 1
        from public.workout_plans as plan
        where plan.owner_id = (select auth.uid())
            and pg_catalog.lower(plan.name) = pg_catalog.lower(plan_name)
    );
$$;

revoke all on function public.plan_name_taken(text) from public, anon, authenticated, service_role;
grant execute on function public.plan_name_taken(text) to authenticated;

commit;
