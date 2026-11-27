begin;

grant delete on table public.follows to authenticated;

create policy follows_delete_own on public.follows
    for delete to authenticated
    using (follower_id = (select auth.uid()));

commit;
