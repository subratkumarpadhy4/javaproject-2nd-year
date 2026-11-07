package com.verivote.client.admin;

import com.verivote.client.ApplicationConfig;
import com.verivote.client.admin.screen.AuditView;
import com.verivote.client.admin.screen.OperatorView;
import com.verivote.client.admin.screen.RecoveryView;
import com.verivote.client.admin.screen.TallyView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

public class AdminApp extends Application {

    private final AdminApiClient apiClient;

    public AdminApp() {
        this.apiClient = new AdminApiClient(ApplicationConfig.get("verivote.server.url", "http://localhost:8080"));
    }

    public AdminApp(AdminApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Override
    public void start(Stage primaryStage) {
        openOnStage(primaryStage);
    }

    public void openOnStage(Stage stage) {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab auditTab = new Tab("Audit & Integrity", new AuditView(apiClient));
        Tab recoveryTab = new Tab("Recovery Ceremony", new RecoveryView(apiClient));
        Tab tallyTab = new Tab("Election Tally", new TallyView(apiClient));
        Tab operatorTab = new Tab("Operators", new OperatorView(apiClient));

        tabPane.getTabs().addAll(auditTab, recoveryTab, tallyTab, operatorTab);

        Scene scene = new Scene(tabPane, 900, 650);
        stage.setTitle("VeriVote-J — Admin & Auditor Console");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
