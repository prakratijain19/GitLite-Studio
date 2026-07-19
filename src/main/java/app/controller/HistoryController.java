package app.controller;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import app.model.Commit;
import app.model.Repository;
import app.service.HistoryService;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * Controller for the History view: displays the commit history of the current branch.
 */
public final class HistoryController {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final HistoryService historyService = new HistoryService();

    private Repository repository;

    @FXML
    private TableView<Commit> commitTable;
    @FXML
    private TableColumn<Commit, String> hashCol;
    @FXML
    private TableColumn<Commit, String> dateCol;
    @FXML
    private TableColumn<Commit, String> authorCol;
    @FXML
    private TableColumn<Commit, String> messageCol;

    @FXML
    private void initialize() {
        hashCol.setCellValueFactory(data -> new SimpleStringProperty(shortId(data.getValue().id())));
        dateCol.setCellValueFactory(data -> new SimpleStringProperty(DATE_FORMATTER.format(data.getValue().timestamp())));
        authorCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().author()));
        messageCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().message()));
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
        render();
    }

    @FXML
    private void onRefresh() {
        render();
    }

    private void render() {
        if (repository == null) {
            return;
        }
        try {
            List<Commit> commits = historyService.history(repository);
            commitTable.setItems(FXCollections.observableArrayList(commits));
        } catch (RuntimeException e) {
            showError("Failed to load history", e.getMessage());
        }
    }

    private static String shortId(String commitId) {
        return commitId.length() > 7 ? commitId.substring(0, 7) : commitId;
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("GitLiteStudio");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
