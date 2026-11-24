package data_access;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import entity.User;
import use_case.account.AccountProfile;
import use_case.following.FollowingDataAccessInterface;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static data_access.SupabaseJson.id;
import static data_access.SupabaseJson.object;
import static data_access.SupabaseJson.parse;
import static data_access.SupabaseJson.string;

public final class SupabaseFollowingDao implements FollowingDataAccessInterface {
    private final SupabaseClient client;
    private final InMemorySessionStore session;

    public SupabaseFollowingDao(SupabaseClient client, InMemorySessionStore session) {
        this.client = client;
        this.session = session;
    }

    @Override
    public List<AccountProfile> list(UUID viewerId) throws RequestFailed {
        return readProfiles(post(viewerId, "/rest/v1/rpc/list_following", new JsonObject()));
    }

    @Override
    public Optional<AccountProfile> findByUsername(UUID viewerId, String username) throws RequestFailed {
        final JsonObject body = new JsonObject();
        body.addProperty("target_username", username);
        final List<AccountProfile> matches = readProfiles(post(viewerId, "/rest/v1/rpc/find_follow_profile", body));
        if (matches.size() > 1 || !matches.isEmpty() && !username.equals(matches.getFirst().username())) {
            throw new RequestFailed();
        }
        return matches.stream().findFirst();
    }

    @Override
    public SaveResult save(UUID viewerId, UUID followedId) throws RequestFailed {
        final JsonObject body = new JsonObject();
        body.addProperty("followed_id", followedId.toString());
        final HttpResponse<String> response = post(viewerId, "/rest/v1/follows", body);
        if (response.statusCode() == 201) {
            return SaveResult.SAVED;
        }
        if (response.statusCode() == 409 && "23505".equals(string(object(parse(response.body())), "code"))) {
            return SaveResult.ALREADY_FOLLOWING;
        }
        throw new RequestFailed();
    }

    private HttpResponse<String> post(UUID viewerId, String path, JsonObject body) throws RequestFailed {
        final String token = session.accessTokenFor(viewerId).orElseThrow(RequestFailed::new);
        try {
            return client.post(path, body, token);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            throw new RequestFailed();
        } catch (IOException ignored) {
            throw new RequestFailed();
        }
    }

    private static List<AccountProfile> readProfiles(HttpResponse<String> response) throws RequestFailed {
        final JsonElement rows = parse(response.body());
        if (response.statusCode() != 200 || rows == null || !rows.isJsonArray()) {
            throw new RequestFailed();
        }
        final List<AccountProfile> profiles = new ArrayList<>();
        for (JsonElement value : rows.getAsJsonArray()) {
            final JsonObject row = object(value);
            final UUID userId = id(row);
            final String username = string(row, "username");
            final String displayName = string(row, "display_name");
            if (userId == null || !User.isValidUsername(username) || !User.isValidDisplayName(displayName)) {
                throw new RequestFailed();
            }
            profiles.add(new AccountProfile(userId, username, displayName));
        }
        return List.copyOf(profiles);
    }
}
