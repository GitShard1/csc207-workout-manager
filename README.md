# Team Project

Please keep this up-to-date with information about your project throughout the term.

The readme should include information such as:
- a summary of what your application is all about
- a list of the user stories, along with who is responsible for each one
- information about the API(s) that your project uses 
- screenshots or animations demonstrating current functionality

By keeping this README up-to-date,
your team will find it easier to prepare for the final presentation
at the end of the term.

# Project Name

## Team Information

### Team Members
- Jirat
- Demeng
- Eric
- David
- Amgalan

---

## Project Description

### Domain

A workout companion app that logs user exercises as well as having social capabilities.

---

## User Stories

### As a user, I want to register my account so that I can start using the app.
### As a beginner, I want to be able to search exercises by filters so I can find exercises I didn't know existed.
### As a regular user, I want to create a workout plan so I don't have to log the same exercises every time.
### As a user, I want to be able to edit my workout plan so I don't have to create a new one each time.
### As a user, I want to log my exercises to track my progress and look at my history.
### As a user, I want to see my history and personal records so I can see my own growth and feel motivated to keep working out.
### As a user with friends, I want to send my friends friend requests to check in on each other.
### As a user, I want to be able to respond to friend requests so I can choose whether I want to stay in touch or not.
### As a user, I want to copy my friend's workout plans so I can do the same exercises as my friends.

---

## Use Cases

### Register an Account

**Actor:** New user. **Precondition:** Signed out. **Trigger:** The user opens registration.

**Success outcome:** The account and profile are saved, and the user is signed in on Home.

1. The user submits an email address, username, display name, password, and password confirmation.
2. The system validates the details and creates the account.
3. The system loads the profile and opens Home.

**Extensions:** At step 2, the user can correct rejected details; if account creation cannot be confirmed, they should try logging in before registering again. At step 3, the user stays signed out if the profile cannot be loaded or email confirmation is required, and can log in later.

### Log In

**Actor:** Registered user. **Precondition:** Signed out. **Trigger:** The user submits their login details.

**Success outcome:** The user is signed in on Home, the account landing screen.

1. The user provides their email address and password.
2. The system verifies the credentials and loads the profile.
3. Home shows the user's display name and username. Workout features are not available yet.

**Extensions:** At step 2, invalid credentials, pending email confirmation, or a service failure leave the user signed out. The app explains what they can try next.

### Log Out

**Actor:** Signed-in user. **Precondition:** Home is open. **Trigger:** The user selects Log Out.

**Success outcome:** The app no longer holds the signed-in profile or session, and Log In is visible.

1. The user requests to log out.
2. The app clears the account details from the screen and returns to Log In.
3. The app ends the local session and attempts to revoke that session with the account service.

**Extension:** At step 3, a remote revocation failure does not restore the local session.

### Find an Exercise

The user browses the exercise library, filters by name, muscle group or equipment, and opens an exercise to see its description, muscles worked, equipment and picture.

### Create a Workout Plan

The user starts a blank or preset plan, names it, adds exercises with target sets and reps, and saves it to their list of plans.

### Edit a Workout Plan

The user changes a plan's name, exercises or targets, or deletes the plan. Workouts already logged from it are kept unchanged.

### Log a Workout

The user starts a workout from a plan, records the weight and reps of each set (with a rest timer between sets), and finishes. The system saves the workout and shows a summary with any new personal records.

### Review Workout History and Personal Records

The user views their past workouts, newest first, and opens one to see its sets. Their profile shows total workouts and their heaviest lift for each exercise.

### Send a Friend Request

The user enters another user's username. If the request is allowed, the system records it as pending and shows it in the other user's requests.

### Respond to a Friend Request

The user views pending friend requests and accepts or declines each one. Accepting makes the two users friends, so they can see each other's profiles, plans and recent workouts.

### Copy a Friend's Workout Plan

The user opens a friend's profile and copies one of their plans. The system saves an independent copy to the user's own plans, named to show where it came from.

