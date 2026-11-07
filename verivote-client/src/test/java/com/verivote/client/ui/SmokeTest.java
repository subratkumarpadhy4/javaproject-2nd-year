package com.verivote.client.ui;

import com.verivote.client.crypto.PinnedKeys;
import com.verivote.client.http.ApiClient;
import com.verivote.client.screen.CastScreen;
import com.verivote.client.screen.LoginScreen;
import com.verivote.client.screen.TokenScreen;
import com.verivote.client.screen.VerifyScreen;
import com.verivote.client.state.SessionState;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@Disabled("Headless JavaFX smoke test — run manually via `mvn javafx:run`")
class SmokeTest {

    @Test
    @DisplayName("Construct all screens without crash")
    void constructScreens() throws Exception {
        ApiClient api = new ApiClient("http://localhost:8080");
        SessionState state = new SessionState();
        PinnedKeys pinned = new PinnedKeys(null, null);

        LoginScreen loginScreen = new LoginScreen(api, token -> {});
        assertNotNull(loginScreen);

        TokenScreen tokenScreen = new TokenScreen(api, state, () -> {}, () -> {});
        assertNotNull(tokenScreen);

        CastScreen castScreen = new CastScreen(api, state, pinned, () -> {}, () -> {});
        assertNotNull(castScreen);

        VerifyScreen verifyScreen = new VerifyScreen(api, state, pinned, () -> {});
        assertNotNull(verifyScreen);
    }
}
