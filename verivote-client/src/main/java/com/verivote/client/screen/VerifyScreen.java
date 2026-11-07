package com.verivote.client.screen;

import com.verivote.client.crypto.ClientCrypto;
import com.verivote.client.crypto.PinnedKeys;
import com.verivote.client.http.ApiClient;
import com.verivote.client.state.SessionState;
import com.verivote.client.ui.UiHelpers;
import com.verivote.core.crypto.MerkleTree;
import com.verivote.core.crypto.MerkleVerifier;
import com.verivote.core.encoding.Hex;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.*;

public class VerifyScreen extends VBox {

    private final ApiClient api;
    private final SessionState state;
    private final PinnedKeys pinnedKeys;
    private final Runnable onBack;

    private final TextField receiptField = new TextField();
    private final Button verifyButton = UiHelpers.createPrimaryButton("Verify Vote (1 Click)");
    private final Button retryButton = UiHelpers.createSecondaryButton("Retry");
    private final Button backButton = UiHelpers.createSecondaryButton("Back to Ballot");

    private final VBox resultContainer = new VBox(10);
    private final Label stateIconLabel = new Label();
    private final Label stateTitleLabel = new Label();
    private final Label stateDetailsLabel = new Label();

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> retryTask;
    private int retryCount = 0;
    private static final int MAX_RETRIES = 30; // 5 minutes at 10s intervals

    public VerifyScreen(ApiClient api, SessionState state, PinnedKeys pinnedKeys, Runnable onBack) {
        this.api = api;
        this.state = state;
        this.pinnedKeys = pinnedKeys;
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
        Label title = UiHelpers.createHeader("Vote Verification");
        Label subtitle = UiHelpers.createSubHeader("Verify that your vote is cryptographically anchored in the ledger");

        if (state.getReceiptHash() != null && !state.getReceiptHash().isBlank()) {
            receiptField.setText(state.getReceiptHash());
        }
        receiptField.setPromptText("Receipt Hash (64-character hex)");
        receiptField.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        receiptField.setMaxWidth(Double.MAX_VALUE);

        verifyButton.setMaxWidth(Double.MAX_VALUE);
        verifyButton.setOnAction(e -> {
            stopRetryTimer();
            retryCount = 0;
            performVerification();
        });

        retryButton.setMaxWidth(Double.MAX_VALUE);
        retryButton.setVisible(false);
        retryButton.setManaged(false);
        retryButton.setOnAction(e -> {
            stopRetryTimer();
            performVerification();
        });

        backButton.setMaxWidth(Double.MAX_VALUE);
        backButton.setOnAction(e -> {
            stopRetryTimer();
            onBack.run();
        });

        resultContainer.setAlignment(Pos.CENTER);
        resultContainer.setPadding(new Insets(16));
        resultContainer.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 8;");
        resultContainer.setVisible(false);
        resultContainer.setManaged(false);

        stateIconLabel.setStyle("-fx-font-size: 32px;");
        stateTitleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        stateDetailsLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #475569; -fx-font-family: monospace;");
        stateDetailsLabel.setWrapText(true);

        resultContainer.getChildren().addAll(stateIconLabel, stateTitleLabel, stateDetailsLabel);

        card.getChildren().addAll(
                title, subtitle,
                new Label("Receipt Hash:"), receiptField,
                verifyButton, retryButton,
                resultContainer,
                backButton
        );
        getChildren().add(card);

        // If prefilled from cast screen, trigger verification automatically
        if (state.getReceiptHash() != null && !state.getReceiptHash().isBlank()) {
            Platform.runLater(this::performVerification);
        }
    }

    public void setReceiptHash(String hash) {
        receiptField.setText(hash);
    }