---

## Minimal Viable Product (MVP)

| Team Member | MVP Use Case |
|-------------|--------------|
| Jirat | Use Case |
| Demeng | Register an Account, Log In, Log Out |
| Eric | Use Case |
| David | Use Case |
| Amgalan | Use Case |

---

## Entities

### User
Instance Attributes:
- UserID (UUID)
- Username (String, unique)
- Display Name (String)
- Workout Plans (List of WorkoutPlan)
- Workout History (List of Workout)

### Exercise
Instance Attributes:
- ExerciseID (String, from the WorkoutX API)
- Name (String)
- Description (String)
- Main Muscle Group (MuscleGroup enum)
- Muscles Worked (List of MuscleGroup)
- Equipment (String)
- Image URL (String, optional)

### PlannedExercise
Instance Attributes:
- Exercise (Exercise)
- Target Sets (int)
- Target Reps (int)
- Target Weight (double, kg, optional)

### WorkoutPlan
Instance Attributes:
- WorkoutPlanID (UUID)
- Name (String)
- UserOwner ID (UUID)
- Exercises (ordered List of PlannedExercise)
- WorkoutCompleted (boolean) :: assuming that we're going with you just clicking complete after u finish the workout (no individual completion tracking)
- Is Preset (boolean)
- Copied From User ID (UUID, optional)

### ExerciseSet
Instance Attributes:
- Weight (double, kg)
- Reps (int)

### LoggedExercise
Instance Attributes:
- Exercise (Exercise)
- Completed Sets (List of ExerciseSet)

### Workout
Instance Attributes:
- WorkoutID (UUID)
- UserOwner ID (UUID)
- Plan ID (UUID)
- Plan Name (String)
- Date (LocalDate)
- Exercises (List of LoggedExercise)

### FriendRequest
Instance Attributes:
- FriendReqID (UUID)
- Sender ID (UUID)
- Target ID (UUID)
- Status (RequestStatus enum: PENDING, ACCEPTED, DECLINED, CANCELLED)
- Created At (Instant)

### Friendship
Instance Attributes:
- User A (UUID, always the smaller of the two IDs)
- User B (UUID, always the larger of the two IDs)
- Since (Instant)

---

## API Usage

- [Supabase Auth](https://supabase.com/docs/guides/auth/passwords) handles signup, login, and logout.
- [Supabase Data REST](https://supabase.com/docs/guides/api) loads the signed-in user's profile.
- [WorkoutX](https://workoutxapp.com/docs.html) is planned for exercise lookup.

The hosted Supabase Auth settings endpoint responded successfully. Hosted account flows and authenticated profile reads remain unverified.

---

## Design Diagrams

### UML Class Diagram

### Sequence Diagrams

---

## Development setup

Install JDK 25 (LTS) and open `pom.xml` as a Maven project. The Maven wrapper downloads Maven automatically.

- `./mvnw test`: check style and run unit tests.
- `./mvnw verify`: also build the JAR and generate coverage at `target/site/jacoco/index.html`.

On Windows PowerShell, use `.\mvnw.cmd` instead of `./mvnw`.
Tests belong in `src/test/java`. GitHub Actions runs the checks on pull requests and pushes to `main`.

## How to Run

With JDK 25 on your `PATH`, run this from the repository root, or run `app.WorkoutManagerApp` from your Maven-enabled IDE:

```sh
./mvnw compile org.codehaus.mojo:exec-maven-plugin:3.6.3:exec -Dexec.executable=java '-Dexec.args=-cp %classpath app.WorkoutManagerApp'
```

The app uses the team's demo Supabase project by default. To use another isolated demo project, apply the [profile migration](supabase/migrations/20261123190718_account_profiles.sql), enable email signup with an 8-character password minimum, and disable email confirmation for automatic sign-in after signup. Set both `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` in the launcher environment. Leave both unset to use the demo project.
