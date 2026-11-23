package data_access;

import com.google.gson.JsonParser;
import entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import use_case.login.LogInAccountGateway;
import use_case.logout.LogOutSessionGateway;
import use_case.register.RegisterAccountGateway;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SupabaseAccountDaoTest {
    private static final String EMAIL = "loopback@example.invalid";
    private static final String PASSWORD = " test\"password ";
    private static final String USERNAME = "first_user";
    private static final String DISPLAY_NAME = "First User";
    private static final UUID FIRST_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SECOND_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String FIRST_TOKEN = "first.user.token";
    private static final String SECOND_TOKEN = "second.user.token";
    private static final String HIDDEN_INSERT_ERROR =
            """
            {"code":500,"error_code":"unexpected_failure","msg":"Database error saving new user"}
            """;

    private LoopbackSupabase server;
    private SupabaseClient client;
    private SupabaseAccountDao dao;

    @BeforeEach
    void setUp() throws IOException {
        server = new LoopbackSupabase();
        client = server.client(Duration.ofSeconds(3));
        dao = new SupabaseAccountDao(client);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void registerSendsMetadataAndPublishesOnlyAfterTheMatchingProfile() throws InterruptedException {
        final AtomicBoolean profileStartedSignedOut = new AtomicBoolean();
        server.enqueue(200, session(FIRST_ID, FIRST_TOKEN));
        server.enqueue(200, profile(FIRST_ID, USERNAME, DISPLAY_NAME),
                () -> profileStartedSignedOut.set(dao.currentUser().isEmpty()));

        final var result = assertInstanceOf(RegisterAccountGateway.Success.class, register());
        assertTrue(profileStartedSignedOut.get());
        assertSame(result.user(), dao.currentUser().orElseThrow());
        assertProfile(result.user(), FIRST_ID, USERNAME, DISPLAY_NAME);

        final var signup = server.takeCall();
        assertCall(signup, "POST", "/auth/v1/signup", null);
        assertCredentials(signup);
        final var metadata = JsonParser.parseString(signup.body()).getAsJsonObject().getAsJsonObject("data");
        assertEquals(USERNAME, metadata.get("username").getAsString());
        assertEquals(DISPLAY_NAME, metadata.get("display_name").getAsString());
        assertProfileCall(server.takeCall(), FIRST_ID, FIRST_TOKEN);
        assertNull(server.unexpectedCall());
    }

    @Test
    void logInAndLogOutUseOnlyTheCurrentInstancesUserAndToken() throws InterruptedException {
        final SupabaseAccountDao other = new SupabaseAccountDao(client);
        server.enqueue(200, session(FIRST_ID, FIRST_TOKEN));
        server.enqueue(200, profile(FIRST_ID, USERNAME, DISPLAY_NAME));
        server.enqueue(200, session(SECOND_ID, SECOND_TOKEN));
        server.enqueue(200, profile(SECOND_ID, "second_user", "Second User"));
        server.enqueue(204, "");
        server.enqueue(204, "");

        assertInstanceOf(LogInAccountGateway.Success.class, dao.logIn(EMAIL, PASSWORD));
        assertTrue(other.currentUser().isEmpty());
        assertEquals(LogOutSessionGateway.Revocation.NOT_NEEDED, other.logOutCurrentSession());
        assertInstanceOf(LogInAccountGateway.Success.class, other.logIn(EMAIL, PASSWORD));
        assertEquals(FIRST_ID, dao.currentUser().orElseThrow().getUserID());
        assertProfile(other.currentUser().orElseThrow(), SECOND_ID, "second_user", "Second User");
        assertEquals(LogOutSessionGateway.Revocation.REVOKED, dao.logOutCurrentSession());
        assertTrue(dao.currentUser().isEmpty());
        assertTrue(other.currentUser().isPresent());
        assertEquals(LogOutSessionGateway.Revocation.NOT_NEEDED, dao.logOutCurrentSession());
        assertEquals(LogOutSessionGateway.Revocation.REVOKED, other.logOutCurrentSession());

        assertLoginCall(server.takeCall());
        assertProfileCall(server.takeCall(), FIRST_ID, FIRST_TOKEN);
        assertLoginCall(server.takeCall());
        assertProfileCall(server.takeCall(), SECOND_ID, SECOND_TOKEN);
        assertLogoutCall(server.takeCall(), FIRST_TOKEN);
        assertLogoutCall(server.takeCall(), SECOND_TOKEN);
        assertNull(server.unexpectedCall());
    }

    @ParameterizedTest
    @ValueSource(strings = {"<html>error</html>", "{user:{}}", "[]", "{\"access_token\":\"usable-looking\"}"})
    void malformedAuthResponseCannotPublishAUser(String body) throws InterruptedException {
        server.enqueue(200, body);
        server.enqueue(200, body);
        assertEquals(LogInAccountGateway.Failure.RETRYABLE_FAILURE, dao.logIn(EMAIL, PASSWORD));
        assertEquals(RegisterAccountGateway.Failure.SIGNUP_UNCERTAIN, register());
        assertTrue(dao.currentUser().isEmpty());
        assertLoginCall(server.takeCall());
        assertCall(server.takeCall(), "POST", "/auth/v1/signup", null);
        assertNull(server.unexpectedCall());
    }

    @Test
    void userWithoutAUsableSessionDoesNotCauseAProfileRequest() throws InterruptedException {
        server.enqueue(200, "{\"id\":\"" + FIRST_ID + "\"}");
        server.enqueue(200, session(FIRST_ID, LoopbackSupabase.API_KEY));
        assertEquals(RegisterAccountGateway.Failure.NO_USABLE_SESSION, register());
        assertEquals(LogInAccountGateway.Failure.NO_USABLE_SESSION, dao.logIn(EMAIL, PASSWORD));
        assertTrue(dao.currentUser().isEmpty());
        assertCall(server.takeCall(), "POST", "/auth/v1/signup", null);
        assertLoginCall(server.takeCall());
        assertNull(server.unexpectedCall());
    }

    private static Stream<String> invalidProfiles() {
        return Stream.of("[]", "null", "[null]", "[{}, {}]", "{\"username\":\"first_user\"}",
                profile(SECOND_ID, USERNAME, DISPLAY_NAME),
                profile(FIRST_ID, "First_user", DISPLAY_NAME),
                profile(FIRST_ID, USERNAME, "\\u00a0"),
                "[{\"id\":\"%s\",\"username\":12345,\"display_name\":\"First\"}]".formatted(FIRST_ID));
    }

    @ParameterizedTest
    @MethodSource("invalidProfiles")
    void invalidProfileLeavesTheProvisionalSessionUnpublished(String body) throws InterruptedException {
        server.enqueue(200, session(FIRST_ID, FIRST_TOKEN));
        server.enqueue(200, body);
        assertEquals(LogInAccountGateway.Failure.RETRYABLE_FAILURE, dao.logIn(EMAIL, PASSWORD));
        assertTrue(dao.currentUser().isEmpty());
        assertEquals(LogOutSessionGateway.Revocation.NOT_NEEDED, dao.logOutCurrentSession());
        assertLoginCall(server.takeCall());
        assertProfileCall(server.takeCall(), FIRST_ID, FIRST_TOKEN);
        assertNull(server.unexpectedCall());
    }

    @Test
    void signupWithProfileServiceFailureIsRetryableWithoutPublishingTheSession() throws InterruptedException {
        server.enqueue(200, session(FIRST_ID, FIRST_TOKEN));
        server.enqueue(503, "{\"message\":\"fixture provider detail\"}");
        assertEquals(RegisterAccountGateway.Failure.RETRYABLE_FAILURE, register());
        assertTrue(dao.currentUser().isEmpty());
        assertCall(server.takeCall(), "POST", "/auth/v1/signup", null);
        assertProfileCall(server.takeCall(), FIRST_ID, FIRST_TOKEN);
        assertNull(server.unexpectedCall());
    }

    @Test
    void loginDoesNotExposeProviderDetailsOrDistinguishUnknownEmailFromBadPassword() throws InterruptedException {
        server.enqueue(400, "{\"error_code\":\"invalid_credentials\",\"msg\":\"provider: wrong password\"}");
        server.enqueue(400, "{\"error_code\":\"invalid_credentials\",\"msg\":\"provider: unknown email\"}");
        server.enqueue(429, "{\"error_code\":\"over_request_rate_limit\"}");
        server.enqueue(400, "{\"error_code\":\"email_not_confirmed\"}");
        assertEquals(LogInAccountGateway.Failure.BAD_CREDENTIALS, dao.logIn(EMAIL, PASSWORD));
        assertEquals(LogInAccountGateway.Failure.BAD_CREDENTIALS, dao.logIn(EMAIL, PASSWORD));
        assertEquals(LogInAccountGateway.Failure.RETRYABLE_FAILURE, dao.logIn(EMAIL, PASSWORD));
        assertEquals(LogInAccountGateway.Failure.NO_USABLE_SESSION, dao.logIn(EMAIL, PASSWORD));
        assertTrue(dao.currentUser().isEmpty());
        for (int index = 0; index < 4; index++) {
            assertLoginCall(server.takeCall());
        }
        assertNull(server.unexpectedCall());
    }

    @ParameterizedTest
    @CsvSource({
        "email_address_invalid, INVALID_EMAIL",
        "weak_password, PASSWORD_REJECTED",
        "email_exists, ACCOUNT_UNAVAILABLE",
        "user_already_exists, ACCOUNT_UNAVAILABLE"
    })
    void signupMapsExplicitRejectionsWithoutExposingProviderTextOrProbingUsername(
            String code, RegisterAccountGateway.Failure expected) throws InterruptedException {
        server.enqueue(422, """
                {"error_code":"%s","msg":"private provider diagnostic"}
                """.formatted(code));
        assertEquals(expected, register());
        assertTrue(dao.currentUser().isEmpty());
        assertCall(server.takeCall(), "POST", "/auth/v1/signup", null);
        assertNull(server.unexpectedCall());
    }

    @Test
    void signupMapsThrottlingAndUnconfirmedServerFailureWithoutProbingUsername()
            throws InterruptedException {
        server.enqueue(429, "{\"error_code\":\"over_request_rate_limit\"}");
        server.enqueue(500, "{\"error_code\":\"unexpected_failure\",\"msg\":\"Other provider failure\"}");
        server.enqueue(504, "");
        assertEquals(RegisterAccountGateway.Failure.RETRYABLE_FAILURE, register());
        assertEquals(RegisterAccountGateway.Failure.SIGNUP_UNCERTAIN, register());
        assertEquals(RegisterAccountGateway.Failure.SIGNUP_UNCERTAIN, register());
        assertTrue(dao.currentUser().isEmpty());
        for (int index = 0; index < 3; index++) {
            assertCall(server.takeCall(), "POST", "/auth/v1/signup", null);
        }
        assertNull(server.unexpectedCall());
    }

    @Test
    void signupWriteTimeoutIsUncertainAndSendsOnlyOneSignup() throws InterruptedException {
        dao = new SupabaseAccountDao(server.client(Duration.ofMillis(700)));
        server.stallBody(200, session(FIRST_ID, FIRST_TOKEN), () -> { });
        assertEquals(RegisterAccountGateway.Failure.SIGNUP_UNCERTAIN, register());
        assertTrue(dao.currentUser().isEmpty());
        assertCall(server.takeCall(), "POST", "/auth/v1/signup", null);
        assertNull(server.unexpectedCall());
    }

    @Test
    void hiddenDatabaseRejectionWithExactUnavailableBooleanMapsToUsernameTaken() throws InterruptedException {
        server.enqueue(500, HIDDEN_INSERT_ERROR);
        server.enqueue(200, "\n false \t");
        assertEquals(RegisterAccountGateway.Failure.USERNAME_TAKEN, register());
        assertTrue(dao.currentUser().isEmpty());
        assertCall(server.takeCall(), "POST", "/auth/v1/signup", null);
        final var rpc = server.takeCall();
        assertCall(rpc, "POST", "/rest/v1/rpc/username_is_available", null);
        final var body = JsonParser.parseString(rpc.body()).getAsJsonObject();
        assertEquals(1, body.size());
        assertEquals(USERNAME, body.get("candidate").getAsString());
        assertNull(server.unexpectedCall());
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "\"false\"", "[false]", "{\"available\":false}", "false false", "FALSE", "null"})
    void hiddenDatabaseRejectionWithoutExactUnavailableBooleanRemainsRetryable(String value)
            throws InterruptedException {
        server.enqueue(500, HIDDEN_INSERT_ERROR);
        server.enqueue(200, value);
        assertEquals(RegisterAccountGateway.Failure.RETRYABLE_FAILURE, register());
        assertTrue(dao.currentUser().isEmpty());
        assertCall(server.takeCall(), "POST", "/auth/v1/signup", null);
        assertCall(server.takeCall(), "POST", "/rest/v1/rpc/username_is_available", null);
        assertNull(server.unexpectedCall());
    }

    @Test
    void hiddenDatabaseRejectionWithFailedAvailabilityRequestRemainsRetryable() throws InterruptedException {
        server.enqueue(500, HIDDEN_INSERT_ERROR);
        server.enqueue(403, "false");
        assertEquals(RegisterAccountGateway.Failure.RETRYABLE_FAILURE, register());
        assertTrue(dao.currentUser().isEmpty());
        assertCall(server.takeCall(), "POST", "/auth/v1/signup", null);
        assertCall(server.takeCall(), "POST", "/rest/v1/rpc/username_is_available", null);
        assertNull(server.unexpectedCall());
    }

    @Test
    void failedLogoutDetachesUserBeforeRevocationAndDoesNotRestoreSession() throws InterruptedException {
        final AtomicBoolean clearedWhenRequestArrived = new AtomicBoolean();
        server.enqueue(200, session(FIRST_ID, FIRST_TOKEN));
        server.enqueue(200, profile(FIRST_ID, USERNAME, DISPLAY_NAME));
        server.enqueue(500, "{\"msg\":\"provider detail\"}",
                () -> clearedWhenRequestArrived.set(dao.currentUser().isEmpty()));
        assertInstanceOf(LogInAccountGateway.Success.class, dao.logIn(EMAIL, PASSWORD));
        assertFalse(dao.currentUser().isEmpty());
        assertEquals(LogOutSessionGateway.Revocation.FAILED, dao.logOutCurrentSession());
        assertTrue(clearedWhenRequestArrived.get());
        assertTrue(dao.currentUser().isEmpty());
        assertEquals(LogOutSessionGateway.Revocation.NOT_NEEDED, dao.logOutCurrentSession());
        assertLoginCall(server.takeCall());
        assertProfileCall(server.takeCall(), FIRST_ID, FIRST_TOKEN);
        assertLogoutCall(server.takeCall(), FIRST_TOKEN);
        assertNull(server.unexpectedCall());
    }

    @Test
    void logoutResponseBodyTimeoutStillLeavesTheUserSignedOut() throws InterruptedException {
        dao = new SupabaseAccountDao(server.client(Duration.ofMillis(700)));
        server.enqueue(200, session(FIRST_ID, FIRST_TOKEN));
        server.enqueue(200, profile(FIRST_ID, USERNAME, DISPLAY_NAME));
        server.stallBody(500, "{}", () -> { });
        assertInstanceOf(LogInAccountGateway.Success.class, dao.logIn(EMAIL, PASSWORD));
        assertEquals(LogOutSessionGateway.Revocation.FAILED, dao.logOutCurrentSession());
        assertTrue(dao.currentUser().isEmpty());
        assertLoginCall(server.takeCall());
        assertProfileCall(server.takeCall(), FIRST_ID, FIRST_TOKEN);
        assertLogoutCall(server.takeCall(), FIRST_TOKEN);
        assertNull(server.unexpectedCall());
    }

    private RegisterAccountGateway.Result register() {
        return dao.register(EMAIL, USERNAME, DISPLAY_NAME, PASSWORD);
    }

    private static void assertCall(LoopbackSupabase.Call call, String method, String path, String authorization) {
        assertEquals(method, call.method());
        assertEquals(path, call.path());
        assertEquals(LoopbackSupabase.API_KEY, call.apiKey());
        assertEquals(authorization, call.authorization());
    }

    private static void assertCredentials(LoopbackSupabase.Call call) {
        assertEquals("application/json", call.contentType());
        final var body = JsonParser.parseString(call.body()).getAsJsonObject();
        assertEquals(EMAIL, body.get("email").getAsString());
        assertEquals(PASSWORD, body.get("password").getAsString());
    }

    private static void assertProfile(User user, UUID id, String username, String displayName) {
        assertEquals(id, user.getUserID());
        assertEquals(username, user.getUsername());
        assertEquals(displayName, user.getDisplayName());
    }

    private static void assertLoginCall(LoopbackSupabase.Call call) {
        assertCall(call, "POST", "/auth/v1/token?grant_type=password", null);
        assertCredentials(call);
    }

    private static void assertProfileCall(LoopbackSupabase.Call call, UUID id, String token) {
        assertCall(call, "GET", "/rest/v1/profiles?select=id,username,display_name&id=eq." + id, "Bearer " + token);
        assertEquals("", call.body());
    }

    private static void assertLogoutCall(LoopbackSupabase.Call call, String token) {
        assertCall(call, "POST", "/auth/v1/logout?scope=local", "Bearer " + token);
        assertEquals("", call.body());
    }

    private static String session(UUID id, String token) {
        return """
                {"access_token":"%s","refresh_token":"unused-refresh-fixture","user":{"id":"%s"}}
                """.formatted(token, id);
    }

    private static String profile(UUID id, String username, String displayName) {
        return """
                [{"id":"%s","username":"%s","display_name":"%s"}]
                """.formatted(id, username, displayName);
    }
}
