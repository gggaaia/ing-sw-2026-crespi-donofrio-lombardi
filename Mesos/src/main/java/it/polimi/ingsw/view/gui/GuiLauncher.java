package it.polimi.ingsw.view.gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

/**
 * Main entry point for the Mesos game Graphical User Interface.
 * This class extends the JavaFX Application class to initialize and launch the GUI.
 */
public class GuiLauncher extends Application {

    /**
     * Initializes and displays the primary window of the JavaFX application.
     * It loads the initial welcome screen from the FXML layout file.
     *
     * @param primaryStage the primary window for this application, onto which
     * the application scene will be set.
     * @throws Exception if the FXML file cannot be loaded or found.
     */
    @Override
    public void start(Stage primaryStage) throws Exception {

        // 1. Locate the FXML file for the welcome screen.
        URL fxmlLocation = getClass().getResource("/fxml/welcome.fxml");

        if (fxmlLocation == null) {
            System.err.println("CRITICAL ERROR: Cannot find the welcome.fxml file!");
            System.exit(1);
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        // 2. Create the Scene with standard resolution (1280x720).
        Scene scene = new Scene(root, 1280, 720);


        GuiView.setMainStage(primaryStage);


        // 3. Configure the main window and display it.
        primaryStage.setTitle("Mesos");
        primaryStage.setScene(scene);

        // Prevent the user from resizing the window to avoid layout distortion.
        primaryStage.setResizable(false);

        primaryStage.show();
    }

    /**
     * Main method to launch the JavaFX application.
     * @param args command line arguments passed to the application.
     */
    public static void main(String[] args) {
        launch(args);
    }
}