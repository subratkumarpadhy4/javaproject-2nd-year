package com.verivote.client.admin.screen;

import com.verivote.client.admin.AdminApiClient;
import com.verivote.client.http.ApiException;
import com.verivote.client.ui.UiHelpers;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class OperatorView extends VBox {

    public record OperatorRow(String name, String role, String status) {}

    private final AdminApiClient apiClient;
    private final TableView<OperatorRow> table;
    private final TextField nameField;
    private final TextField pubKeyField;
    private final ComboBox<String> roleBox;
    private final Button enrollBtn;
    private final Button refreshBtn;
    private final ProgressIndicator spinner;

    public OperatorView(AdminApiClient apiClient) {
        this.apiClient = apiClient;

        setSpacing(16);
        setPadding(new Insets(24));
        setAlignment(Pos.TOP_LEFT);

        Label title = UiHelpers.createHeader("Operator Management");
        Label subtitle = UiHelpers.createSubHeader("Enrolled cryptographic operators for multi-party recovery authorization (AUDIT) and key custody (TALLY).");

        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);
        refreshBtn = new Button("Refresh List");
        refreshBtn.setStyle("-fx-padding: 8 16; -fx-cursor: hand;");
        spinner = new ProgressIndicator();
        spinner.setMaxSize(24, 24);
        spinner.setVisible(false);
        topBar.getChildren().addAll(refreshBtn, spinner);

        table = new TableView<>();
        table.setPrefHeight(200);
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<OperatorRow, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name()));
        nameCol.setPrefWidth(180);

        TableColumn<OperatorRow, String> roleCol = new TableColumn<>("Role");
        roleCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().role()));
        roleCol.setPrefWidth(120);

        TableColumn<OperatorRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().status()));
        statusCol.setPrefWidth(100);

        table.getColumns().addAll(nameCol, roleCol, statusCol);

        Label enrollHeader = UiHelpers.createHeader("Enroll New Operator");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);

        nameField = new TextField();
        nameField.setPromptText("Operator name (unique identifier)");

        pubKeyField = new TextField();
        pubKeyField.setPromptText("Ed25519 Public Key (Base64 SPKI)");

        roleBox = new ComboBox<>();
        roleBox.getItems().addAll("AUDIT", "ADMIN", "TALLY");
        roleBox.setValue("AUDIT");

        form.add(new Label("Operator Name:"), 0, 0);
        form.add(nameField, 1, 0);

        form.add(new Label("Role:"), 0, 1);
        form.add(roleBox, 1, 1);

        form.add(new Label("Ed25519 PubKey:"), 0, 2);
        form.add(pubKeyField, 1, 2);

        enrollBtn = UiHelpers.createPrimaryButton("Enroll Operator");

        enrollBtn.setOnAction(e -> handleEnroll());
        refreshBtn.setOnAction(e -> loadOperators());

        getChildren().addAll(title, subtitle, topBar, table, new Separator(), enrollHeader, form, enrollBtn);

        loadOperators();
    }

    private void loadOperators() {
        setLoading(true);
        CompletableFuture.runAsync(() -> {
            try {
                List<Map<String, Object>> ops = apiClient.listOperators();
                List<OperatorRow> rows = ops.stream().map(m -> new OperatorRow(
                        (String) m.get("name"),
                        (String) m.get("role"),
                        Boolean.TRUE.equals(m.get("active")) ? "ACTIVE" : "INACTIVE"
                )).toList();

                Platform.runLater(() -> {
                    setLoading(false);
                    table.setItems(FXCollections.observableArrayList(rows));
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setLoading(false);
                    UiHelpers.showError("Failed to Load Operators", e.getMessage());
                });
            }
        });
    }

    private void handleEnroll() {
        String name = nameField.getText().trim();
        String pub = pubKeyField.getText().trim();
        String role = roleBox.getValue();

        if (name.isEmpty() || pub.isEmpty()) {
            UiHelpers.showError("Validation Error", "Name and Ed25519 public key are required");
            return;
        }

        setLoading(true);
        CompletableFuture.runAsync(() -> {
            try {
                apiClient.enrollOperator(name, pub, role);
                Platform.runLater(() -> {
                    setLoading(false);
                    UiHelpers.showInfo("Enrolled", "Operator " + name + " enrolled successfully.");
                    nameField.clear();
                    pubKeyField.clear();
                    loadOperators();
                });
            } catch (ApiException e) {
                Platform.runLater(() -> {
                    setLoading(false);
                    UiHelpers.showError("Enrollment Failed", "Status " + e.getStatusCode() + ": " + e.getResponseBody());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setLoading(false);
                    UiHelpers.showError("Enrollment Error", e.getMessage());
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        refreshBtn.setDisable(loading);
        enrollBtn.setDisable(loading);
        spinner.setVisible(loading);
    }
}
