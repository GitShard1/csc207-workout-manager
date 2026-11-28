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

## Log a Workout

- `use_case.workout_common.WorkoutFixtures` holds the shared test data for the workout use cases: a user, bench press and squat, the Push Day, starter and someone else's plans, and builders for earlier workouts and logged exercises.
- `StartWorkoutInteractorTest` and `FinishWorkoutInteractorTest` use in-memory plan and workout DAOs and a recording presenter. They check ownership, starter plans, last time's sets and date, skipped exercises, invalid sets, personal records, and failures to load or save.
- The controller and presenter tests check the use-case input and the resulting view states, including the discard prompt and what stays on screen after an error.
- `LogWorkoutViewTest` and `WorkoutSummaryViewTest` use real Swing controls with the real presenters and scripted use cases. They check ticking and locking sets, the rest timer, adding and removing sets, switching exercises, finishing, discarding, and the summary.
