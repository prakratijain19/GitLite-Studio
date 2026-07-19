package app.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import app.model.Repository;
import app.model.StatusReport;
import app.service.BranchService;
import app.service.RepositoryService;
import app.service.StatusService;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Controller for the Home view: open or initialize a repository and display its
 * current branch and status.
 *
 * <p>The controller is intentionally thin. It translates button clicks into calls
 * on the (tested) service layer — {@link RepositoryService}, {@link BranchService},
 * {@link StatusService} — and renders the results; it holds no version-control
 * logic of its own. The default no-argument constructors are used because the
 * services are stateless collaborators.
 */
public final class HomeController {

    private static final String DEFAULT_BRANCH = "master";

    private final RepositoryService repositoryService = new RepositoryService();
    private final BranchService branchService = new BranchService();
    private final StatusService statusService = new StatusService();

    /** The currently open repository, or {@code null} if none. */
    private Repository repository;

    @FXML
    private VBox root;
    @FXML
    private Label pathLabel;
    @FXML
    private Label branchLabel;
    @FXML
    private TextArea statusArea;

    @FXML
    private void initialize() {
        render();
    }

    @FXML
    private void onOpenRepository() {
        chooseDirectory("Open Repository").ifPresent(dir -> {
            try {
                if (!repositoryService.isRepository(dir)) {
                    showError("Not a repository", dir + " is not a GitLite repository.");
                    return;
                }
                repository = repositoryService.open(dir);
                render();
            } catch (RuntimeException e) {
                showError("Failed to open repository", e.getMessage());
            }
        });
    }

    @FXML
    private void onInitRepository() {
        chooseDirectory("Initialize Repository").ifPresent(dir -> {
            if (repositoryService.isRepository(dir)) {
                showError("Already a repository", dir + " already contains a GitLite repository.");
                return;
            }
            promptUserName().ifPresent(userName -> {
                try {
                    repository = repositoryService.initialize(dir, userName, DEFAULT_BRANCH);
                    render();
                } catch (RuntimeException e) {
                    showError("Failed to initialize repository", e.getMessage());
                }
            });
        });
    }

    @FXML
    private void onRefresh() {
        render();
    }

    @FXML
    private void onOpenCommitView() {
        if (repository == null) {
            showError("No repository", "Open or initialize a repository first.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/app/view/commit.fxml"));
            Parent view = loader.load();
            CommitController controller = loader.getController();
            controller.setRepository(repository);

            Stage stage = new Stage();
            stage.setTitle("Commit — " + repository.getRootPath().getFileName());
            stage.setScene(new Scene(view, 640, 480));
            stage.initOwner(window());
            stage.setOnHidden(event -> render()); // reflect new commit in Home status
            stage.show();
        } catch (IOException e) {
            showError("Failed to open commit view", e.getMessage());
        }
    }

    @FXML
    private void onOpenHistoryView() {
        if (repository == null) {
            showError("No repository", "Open or initialize a repository first.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/app/view/history.fxml"));
            Parent view = loader.load();
            HistoryController controller = loader.getController();
            controller.setRepository(repository);

            Stage stage = new Stage();
            stage.setTitle("History — " + repository.getRootPath().getFileName());
            stage.setScene(new Scene(view, 750, 480));
            stage.initOwner(window());
            stage.show();
        } catch (IOException e) {
            showError("Failed to open history view", e.getMessage());
        }
    }

    @FXML
    private void onOpenBranchView() {
        if (repository == null) {
            showError("No repository", "Open or initialize a repository first.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/app/view/branch.fxml"));
            Parent view = loader.load();
            BranchController controller = loader.getController();
            controller.setRepository(repository);

            Stage stage = new Stage();
            stage.setTitle("Branches — " + repository.getRootPath().getFileName());
            stage.setScene(new Scene(view, 400, 400));
            stage.initOwner(window());
            stage.setOnHidden(event -> render()); // refresh branch label on close
            stage.show();
        } catch (IOException e) {
            showError("Failed to open branch view", e.getMessage());
        }
    }

    @FXML
    private void onOpenDiffView() {
        if (repository == null) {
            showError("No repository", "Open or initialize a repository first.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Diff Viewer");
        dialog.setHeaderText("Enter relative path of file to diff:");
        dialog.setContentText("Path:");
        dialog.showAndWait().ifPresent(path -> {
            try {
                Path file = repository.getRootPath().resolve(path);
                byte[] content = java.nio.file.Files.exists(file) ? java.nio.file.Files.readAllBytes(file) : new byte[0];
                
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/app/view/diff.fxml"));
                Parent view = loader.load();
                DiffController controller = loader.getController();
                controller.setFileToDiff(repository, path, null, content); // basic diff vs empty for now

                Stage stage = new Stage();
                stage.setTitle("Diff — " + path);
                stage.setScene(new Scene(view, 600, 480));
                stage.initOwner(window());
                stage.show();
            } catch (IOException e) {
                showError("Failed to open diff view", e.getMessage());
            }
        });
    }

    @FXML
    private void onOpenMergeView() {
        if (repository == null) {
            showError("No repository", "Open or initialize a repository first.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/app/view/merge.fxml"));
            Parent view = loader.load();
            MergeController controller = loader.getController();
            controller.setRepository(repository);

            Stage stage = new Stage();
            stage.setTitle("Merge — " + repository.getRootPath().getFileName());
            stage.setScene(new Scene(view, 400, 400));
            stage.initOwner(window());
            stage.setOnHidden(event -> render());
            stage.show();
        } catch (IOException e) {
            showError("Failed to open merge view", e.getMessage());
        }
    }

    private void render() {
        if (repository == null) {
            pathLabel.setText("(none)");
            branchLabel.setText("—");
            statusArea.setText("No repository open.");
            return;
        }
        pathLabel.setText(repository.getRootPath().toString());
        branchLabel.setText(branchService.currentBranch(repository));
        statusArea.setText(describeStatus(statusService.status(repository)));
    }

    private static String describeStatus(StatusReport status) {
        if (status.isClean()) {
            return "Working tree clean.";
        }
        StringBuilder text = new StringBuilder();
        status.stagedChanges().forEach((path, type) ->
                text.append("staged     ").append(type).append("  ").append(path).append('\n'));
        status.unstagedChanges().forEach((path, type) ->
                text.append("unstaged   ").append(type).append("  ").append(path).append('\n'));
        status.untracked().forEach(path ->
                text.append("untracked  ").append(path).append('\n'));
        return text.toString();
    }

    private Optional<Path> chooseDirectory(String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        File selected = chooser.showDialog(window());
        return Optional.ofNullable(selected).map(File::toPath);
    }

    private Optional<String> promptUserName() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Initialize Repository");
        dialog.setHeaderText("Enter the user name to record for commits:");
        dialog.setContentText("User name:");
        return dialog.showAndWait().map(String::strip).filter(name -> !name.isBlank());
    }

    private void showError(String header, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("GitLiteStudio");
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private Window window() {
        return root.getScene().getWindow();
    }
}
