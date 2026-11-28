# Share a Workout Plan

## Desktop use

1. Sign in and open **Workout Plans**. Select one of your saved plans and generate a share code.
2. Send the code to the recipient. A forwarded code works for any signed-in account and opens only the selected plan. Each code lasts seven days.
3. The recipient signs in, opens **Workout Plans**, enters the code, and previews the exercises, sets, reps, and optional weights. Choose **Save copy** to create a plan in their own list.
4. Open the new plan to edit its name, exercise order, or targets. The source and the recipient's copy are separate plans.

Generate a replacement to invalidate the previous code, or revoke it from the source plan. Deleting the source also ends access. Existing copies stay available. Code access is independent of private Following bookmarks.

## Storage and API

Each share stores a digest of the code and a reference to the source plan. The raw code is shown after generation. The plan keeps its normal ownership rules, and the sharing operations use the active account to check permissions.

| RPC | Purpose |
| --- | --- |
| `list_owned_workout_plans` | Return the caller's saved plans and active code expiry. |
| `generate_workout_plan_share` | Generate or replace a code for a plan the caller owns. |
| `revoke_workout_plan_share` | Revoke the code for a plan the caller owns. |
| `preview_shared_workout_plan` | Resolve a code to a title, ordered exercise targets, and preview revision. |
| `copy_shared_workout_plan` | Validate the code and revision and atomically save a recipient-owned plan. |

Saving validates the current source against the preview. An edit between preview and save requires a fresh preview. The copy keeps the exercises and targets and starts incomplete. It uses the source name if available, then adds a distinct suffix as needed. Each save attempt has a request ID that the app reuses after a transport failure, so a lost response can be retried without creating another copy.

### Troubleshooting

- An invalid, expired, replaced, or revoked code may be reported as unavailable. Check the pasted code, then ask the owner for a current one.
- If the source changed after preview, enter or look up the code again and review the updated targets before saving.
- If saving returns a retryable request error, retry the save for the same preview. If the app confirms the copy was saved and the list failed to refresh, refresh the plan list.
- If sharing actions are unavailable in a configured test project, check the project URL and the applied migration versions before testing with another account.

## Verification and rollout

Use a disposable hosted Supabase project with confirmed test accounts for the hosted smoke and desktop checks. Apply the repository migrations in filename order. Run the existing privacy suites and the sharing SQL suite listed in the [test guide](../src/test/README.md) before testing the desktop app.

### Hosted smoke

Set `WORKOUT_SHARING_SMOKE=1` and supply the following environment variables in the test shell. These variables are separate from the app's settings, so enabling the test requires an explicit project.

| Variable | Value |
| --- | --- |
| `WORKOUT_SHARING_SMOKE_URL` | Disposable HTTPS Supabase project origin |
| `WORKOUT_SHARING_SMOKE_KEY` | Project publishable key |
| `WORKOUT_SHARING_SMOKE_OWNER_EMAIL`, `WORKOUT_SHARING_SMOKE_OWNER_PASSWORD` | Confirmed owner test credentials |
| `WORKOUT_SHARING_SMOKE_RECIPIENT_EMAIL`, `WORKOUT_SHARING_SMOKE_RECIPIENT_PASSWORD` | Confirmed recipient test credentials |

```sh
./mvnw -Dtest=HostedWorkoutPlanSharingSmokeTest test
```

On Windows, use `.\mvnw.cmd -Dtest=HostedWorkoutPlanSharingSmokeTest test`. The test seeds a uniquely named source plan, checks it remains private under normal plan reads, exercises the share and retry operations, revokes the share, and removes its test plans.

### Desktop end to end

1. Set `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` to the disposable project and run `./mvnw compile exec:exec` with JDK 25. On Windows, use `.\mvnw.cmd compile exec:exec`.
2. Give the owner test account a saved private plan with at least two exercises and different targets. Use an existing fixture or seed a row through the test database. A sample seed is below.
3. Sign in as the owner, generate a share code, and check that an expiry is shown.
4. Sign out and sign in as the recipient. Try an invalid code, then the valid one. Check the order, sets, reps, and weights in the preview. Save and verify the new plan appears in the list.
5. Sign out and back in as the recipient. Open and edit the copy. Sign back in as the owner and verify the source still has its original name and targets.
6. Enter the forwarded code as a third signed-in account. Verify that account gets its own copy. Replace or revoke the code as the owner, then verify the previous code cannot be previewed and the earlier copies still open.

For a new test project, run this fixture with `psql "$TEST_DATABASE_URL" -X -v ON_ERROR_STOP=1 -v owner_email=owner@example.test` and substitute the confirmed owner test address. Run it once in the disposable database.

```sql
insert into public.workout_plans (owner_id, name, exercises)
select id, 'Sharing E2E', '[
  {"exercise_id":"e2e-bench","name":"Bench Press","body_part":"CHEST","sets":3,"reps":8,"weight":20},
  {"exercise_id":"e2e-pushup","name":"Push-up","body_part":"CHEST","sets":2,"reps":12,"weight":null}
]'::jsonb
from auth.users
where email = :'owner_email';
```

Record the verified commit, migration versions, test tools, and results. Keep raw codes, login credentials, and tokens out of evidence. After the disposable checks pass, deploy the migration to the team project ahead of the updated app. Individual codes can be revoked if access needs to be withdrawn during rollout.
