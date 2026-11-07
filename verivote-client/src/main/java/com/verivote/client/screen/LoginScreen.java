package com.verivote.client.screen;

import com.verivote.client.http.ApiClient;
import com.verivote.client.ui.UiHelpers;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class LoginScreen extends VBox {

    private final ApiClient api;
    private final Consumer<String> onLoginSuccess;

    private final TextField nationalIdField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField displayNameField = new TextField();
    private final TextField totpField = new TextField();
    private final Label statusLabel = new Label();
    private final Button actionButton = UiHelpers.createPrimaryButton("Login");
    private final Button switchModeButton = new Button("Register instead");

    private String challengeToken;
    private boolean isRegisterMode = false;

    public LoginScreen(ApiClient api, Consumer<String> onLoginSuccess) {
        this.api = api;
        this.onLoginSuccess = onLoginSuccess;

        setAlignment(Pos.CENTER);
        setPadding(new Insets(30));
        setSpacing(20);
        setStyle("-fx-background-color: #f8fafc;");

        buildUi();
    }

    private void buildUi() {
        getChildren().clear();

        VBox card = UiHelpers.createCard();

        Label title = UiHelpers.createHeader(isRegisterMode ? "Voter Registration" : "Voter Login");
        Label subtitle = UiHelpers.createSubHeader(isRegisterMode ? "Create your credentials" : "Sign in to vote");

        nationalIdField.setPromptText("National ID (e.g. NID-12345)");
        passwordField.setPromptText("Password");
        displayNameField.setPromptText("Full Name");
        totpField.setPromptText("6-digit TOTP Code");
        totpField.setVisible(false);
        totpField.setManaged(false);

        statusLabel.setStyle("-fx-text-fill: #dc2626; -fx-font-size: 12px;");
        statusLabel.setWrapText(true);

        actionButton.setMaxWidth(Double.MAX_VALUE);
        actionButton.setOnAction(e -> handleAction());

        switchModeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #2563eb; -fx-cursor: hand; -fx-underline: true;");
        switchModeButton.setOnAction(e -> {
            isRegisterMode = !isRegisterMode;
            challengeToken = null;
            statusLabel.setText("");
            buildUi();
        });

        card.getChildren().addAll(title, subtitle);

        if (isRegisterMode) {
            card.getChildren().addAll(
                    new Label("Full Name:"), displayNameField,
                    new Label("National ID:"), nationalIdField,
                    new Label("Password:"), passwordField
            );
            actionButton.setText("Register");
            switchModeButton.setText("Already registered? Login instead");
        } else {
            card.getChildren().addAll(
                    new Label("National ID:"), nationalIdField,
                    new Label("Password:"), passwordField
            );
            if (challengeToken != null) {
                totpField.setVisible(true);
                totpField.setManaged(true);
                card.getChildren().addAll(new Label("TOTP Code:"), totpField);
                actionButton.setText("Verify TOTP & Proceed");
            } else {
                actionButton.setText("Login");
            }
            switchModeButton.setText("Need an account? Register instead");
        }

        card.getChildren().addAll(statusLabel, actionButton, switchModeButton);
        getChildren().add(card);
    }

    private void handleAction() {
        statusLabel.setText("");
        actionButton.setDisable(true);

        if (isRegisterMode) {
            String nid = nationalIdField.getText().trim();
            String name = displayNameField.getText().trim();
            String pw = passwordField.getText();

            if (nid.isEmpty() || name.isEmpty() || pw.isEmpty()) {
                statusLabel.setText("All fields are required.");
                actionButton.setDisable(false);
                return;
            }

            CompletableFuture.supplyAsync(() -> api.register(nid, name, pw))
                    .thenAcceptAsync(resp -> Platform.runLater(() -> {
                        actionButton.setDisable(false);
                        showRegistrationResult(resp.totpSecretBase32());
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            actionButton.setDisable(false);
                            statusLabel.setText("Registration error: " + ex.getCause().getMessage());
                        });
                        return null;
                    });
        } else if (challengeToken == null) {
            String nid = nationalIdField.getText().trim();
            String pw = passwordField.getText();

            if (nid.isEmpty() || pw.isEmpty()) {
                statusLabel.setText("National ID and password are required.");
                actionButton.setDisable(false);
                return;
            }

            CompletableFuture.supplyAsync(() -> api.login(nid, pw))
                    .thenAcceptAsync(resp -> Platform.runLater(() -> {
                        actionButton.setDisable(false);
                        this.challengeToken = resp.challengeToken();
                        buildUi();
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            actionButton.setDisable(false);
                            statusLabel.setText("Login error: " + ex.getCause().getMessage());
                        });
                        return null;
                    });
        } else {
            String codeStr = totpField.getText().trim();
            int code;
            try {
                code = Integer.parseInt(codeStr);
            } catch (NumberFormatException e) {
                statusLabel.setText("TOTP code must be a 6-digit number.");
                actionButton.setDisable(false);
                return;
            }

            CompletableFuture.supplyAsync(() -> api.verifyTotp(challengeToken, code))
                    .thenAcceptAsync(resp -> Platform.runLater(() -> {
                        actionButton.setDisable(false);
                        onLoginSuccess.accept(resp.sessionToken());
                    }))
                    .exceptionally(ex -> {
                        Platform.runLater(() -> {
                            actionButton.setDisable(false);
                            statusLabel.setText("TOTP error: " + ex.getCause().getMessage());
                        });
                        return null;
                    });
        }
    }

    private void showRegistrationResult(String totpSecret) {
        getChildren().clear();
        VBox card = UiHelpers.createCard();

        Label title = UiHelpers.createHeader("Registration Successful!");
        Label sub = UiHelpers.createSubHeader("Save your TOTP Secret into your authenticator app:");

        TextArea secretArea = new TextArea(totpSecret);
        secretArea.setEditable(false);
        secretArea.setWrapText(true);
        secretArea.setPrefRowCount(2);

        Button copyBtn = UiHelpers.createSecondaryButton("Copy Secret");
        copyBtn.setOnAction(e -> UiHelpers.copyToClipboard(totpSecret));

        Button backToLogin = UiHelpers.createPrimaryButton("Proceed to Login");
        backToLogin.setMaxWidth(Double.MAX_VALUE);
        backToLogin.setOnAction(e -> {
            isRegisterMode = false;
            challengeToken = null;
            buildUi();
        });

        card.getChildren().addAll(title, sub, secretArea, copyBtn, backToLogin);
        getChildren().add(card);
    }
}