    private void performVerification() {
        String receipt = receiptField.getText().trim();
        if (receipt.isEmpty()) {
            showState(4, "Invalid Input", "Receipt hash must not be empty.", null);
            return;
        }

        verifyButton.setDisable(true);
        retryButton.setDisable(true);

        CompletableFuture.runAsync(() -> {
            try {
                // 1. GET /bb/verify/{receiptHash}
                ApiClient.VerifyResponse vResp;
                try {
                    vResp = api.verifyReceipt(receipt);
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        showState(4, "Receipt Not Found", "Server returned 404: Receipt hash was not found in the ledger database.", null);
                        verifyButton.setDisable(false);
                    });
                    return;
                }

                String status = vResp.status();
                if ("NOT_FOUND".equalsIgnoreCase(status)) {
                    Platform.runLater(() -> {
                        showState(4, "Receipt Not Found", "Receipt not found in ledger: " + receipt, null);
                        verifyButton.setDisable(false);
                    });
                    return;
                }

                long blockHeight = vResp.index() != null ? vResp.index() : state.getBlockHeight();

                if ("PENDING_INCLUSION".equalsIgnoreCase(status) || vResp.proof() == null || vResp.proof().isEmpty()) {
                    try {
                        api.triggerCheckpoint();
                        vResp = api.verifyReceipt(receipt);
                        status = vResp.status();
                        if (vResp.index() != null) {
                            blockHeight = vResp.index();
                        }
                    } catch (Exception ignored) {
                    }
                }

                if ("PENDING_INCLUSION".equalsIgnoreCase(status) || vResp.proof() == null || vResp.proof().isEmpty()) {
                    long finalBlockHeight = blockHeight;
                    // STATE 1 or STATE 2: PENDING / PENDING_INCLUSION
                    Platform.runLater(() -> {
                        showState(1, "Vote Pending Checkpoint Inclusion",
                                "Your vote is recorded in the ledger (block #" + finalBlockHeight + "), awaiting inclusion in a signed checkpoint.\nAuto-retrying every 10 seconds...",
                                null);
                        scheduleAutoRetry();
                        verifyButton.setDisable(false);
                        retryButton.setDisable(false);
                    });
                    return;
                }

                // Status is OK, fetch checkpoints to verify signature and root
                long treeSize = vResp.treeSize() != null ? vResp.treeSize() : 0L;
                List<ApiClient.CheckpointResponse> checkpoints = api.getCheckpoints();

                // Find smallest checkpoint with treeSize >= proof treeSize
                ApiClient.CheckpointResponse coveringCp = checkpoints.stream()
                        .filter(cp -> cp.treeSize() >= treeSize)
                        .min(Comparator.comparingLong(ApiClient.CheckpointResponse::treeSize))
                        .orElse(null);

                if (coveringCp == null) {
                    Platform.runLater(() -> {
                        showState(2, "Awaiting Checkpoint Coverage",
                                "Your vote proof is ready (tree size " + treeSize + "), but no published checkpoint covers it yet.\nAuto-retrying...",
                                null);
                        scheduleAutoRetry();
                        verifyButton.setDisable(false);
                        retryButton.setDisable(false);
                    });
                    return;
                }

                // Checkpoint Ed25519 signature verification (if pinned key exists)
                PublicKey ckptPub = (pinnedKeys != null) ? pinnedKeys.ckptPub() : null;
                boolean sigValid = true;
                String sigStatusMsg;
                if (ckptPub != null) {
                    byte[] sigBytes;
                    try {
                        sigBytes = com.verivote.core.encoding.Base64Codec.decode(coveringCp.signature());
                    } catch (Exception e) {
                        sigBytes = Hex.decode(coveringCp.signature());
                    }
                    byte[] rootBytes = Hex.decode(coveringCp.merkleRoot());
                    byte[] headBytes = Hex.decode(coveringCp.headHash());
                    sigValid = ClientCrypto.verifyCheckpointSignature(coveringCp.treeSize(), rootBytes, headBytes, sigBytes, ckptPub);
                    if (!sigValid) {
                        Platform.runLater(() -> {
                            showState(4, "Checkpoint Signature Invalid",
                                    "Verification failed: Checkpoint signature does not match pinned checkpoint public key!\nCheckpoint Root: " +
                                            coveringCp.merkleRoot(),
                                    null);
                            verifyButton.setDisable(false);
                        });
                        return;
                    }
                    sigStatusMsg = "Signature: Verified with pinned Ed25519 public key.";
                } else {
                    sigStatusMsg = "Signature: No pinned checkpoint key in client config (skipped in dev mode).";
                }

                // Verify Merkle inclusion proof
                byte[] rawLeaf = Hex.decode(vResp.leafData());
                byte[] leafHash = (rawLeaf.length == 32) ? MerkleTree.leafHash(rawLeaf) : rawLeaf;
                List<byte[]> proofBytes = vResp.proof().stream().map(Hex::decode).toList();
                byte[] rootBytes = Hex.decode(coveringCp.merkleRoot());

                long leafIndex = vResp.index() != null ? vResp.index() : 0L;
                long verifyTreeSize = coveringCp.treeSize();

                boolean proofValid = MerkleVerifier.verify(leafHash, leafIndex, verifyTreeSize, proofBytes, rootBytes);
                if (!proofValid) {
                    // Try raw leaf in case leafData was already leafHash
                    proofValid = MerkleVerifier.verify(rawLeaf, leafIndex, verifyTreeSize, proofBytes, rootBytes);
                }

                if (!proofValid) {
                    Platform.runLater(() -> {
                        showState(4, "Merkle Proof Verification Failed",
                                "Verification failed: Merkle proof evaluation does not evaluate to checkpoint root!\n" +
                                        "Expected Root: " + coveringCp.merkleRoot() + "\n" +
                                        "Receipt: " + receipt,
                                null);
                        verifyButton.setDisable(false);
                    });
                    return;
                }

                // STATE 3: VALID
                stopRetryTimer();
                final long validBlockHeight = blockHeight;
                Platform.runLater(() -> {
                    String rootTrunc = coveringCp.merkleRoot();
                    if (rootTrunc.length() > 24) {
                        rootTrunc = rootTrunc.substring(0, 12) + "..." + rootTrunc.substring(rootTrunc.length() - 12);
                    }
                    String details = "Block Height: #" + validBlockHeight + "\n" +
                            "Merkle Root: " + rootTrunc + "\n" +
                            "Checkpoint Tree Size: " + coveringCp.treeSize() + "\n" +
                            sigStatusMsg;
                    showState(3, "Vote Verified in Ledger!", details, "#16a34a");
                    verifyButton.setDisable(false);
                    retryButton.setVisible(false);
                    retryButton.setManaged(false);
                });

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    showState(4, "Verification Error", ex.getMessage(), null);
                    verifyButton.setDisable(false);
                });
            }
        });
    }

    private void showState(int stateCode, String title, String details, String color) {
        resultContainer.setVisible(true);
        resultContainer.setManaged(true);
        stateTitleLabel.setText(title);
        stateDetailsLabel.setText(details);

        switch (stateCode) {
            case 1, 2 -> {
                stateIconLabel.setText("⏳");
                stateTitleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #d97706;");
                resultContainer.setStyle("-fx-background-color: #fef3c7; -fx-border-color: #f59e0b; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 16;");
                retryButton.setVisible(true);
                retryButton.setManaged(true);
            }
            case 3 -> {
                stateIconLabel.setText("✓");
                stateIconLabel.setStyle("-fx-font-size: 32px; -fx-text-fill: #16a34a; -fx-font-weight: bold;");
                stateTitleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #16a34a;");
                resultContainer.setStyle("-fx-background-color: #dcfce7; -fx-border-color: #22c55e; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 16;");
            }
            case 4 -> {
                stateIconLabel.setText("✗");
                stateIconLabel.setStyle("-fx-font-size: 32px; -fx-text-fill: #dc2626; -fx-font-weight: bold;");
                stateTitleLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #dc2626;");
                resultContainer.setStyle("-fx-background-color: #fee2e2; -fx-border-color: #ef4444; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 16;");
                retryButton.setVisible(true);
                retryButton.setManaged(true);
            }
        }
    }

    private void scheduleAutoRetry() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        if (retryTask != null && !retryTask.isDone()) {
            return;
        }
        if (retryCount < MAX_RETRIES) {
            retryCount++;
            retryTask = scheduler.schedule(this::performVerification, 10, TimeUnit.SECONDS);
        }
    }

    private void stopRetryTimer() {
        if (retryTask != null) {
            retryTask.cancel(true);
            retryTask = null;
        }
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
}
