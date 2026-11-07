package com.verivote.client.screen;

import com.verivote.client.crypto.ClientCrypto;
import com.verivote.client.crypto.PinnedKeys;
import com.verivote.client.http.ApiClient;
import com.verivote.client.state.SessionState;
import com.verivote.client.ui.UiHelpers;
import com.verivote.core.encoding.Base64Codec;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.security.PublicKey;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class CastScreen extends VBox {

    public record CandidateOption(int id, String name, String party) {
        @Override
        public String toString() {
            return name + " (" + party + ")";
        }
    }

    private final ApiClient api;
    private final SessionState state;
    private final PinnedKeys pinnedKeys;
    private final Runnable onVoteCast;
    private final Runnable onBack;

    private final ComboBox<CandidateOption> candidateBox = new ComboBox<>();
    private final Button castButton = UiHelpers.createPrimaryButton("Cast Vote");
    private final Button verifyButton = UiHelpers.createPrimaryButton("Proceed to Verify");
    private final Button backButton = UiHelpers.createSecondaryButton("Back");
    private final Label statusLabel = new Label();
    private final Label receiptLabel = new Label();

    private static final long ELECTION_ID = 1L;

    public CastScreen(ApiClient api, SessionState state, PinnedKeys pinnedKeys, Runnable onVoteCast, Runnable onBack) {
        this.api = api;
        this.state = state;
        this.pinnedKeys = pinnedKeys;
        this.onVoteCast = onVoteCast;
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
        Label title = UiHelpers.createHeader("Official Ballot");
        Label subtitle = UiHelpers.createSubHeader("Select your preferred candidate:");

        candidateBox.getItems().setAll(
                new CandidateOption(1, "Alice", "Party A"),
                new CandidateOption(2, "Bob", "Party B"),
                new CandidateOption(3, "Charlie", "Party C")
        );
        candidateBox.getSelectionModel().selectFirst();
        candidateBox.setMaxWidth(Double.MAX_VALUE);

        statusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");
        statusLabel.setWrapText(true);

        receiptLabel.setStyle("-fx-text-fill: #0f172a; -fx-font-family: monospace; -fx-font-size: 12px;");
        receiptLabel.setWrapText(true);
        receiptLabel.setVisible(false);
        receiptLabel.setManaged(false);

        castButton.setMaxWidth(Double.MAX_VALUE);
        castButton.setOnAction(e -> handleCastVote());

        verifyButton.setMaxWidth(Double.MAX_VALUE);
        verifyButton.setVisible(false);
        verifyButton.setManaged(false);
        verifyButton.setOnAction(e -> onVoteCast.run());

        backButton.setMaxWidth(Double.MAX_VALUE);
        backButton.setOnAction(e -> onBack.run());

        card.getChildren().addAll(
                title, subtitle,
                new Label("Candidate:"), candidateBox,
                statusLabel, receiptLabel,
                castButton, verifyButton, backButton
        );
        getChildren().add(card);
    }

    private void handleCastVote() {
        CandidateOption selected = candidateBox.getValue();
        if (selected == null) {
            statusLabel.setText("Please select a candidate.");
            return;
        }

        statusLabel.setText("");
        castButton.setDisable(true);

        CompletableFuture.runAsync(() -> {
            try {
                // 1. Generate ballot ID
                byte[] ballotId = ClientCrypto.randomBytes32();
                state.setBallotId(ballotId);

                // 2. Obtain tally public key (from pinned keys or server pubkey)
                PublicKey tallyKey = (pinnedKeys != null) ? pinnedKeys.tallyPub() : null;
                byte[] ciphertext;
                if (tallyKey != null) {
                    ciphertext = ClientCrypto.encryptVote(tallyKey, ELECTION_ID, selected.id(), ballotId);
                } else {
                    ApiClient.PubkeyResponse pubResp = api.getTallyPubkey();
                    byte[] spki = Base64Codec.decode(pubResp.publicKeySpki());
                    ciphertext = ClientCrypto.encryptVote(spki, ELECTION_ID, selected.id(), ballotId);
                }

                // 3. Client nonce
                byte[] clientNonce = ClientCrypto.randomBytes32();

                // 4. POST /api/vote
                ApiClient.VoteResponse voteResp = api.castVote(
                        ciphertext,
                        state.getTokenPlain(),
                        state.getSignature(),
                        clientNonce
                );

                state.setReceiptHash(voteResp.receiptHash());
                state.setBlockHeight(voteResp.blockHeight());

                Platform.runLater(() -> {
                    castButton.setVisible(false);
                    castButton.setManaged(false);
                    candidateBox.setDisable(true);

                    receiptLabel.setText("Vote cast successfully!\n\nReceipt Hash:\n" +
                            voteResp.receiptHash() + "\n\nBlock Height: #" + voteResp.blockHeight());
                    receiptLabel.setVisible(true);
                    receiptLabel.setManaged(true);

                    verifyButton.setVisible(true);
                    verifyButton.setManaged(true);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    castButton.setDisable(false);
                    statusLabel.setText("Vote submission failed: " + ex.getMessage());
                });
            }
        });
    }
}
