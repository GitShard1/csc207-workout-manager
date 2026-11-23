package data_access;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import entity.User;
import use_case.account.AccountProfileRules;
import use_case.login.LogInAccountGateway;
import use_case.logout.LogOutSessionGateway;
import use_case.register.RegisterAccountGateway;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpResponse;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SupabaseAccountDao implements RegisterAccountGateway, LogInAccountGateway,
        LogOutSessionGateway {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private static final System.Logger LOGGER = System.getLogger(SupabaseAccountDao.class.getName());

    private final SupabaseClient client;
    private volatile SignedIn signedIn;

    public SupabaseAccountDao(SupabaseClient client) {
        this.client = Objects.requireNonNull(client);
    }

    Optional<User> currentUser() {
        final SignedIn current = signedIn;
        return current == null ? Optional.empty() : Optional.of(current.user);
    }

    @Override
    public RegisterAccountGateway.Result register(String email, String username, String displayName, String password) {
        final JsonObject body = credentials(email, password);
        final JsonObject metadata = new JsonObject();
        metadata.addProperty("username", username);
        metadata.addProperty("display_name", displayName);
        body.add("data", metadata);

        final HttpResponse<String> auth;
        try {
            auth = client.post("/auth/v1/signup", body, null);
        } catch (HttpConnectTimeoutException | ConnectException | UnknownHostException ignored) {
            return RegisterAccountGateway.Failure.RETRYABLE_FAILURE;
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return RegisterAccountGateway.Failure.SIGNUP_UNCERTAIN;
        } catch (IOException ignored) {
            return RegisterAccountGateway.Failure.SIGNUP_UNCERTAIN;
        }

        final JsonObject result = object(parse(auth.body()));
        if (auth.statusCode() != 200) {
            return registrationFailure(auth.statusCode(), result, username);
        }
        return switch (establishSession(result)) {
            case ESTABLISHED -> new RegisterAccountGateway.Success(signedIn.user);
            case INVALID_AUTH_RESPONSE -> RegisterAccountGateway.Failure.SIGNUP_UNCERTAIN;
            case NO_USABLE_SESSION -> RegisterAccountGateway.Failure.NO_USABLE_SESSION;
            case PROFILE_FAILURE -> RegisterAccountGateway.Failure.ACCOUNT_CREATED_PROFILE_UNAVAILABLE;
        };
    }

    @Override
    public LogInAccountGateway.Result logIn(String email, String password) {
        final HttpResponse<String> auth;
        try {
            auth = client.post("/auth/v1/token?grant_type=password", credentials(email, password), null);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return LogInAccountGateway.Failure.RETRYABLE_FAILURE;
        } catch (IOException ignored) {
            return LogInAccountGateway.Failure.RETRYABLE_FAILURE;
        }

        if (auth.statusCode() != 200) {
            return loginFailure(auth);
        }
        return switch (establishSession(object(parse(auth.body())))) {
            case ESTABLISHED -> new LogInAccountGateway.Success(signedIn.user);
            case NO_USABLE_SESSION -> LogInAccountGateway.Failure.NO_USABLE_SESSION;
            case INVALID_AUTH_RESPONSE, PROFILE_FAILURE -> LogInAccountGateway.Failure.RETRYABLE_FAILURE;
        };
    }

    private static LogInAccountGateway.Failure loginFailure(HttpResponse<String> auth) {
        if (!isClientError(auth.statusCode())) {
            return LogInAccountGateway.Failure.RETRYABLE_FAILURE;
        }
        return switch (string(object(parse(auth.body())), "error_code")) {
            case "invalid_credentials", "user_not_found" -> LogInAccountGateway.Failure.BAD_CREDENTIALS;
            case "email_not_confirmed" -> LogInAccountGateway.Failure.NO_USABLE_SESSION;
            case null, default -> LogInAccountGateway.Failure.RETRYABLE_FAILURE;
        };
    }

    @Override
    public void logOutCurrentSession() {
        final SignedIn previous = signedIn;
        signedIn = null;
        if (previous == null) {
            return;
        }
        try {
            final var reply = client.post("/auth/v1/logout?scope=local", null, previous.accessToken);
            if (reply.statusCode() != 204) {
                LOGGER.log(System.Logger.Level.WARNING, "Remote session revocation returned HTTP {0}.",
                        reply.statusCode());
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            LOGGER.log(System.Logger.Level.WARNING, "Remote session revocation was interrupted.");
        } catch (IOException ignored) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not confirm remote session revocation.");
        }
    }

    private RegisterAccountGateway.Failure registrationFailure(int status, JsonObject response, String username) {
        final String code = string(response, "error_code");
        // GoTrue rolls back this specific insert failure but hides the failed constraint.
        if (status == 500 && "unexpected_failure".equals(code)
                && "Database error saving new user".equals(string(response, "msg"))) {
            return AccountProfileRules.isCanonicalUsername(username) && usernameUnavailable(username)
                    ? RegisterAccountGateway.Failure.USERNAME_TAKEN : RegisterAccountGateway.Failure.RETRYABLE_FAILURE;
        }
        if (status == 408 || status >= 500) {
            return RegisterAccountGateway.Failure.SIGNUP_UNCERTAIN;
        }
        if (!isClientError(status)) {
            return RegisterAccountGateway.Failure.RETRYABLE_FAILURE;
        }
        return switch (code) {
            case "email_address_invalid" -> RegisterAccountGateway.Failure.INVALID_EMAIL;
            case "weak_password" -> RegisterAccountGateway.Failure.PASSWORD_REJECTED;
            case "user_already_exists", "email_exists", "validation_failed" ->
                    RegisterAccountGateway.Failure.ACCOUNT_UNAVAILABLE;
            case null, default -> RegisterAccountGateway.Failure.RETRYABLE_FAILURE;
        };
    }

    private boolean usernameUnavailable(String username) {
        final JsonObject body = new JsonObject();
        body.addProperty("candidate", username);
        try {
            final HttpResponse<String> response = client.post("/rest/v1/rpc/username_is_available", body, null);
            final JsonElement value = parse(response.body());
            return response.statusCode() == 200 && value != null && value.isJsonPrimitive()
                    && value.getAsJsonPrimitive().isBoolean() && !value.getAsBoolean();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException ignored) {
            return false;
        }
    }

    private SessionOutcome establishSession(JsonObject auth) {
        if (auth == null) {
            return SessionOutcome.INVALID_AUTH_RESPONSE;
        }
        final UUID id = userId(object(auth.get("user")));
        final String accessToken = string(auth, "access_token");
        if (!SupabaseClient.isUserBearer(accessToken)) {
            return id != null || userId(auth) != null ? SessionOutcome.NO_USABLE_SESSION
                    : SessionOutcome.INVALID_AUTH_RESPONSE;
        }
        if (id == null) {
            return SessionOutcome.INVALID_AUTH_RESPONSE;
        }

        try {
            final User user = loadProfile(id, accessToken);
            if (user == null) {
                return SessionOutcome.PROFILE_FAILURE;
            }
            signedIn = new SignedIn(accessToken, user);
            return SessionOutcome.ESTABLISHED;
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return SessionOutcome.PROFILE_FAILURE;
        } catch (IOException ignored) {
            return SessionOutcome.PROFILE_FAILURE;
        }
    }

    private User loadProfile(UUID id, String accessToken) throws IOException, InterruptedException {
        final HttpResponse<String> response = client.get(
                "/rest/v1/profiles?select=id,username,display_name&id=eq." + id, accessToken);
        if (response.statusCode() != 200) {
            return null;
        }
        final JsonElement rows = parse(response.body());
        if (rows == null || !rows.isJsonArray() || rows.getAsJsonArray().size() != 1) {
            return null;
        }
        final JsonObject profile = object(rows.getAsJsonArray().get(0));
        final String username = string(profile, "username");
        final String displayName = string(profile, "display_name");
        if (!id.equals(userId(profile)) || !AccountProfileRules.isCanonicalUsername(username)
                || !AccountProfileRules.hasDisplayName(displayName)) {
            return null;
        }
        return new User(id, username, displayName);
    }

    private static boolean isClientError(int status) {
        return status >= 400 && status < 500;
    }

    private static JsonObject credentials(String email, String password) {
        final JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        return body;
    }

    private static JsonElement parse(String body) {
        try {
            return JSON.fromJson(body, JsonElement.class);
        } catch (JsonParseException ignored) {
            return null;
        }
    }

    private static JsonObject object(JsonElement value) {
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private static String string(JsonObject object, String property) {
        final JsonElement value = object == null ? null : object.get(property);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }

    private static UUID userId(JsonObject object) {
        final String value = string(object, "id");
        if (value == null || value.length() != 36) {
            return null;
        }
        try {
            final UUID id = UUID.fromString(value);
            return id.toString().equalsIgnoreCase(value) ? id : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private enum SessionOutcome {
        ESTABLISHED,
        INVALID_AUTH_RESPONSE,
        NO_USABLE_SESSION,
        PROFILE_FAILURE
    }

    private static final class SignedIn {
        private final String accessToken;
        private final User user;

        private SignedIn(String accessToken, User user) {
            this.accessToken = accessToken;
            this.user = user;
        }
    }
}
