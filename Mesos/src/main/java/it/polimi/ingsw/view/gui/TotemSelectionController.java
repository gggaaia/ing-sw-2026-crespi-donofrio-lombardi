package it.polimi.ingsw.view.gui;

import it.polimi.ingsw.model.enums.TotemColor;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for the Totem Selection popup in the GUI.
 * Manages the user interface for selecting a totem color, displaying available options,
 * and handling user interactions during the color picking phase.
 */
public class TotemSelectionController {

    @FXML private ImageView sfondoPopup;
    @FXML private Label titleLabel;
    @FXML private HBox totemContainer;
    @FXML private Button continueButton;

    // References to the fixed totems in the FXML
    @FXML private ImageView totem_BLACK, totem_BLUE, totem_RED, totem_WHITE, totem_YELLOW;

    /**
     * Stores the currently selected totem color by the user.
     */
    private TotemColor selectedColor = null;

    /**
     * Initializes the controller class. This method is automatically called
     * after the FXML file has been loaded.
     * Sets up the rounded clipping for the background, disables the continue button initially,
     * and initializes the click event handlers for the totem images.
     */
    @FXML
    public void initialize() {
        // Radius 30 for the background
        Rectangle clip = new Rectangle(400, 250); // AnchorPane dimensions
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        sfondoPopup.setClip(clip);

        continueButton.setDisable(true);
        setupClickHandlers();
    }

    /**
     * Configures the texts and the state of the totems based on the turn information received from the network.
     *
     * @param currentPlayer The nickname of the player whose turn it currently is.
     * @param myName        The nickname of the client's player.
     * @param available     The set of totem colors that are still available to be picked.
     * @param future        The CompletableFuture to be resolved with the selected color once the user confirms.
     */
    public void updateState(String currentPlayer, String myName, Set<TotemColor> available, CompletableFuture<TotemColor> future) {
        boolean isMyTurn = currentPlayer.equals(myName);

        // 1. Label Management
        if (isMyTurn) {
            titleLabel.setText("Choose the color of your totem");
        } else {
            titleLabel.setText(currentPlayer + " is choosing their totem color...");
        }

        // 2. Availability Management (Grayed out if taken or if it's not my turn)
        updateTotemVisibility(totem_BLACK, TotemColor.BLACK, available, isMyTurn);
        updateTotemVisibility(totem_BLUE, TotemColor.BLUE, available, isMyTurn);
        updateTotemVisibility(totem_RED, TotemColor.RED, available, isMyTurn);
        updateTotemVisibility(totem_WHITE, TotemColor.WHITE, available, isMyTurn);
        updateTotemVisibility(totem_YELLOW, TotemColor.YELLOW, available, isMyTurn);

        // 3. Continue button action
        continueButton.setOnAction(e -> {
            if (selectedColor != null && isMyTurn) {
                future.complete(selectedColor);
            }
        });
    }

    /**
     * Updates the visual appearance and interaction state of a specific totem image.
     *
     * @param img       The ImageView representing the totem.
     * @param color     The TotemColor associated with the ImageView.
     * @param available The set of currently available totem colors.
     * @param isMyTurn  A boolean indicating if it is the current client's turn.
     */
    private void updateTotemVisibility(ImageView img, TotemColor color, Set<TotemColor> available, boolean isMyTurn) {
        if (!available.contains(color)) {
            img.setOpacity(0.3); // Gray/Transparent if already taken
            img.setDisable(true);
            img.setEffect(null);
        } else {
            img.setOpacity(1.0);
            img.setDisable(!isMyTurn); // Clickable only if it's my turn
        }
    }

    /**
     * Sets up the mouse click event handlers for all the totem images.
     * Handles the selection logic, visual feedback (glow effect), and enabling the continue button.
     */
    private void setupClickHandlers() {
        ImageView[] totems = {totem_BLACK, totem_BLUE, totem_RED, totem_WHITE, totem_YELLOW};
        for (ImageView img : totems) {
            img.setOnMouseClicked(e -> {
                // Remove effect from all
                for (ImageView t : totems) t.setEffect(null);

                // Extract the color from the ID (e.g., totem_RED -> RED)
                String colorName = img.getId().replace("totem_", "");
                selectedColor = TotemColor.valueOf(colorName);

                // Apply the luminous aura
                applyGlow(img, selectedColor);
                continueButton.setDisable(false);
            });
        }
    }

    /**
     * Applies a glowing DropShadow effect to the selected totem image.
     * The color of the glow matches or complements the selected totem's color.
     *
     * @param img   The ImageView to apply the effect to.
     * @param color The selected TotemColor used to determine the glow color.
     */
    private void applyGlow(ImageView img, TotemColor color) {
        DropShadow glow = new DropShadow();
        glow.setRadius(30);
        glow.setSpread(0.7);

        // Aura color based on the totem
        switch (color) {
            case RED -> glow.setColor(Color.RED);
            case BLUE -> glow.setColor(Color.CYAN);
            case YELLOW -> glow.setColor(Color.GOLD);
            case WHITE -> glow.setColor(Color.WHITE);
            case BLACK -> glow.setColor(Color.PURPLE);
        }
        img.setEffect(glow);
    }

}