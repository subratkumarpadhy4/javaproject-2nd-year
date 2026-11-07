package com.verivote.client.admin.screen;

import com.verivote.client.admin.AdminApiClient;
import com.verivote.client.admin.AdminApiClient.RecoveryRequest;
import com.verivote.client.admin.AdminApiClient.RecoveryResponse;
import com.verivote.client.http.ApiException;
import com.verivote.client.ui.UiHelpers;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.concurrent.CompletableFuture;

public class RecoveryView extends ScrollPane {

    private final AdminApiClient apiClient;

    private final TextField heightField;
    private final ComboBox<String> actionBox;
    private final TextField reasonField;
    private final TextField blockHashField;
    private final TextField tokenHashField;
    private final TextField op1Field;
    private final TextArea sig1Area;
    private final TextField op2Field;
    private final TextArea sig2Area;
    private final Button executeBtn;
    private final Label resultLabel;
    private final ProgressIndicator spinner;

    public RecoveryView(AdminApiClient apiClient) {
        this.apiClient = apiClient;

        setFitToWidth(true);
        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.setAlignment(Pos.TOP_LEFT);

        Label title = UiHelpers.createHeader("Ledger Recovery Ceremony (§8.4)");
        Label subtitle = UiHelpers.createSubHeader("Execute multi-party authorized ledger recovery operations (ACCEPT_AS_ORPHAN or RECOVER_FROM_LEDGER).");

        VBox noticeCard = new VBox(8);
        noticeCard.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #cbd5e1; -fx-border-radius: 4; -fx-padding: 12;");
        Label noteTitle = new Label("Operator Signing Instructions");
        noteTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #334155;");
        Label noteText = new Label("Signatures must be pre-computed. Use RecoveryOperatorSigner.sign() offline (in a separate tool) " +
                "or use the /api/admin/recover endpoint directly with pre-signed data. Real production uses HSMs / hardware keys.");
        noteText.setWrapText(true);
        noteText.setStyle("-fx-text-fill: #64748b; -fx-font-size: 12px;");
        noticeCard.getChildren().addAll(noteTitle, noteText);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);

        heightField = new TextField();
        heightField.setPromptText("e.g. 1");

        actionBox = new ComboBox<>();
        actionBox.getItems().addAll("ACCEPT_AS_ORPHAN", "RECOVER_FROM_LEDGER");
        actionBox.setValue("ACCEPT_AS_ORPHAN");

        reasonField = new TextField();
        reasonField.setPromptText("Reason for recovery operation");

        blockHashField = new TextField();
        blockHashField.setPromptText("64-char hex block hash");

        tokenHashField = new TextField();
        tokenHashField.setPromptText("64-char hex token hash");

        op1Field = new TextField();
        op1Field.setPromptText("Name of first AUDIT operator");

        sig1Area = new TextArea();
        sig1Area.setPromptText("Base64 Ed25519 signature from operator 1");
        sig1Area.setPrefRowCount(2);
        sig1Area.setWrapText(true);

        op2Field = new TextField();
        op2Field.setPromptText("Name of second AUDIT operator (required for ACCEPT_AS_ORPHAN)");

        sig2Area = new TextArea();
        sig2Area.setPromptText("Base64 Ed25519 signature from operator 2");
        sig2Area.setPrefRowCount(2);
        sig2Area.setWrapText(true);

        int row = 0;
        form.add(new Label("Block Height:"), 0, row);
        form.add(heightField, 1, row++);

        form.add(new Label("Action:"), 0, row);
        form.add(actionBox, 1, row++);

        form.add(new Label("Reason:"), 0, row);
        form.add(reasonField, 1, row++);

        form.add(new Label("Block Hash:"), 0, row);
        form.add(blockHashField, 1, row++);

        form.add(new Label("Token Hash:"), 0, row);
        form.add(tokenHashField, 1, row++);

        form.add(new Label("Operator 1:"), 0, row);
        form.add(op1Field, 1, row++);

        form.add(new Label("Signature 1:"), 0, row);
        form.add(sig1Area, 1, row++);

        form.add(new Label("Operator 2:"), 0, row);
        form.add(op2Field, 1, row++);

        form.add(new Label("Signature 2:"), 0, row);
        form.add(sig2Area, 1, row++);

        executeBtn = UiHelpers.createPrimaryButton("Execute Recovery");
        spinner = new ProgressIndicator();
        spinner.setMaxSize(24, 24);
        spinner.setVisible(false);

        HBox btnBox = new HBox(12, executeBtn, spinner);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        resultLabel = new Label();
        resultLabel.setWrapText(true);
        resultLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        executeBtn.setOnAction(e -> handleExecute());

        root.getChildren().addAll(title, subtitle, noticeCard, form, btnBox, resultLabel);
        setContent(root);
    }

    private void handleExecute() {
        String hStr = heightField.getText().trim();
        if (hStr.isEmpty()) {
            UiHelpers.showError("Validation Error", "Block height is required");
            return;
        }
        long height;
        try {
            height = Long.parseLong(hStr);
        } catch (NumberFormatException e) {
            UiHelpers.showError("Validation Error", "Invalid block height: " + hStr);
            return;
        }

        String action = actionBox.getValue();
        String reason = reasonField.getText().trim();
        String blockHash = blockHashField.getText().trim();
        String tokenHash = tokenHashField.getText().trim();
        String op1 = op1Field.getText().trim();
        String sig1 = sig1Area.getText().trim();
        String op2 = op2Field.getText().trim();
        String sig2 = sig2Area.getText().trim();

        RecoveryRequest req = new RecoveryRequest(
                height, action, reason, blockHash, tokenHash, op1, sig1, op2, sig2
        );

        setLoading(true);
        CompletableFuture.runAsync(() -> {
            try {
                RecoveryResponse res = apiClient.recover(req);
                Platform.runLater(() -> {
                    setLoading(false);
                    resultLabel.setText(String.format("SUCCESS: Event #%d executed for block %d (%s)",
                            res.eventId(), res.blockHeight(), res.action()));
                    resultLabel.setStyle("-fx-text-fill: #16a34a; -fx-font-weight: bold; -fx-font-size: 13px;");
                    UiHelpers.showInfo("Recovery Successful", "Recovery Event #" + res.eventId() + " completed successfully.");
                });
            } catch (ApiException e) {
                Platform.runLater(() -> {
                    setLoading(false);
                    resultLabel.setText("ERROR (" + e.getStatusCode() + "): " + e.getResponseBody());
                    resultLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-font-size: 13px;");
                    UiHelpers.showError("Recovery Failed", "Status " + e.getStatusCode() + ": " + e.getResponseBody());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setLoading(false);
                    resultLabel.setText("ERROR: " + e.getMessage());
                    resultLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-weight: bold; -fx-font-size: 13px;");
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        executeBtn.setDisable(loading);
        spinner.setVisible(loading);
    }
}
