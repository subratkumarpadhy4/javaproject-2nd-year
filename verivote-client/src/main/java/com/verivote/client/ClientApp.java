package com.verivote.client;

import com.verivote.client.crypto.PinnedKeys;
import com.verivote.client.http.ApiClient;
import com.verivote.client.screen.CastScreen;
import com.verivote.client.screen.LoginScreen;
import com.verivote.client.screen.TokenScreen;
import com.verivote.client.screen.VerifyScreen;
import com.verivote.client.state.SessionState;
import com.verivote.client.admin.AdminApp;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class ClientApp extends Application {

    private final SessionState state = new SessionState();
    private final ApiClient api = new ApiClient(ApplicationConfig.get("verivote.server.url"));
    private PinnedKeys pinnedKeys;
    private BorderPane root;

    @Override
    public void init() {
        try {
            pinnedKeys = PinnedKeys.fromConfig(null);
        } catch (Exception ignored) {
            pinnedKeys = null;
        }
    }

    @Override
    public void start(Stage stage) {
        root = new BorderPane();
        root.setStyle("-fx-background-color: #f8fafc;");

        // Top bar
        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(12, 24, 12, 24));
        topBar.setStyle("-fx-background-color: #1e293b;");
        Label appTitle = new Label("VeriVote-J · Verifiable E-Voting Client");
        appTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button adminBtn = new Button("Admin Console");
        adminBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px; -fx-padding: 4 12; -fx-cursor: hand; -fx-background-radius: 4;");
        adminBtn.setOnAction(e -> {
            Stage adminStage = new Stage();
            new AdminApp().openOnStage(adminStage);
        });

        topBar.getChildren().addAll(appTitle, spacer, adminBtn);
        root.setTop(topBar);

        showLogin();

        Scene scene = new Scene(root, 800, 600);
        stage.setTitle("VeriVote-J");
        stage.setScene(scene);
        stage.setResizable(true);
        stage.show();
    }

    private void showLogin() {
        state.clear();
        LoginScreen loginScreen = new LoginScreen(api, sessionToken -> {
            state.setSessionToken(sessionToken);
            showToken();
        });
        root.setCenter(loginScreen);
    }

    private void showToken() {
        TokenScreen tokenScreen = new TokenScreen(api, state, this::showCast, this::showLogin);
        root.setCenter(tokenScreen);
    }

    private void showCast() {
        CastScreen castScreen = new CastScreen(api, state, pinnedKeys, this::showVerify, this::showToken);
        root.setCenter(castScreen);
    }

    private void showVerify() {
        VerifyScreen verifyScreen = new VerifyScreen(api, state, pinnedKeys, this::showCast);
        root.setCenter(verifyScreen);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
