package it.polimi.ingsw.view.gui;

import it.polimi.ingsw.controller.client.ClientController;
import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.CardRow;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.TurnOrderSlot;
import it.polimi.ingsw.model.TurnOrderTile;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.card.EventCard;
import it.polimi.ingsw.model.enums.TotemColor;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GameBoardController {

    @FXML private VBox leftOpponentsBox;
    @FXML private VBox rightOpponentsBox;
    @FXML private HBox myStatusBox;
    @FXML private HBox upperRowBox;
    @FXML private HBox lowerRowBox;
    @FXML private HBox turnOrderBox;
    @FXML private HBox offeringTrackBox;
    @FXML private HBox decksBox;
    @FXML private Label phaseLabel;

    //memory for prompts
    private boolean isOfferingPromptActive = false;
    private int activeOfferingTrackSize = 0;
    private CompletableFuture<Integer> activeOfferingFuture = null;

    private boolean isCardPromptActive = false;
    private String activeCardRowName = "";
    private String activeCardTargetType = "";
    private int activeCardBudget = 0;
    private CompletableFuture<List<Integer>> activeCardFuture = null;
    private List<Integer> currentSelectedCardIds = new ArrayList<>();

    private boolean isSingleCardPromptActive = false;
    private String activeSingleCardTargetType = "";
    private CompletableFuture<Integer> activeSingleCardFuture = null;

    public void updateBoard(ClientController state) {
        Platform.runLater(() -> {
            try {
                if (phaseLabel != null && !isOfferingPromptActive && !isCardPromptActive && !isSingleCardPromptActive) {
                    phaseLabel.setText(state.getCurrentPhase() + " | Active Player: " + state.getCurrentPlayer());
                    phaseLabel.setTextFill(Color.WHITE);
                }

                updatePlayersRecap(state);

                Board board = state.getCurrentBoard();
                if (board != null) {
                    updateCardRow(upperRowBox, board.getUpperRow());
                    updateCardRow(lowerRowBox, board.getLowerRow());
                    updateTurnOrderTile(board.getTurnOrderTile());
                    updateOfferingTrack(board.getOfferingTrack());
                    updateDecks();
                }

                if (isOfferingPromptActive) applyOfferingTileSelectionVisuals();
                if (isCardPromptActive) applyCardSelectionVisuals();
                if (isSingleCardPromptActive) applySingleCardSelectionVisuals();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void updateDecks() {
        if (decksBox == null) return;
        decksBox.getChildren().clear();
        decksBox.getChildren().add(createDeckNode("back_0"));
        decksBox.getChildren().add(createDeckNode("back_96"));
    }

    private StackPane createDeckNode(String backImageName) {
        StackPane pane = new StackPane();
        pane.setPrefSize(100, 140);
        ImageView deckImage = new ImageView();
        deckImage.setFitWidth(100);
        deckImage.setFitHeight(140);
        deckImage.setPreserveRatio(true);

        try {
            String imagePath = "/images/cards/back/" + backImageName + ".png";
            Image img = new Image(getClass().getResourceAsStream(imagePath));
            if (img.isError()) throw new Exception();
            deckImage.setImage(img);
            pane.getChildren().add(deckImage);
        } catch (Exception e) {
            Rectangle rect = new Rectangle(100, 140, Color.web("#2C3E50"));
            rect.setStroke(Color.WHITE); rect.setStrokeWidth(2);
            Label lbl = new Label("DECK");
            lbl.setTextFill(Color.WHITE); lbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 14));
            pane.getChildren().addAll(rect, lbl);
        }
        pane.setStyle("-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.8), 10, 0, 5, 5);");
        return pane;
    }

    private void updatePlayersRecap(ClientController state) {
        if (leftOpponentsBox == null || rightOpponentsBox == null || myStatusBox == null) return;

        leftOpponentsBox.getChildren().clear();
        rightOpponentsBox.getChildren().clear();

        // Keep the confirmation button if present
        //FIXME da capire @Gaia
        javafx.scene.Node confirmBtn = null;
        for (javafx.scene.Node n : myStatusBox.getChildren()) {
            if ("confirmSelectionBtn".equals(n.getId())) {
                confirmBtn = n;
                break;
            }
        }
        myStatusBox.getChildren().clear();
        myStatusBox.setStyle("-fx-background-color: transparent;");

        // BOX WITH MATCH INFORMATION
        VBox gameInfoBox = new VBox(8);
        gameInfoBox.setStyle("-fx-background-color: #562A2Cff; -fx-border-color: #EFE4CB; -fx-border-width: 2; -fx-border-radius: 5; -fx-padding: 10;");
        String eraStr = (state.getCurrentEra() != null) ? state.getCurrentEra().name().replace("ERA_", "") : "1";
        Label eraLbl = new Label("Current Era: \t\t" + eraStr);
        eraLbl.setTextFill(Color.WHITE); eraLbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 14));

        int remainingRounds = Math.max(0, 11 - state.getCurrentRound());
        Label roundLbl = new Label("Remaining rounds: \t" + remainingRounds);
        roundLbl.setTextFill(Color.WHITE); roundLbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 14));

        gameInfoBox.getChildren().addAll(eraLbl, roundLbl);
        rightOpponentsBox.getChildren().add(gameInfoBox);

        List<String> players = state.getJoinedPlayers();
        if (players == null) return;

        String myName = state.getMyNickname();
        int opponentCount = 0;

        for (String p : players) {
            TotemColor color = (state.getColorsByPlayer() != null) ? state.getColorsByPlayer().get(p) : null;
            int pp = (state.getPrestige() != null && state.getPrestige().containsKey(p)) ? state.getPrestige().get(p) : 0;
            int f = (state.getFood() != null && state.getFood().containsKey(p)) ? state.getFood().get(p) : 0;
            int buildSize = (state.getBuildingsByPlayer() != null && state.getBuildingsByPlayer().containsKey(p)) ? state.getBuildingsByPlayer().get(p).size() : 0;

            // COUNTER CALCULATIONS
            int numArtist = 0, numShaman = 0, numBuilder = 0, numGatherer = 0, numHunter = 0, numInventor = 0;
            int extraStars = 0;

            if (state.getHandsByPlayer() != null && state.getHandsByPlayer().containsKey(p)) {
                for (CharacterCard c : state.getHandsByPlayer().get(p)) {
                    if (c instanceof it.polimi.ingsw.model.card.character.Artist) numArtist++;
                    else if (c instanceof it.polimi.ingsw.model.card.character.Shaman shaman) {
                        numShaman++;
                        extraStars += shaman.getStars();
                    }
                    else if (c instanceof it.polimi.ingsw.model.card.character.Builder) numBuilder++;
                    else if (c instanceof it.polimi.ingsw.model.card.character.Gatherer) numGatherer++;
                    else if (c instanceof it.polimi.ingsw.model.card.character.Hunter) numHunter++;
                    else if (c instanceof it.polimi.ingsw.model.card.character.Inventor) numInventor++;
                }
            }

            if (state.getBuildingsByPlayer() != null && state.getBuildingsByPlayer().containsKey(p)) {
                for (BuildingCard b : state.getBuildingsByPlayer().get(p)) {
                    if (b instanceof it.polimi.ingsw.model.card.building.RitualStarBonus) {
                        extraStars += 3;
                    }
                }
            }

            String hexColor = toHexString(getJavaFXColor(color));

            // Building rows in the boxes
            HBox row1 = new HBox(15);
            row1.getChildren().addAll(
                    createIconStat("Food_icon.jpg", f, 22),
                    createIconStat("Artist_icon.jpg", numArtist, 22),
                    createIconStat("Buildings_icon.jpg", buildSize, 22)
            );

            HBox row2 = new HBox(15);
            row2.getChildren().addAll(
                    createIconStat("Ppoints_icon.png", pp, 22),
                    createIconStat("Constructor_icon.jpg", numBuilder, 22),
                    createIconStat("Farmer_icon.jpg", numGatherer, 22)
            );

            HBox row3 = new HBox(15);
            row3.getChildren().addAll(
                    createIconStat("Hunter_icon.jpg", numHunter, 22),
                    createIconStat("Inventor_icon.jpg", numInventor, 22),
                    createIconStat("Shaman_icon.jpg", numShaman, 22)
            );

            if (p.equals(myName)) {
                // Current player panel
                myStatusBox.setStyle("-fx-background-color: #562A2Cff; -fx-border-color: " + hexColor + "; -fx-border-width: 3; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 10;");
                myStatusBox.setSpacing(30);

                VBox nameBox = new VBox(5);
                nameBox.setAlignment(Pos.CENTER_LEFT);
                Label nameLbl = new Label(p + " (YOU)");
                nameLbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 18));
                nameLbl.setTextFill(getJavaFXColor(color));
                Label ppLbl = new Label("Prestige: " + pp);
                ppLbl.setTextFill(Color.GOLD); ppLbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 14));
                nameBox.getChildren().addAll(nameLbl, ppLbl);

                myStatusBox.getChildren().addAll(nameBox, row1, row2, row3);
                if (confirmBtn != null) myStatusBox.getChildren().add(confirmBtn);

            } else {
                // Opponents panels
                VBox oppBox = new VBox(10);
                oppBox.setStyle("-fx-background-color: #562A2Cff; -fx-border-color: " + hexColor + "; -fx-border-width: 2; -fx-border-radius: 5; -fx-padding: 10;");

                HBox topRow = new HBox(10);
                topRow.setAlignment(Pos.CENTER_LEFT);
                Label nameLbl = new Label(p);
                nameLbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 14));
                nameLbl.setTextFill(getJavaFXColor(color));
                Label ppLbl = new Label(pp + " PP");
                ppLbl.setTextFill(Color.GOLD); ppLbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 12));
                topRow.getChildren().addAll(nameLbl, ppLbl);

                oppBox.getChildren().addAll(topRow, row1, row2, row3);

                if (opponentCount % 2 == 0) leftOpponentsBox.getChildren().add(oppBox);
                else rightOpponentsBox.getChildren().add(oppBox);
                opponentCount++;
            }
        }
    }
    private void updateCardRow(HBox rowBox, CardRow rowData) {
        if (rowBox == null) return;
        rowBox.getChildren().clear();
        if (rowData == null) return;

        for (CharacterCard c : rowData.getCharacterCards()) rowBox.getChildren().add(createCardNode(c, Color.web("#2E4053")));
        for (EventCard e : rowData.getEventCards()) rowBox.getChildren().add(createCardNode(e, Color.web("#78281F")));
        for (BuildingCard b : rowData.getBuildingCards()) rowBox.getChildren().add(createCardNode(b, Color.web("#7E5109")));
    }

    private StackPane createCardNode(Card c, Color defaultColor) {
        StackPane pane = new StackPane();
        pane.setPrefSize(100, 140);
        pane.getProperties().put("cardId", c.getCardID());
        String typeStr = (c instanceof CharacterCard) ? "CHARACTER" : (c instanceof BuildingCard ? "BUILDING" : "EVENT");
        pane.getProperties().put("cardType", typeStr);

        ImageView cardImage = new ImageView();
        cardImage.setFitWidth(100); cardImage.setFitHeight(140);
        cardImage.setPreserveRatio(true);

        try {
            String prefix = (c instanceof CharacterCard) ? "tribe_" : (c instanceof EventCard) ? "event_" : "building_";
            String imagePath = "/images/cards/front/" + prefix + c.getCardID() + ".png";
            Image img = new Image(getClass().getResourceAsStream(imagePath));
            if (img.isError()) throw new Exception();
            cardImage.setImage(img);
            pane.getChildren().add(cardImage);
        } catch (Exception ex) {
            Rectangle rect = new Rectangle(100, 140);
            rect.setArcWidth(12); rect.setArcHeight(12);
            rect.setFill(defaultColor);
            rect.setStroke(Color.WHITE); rect.setStrokeWidth(1.5);
            VBox info = new VBox(4); info.setAlignment(Pos.CENTER);
            Label id = new Label("#" + c.getCardID()); id.setTextFill(Color.WHITE); id.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 12));
            info.getChildren().add(id);
            pane.getChildren().addAll(rect, info);
        }
        return pane;
    }

    private void updateTurnOrderTile(TurnOrderTile tile) {
        if (turnOrderBox == null) return;
        turnOrderBox.getChildren().clear();
        if (tile == null || tile.getTurnOrderSlots() == null) return;

        TurnOrderSlot[] slots = tile.getTurnOrderSlots();
        int numPlayers = slots.length;
        StackPane tilePane = new StackPane();

        try {
            int fileIndex = numPlayers - 1;
            String path = "/images/cards/orderCards/turnorderslot_" + fileIndex + ".png";
            Image img = new Image(getClass().getResourceAsStream(path));
            if (img.isError()) throw new Exception();
            ImageView bgImage = new ImageView(img);
            bgImage.setFitHeight(140); bgImage.setPreserveRatio(true);
            tilePane.getChildren().add(bgImage);
        } catch (Exception e) {
            Rectangle rect = new Rectangle(70, 140, Color.web("#34495E"));
            tilePane.getChildren().add(rect);
        }

        VBox totemsOverlay = new VBox();
        totemsOverlay.setAlignment(Pos.CENTER);
        totemsOverlay.setPadding(new javafx.geometry.Insets(15, 0, 15, 0));

        for (int i = 0; i < numPlayers; i++) {
            TurnOrderSlot s = slots[i];
            StackPane slotContainer = new StackPane();
            slotContainer.setPrefHeight(110.0 / numPlayers);

            if (s.isOccupied()) {
                ImageView totemImg = getTotemImageView(s.getTotemColor());
                if (totemImg != null) slotContainer.getChildren().add(totemImg);
            }
            totemsOverlay.getChildren().add(slotContainer);
        }
        tilePane.getChildren().add(totemsOverlay);
        turnOrderBox.getChildren().add(tilePane);
    }

    private void updateOfferingTrack(List<OfferingTile> track) {
        if (offeringTrackBox == null) return;
        offeringTrackBox.getChildren().clear();
        if (track == null) return;

        for (OfferingTile t : track) {
            StackPane tilePane = new StackPane();
            try {
                String path = "/images/cards/offerTile/offertile_" + t.getLetter() + ".png";
                Image img = new Image(getClass().getResourceAsStream(path));
                if (img.isError()) throw new Exception();
                ImageView iv = new ImageView(img);
                iv.setFitWidth(100); iv.setFitHeight(140); iv.setPreserveRatio(true);
                tilePane.getChildren().add(iv);
            } catch (Exception e) {
                Rectangle rect = new Rectangle(100, 140, Color.web("#1E8449"));
                tilePane.getChildren().add(rect);
            }
            if (t.isOccupied()) {
                ImageView totemImg = getTotemImageView(t.getCurrentTotemColor());
                if (totemImg != null) tilePane.getChildren().add(totemImg);
            }
            offeringTrackBox.getChildren().add(tilePane);
        }
    }

    public void enableOfferingTileSelection(int trackSize, CompletableFuture<Integer> future) {
        this.isOfferingPromptActive = true;
        this.activeOfferingTrackSize = trackSize;
        this.activeOfferingFuture = future;
        applyOfferingTileSelectionVisuals();
    }

    private void applyOfferingTileSelectionVisuals() {
        if (!isOfferingPromptActive || offeringTrackBox == null) return;
        if (phaseLabel != null) {
            phaseLabel.setText("IT'S YOUR TURN! Click an Offering Tile to place your totem.");
            phaseLabel.setTextFill(Color.YELLOW);
        }

        List<javafx.scene.Node> tiles = offeringTrackBox.getChildren();
        int max = Math.min(tiles.size(), activeOfferingTrackSize);
        for (int i = 0; i < max; i++) {
            final int index = i;
            StackPane tilePane = (StackPane) tiles.get(i);
            tilePane.setStyle("-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, gold, 15, 0, 0, 0);");
            tilePane.setOnMouseClicked(event -> {
                CompletableFuture<Integer> f = activeOfferingFuture;
                disableSelectionStates();
                if (f != null) f.complete(index);
            });
        }
    }

    public void enableCardSelection(String rowName, String targetType, int budget, CompletableFuture<List<Integer>> future) {
        this.isCardPromptActive = true;
        this.activeCardRowName = rowName;
        this.activeCardTargetType = targetType;
        this.activeCardBudget = budget;
        this.activeCardFuture = future;
        this.currentSelectedCardIds.clear();
        applyCardSelectionVisuals();
    }

    private void applyCardSelectionVisuals() {
        if (!isCardPromptActive) return;
        if (phaseLabel != null) {
            phaseLabel.setText("YOUR TURN! Select " + activeCardTargetType + " from " + activeCardRowName + " ROW (Max " + activeCardBudget + " cards). Then click Confirm.");
            phaseLabel.setTextFill(Color.YELLOW);
        }

        HBox targetBox = activeCardRowName.equals("UPPER") ? upperRowBox : lowerRowBox;
        if (targetBox == null) return;

        if (myStatusBox != null) {
            myStatusBox.getChildren().removeIf(n -> "confirmSelectionBtn".equals(n.getId()));
            Button confirmBtn = new Button("CONFIRM SELECTION");
            confirmBtn.setId("confirmSelectionBtn");
            confirmBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14; -fx-cursor: hand;");
            confirmBtn.setOnAction(e -> {
                List<Integer> result = new ArrayList<>(currentSelectedCardIds);
                CompletableFuture<List<Integer>> f = activeCardFuture;
                disableSelectionStates();
                if (f != null) f.complete(result);
            });
            myStatusBox.getChildren().add(confirmBtn);
        }

        for (javafx.scene.Node node : targetBox.getChildren()) {
            if (activeCardTargetType.equals(node.getProperties().get("cardType"))) {
                int cardId = (int) node.getProperties().get("cardId");

                if (currentSelectedCardIds.contains(cardId)) {
                    node.setStyle("-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, #00ff00, 20, 0, 0, 0);");
                } else {
                    node.setStyle("-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(255,255,255,0.5), 10, 0, 0, 0);");
                }

                node.setOnMouseClicked(e -> {
                    if (currentSelectedCardIds.contains(cardId)) {
                        currentSelectedCardIds.remove((Integer) cardId);
                        node.setStyle("-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(255,255,255,0.5), 10, 0, 0, 0);");
                    } else {
                        // Impedisce visivamente all'utente di selezionare più carte di quante ne possa pagare
                        if (currentSelectedCardIds.size() < activeCardBudget) {
                            currentSelectedCardIds.add(cardId);
                            node.setStyle("-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, #00ff00, 20, 0, 0, 0);");
                        }
                    }
                });
            }
        }
    }

    public void enableSingleCardSelection(String targetType, CompletableFuture<Integer> future) {
        this.isSingleCardPromptActive = true;
        this.activeSingleCardTargetType = targetType;
        this.activeSingleCardFuture = future;
        applySingleCardSelectionVisuals();
    }

    private void applySingleCardSelectionVisuals() {
        if (!isSingleCardPromptActive) return;
        if (phaseLabel != null) {
            phaseLabel.setText("SELECT YOUR EXTRA CARD: Click on the card you want to pick.");
            phaseLabel.setTextFill(Color.YELLOW);
        }

        for (HBox row : new HBox[]{upperRowBox, lowerRowBox}) {
            if (row == null) continue;
            for (javafx.scene.Node node : row.getChildren()) {
                if (activeSingleCardTargetType.equals(node.getProperties().get("cardType"))) {
                    node.setStyle("-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, #00ff00, 20, 0, 0, 0);");
                    node.setOnMouseClicked(e -> {
                        int id = (int) node.getProperties().get("cardId");
                        CompletableFuture<Integer> f = activeSingleCardFuture;
                        disableSelectionStates();
                        if (f != null) f.complete(id);
                    });
                }
            }
        }
    }

    private void disableSelectionStates() {
        isOfferingPromptActive = false;
        isCardPromptActive = false;
        isSingleCardPromptActive = false;

        if (phaseLabel != null) {
            phaseLabel.setText("Waiting for other players...");
            phaseLabel.setTextFill(Color.web("#00ff4d"));
        }
        if (myStatusBox != null) myStatusBox.getChildren().removeIf(n -> "confirmSelectionBtn".equals(n.getId()));

        if (offeringTrackBox != null) {
            for (javafx.scene.Node node : offeringTrackBox.getChildren()) {
                node.setStyle(""); node.setOnMouseClicked(null);
            }
        }
        for (HBox row : new HBox[]{upperRowBox, lowerRowBox}) {
            if (row != null) {
                for (javafx.scene.Node node : row.getChildren()) {
                    node.setStyle(""); node.setOnMouseClicked(null);
                }
            }
        }
    }

    private ImageView getTotemImageView(TotemColor color) {
        if (color == null) return null;
        try {
            String path = "/images/" + color.name().toLowerCase() + "_totem.png";
            ImageView iv = new ImageView(new Image(getClass().getResourceAsStream(path)));
            iv.setFitWidth(40); iv.setFitHeight(40); iv.setPreserveRatio(true);
            return iv;
        } catch (Exception e) { return null; }
    }

    private Color getJavaFXColor(TotemColor color) {
        if (color == null) return Color.WHITE;
        return switch (color) {
            case RED -> Color.web("#EC7063"); case BLUE -> Color.web("#5DADE2");
            case YELLOW -> Color.web("#F4D03F"); case BLACK -> Color.web("#566573");
            case WHITE -> Color.web("#FBFCFC");
        };
    }

    private String toHexString(Color color) {
        return String.format("#%02X%02X%02X", (int)(color.getRed()*255), (int)(color.getGreen()*255), (int)(color.getBlue()*255));
    }
    /**
     * Crea un piccolo contenitore HBox con un'icona e un numero di fianco.
     */
    private HBox createIconStat(String iconName, int value, int iconSize) {
        HBox box = new HBox(5);
        box.setAlignment(Pos.CENTER_LEFT);
        try {
            // Assicurati che il path corrisponda alla cartella dove hai salvato le tue icone!
            String imagePath = "/images/icons/" + iconName;
            Image img = new Image(getClass().getResourceAsStream(imagePath));
            if (img.isError()) throw new Exception("Image not found");

            ImageView icon = new ImageView(img);
            icon.setFitWidth(iconSize);
            icon.setFitHeight(iconSize);
            icon.setPreserveRatio(true);

            Label lbl = new Label(String.valueOf(value));
            lbl.setTextFill(Color.WHITE);
            lbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 14));

            box.getChildren().addAll(icon, lbl);
        } catch (Exception e) {
            // Fallback: Se l'icona manca o il nome è sbagliato, stampa solo il testo come backup.
            String cleanName = iconName.split("\\.")[0].toUpperCase();
            Label lbl = new Label(cleanName + ": " + value);
            lbl.setTextFill(Color.WHITE);
            box.getChildren().add(lbl);
        }
        return box;
    }
}