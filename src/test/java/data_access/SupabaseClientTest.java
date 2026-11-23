package data_access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SupabaseClientTest {
    private static final String URL = "SUPABASE_URL";
    private static final String KEY = "SUPABASE_PUBLISHABLE_KEY";

    @Test
    void configurationUsesValidatedDefaultsAndSameNamedOverrides() {
        assertDoesNotThrow(() -> SupabaseClient.fromConfiguration(Map.of(), defaults()));
        assertDoesNotThrow(() -> SupabaseClient.fromConfiguration(
                Map.of(URL, "https://override-fixture.supabase.co/", KEY, "sb_publishable_override-fixture"),
                new Properties()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sb_secret_test-fixture", "eyJrole.service_role.signature", "", "sb_publishable_"})
    void configurationRejectsPrivilegedOrInvalidOverridesWithoutExposingValues(String key) {
        final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> SupabaseClient.fromConfiguration(Map.of(KEY, key), defaults()));
        assertEquals("SUPABASE_PUBLISHABLE_KEY must be a Supabase publishable key.", failure.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://fixture.supabase.co", "https://supabase.co", "https://fixture.supabase.co.example.invalid",
            "https://fixture.supabase.co@127.0.0.1", "https://fixture.supabase.co/rest",
            "https://fixture.supabase.co?x=y"
    })
    void configurationOnlyAcceptsAnHttpsSupabaseOrigin(String url) {
        final IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> SupabaseClient.fromConfiguration(Map.of(URL, url), defaults()));
        assertFalse(failure.getMessage().contains(url));
    }

    @Test
    void authenticatedRequestNeitherFollowsRedirectsNorAllowsAnAbsoluteEndpoint()
            throws IOException, InterruptedException {
        try (LoopbackSupabase origin = new LoopbackSupabase(); LoopbackSupabase other = new LoopbackSupabase()) {
            final SupabaseClient client = origin.client(Duration.ofSeconds(3));
            origin.redirectTo(other.origin().resolve("/different-origin"));
            assertEquals(307, client.get("/same-origin", "test.user.token").statusCode());
            final var call = origin.takeCall();
            assertEquals(LoopbackSupabase.API_KEY, call.apiKey());
            assertEquals("Bearer test.user.token", call.authorization());
            assertThrows(IllegalArgumentException.class, () -> client.get(other.origin().toString(), null));
            assertThrows(IllegalArgumentException.class, () -> client.get("//" + other.origin().getAuthority(), null));
            assertThrows(IllegalArgumentException.class, () -> client.get("/same-origin", LoopbackSupabase.API_KEY));
            assertNull(other.unexpectedCall());
            assertNull(origin.unexpectedCall());
        }
    }

    private static Properties defaults() {
        final Properties defaults = new Properties();
        defaults.setProperty(URL, "https://fixture.supabase.co");
        defaults.setProperty(KEY, LoopbackSupabase.API_KEY);
        return defaults;
    }
}
