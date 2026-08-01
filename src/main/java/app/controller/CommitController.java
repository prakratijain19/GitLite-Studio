package app.controller;

import java.util.ArrayList;
import java.util.Comparator;
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
 * <p>Untracked files, unstaged modifications, and unstaged deletions are all
 * offered for staging. Deletions are staged differently from the other two —
 * {@link StagingService#stageDeletion} removes the index entry instead of
 * reading file content, since a deleted file has none — so each row in the
 * Changes list carries its {@link ChangeType} alongside its path via
 * {@link ChangeEntry}, rather than a bare path string, so {@link #onStage()}
 * knows which staging operation applies.
 */
public final class CommitController {

    /**
     * A single row in the Changes list: a path plus the change it represents.
     * {@code type} is {@code null} for untracked files, which have no
     * {@link ChangeType} of their own in {@link StatusReport}.
     */
    private record ChangeEntry(String path, ChangeType type) {
        @Override
        public String toString() {
            return type == ChangeType.DELETED ? "DELETE " + path : path;
        }
    }

    private final StagingService stagingService = new StagingService();
    private final CommitService commitService = new CommitService();
    private final StatusService statusService = new StatusService();

    private Repository repository;

    @FXML
    private ListView<ChangeEntry> unstagedList;
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
        List<ChangeEntry> selected = new ArrayList<>(unstagedList.getSelectionModel().getSelectedItems());
        try {
            for (ChangeEntry entry : selected) {
                if (entry.type() == ChangeType.DELETED) {
                    stagingService.stageDeletion(repository, entry.path());
                } else {
                    stagingService.stage(repository, repository.getRootPath().resolve(entry.path()));
                }
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

        List<ChangeEntry> stageable = new ArrayList<>();
        status.untracked().forEach(path -> stageable.add(new ChangeEntry(path, null)));
        status.unstagedChanges().forEach((path, type) -> {
            if (type == ChangeType.MODIFIED || type == ChangeType.DELETED) {
                stageable.add(new ChangeEntry(path, type));
            }
        });
        stageable.sort(Comparator.comparing(ChangeEntry::path));
        unstagedList.getItems().setAll(stageable);

        List<String> staged = new ArrayList<>();
        new TreeSet<>(status.stagedChanges().keySet()).forEach(path -> {
            ChangeType type = status.stagedChanges().get(path);
            staged.add(type == ChangeType.DELETED ? "DELETE " + path : path);
        });
        stagedList.getItems().setAll(staged);
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
