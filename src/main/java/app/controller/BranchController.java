package app.controller;

import java.util.List;

import app.model.Repository;
import app.service.BranchService;
import app.service.CheckoutService;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

/**
 * Controller for the Branch view: lists branches, creates branches, and checks them out.
 */
public final class BranchController {

    private final BranchService branchService = new BranchService();
    private final CheckoutService checkoutService = new CheckoutService();

    private Repository repository;

    @FXML
    private ListView<String> branchList;
    @FXML
    private TextField newBranchField;

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
    private void onCheckout() {
        String selected = branchList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("No branch selected", "Please select a branch to checkout.");
            return;
        }
        
        // Remove the (*) indicator from the current branch name when selecting
        if (selected.endsWith(" (*)")) {
            selected = selected.substring(0, selected.length() - 4);
        }

        try {
            checkoutService.checkout(repository, selected);
            render();
            showInfo("Checkout successful", "Checked out branch '" + selected + "'.");
        } catch (RuntimeException e) {
            showError("Failed to checkout branch", e.getMessage());
        }
    }

    @FXML
    private void onCreateBranch() {
        String branchName = newBranchField.getText().strip();
        if (branchName.isBlank()) {
            showError("Invalid branch name", "Branch name cannot be empty.");
            return;
        }
        try {
            branchService.createBranch(repository, branchName);
            newBranchField.clear();
            render();
            showInfo("Branch created", "Created new branch '" + branchName + "'.");
        } catch (RuntimeException e) {
            showError("Failed to create branch", e.getMessage());
        }
    }

    private void render() {
        if (repository == null) {
            return;
        }
        try {
            List<String> branches = branchService.listBranches(repository).stream()
                    .map(app.model.Branch::getName)
                    .toList();
            String current = branchService.currentBranch(repository);
            
            // Highlight current branch with (*)
            List<String> displayList = branches.stream()
                    .map(b -> b.equals(current) ? b + " (*)" : b)
                    .toList();
            
            branchList.setItems(FXCollections.observableArrayList(displayList));
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

    private void showInfo(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("GitLiteStudio");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
