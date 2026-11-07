package com.verivote.client.screen;

import com.verivote.client.crypto.ClientCrypto;
import com.verivote.client.http.ApiClient;
import com.verivote.client.state.SessionState;
import com.verivote.client.ui.UiHelpers;
import com.verivote.core.crypto.BlindSignatureService;
import com.verivote.core.encoding.Base64Codec;
import com.verivote.core.encoding.Hex;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;

public class TokenScreen extends VBox {

    private final ApiClient api;
    private final SessionState state;
    private final Runnable onContinue;
    private final Runnable onBack;

    private final Label statusLabel = new Label();
    private final Label infoLabel = new Label();
    private final Button issueButton = UiHelpers.createPrimaryButton("Issue Voting Token");
    private final Button continueButton = UiHelpers.createPrimaryButton("Continue to Ballot");
    private final Button backButton = UiHelpers.createSecondaryButton("Logout / Back");

    private static final long ELECTION_ID = 1L;

    public TokenScreen(ApiClient api, SessionState state, Runnable onContinue, Runnable onBack) {
        this.api = api;
        this.state = state;
        this.onContinue = onContinue;
        this.onBack = onBack;

        setAlignment(Pos.CENTER);
        setPadding(new Insets(30));
        setSpacing(20);
        setStyle("-fx-background-color: #f8fafc;");

        buildUi();
    }

    private void buildUi() {
        getChildren().clear();

        VBox card = UiHelpers.createCard();
        Label title = UiHelpers.createHeader("Token Issuance");
        Label subtitle = UiHelpers.createSubHeader("Request an anonymous blind-signed voting token");

        statusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");
        statusLabel.setWrapText(true);

        infoLabel.setStyle("-fx-text-fill: #16a34a; -fx-font-size: 13px; -fx-font-weight: bold;");
        infoLabel.setWrapText(true);

        issueButton.setMaxWidth(Double.MAX_VALUE);
        issueButton.setOnAction(e -> handleIssueToken());

        continueButton.setMaxWidth(Double.MAX_VALUE);
        continueButton.setVisible(false);
        continueButton.setManaged(false);
        continueButton.setOnAction(e -> onContinue.run());

        backButton.setMaxWidth(Double.MAX_VALUE);
        backButton.setOnAction(e -> onBack.run());

        card.getChildren().addAll(title, subtitle, statusLabel, infoLabel, issueButton, continueButton, backButton);
        getChildren().add(card);
    }

    private void handleIssueToken() {
        statusLabel.setText("");
        infoLabel.setText("");
        issueButton.setDisable(true);

        CompletableFuture.runAsync(() -> {
            try {
                // 1. Fetch server's blind-sign public key
                ApiClient.PubkeyResponse pubkeyResp = api.getTallyPubkey();
                byte[] pubSpki = Base64Codec.decode(pubkeyResp.publicKeySpki());
                BlindSignatureService.KeyPair kp = new BlindSignatureService.KeyPair(pubSpki, null);

                // 2. Generate random 32-byte token
                byte[] tokenPlain = ClientCrypto.randomBytes32();

                // 3. Build token preimage
                byte[] preimage = ClientCrypto.buildTokenPreimage(ELECTION_ID, tokenPlain);

                // 4. Blind
                BigInteger[] bf = new BigInteger[1];
                byte[] blinded = BlindSignatureService.blind(preimage, kp, new SecureRandom(), bf);

                // 5. POST /api/token/request
                ApiClient.TokenIssuanceResponse issuance = api.requestToken(state.getSessionToken(), blinded);
                byte[] signatureBytes = Base64Codec.decode(issuance.signature());

                // 6. Unblind
                byte[] unblinded = BlindSignatureService.unblind(signatureBytes, bf[0].toByteArray(), kp, 256);

                // 7. Verify unblinded signature client-side
                boolean valid = BlindSignatureService.verify(preimage, unblinded, kp);
                if (!valid) {
                    throw new IllegalStateException("Client verification failed: server returned an invalid blind signature!");
                }

                // 8. Store in session state
                state.setTokenPlain(tokenPlain);
                state.setSignature(unblinded);

                Platform.runLater(() -> {
                    issueButton.setDisable(true);
                    issueButton.setVisible(false);
                    issueButton.setManaged(false);

                    infoLabel.setText("Token successfully issued and verified!\nToken Hash: " +
                            Hex.encode(ClientCrypto.computeSpendHash(ELECTION_ID, tokenPlain)).substring(0, 16) + "...");
                    continueButton.setVisible(true);
                    continueButton.setManaged(true);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    issueButton.setDisable(false);
                    statusLabel.setText("Issuance failed: " + ex.getMessage());
                });
            }
        });
    }
}
