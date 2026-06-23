package it.polimi.ingsw.view.gui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Map;

/**
 * Controller for the Global Leaderboard screen in the GUI.
 * Manages the display of historical player rankings fetched from the database
 * and provides the user with the ability to safely exit the game application.
 */
public class LeaderboardController {

    @FXML private VBox globalLeaderboardBox;
    @FXML private Button exitGameBtn;
    @FXML private Label userCategoryRankLbl;

    /**
     * Initializes the controller class. This method is automatically called
     * after the FXML file has been loaded.
     * Sets up the style and event handler for the exit button.
     */
    @FXML
    public void initialize() {
        String btnStyle = "-fx-text-fill: #EFE4CB; -fx-font-family: 'Tempus Sans ITC'; -fx-font-weight: bold; -fx-font-size: 14; -fx-cursor: hand; -fx-background-radius: 20;";

        if (exitGameBtn != null) {
            exitGameBtn.setOnAction(e -> onExitClicked());
            exitGameBtn.setStyle("-fx-background-color: #c0392b; " + btnStyle);
            addHoverEffect(exitGameBtn);
        }
    }

    /**
     * Completely closes the client application.
     *
     */
    @FXML
    public void onExitClicked() {
        Platform.exit();
        System.exit(0);
    }

    /**
     * Adds a scaling and glowing hover effect to the specified button.
     *
     * @param btn The button to apply the hover effect to.
     */
    private void addHoverEffect(Button btn) {
        DropShadow glow = new DropShadow();
        glow.setColor(Color.web("#FFFFAA"));
        glow.setRadius(15);
        glow.setSpread(0.4);

        btn.setOnMouseEntered(e -> {
            btn.setScaleX(1.05);
            btn.setScaleY(1.05);
            btn.setEffect(glow);
        });

        btn.setOnMouseExited(e -> {
            btn.setScaleX(1.0);
            btn.setScaleY(1.0);
            btn.setEffect(null);
        });
    }

    /**
     * Populates the global leaderboard UI with the provided historical statistics.
     * It dynamically highlights the row corresponding to the current user and displays
     * their specific rank based on the player count category.
     *
     * @param historicalStats A map containing player nicknames and their corresponding high scores.
     * @param myNickname      The nickname of the client's player to highlight their score.
     * @param categorySize    The number of players in the match, used to contextualize the ranking category.
     */
    public void updateGlobalLeaderboard(Map<String, Integer> historicalStats, String myNickname, int categorySize) {
        if (globalLeaderboardBox == null) return;
        globalLeaderboardBox.getChildren().clear();

        if (userCategoryRankLbl != null) {
            userCategoryRankLbl.setText(""); // Clears the initial text
        }

        if (historicalStats == null || historicalStats.isEmpty()) {
            Label emptyLbl = new Label("No historical records found in the database.");
            emptyLbl.setTextFill(Color.web("#EFE4CB"));
            emptyLbl.setFont(Font.font("Tempus Sans ITC", 18));
            globalLeaderboardBox.getChildren().add(emptyLbl);
            return;
        }

        int rank = 1;
        for (Map.Entry<String, Integer> entry : historicalStats.entrySet()) {
            HBox row = new HBox(20);
            row.setAlignment(Pos.CENTER);
            row.setStyle("-fx-background-color: #562A2C; -fx-border-color: #EFE4CB; -fx-border-width: 2; -fx-border-radius: 35; -fx-background-radius: 35; -fx-padding: 10;");
            row.setMaxWidth(400);

            // Checks if this row belongs to the current user
            boolean isMe = (myNickname != null && myNickname.equals(entry.getKey()));

            if (isMe) {
                // Applies glow effect to highlight the user's row
                DropShadow subtleGlow = new DropShadow();
                subtleGlow.setColor(Color.web("#FFFFAA", 0.5));
                subtleGlow.setRadius(8);
                subtleGlow.setSpread(0.1);
                row.setEffect(subtleGlow);

                // Updates the label with the user's specific ranking
                if (userCategoryRankLbl != null) {
                    userCategoryRankLbl.setText("You are the #" + rank + " player in your category (" + categorySize + " players match)");
                }
            }

            Label rankLbl = new Label("#" + rank);
            rankLbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 18));
            rankLbl.setTextFill(rank == 1 ? Color.GOLD : Color.web("#EFE4CB"));

            Label nameLbl = new Label(entry.getKey() + (isMe ? " (YOU)" : ""));
            nameLbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 18));
            nameLbl.setTextFill(Color.web("#EFE4CB"));
            nameLbl.setPrefWidth(200);

            Label scoreLbl = new Label(entry.getValue() + " PP");
            scoreLbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 18));
            scoreLbl.setTextFill(Color.GOLD);

            row.getChildren().addAll(rankLbl, nameLbl, scoreLbl);
            globalLeaderboardBox.getChildren().add(row);
            rank++;
        }
    }
}