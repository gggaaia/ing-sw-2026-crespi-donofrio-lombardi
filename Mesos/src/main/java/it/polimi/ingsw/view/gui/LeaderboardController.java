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

public class LeaderboardController {

    @FXML private VBox globalLeaderboardBox;
    @FXML private Button backToWelcomeBtn;
    @FXML private Button exitGameBtn;
    @FXML private Label userCategoryRankLbl; // Nuova etichetta richiesta

    @FXML
    public void initialize() {
        String btnStyle = "-fx-text-fill: #EFE4CB; -fx-font-family: 'Tempus Sans ITC'; -fx-font-weight: bold; -fx-font-size: 14; -fx-cursor: hand; -fx-background-radius: 20;";

        if (backToWelcomeBtn != null) {
            backToWelcomeBtn.setOnAction(e -> GuiView.abortLobbyAndDisconnect());
            backToWelcomeBtn.setStyle("-fx-background-color: #EB5E28; " + btnStyle);
            addHoverEffect(backToWelcomeBtn);
        }

        if (exitGameBtn != null) {
            exitGameBtn.setOnAction(e -> {
                Platform.exit();
                System.exit(0);
            });
            exitGameBtn.setStyle("-fx-background-color: #c0392b; " + btnStyle);
            addHoverEffect(exitGameBtn);
        }
    }

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
     * Popola la classifica. Ora riceve anche il nickname e il numero di giocatori.
     */
    public void updateGlobalLeaderboard(Map<String, Integer> historicalStats, String myNickname, int categorySize) {
        if (globalLeaderboardBox == null) return;
        globalLeaderboardBox.getChildren().clear();

        if (userCategoryRankLbl != null) {
            userCategoryRankLbl.setText(""); // Pulisce il testo iniziale
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

            // Controlla se questa riga appartiene all'utente
            boolean isMe = (myNickname != null && myNickname.equals(entry.getKey()));

            if (isMe) {
                // Illumina la riga in modo molto leggero (opacità al 50%)
                DropShadow subtleGlow = new DropShadow();
                subtleGlow.setColor(Color.web("#FFFFAA", 0.5));
                subtleGlow.setRadius(8);
                subtleGlow.setSpread(0.1);
                row.setEffect(subtleGlow);

                // Scrive il testo nella nuova Label
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