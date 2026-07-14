package app;

import java.net.URL;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX application entry point for GitLiteStudio.
 *
 * <p>This is the top of the Presentation layer. It loads the Home view from FXML
 * and shows it; all behaviour lives in the view's controller, which delegates to
 * the (already tested) service layer. The application holds no business logic
 * itself.
 *
 * <p>Run with {@code mvn javafx:run} (the javafx-maven-plugin sets up the JavaFX
 * module path).
 */
public final class GitLiteApp extends Application {

    private static final String HOME_VIEW = "/app/view/home.fxml";
    private static final String WINDOW_TITLE = "GitLiteStudio";
    private static final double INITIAL_WIDTH = 720;
    private static final double INITIAL_HEIGHT = 460;

    @Override
    public void start(Stage stage) throws Exception {
        URL view = getClass().getResource(HOME_VIEW);
        if (view == null) {
            throw new IllegalStateException("Home view not found on classpath: " + HOME_VIEW);
        }
        Parent root = FXMLLoader.load(view);
        stage.setTitle(WINDOW_TITLE);
        stage.setScene(new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT));
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
