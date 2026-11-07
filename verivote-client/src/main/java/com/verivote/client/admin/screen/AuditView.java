package com.verivote.client.admin.screen;

import com.verivote.client.admin.AdminApiClient;
import com.verivote.client.admin.AdminApiClient.AuditFinding;
import com.verivote.client.admin.AdminApiClient.AuditResponse;
import com.verivote.client.http.ApiException;
import com.verivote.client.ui.UiHelpers;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AuditView extends VBox {

    private final AdminApiClient apiClient;
    private final Label statusBadge;
    private final Label countsLabel;
    private final TableView<AuditFinding> table;
    private final Button runBtn;
    private final Button refreshBtn;
    private final ProgressIndicator spinner;

    public AuditView(AdminApiClient apiClient) {
        this.apiClient = apiClient;

        setSpacing(16);
        setPadding(new Insets(24));
        setAlignment(Pos.TOP_LEFT);

        Label title = UiHelpers.createHeader("Ledger & Database Integrity Audit");
        Label subtitle = UiHelpers.createSubHeader("Audits the append-only ledger against checkpoints and the database cache.");

        HBox topBar = new HBox(12);
        topBar.setAlignment(Pos.CENTER_LEFT);

        runBtn = UiHelpers.createPrimaryButton("Run Audit");
        refreshBtn = new Button("Refresh");
        refreshBtn.setStyle("-fx-padding: 8 16; -fx-cursor: hand;");

        spinner = new ProgressIndicator();
        spinner.setMaxSize(24, 24);
        spinner.setVisible(false);

        statusBadge = new Label("STATUS: NOT RUN");
        statusBadge.setStyle("-fx-background-color: #94a3b8; -fx-text-fill: white; -fx-padding: 6 12; -fx-background-radius: 4; -fx-font-weight: bold;");

        topBar.getChildren().addAll(runBtn, refreshBtn, spinner, statusBadge);

        countsLabel = new Label("Ledger: - blocks (- real votes, - orphans). DB: - votes.");
        countsLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #334155; -fx-font-weight: bold;");

        table = new TableView<>();
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<AuditFinding, String> sevCol = new TableColumn<>("Severity");
        sevCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().severity()));
        sevCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item.toUpperCase()) {
                        case "RED" -> setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold; -fx-alignment: CENTER;");
                        case "ALERT" -> setStyle("-fx-background-color: #f97316; -fx-text-fill: white; -fx-font-weight: bold; -fx-alignment: CENTER;");
                        case "WARN" -> setStyle("-fx-background-color: #eab308; -fx-text-fill: black; -fx-font-weight: bold; -fx-alignment: CENTER;");
                        default -> setStyle("-fx-background-color: #94a3b8; -fx-text-fill: white; -fx-alignment: CENTER;");
                    }
                }
            }
        });
        sevCol.setPrefWidth(90);

        TableColumn<AuditFinding, String> codeCol = new TableColumn<>("Code");
        codeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().code()));
        codeCol.setPrefWidth(190);

        TableColumn<AuditFinding, String> heightCol = new TableColumn<>("Block Height");
        heightCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().blockHeight() != null ? String.valueOf(data.getValue().blockHeight()) : "-"));
        heightCol.setPrefWidth(100);

        TableColumn<AuditFinding, String> sizeCol = new TableColumn<>("Tree Size");
        sizeCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().treeSize() != null ? String.valueOf(data.getValue().treeSize()) : "-"));
        sizeCol.setPrefWidth(90);

        TableColumn<AuditFinding, String> msgCol = new TableColumn<>("Message");
        msgCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().message()));
        msgCol.setPrefWidth(300);

        table.getColumns().addAll(sevCol, codeCol, heightCol, sizeCol, msgCol);

        runBtn.setOnAction(e -> runAudit());
        refreshBtn.setOnAction(e -> runAudit());

        getChildren().addAll(title, subtitle, topBar, countsLabel, table);
    }

    private void runAudit() {
        setLoading(true);
        CompletableFuture.runAsync(() -> {
            try {
                AuditResponse res = apiClient.getAudit();
                Platform.runLater(() -> updateView(res));
            } catch (ApiException e) {
                Platform.runLater(() -> {
                    setLoading(false);
                    UiHelpers.showError("Audit Failed", "Error (" + e.getStatusCode() + "): " + e.getResponseBody());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setLoading(false);
                    UiHelpers.showError("Error", "Unexpected: " + e.getMessage());
                });
            }
        });
    }

    private void updateView(AuditResponse res) {
        setLoading(false);
        if (res.clean()) {
            statusBadge.setText("OVERALL: CLEAN");
            statusBadge.setStyle("-fx-background-color: #16a34a; -fx-text-fill: white; -fx-padding: 6 12; -fx-background-radius: 4; -fx-font-weight: bold;");
        } else {
            statusBadge.setText("OVERALL: DIRTY");
            statusBadge.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-padding: 6 12; -fx-background-radius: 4; -fx-font-weight: bold;");
        }

        countsLabel.setText(String.format("Ledger: %d blocks (%d real votes, %d orphans). DB: %d votes.",
                res.ledgerBlockCount(), res.ledgerRealVoteCount(), res.ledgerOrphanCount(), res.dbVoteCount()));

        List<AuditFinding> findings = res.findings();
        // Sort: RED first, ALERT, WARN, INFO, then by blockHeight
        findings.sort(Comparator
                .comparingInt((AuditFinding f) -> severityRank(f.severity()))
                .thenComparing((AuditFinding f) -> f.blockHeight() != null ? f.blockHeight() : -1L));

        table.setItems(FXCollections.observableArrayList(findings));
    }

    private int severityRank(String sev) {
        if (sev == null) return 99;
        return switch (sev.toUpperCase()) {
            case "RED" -> 1;
            case "ALERT" -> 2;
            case "WARN" -> 3;
            case "INFO" -> 4;
            default -> 10;
        };
    }

    private void setLoading(boolean loading) {
        runBtn.setDisable(loading);
        refreshBtn.setDisable(loading);
        spinner.setVisible(loading);
    }
}
