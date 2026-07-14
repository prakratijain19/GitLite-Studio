package app.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import app.model.ChangeType;
import app.model.Repository;
import app.model.StatusReport;
import app.service.CommitService;
import app.service.NothingToCommitException;
import app.service.StagingService;
import app.service.StatusService;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;

/**
 * Controller for the Commit view: stage changed files and create a commit.
 *
 * <p>Thin by design — it delegates to the tested {@link StagingService},
 * {@link CommitService}, and {@link StatusService} and renders their results. The
 * {@link Repository} to operate on is supplied by the opener via
 * {@link #setRepository(Repository)}.
 *
 * <p>Only files that exist on disk (untracked files and unstaged modifications)
 * are offered for staging, since the current staging API reads file content;
 * staging a deletion is not yet supported.
 */
public final class CommitController {

    private final StagingService stagingService = new StagingService();
    private final CommitService commitService = new CommitService();
    private final StatusService statusService = new StatusService();

    private Repository repository;

    @FXML
    private ListView<String> unstagedList;
    @FXML
    private ListView<String> stagedList;
    @FXML
    private TextArea messageArea;

    @FXML
    private void initialize() {
        unstagedList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    /**
     * Sets the repository this view operates on and refreshes the display.
     *
     * @param repository the open repository.
     */
    public void setRepository(Repository repository) {
        this.repository = repository;
        refresh();
    }

    @FXML
    private void onStage() {
        if (repository == null) {
            return;
        }
        List<String> selected = new ArrayList<>(unstagedList.getSelectionModel().getSelectedItems());
        try {
            for (String path : selected) {
                stagingService.stage(repository, repository.getRootPath().resolve(path));
            }
            refresh();
        } catch (RuntimeException e) {
            showError("Failed to stage", e.getMessage());
        }
    }

    @FXML
    private void onCommit() {
        if (repository == null) {
            return;
        }
        try {
            commitService.commit(repository, messageArea.getText());
            messageArea.clear();
            refresh();
            info("Committed", "Commit created successfully.");
        } catch (IllegalArgumentException | NothingToCommitException e) {
            showError("Cannot commit", e.getMessage());
        } catch (RuntimeException e) {
            showError("Commit failed", e.getMessage());
        }
    }

    @FXML
    private void onRefresh() {
        refresh();
    }

    private void refresh() {
        if (repository == null) {
            return;
        }
        StatusReport status = statusService.status(repository);

        List<String> stageable = new ArrayList<>(status.untracked());
        status.unstagedChanges().forEach((path, type) -> {
            if (type == ChangeType.MODIFIED) {
                stageable.add(path);
            }
        });
        stageable.sort(String::compareTo);
        unstagedList.getItems().setAll(stageable);

        stagedList.getItems().setAll(new TreeSet<>(status.stagedChanges().keySet()));
    }

    private void showError(String header, String message) {
        alert(Alert.AlertType.ERROR, header, message);
    }

    private void info(String header, String message) {
        alert(Alert.AlertType.INFORMATION, header, message);
    }

    private static void alert(Alert.AlertType type, String header, String message) {
        Alert alert = new Alert(type);
        alert.setTitle("GitLiteStudio");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
