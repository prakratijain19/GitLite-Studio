package app.controller;

import java.util.List;
import java.util.stream.Collectors;

import app.model.MergeConflict;
import app.model.MergeResult;
import app.model.Repository;
import app.service.BranchService;
import app.service.MergeConflictException;
import app.service.MergeService;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;

/**
 * Controller for the Merge view: allows merging a selected branch into the current branch.
 */
public final class MergeController {

    private final BranchService branchService = new BranchService();
    private final MergeService mergeService = new MergeService();

    private Repository repository;

    @FXML
    private ListView<String> branchList;

    @FXML
    private void initialize() {
        // Initialization if needed
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
        render();
    }

    @FXML
    private void onRefresh() {
        render();
    }

    @FXML
    private void onMerge() {
        String selected = branchList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("No branch selected", "Please select a branch to merge.");
            return;
        }

        try {
            MergeResult result = mergeService.merge(repository, selected);
            showInfo("Merge Successful", result.message() + "\nResult: " + result.type());
            render();
        } catch (MergeConflictException e) {
            String conflictedFiles = e.getConflicts().stream()
                    .map(MergeConflict::path)
                    .collect(Collectors.joining("\n- ", "- ", ""));
            
            showWarning("Merge Conflicts", "Conflicts occurred during merge. The following files have conflict markers:\n\n" 
                    + conflictedFiles 
                    + "\n\nPlease resolve them in an editor, stage the files, and commit to complete the merge.");
        } catch (RuntimeException e) {
            showError("Merge Failed", e.getMessage());
        }
    }

    private void render() {
        if (repository == null) {
            return;
        }
        try {
            String current = branchService.currentBranch(repository);
            List<String> branches = branchService.listBranches(repository).stream()
                    .map(app.model.Branch::getName)
                    .filter(b -> !b.equals(current))
                    .toList();
            
            branchList.setItems(FXCollections.observableArrayList(branches));
        } catch (RuntimeException e) {
            showError("Failed to load branches", e.getMessage());
        }
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("GitLiteStudio");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showWarning(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("GitLiteStudio");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showInfo(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("GitLiteStudio");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
