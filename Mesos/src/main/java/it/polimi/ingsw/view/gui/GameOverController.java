package it.polimi.ingsw.view.gui;

import it.polimi.ingsw.controller.client.ClientController;
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

import java.util.List;
import java.util.Map;

/**
 * Controller for the Game Over screen in the GUI.
 * Manages the display of the final match results, including the winner(s)
 * and the complete leaderboard of the current match. Also provides navigation
 * to the global leaderboard and a safe exit from the application.
 */
public class GameOverController {

    @FXML private Label winnerLabel;
    @FXML private VBox matchLeaderboardBox;
    @FXML private Button viewGlobalBtn;
    @FXML private Button exitGameBtn;

    /**
     * Initializes the controller class. This method is automatically called
     * after the FXML file has been loaded.
     * Sets up the style and event handlers for the navigation and exit buttons.
     */
    @FXML
    public void initialize() {
        String btnStyle = "-fx-text-fill: #EFE4CB; -fx-font-family: 'Tempus Sans ITC'; -fx-font-weight: bold; -fx-font-size: 14; -fx-cursor: hand; -fx-background-radius: 20;";

        if (exitGameBtn != null) {
            exitGameBtn.setOnAction(e -> {
                Platform.exit();
                System.exit(0);
            });
            exitGameBtn.setStyle("-fx-background-color: #c0392b; " + btnStyle);
            addHoverEffect(exitGameBtn);
        }

        if (viewGlobalBtn != null) {
            viewGlobalBtn.setOnAction(e -> GuiView.showGlobalLeaderboardScreen());
            viewGlobalBtn.setStyle("-fx-background-color: #f39c12; " + btnStyle);
            addHoverEffect(viewGlobalBtn);
        }
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
     * Populates the match results UI with the data from the completed game.
     * Displays the winner(s) and lists all players with their final prestige points.
     *
     * @param state The client controller containing the final match state.
     */
    public void updateMatchResults(ClientController state) {
        System.out.println("LOG: Loading Game Over screen data...");

        if (matchLeaderboardBox == null) {
            System.err.println("GRAPHIC ERROR: matchLeaderboardBox is NULL! Check if fx:id=\"matchLeaderboardBox\" is set correctly in SceneBuilder.");
        }

        if (matchLeaderboardBox != null) {
            matchLeaderboardBox.getChildren().clear();
        }

        List<String> winners = state.getFinalWinners();
        List<String> leaderboard = state.getFinalLeaderboard();
        Map<String, Integer> prestige = state.getPrestige();

        System.out.println("LOG: Winners received -> " + winners);
        System.out.println("LOG: Leaderboard received -> " + leaderboard);

        if (winnerLabel != null) {
            if (winners != null && !winners.isEmpty()) {
                winnerLabel.setText(" MATCH WINNER: " + String.join(", ", winners) );
            } else {
                winnerLabel.setText("Match Over");
            }
        }

        if (leaderboard != null && matchLeaderboardBox != null) {
            int rank = 1;
            for (String playerName : leaderboard) {
                int score = prestige != null && prestige.containsKey(playerName) ? prestige.get(playerName) : 0;

                HBox row = new HBox(20);
                row.setAlignment(Pos.CENTER);
                row.setStyle("-fx-background-color: #562A2C; -fx-border-color: #EFE4CB; -fx-border-width: 2; -fx-border-radius: 5; -fx-padding: 10;");
                row.setMaxWidth(400);

                Label rankLbl = new Label("#" + rank);
                rankLbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 18));
                rankLbl.setTextFill(rank == 1 ? Color.GOLD : Color.web("#EFE4CB"));

                Label nameLbl = new Label(playerName + (state.getMyNickname() != null && state.getMyNickname().equals(playerName) ? " (YOU)" : ""));
                nameLbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 18));
                nameLbl.setTextFill(Color.web("#EFE4CB"));
                nameLbl.setPrefWidth(200);

                Label scoreLbl = new Label(score + " PP");
                scoreLbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 18));
                scoreLbl.setTextFill(Color.GOLD);

                row.getChildren().addAll(rankLbl, nameLbl, scoreLbl);
                matchLeaderboardBox.getChildren().add(row);
                rank++;
            }
        }
    }
}