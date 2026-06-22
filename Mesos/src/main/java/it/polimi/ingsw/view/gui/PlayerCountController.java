package it.polimi.ingsw.view.gui;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

/**
 * Controller class for the Player Count Popup screen.
 * Handles the selection of the number of players, visual highlighting,
 * and confirmation or cancellation of the match creation.
 */
public class PlayerCountController {

    @FXML private ImageView sfondoPopup;

    // References to the button images
    @FXML private ImageView img2;
    @FXML private ImageView img3;
    @FXML private ImageView img4;
    @FXML private ImageView img5;

    /**
     * Stores the currently selected player count.
     * A value of 0 means no selection has been made yet.
     */
    private int selectedCount = 0;

    /**
     * Automatically called when the popup is loaded.
     * Applies rounded corners to the background image.
     */
    @FXML
    public void initialize() {
        if (sfondoPopup != null) {
            Rectangle clip = new Rectangle(sfondoPopup.getFitWidth(), sfondoPopup.getFitHeight());
            clip.setArcWidth(30);
            clip.setArcHeight(30);
            sfondoPopup.setClip(clip);
        }
    }

    /**
     * Helper method to apply a glowing effect to the selected image
     * while removing the effect from all other images.
     *
     * @param count       The number of players associated with the button.
     * @param selectedImg The ImageView to highlight.
     */
    private void selectButton(int count, ImageView selectedImg) {
        this.selectedCount = count;

        // 1. Remove the glow effect from all images
        if (img2 != null) img2.setEffect(null);
        if (img3 != null) img3.setEffect(null);
        if (img4 != null) img4.setEffect(null);
        if (img5 != null) img5.setEffect(null);

        // 2. Create and apply the glow effect to the selected image
        if (selectedImg != null) {
            DropShadow glow = new DropShadow();
            glow.setColor(Color.web("#F39C12")); // Bright golden color
            glow.setRadius(20);                  // Spread of the glow
            glow.setSpread(0.6);                 // Density of the glow

            selectedImg.setEffect(glow);
        }
    }

    // --- NUMBER BUTTON ACTIONS ---

    /** Handles the selection for a 2-player match. */
    @FXML public void on2PlayersSelected(ActionEvent event) { selectButton(2, img2); }

    /** Handles the selection for a 3-player match. */
    @FXML public void on3PlayersSelected(ActionEvent event) { selectButton(3, img3); }

    /** Handles the selection for a 4-player match. */
    @FXML public void on4PlayersSelected(ActionEvent event) { selectButton(4, img4); }

    /** Handles the selection for a 5-player match. */
    @FXML public void on5PlayersSelected(ActionEvent event) { selectButton(5, img5); }

    // --- CONFIRMATION AND CLOSURE BUTTONS ---

    /**
     * Handles the click event on the "Confirm" button.
     * Submits the chosen player count to the network thread if a valid selection was made.
     *
     * @param event The ActionEvent triggered by the confirm button.
     */
    @FXML
    public void onConfirmClicked(ActionEvent event) {
        if (selectedCount == 0) {
            System.out.println("LOG: You must select a number of players before confirming!");
            return;
        }

        System.out.println("LOG: Confirming " + selectedCount + " players.");
        GuiView.submitPlayerCount(selectedCount);
    }

    /**
     * Handles the click event on the "Close" button.
     * Aborts the game creation process, closes the popup,
     * removes the blur effect from the main window, and resets the GUI for a new connection.
     *
     * @param event The ActionEvent triggered by the close button.
     */
    @FXML
    public void onCloseClicked(ActionEvent event) {
        System.out.println("LOG: The user aborted the game creation. Returning to Welcome.");

        // 1. Unlock the network thread (which will trigger the default match creation)
        GuiView.abortPlayerCount();

        // 2. WHILE the thread attempts to start the match, we sever the connection
//    and force a hard reload of the "Welcome" screen.
//    This will eliminate the blur, close the popup, and ignore the Lobby loading.
        GuiView.abortLobbyAndDisconnect();
    }
}