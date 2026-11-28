# Test doubles and fixtures

Tests never call the real WorkoutX API: they use the fakes and saved responses listed here.

## Fakes (Java classes)

### `use_case.search_exercises.FakeExerciseSearch`
- **Stands in for:** `ExerciseSearchDataAccessInterface` (the exercise library).
- **Behaviour:** `FakeExerciseSearch.returning(list)` answers every query with the same exercises. `FakeExerciseSearch.unavailable()` throws `ExerciseLibraryUnavailableException` on every query. Both record each query they receive (`getReceivedQueries()`).
- **Use it to test:** an interactor's success, no-match and unavailable paths; that a search was or wasn't sent (for example, that `CachingExerciseSearch` asks only once for a repeated search).
- **Used by:** `SearchExercisesInteractorTest`, `CachingExerciseSearchTest`.

### `RecordingPresenter` (nested in `SearchExercisesInteractorTest`)
- **Stands in for:** `SearchExercisesOutputBoundary` (the presenter).
- **Behaviour:** stores the last success output or error message, so the test can check what the interactor reported.
- **Use it to test:** what the Search Exercises interactor reports. It's private to that test; copy the idea for other use cases.

## Saved API responses

See [`resources/workoutx/README.md`](resources/workoutx/README.md): real WorkoutX responses for testing the JSON parsing and error handling.

## Unfollow an Account

- `UnfollowAccountInteractorTest` uses an in-memory bookmark set and a recording presenter. It checks ownership, the selected account ID, repeated deletion, and failures before and during a delete.
- `FollowingControllerTest` checks the use-case input. `FollowingPresenterTest` checks the resulting view state, including what stays visible after a retryable error.
- `FollowingViewTest` uses real Swing controls with fake storage. It checks selection, busy controls, retry, refresh, sign-in, and following the same account again.
- `SupabaseFollowingDaoTest` checks HTTP requests against a loopback server, including both account filters and the new token after a password change.

Run the Java checks with JDK 25 using `./mvnw verify` (`.\mvnw.cmd verify` on Windows). Apply the migrations to a disposable Supabase test database before running the privacy script. The script checks owner-only deletion and rolls back its fixtures.

```sh
psql "$TEST_DATABASE_URL" -X -v ON_ERROR_STOP=1 -f supabase/tests/following_privacy.sql
```

## Share a Workout Plan

| Layer | Tests and fixtures |
| --- | --- |
| Unit | The sharing interactor uses a fake gateway to cover input normalization, invalid requests, and retry identity. |
| Swing | The Workout Plans screen tests use real Swing controls and deterministic latches for request timing, view changes, and account switches. |
| HTTP integration | The sharing DAO tests use the loopback Supabase server to inspect account-bound requests, decode results, and exercise failures. |
| Database integration | Apply every migration to a disposable database. The SQL suites run with test accounts and role claims and roll back their fixtures. |
| Hosted smoke | Opt in to `HostedWorkoutPlanSharingSmokeTest` against a migrated disposable Supabase project. The test uses two actual Auth sessions. |
| Desktop end to end | Run the normal app against a migrated disposable project with three accounts. Follow the [desktop verification script](../../docs/workout-plan-sharing.md#desktop-end-to-end) and record observed results. |

Run the Java suite with JDK 25. The ordinary GitHub Actions build exercises Java and loopback tests. Run the database, hosted smoke, and desktop checks separately.

```sh
./mvnw --batch-mode --no-transfer-progress clean verify
psql "$TEST_DATABASE_URL" -X -v ON_ERROR_STOP=1 -f supabase/tests/following_privacy.sql
psql "$TEST_DATABASE_URL" -X -v ON_ERROR_STOP=1 -f supabase/tests/workout_plans_privacy.sql
psql "$TEST_DATABASE_URL" -X -v ON_ERROR_STOP=1 -f supabase/tests/workout_plan_sharing.sql
node supabase/tests/workout_plan_sharing_concurrency.mjs
```

For the concurrency test, keep `TEST_DATABASE_URL` set to a direct connection or session pooler for the disposable database. The runner uses Node.js and separate `psql` sessions to verify that requests overlap before releasing the first transaction. It removes its seeded accounts and plans on exit.

Record the tested commit, migration IDs, JDK and database versions, checks run, and assertion results. Use sanitized screenshots or reports if needed. Omit passwords, access tokens, and raw share codes.
