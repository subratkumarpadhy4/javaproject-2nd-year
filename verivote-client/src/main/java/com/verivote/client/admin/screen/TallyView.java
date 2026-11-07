package com.verivote.client.admin.screen;

import com.verivote.client.admin.AdminApiClient;
import com.verivote.client.admin.AdminApiClient.TallyResponse;
import com.verivote.client.http.ApiException;
import com.verivote.client.ui.UiHelpers;
import javafx.application.Platform;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class TallyView extends VBox {

    public record CandidateRow(long id, String candidate, long count) {}

    private final AdminApiClient apiClient;

    private final TextArea share1Area;
    private final TextArea share2Area;
    private final Button tallyBtn;
    private final ProgressIndicator spinner;

    private final TableView<CandidateRow> resultsTable;
    private final Label invalidCountLabel;
    private final Label closeHeightLabel;
    private final Label closeHeadHashLabel;
    private final Label chainMerkleRootLabel;
    private final Label publishedAtLabel;
    private final VBox resultsContainer;

    public TallyView(AdminApiClient apiClient) {
        this.apiClient = apiClient;

        setSpacing(16);
        setPadding(new Insets(24));
        setAlignment(Pos.TOP_LEFT);

        Label title = UiHelpers.createHeader("Election Tally Ceremony (§8.5)");
        Label subtitle = UiHelpers.createSubHeader("Reconstruct the tally private key using 2-of-3 Shamir threshold shares and compute the verified tally.");

        GridPane shareGrid = new GridPane();
        shareGrid.setHgap(12);
        shareGrid.setVgap(12);

        share1Area = new TextArea();
        share1Area.setPromptText("Trustee Key Share 1 (Base64)");
        share1Area.setPrefRowCount(2);
        share1Area.setWrapText(true);

        share2Area = new TextArea();
        share2Area.setPromptText("Trustee Key Share 2 (Base64)");
        share2Area.setPrefRowCount(2);
        share2Area.setWrapText(true);

        shareGrid.add(new Label("Share 1:"), 0, 0);
        shareGrid.add(share1Area, 1, 0);
        GridPane.setHgrow(share1Area, Priority.ALWAYS);

        shareGrid.add(new Label("Share 2:"), 0, 1);
        shareGrid.add(share2Area, 1, 1);
        GridPane.setHgrow(share2Area, Priority.ALWAYS);

        tallyBtn = UiHelpers.createPrimaryButton("Execute Tally");
        spinner = new ProgressIndicator();
        spinner.setMaxSize(24, 24);
        spinner.setVisible(false);

        HBox btnBox = new HBox(12, tallyBtn, spinner);
        btnBox.setAlignment(Pos.CENTER_LEFT);

        resultsContainer = new VBox(12);
        resultsContainer.setVisible(false);

        Label resultsHeader = UiHelpers.createHeader("Tally Results");
        resultsTable = new TableView<>();
        resultsTable.setPrefHeight(160);

        TableColumn<CandidateRow, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().id())));
        idCol.setPrefWidth(60);

        TableColumn<CandidateRow, String> nameCol = new TableColumn<>("Candidate");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().candidate()));
        nameCol.setPrefWidth(180);

        TableColumn<CandidateRow, String> countCol = new TableColumn<>("Votes");
        countCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().count())));
        countCol.setPrefWidth(100);

        resultsTable.getColumns().addAll(idCol, nameCol, countCol);

        invalidCountLabel = new Label();
        invalidCountLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #dc2626;");

        closeHeightLabel = new Label();
        closeHeadHashLabel = new Label();
        closeHeadHashLabel.setStyle("-fx-font-family: monospace;");
        chainMerkleRootLabel = new Label();
        chainMerkleRootLabel.setStyle("-fx-font-family: monospace;");
        publishedAtLabel = new Label();

        resultsContainer.getChildren().addAll(
                resultsHeader,
                resultsTable,
                invalidCountLabel,
                closeHeightLabel,
                closeHeadHashLabel,
                chainMerkleRootLabel,
                publishedAtLabel
        );

        tallyBtn.setOnAction(e -> handleTally());

        getChildren().addAll(title, subtitle, shareGrid, btnBox, resultsContainer);
    }

    private void handleTally() {
        String s1 = share1Area.getText().trim();
        String s2 = share2Area.getText().trim();

        if (s1.isEmpty() || s2.isEmpty()) {
            UiHelpers.showError("Validation Error", "Please provide two Shamir key shares to satisfy the 2-of-3 threshold.");
            return;
        }

        setLoading(true);
        CompletableFuture.runAsync(() -> {
            try {
                TallyResponse res = apiClient.tally(List.of(s1, s2));
                Platform.runLater(() -> {
                    setLoading(false);
                    displayResults(res);
                });
            } catch (ApiException e) {
                Platform.runLater(() -> {
                    setLoading(false);
                    UiHelpers.showError("Tally Precondition Failed", "Status " + e.getStatusCode() + ": " + e.getResponseBody());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    setLoading(false);
                    UiHelpers.showError("Tally Error", "Unexpected error: " + e.getMessage());
                });
            }
        });
    }

    private void displayResults(TallyResponse res) {
        resultsContainer.setVisible(true);

        List<CandidateRow> rows = new ArrayList<>();
        Map<Long, Long> counts = res.counts();
        counts.forEach((k, v) -> {
            String name = switch (k.intValue()) {
                case 1 -> "Alice (Tech Policy)";
                case 2 -> "Bob (Open Governance)";
                case 3 -> "Charlie (Privacy Rights)";
                default -> "Candidate #" + k;
            };
            rows.add(new CandidateRow(k, name, v));
        });
        resultsTable.setItems(FXCollections.observableArrayList(rows));

        invalidCountLabel.setText("Invalid / Corrupt Ballots: " + res.invalidCount());
        closeHeightLabel.setText("Close Block Height: " + res.closeBlockHeight());

        String head = res.closeHeadHash();
        String truncHead = head != null && head.length() > 16 ? head.substring(0, 16) + "..." : head;
        closeHeadHashLabel.setText("Close Head Hash: " + truncHead);

        String root = res.chainMerkleRoot();
        String truncRoot = root != null && root.length() > 16 ? root.substring(0, 16) + "..." : root;
        chainMerkleRootLabel.setText("Chain Merkle Root: " + truncRoot);

        publishedAtLabel.setText("Published At (Epoch Sec): " + res.publishedAtSec());
    }

    private void setLoading(boolean loading) {
        tallyBtn.setDisable(loading);
        spinner.setVisible(loading);
    }
}
