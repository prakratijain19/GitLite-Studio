package app.controller;

import java.util.List;

import app.model.DiffLine;
import app.model.Repository;
import app.service.DiffService;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;

/**
 * Controller for the Diff view: displays line-level differences for a file.
 */
public final class DiffController {

    private final DiffService diffService = new DiffService();

    @FXML
    private Label titleLabel;
    @FXML
    private Label pathLabel;
    @FXML
    private ListView<DiffLine> diffList;

    @FXML
    private void initialize() {
        diffList.setCellFactory(list -> new DiffCell());
    }

    public void setFileToDiff(Repository repository, String path, byte[] oldContent, byte[] newContent) {
        pathLabel.setText("File: " + path);
        try {
            List<DiffLine> diff = diffService.diff(
                    oldContent == null ? new byte[0] : oldContent,
                    newContent == null ? new byte[0] : newContent
            );
            diffList.setItems(FXCollections.observableArrayList(diff));
        } catch (RuntimeException e) {
            showError("Failed to generate diff", e.getMessage());
        }
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("GitLiteStudio");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static class DiffCell extends ListCell<DiffLine> {
        @Override
        protected void updateItem(DiffLine item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle("");
            } else {
                switch (item.type()) {
                    case ADDED -> {
                        setText("+ " + item.content());
                        setStyle("-fx-background-color: #e6ffed; -fx-text-fill: #22863a;");
                    }
                    case REMOVED -> {
                        setText("- " + item.content());
                        setStyle("-fx-background-color: #ffeef0; -fx-text-fill: #cb2431;");
                    }
                    case CONTEXT -> {
                        setText("  " + item.content());
                        setStyle("-fx-background-color: transparent; -fx-text-fill: #24292e;");
                    }
                }
            }
        }
    }
}
