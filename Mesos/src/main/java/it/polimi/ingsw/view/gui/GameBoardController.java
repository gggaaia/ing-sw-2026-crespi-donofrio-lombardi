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
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Main JavaFX Controller for the Game Board GUI.
 * <p>
 * This class is responsible for rendering the entire game state, including cards, tracks,
 * player boards, and derived statistics. It also manages asynchronous user interactions
 * (such as card selections and totem placements) during the match.
 * </p>
 */
public class GameBoardController {

    // =========================================================================================
    // FXML UI ELEMENTS
    // =========================================================================================

    @FXML private VBox leftOpponentsBox;
    @FXML private VBox rightOpponentsBox;
    @FXML private HBox myStatusBox;
    @FXML private HBox upperRowBox;
    @FXML private HBox lowerRowBox;
    @FXML private HBox turnOrderBox;
    @FXML private HBox offeringTrackBox;
    @FXML private HBox decksBox;
    @FXML private Label phaseLabel;
    @FXML private Button leaveMatchBtn;

    // =========================================================================================
    // INTERACTION MEMORY STATE
    // =========================================================================================

    /* * We use CompletableFuture to safely bridge the gap between two different threads:
     * the asynchronous, event-driven JavaFX Application Thread (which reacts to mouse clicks)
     * and the Client's background Network Thread.
     * * When the server requests user input, the Network Thread blocks waiting for the
     * future to complete, while the UI remains fully responsive. Once the user clicks,
     * the future is completed, waking up the Network Thread to send the payload.
     */

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

    // =========================================================================================
    // INITIALIZATION & CORE REFRESH LOGIC
    // =========================================================================================

    /**
     * Initializes the controller class. Automatically called by JavaFX after FXML loading.
     * Sets up the hover effects for the board buttons.
     */
    @FXML
    public void initialize() {
        if (leaveMatchBtn != null) {
            addHoverEffect(leaveMatchBtn);
        }
    }

    /**
     * Refreshes the entire game board UI based on the current state from the ClientController.
     * <p>
     * This method ensures all UI updates are executed safely on the JavaFX Application Thread
     * via {@code Platform.runLater()}. It re-draws the board components and re-applies any
     * visual cues for active user prompts.
     * </p>
     *
     * @param state The current ClientController containing the updated game model.
     */
    public void updateBoard(ClientController state) {
        Platform.runLater(() -> {
            try {
                // Update phase label only if there are no active user prompts hiding it
                if (phaseLabel != null && !isOfferingPromptActive && !isCardPromptActive && !isSingleCardPromptActive) {
                    String friendlyPhase = getPhaseDescription(state.getCurrentPhase());
                    String activePlayer = state.getCurrentPlayer();

                    if (activePlayer != null && !activePlayer.isEmpty()) {
                        phaseLabel.setText(friendlyPhase + " | Active Player: " + activePlayer);
                    } else {
                        phaseLabel.setText(friendlyPhase);
                    }
                    phaseLabel.setTextFill(Color.web("#EFE4CB"));
                }

                updatePlayersRecap(state);

                Board board = state.getCurrentBoard();
                if (board != null) {
                    updateCardRow(upperRowBox, board.getUpperRow());
                    updateCardRow(lowerRowBox, board.getLowerRow());
                    updateTurnOrderTile(board.getTurnOrderTile());
                    updateOfferingTrack(board.getOfferingTrack());
                    updateDecks(state.getCurrentEra());
                }

                // Re-apply visual cues if a prompt was active during the background refresh
                if (isOfferingPromptActive) applyOfferingTileSelectionVisuals();
                if (isCardPromptActive) applyCardSelectionVisuals();
                if (isSingleCardPromptActive) applySingleCardSelectionVisuals();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // =========================================================================================
    // BOARD COMPONENT RENDERERS
    // =========================================================================================

    /**
     * Renders the deck placeholders on the board dynamically based on the current Era.
     * Maps the specific Era to the corresponding graphical asset for the card backs.
     * @param currentEra The current Era of the game, dictating which card backs to show.
     */
    private void updateDecks(it.polimi.ingsw.model.enums.Era currentEra) {
        if (decksBox == null) return;
        decksBox.getChildren().clear();

        String tribeBack = "back_0";
        String buildingBack = "back_96";

        if (currentEra != null) {
            switch (currentEra) {
                case ERA_1 -> {
                    tribeBack = "back_0";
                    buildingBack = "back_96";
                }
                case ERA_2 -> {
                    tribeBack = "back_29";
                    buildingBack = "back_102";
                }
                case ERA_3 -> {
                    tribeBack = "back_57";
                    buildingBack = "back_109";
                }
            }
        }

        decksBox.getChildren().add(createDeckNode(tribeBack));
        decksBox.getChildren().add(createDeckNode(buildingBack));
    }

    /**
     * Creates a visual node representing a card deck back.
     * Provides a procedural colored rectangle as a fallback if the image resource is missing.
     *
     * @param backImageName The filename of the deck back image (without extension).
     * @return A StackPane containing the deck visual with a drop-shadow effect.
     */
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

    /**
     * Updates the player recap panels (the local player at the bottom, opponents on the sides).
     * <p>
     * <b>Note on derived metrics:</b> Because the client only receives raw lists of cards
     * (and not the server-side pre-calculated logic properties), this method dynamically
     * calculates derived metrics (like building discounts, sustenance requirements, and inventor icons)
     * purely for display purposes. The actual game rules remain enforced on the server.
     * </p>
     *
     * @param state The ClientController providing the current state of all players.
     */
    private void updatePlayersRecap(ClientController state) {
        if (leftOpponentsBox == null || rightOpponentsBox == null || myStatusBox == null) return;
        leftOpponentsBox.setMinWidth(260);
        rightOpponentsBox.setMinWidth(260);
        leftOpponentsBox.getChildren().clear();
        rightOpponentsBox.getChildren().clear();

        // Keep the action buttons if present during a UI refresh to avoid losing prompt interactions
        javafx.scene.Node actionBox = null;
        for (javafx.scene.Node n : myStatusBox.getChildren()) {
            if ("actionButtonsBox".equals(n.getId()) || "confirmSelectionBtn".equals(n.getId())) {
                actionBox = n;
                break;
            }
        }
        myStatusBox.getChildren().clear();
        myStatusBox.setStyle("-fx-background-color: transparent;");

        // --- MATCH INFORMATION BOX ---
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
            boolean isDisconnected = state.getDisconnectedPlayers().contains(p);
            TotemColor color = (state.getColorsByPlayer() != null) ? state.getColorsByPlayer().get(p) : null;
            int pp = (state.getPrestige() != null && state.getPrestige().containsKey(p)) ? state.getPrestige().get(p) : 0;
            int f = (state.getFood() != null && state.getFood().containsKey(p)) ? state.getFood().get(p) : 0;

            // Derived Metric Calculations
            int numArtist = 0, numShaman = 0, numBuilder = 0, numGatherer = 0, numHunter = 0, numInventor = 0;
            int extraStars = 0;
            int farmerDiscount = 0;
            int buildingDiscount = 0;
            int peopleToFeed = 0;
            int inventorIcons = 0;

            // Set to count unique Inventor icons for endgame scoring visualization
            java.util.Set<it.polimi.ingsw.model.enums.InventorIcon> distinctInventorIcons = new java.util.HashSet<>();

            if (state.getHandsByPlayer() != null && state.getHandsByPlayer().containsKey(p)) {
                List<CharacterCard> hand = state.getHandsByPlayer().get(p);
                // Sustenance requirement equals the number of character cards in hand
                peopleToFeed = hand.size();

                for (CharacterCard c : hand) {
                    if (c instanceof it.polimi.ingsw.model.card.character.Artist) numArtist++;
                    else if (c instanceof it.polimi.ingsw.model.card.character.Shaman shaman) {
                        numShaman++;
                        extraStars += shaman.getStars();
                    }
                    else if (c instanceof it.polimi.ingsw.model.card.character.Builder builder) {
                        numBuilder++;
                        buildingDiscount += builder.getBuildingCostRedux();
                    }
                    else if (c instanceof it.polimi.ingsw.model.card.character.Gatherer) numGatherer++;
                    else if (c instanceof it.polimi.ingsw.model.card.character.Hunter) numHunter++;
                    else if (c instanceof it.polimi.ingsw.model.card.character.Inventor inventor) {
                        numInventor++;
                        if (inventor.getInventorIcon() != null) {
                            distinctInventorIcons.add(inventor.getInventorIcon());
                        }
                    }
                }
            }

            inventorIcons = distinctInventorIcons.size();
            farmerDiscount = numGatherer; // Base sustenance discount

            // Apply specific bonuses provided by constructed Buildings
            if (state.getBuildingsByPlayer() != null && state.getBuildingsByPlayer().containsKey(p)) {
                for (BuildingCard b : state.getBuildingsByPlayer().get(p)) {
                    if (b instanceof it.polimi.ingsw.model.card.building.RitualStarBonus) {
                        extraStars += 3;
                    }
                    else if (b instanceof it.polimi.ingsw.model.card.building.SustenanceBonusGatherer) {
                        farmerDiscount += numGatherer;
                    }
                    else if (b instanceof it.polimi.ingsw.model.card.building.SustenanceBonusPainter) {
                        farmerDiscount += numArtist;
                    }
                    else if (b instanceof it.polimi.ingsw.model.card.building.SustenanceBonusInventor) {
                        farmerDiscount += numInventor;
                    }
                }
            }

            String hexColor = toHexString(getJavaFXColor(color));

            // --- BUILD STAT ROWS ---
            HBox row1 = new HBox(15);
            row1.getChildren().addAll(
                    createIconStat("Food_icon.png", f, 22, "FOOD", "Current food resources. \nFood can be used to feed your tribe and to buy buildings. \nDuring Sustenance events you will lose PP if you don't have enough food to feed your tribe."),
                    createIconStat("Tribe_icon.png", peopleToFeed, 22, "TRIBE", "Number of Character cards in your Tribe that will need to be fed during Sustenance Events (excluding buildings)."),
                    createIconStat("Farmer_discount_icon.png", farmerDiscount, 22, "DISCOUNT", "Sustenance reduction. Includes the Gatherers' discount and any additional bonus provided by buildings.")
            );

            HBox row2 = new HBox(15);
            row2.getChildren().addAll(
                    createIconStat("Shaman_stars_icon.png", extraStars, 22, "STARS","The total number of Star icons on Shaman and Building cards. \nDuring Shamanic Ritual Events, you will gain or lose points \nbased on the number of Stars you have compared to other players." ),
                    createIconStat("Constructor_discount_icon.png", buildingDiscount, 22, "BUILDINGS DISCOUNT","The total discount in Food that you receive from your Builders,\nwhich will be subtracted from the cost of any Building card you want to take."),
                    createIconStat("Inventor_icon.png", inventorIcons, 22, "INVENTION ICONS","The total number of different invention icons among Invention cards. \nAt the end of the game, you will get Prestige Points equal to the number of different Inventions \nmultiplied by the number of Invention cards owned.")
            );

            HBox row3 = new HBox(15);
            row3.getChildren().addAll(
                    createIconStat("Artist_icon.png", numArtist, 22, "ARTISTS","Total number of artists in your tribe"),
                    createIconStat("Constructor_icon.png", numBuilder, 22, "BUILDERS","Total number of builders in your tribe"),
                    createIconStat("Farmer_icon.png", numGatherer, 22, "GATHERERS","Total number of gatherers in your tribe")
            );

            HBox row4 = new HBox(15);
            row4.getChildren().addAll(
                    createIconStat("Hunter_icon.png", numHunter, 22, "HUNTERS","Total number of hunters in your tribe"),
                    createIconStat("Inventor_icon.png", numInventor, 22, "INVENTORS","Total number of inventors in your tribe"),
                    createIconStat("Shaman_icon.png", numShaman, 22, "SHAMANS","Total number of shamans in your tribe")
            );

            // --- POPULATE PLAYER PANELS ---
            if (p.equals(myName)) {
                // Main local player panel (Bottom)
                myStatusBox.setStyle("-fx-background-color: #562A2Cff; -fx-border-color: " + hexColor + "; -fx-border-width: 3; -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 10;");
                myStatusBox.setSpacing(15);

                VBox nameBox = new VBox(5);
                nameBox.setAlignment(Pos.CENTER_LEFT);
                Label nameLbl = new Label(p + " (YOU)");
                nameLbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 18));
                nameLbl.setTextFill(getJavaFXColor(color));

                Label ppLbl = new Label("Prestige: " + pp);
                ppLbl.setTextFill(Color.GOLD); ppLbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 14));
                nameBox.getChildren().addAll(nameLbl, ppLbl);

                myStatusBox.getChildren().addAll(
                        nameBox,
                        createSeparator(true), row1,
                        createSeparator(true), row2,
                        createSeparator(true), row3,
                        createSeparator(true), row4
                );
                if (actionBox != null) myStatusBox.getChildren().add(actionBox);

            } else {
                // Opponents panels (Left/Right)
                VBox oppBox = new VBox(8);
                oppBox.setStyle("-fx-background-color: #562A2Cff; -fx-border-color: " + hexColor + "; -fx-border-width: 2; -fx-border-radius: 5; -fx-padding: 10;");

                HBox topRow = new HBox(10);
                topRow.setAlignment(Pos.CENTER_LEFT);
                Label nameLbl = new Label(p);
                nameLbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 14));
                nameLbl.setTextFill(getJavaFXColor(color));

                Label ppLbl = new Label(pp + " PP");
                ppLbl.setTextFill(Color.GOLD); ppLbl.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 12));
                topRow.getChildren().addAll(nameLbl, ppLbl);

                if (isDisconnected) {
                    oppBox.setOpacity(0.5);
                    oppBox.setStyle("-fx-background-color: #2c3e50; -fx-border-color: #7f8c8d; -fx-border-width: 2; -fx-border-radius: 5; -fx-padding: 10;");
                    nameLbl.setText(p + " (OFFLINE)");
                    nameLbl.setTextFill(Color.web("#bdc3c7"));
                }

                oppBox.getChildren().addAll(
                        topRow,
                        createSeparator(false), row1,
                        createSeparator(false), row2,
                        createSeparator(false), row3,
                        createSeparator(false), row4
                );

                if (opponentCount % 2 == 0) leftOpponentsBox.getChildren().add(oppBox);
                else rightOpponentsBox.getChildren().add(oppBox);
                opponentCount++;
            }
        }
    }

    /**
     * Creates a thin separator line for the player recap panels.
     *
     * @param isVertical True if the separator should be vertical, false for horizontal.
     * @return A configured Separator node.
     */
    private javafx.scene.control.Separator createSeparator(boolean isVertical) {
        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        sep.setOrientation(isVertical ? javafx.geometry.Orientation.VERTICAL : javafx.geometry.Orientation.HORIZONTAL);
        sep.setOpacity(0.25);
        return sep;
    }

    /**
     * Renders a specific row of cards (Upper or Lower) based on the board data.
     *
     * @param rowBox The HBox UI container representing the row.
     * @param rowData The CardRow data object containing current cards.
     */
    private void updateCardRow(HBox rowBox, CardRow rowData) {
        if (rowBox == null) return;
        rowBox.getChildren().clear();
        if (rowData == null) return;

        for (CharacterCard c : rowData.getCharacterCards()) rowBox.getChildren().add(createCardNode(c, Color.web("#2E4053")));
        for (EventCard e : rowData.getEventCards()) rowBox.getChildren().add(createCardNode(e, Color.web("#78281F")));
        for (BuildingCard b : rowData.getBuildingCards()) rowBox.getChildren().add(createCardNode(b, Color.web("#7E5109")));
    }

    /**
     * Creates a visual node for a single card.
     * <p>
     * If the graphical asset is missing, it dynamically generates a colored rectangle
     * with the Card ID as a fallback, ensuring the game remains playable. It also injects
     * metadata properties (ID and Type) into the node, which are required for user interaction prompts.
     * </p>
     *
     * @param c The model Card object.
     * @param defaultColor The fallback color used if the image is missing.
     * @return A StackPane containing the visual representation of the card.
     */
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

    /**
     * Renders the Turn Order Tile and maps occupied slots to player totems.
     * <p>
     * This method uses absolute pixel coordinates to precisely overlay the player's
     * totem image onto the designated slots drawn on the underlying background image.
     * </p>
     *
     * @param tile The {@link TurnOrderTile} model object containing the current order state.
     */
    private void updateTurnOrderTile(TurnOrderTile tile) {
        if (turnOrderBox == null) return;
        turnOrderBox.getChildren().clear();
        if (tile == null || tile.getTurnOrderSlots() == null) return;

        TurnOrderSlot[] slots = tile.getTurnOrderSlots();
        int numPlayers = slots.length;
        int fileIndex = numPlayers - 1; // 1 = 2p, 2 = 3p, etc.

        double cardW = 80, cardH = 140, totemW = 40, totemH = 40;

        // Pre-calculated vertical centers (in pixels) mapping to the background image.
        double[][] yCenters = {
                {},
                {29, 55},
                {25, 51, 77},
                {13, 39, 65, 91},
                {6, 32, 58, 84, 110}
        };

        StackPane tilePane = new StackPane();
        tilePane.setPrefSize(cardW, cardH);

        try {
            String path = "/images/cards/orderCards/turnorderslot_" + fileIndex + ".png";
            Image img = new Image(getClass().getResourceAsStream(path));
            if (img.isError()) throw new Exception();
            ImageView bgImage = new ImageView(img);
            bgImage.setFitWidth(cardW);
            bgImage.setFitHeight(cardH);
            bgImage.setPreserveRatio(false);
            tilePane.getChildren().add(bgImage);
        } catch (Exception e) {
            Rectangle rect = new Rectangle(cardW, cardH, Color.web("#34495E"));
            tilePane.getChildren().add(rect);
        }

        Pane totemsOverlay = new Pane();
        totemsOverlay.setPrefSize(cardW, cardH);
        double[] yPos = yCenters[fileIndex];

        for (int i = 0; i < numPlayers; i++) {
            TurnOrderSlot s = slots[i];
            if (s.isOccupied()) {
                ImageView totemImg = getTotemImageView(s.getTotemColor());
                if (totemImg != null) {
                    totemImg.setLayoutX((cardW - totemW) / 2.0);
                    totemImg.setLayoutY(yPos[i] - totemH / 2.0);
                    totemsOverlay.getChildren().add(totemImg);
                }
            }
        }

        tilePane.getChildren().add(totemsOverlay);
        turnOrderBox.getChildren().add(tilePane);
    }

    /**
     * Renders the Offering Track and places player totems on occupied tiles.
     * Uses absolute positioning to place the totem exactly inside the designated area.
     *
     * @param track List of OfferingTiles representing the current track.
     */
    private void updateOfferingTrack(List<OfferingTile> track) {
        if (offeringTrackBox == null) return;
        offeringTrackBox.getChildren().clear();
        if (track == null) return;

        double cardW = 100, cardH = 140;
        double totemW = 40, totemH = 40;
        double whiteBoxCenterY = 30.0;

        for (OfferingTile t : track) {
            StackPane tilePane = new StackPane();
            tilePane.setPrefSize(cardW, cardH);

            try {
                String path = "/images/cards/offerTile/offertile_" + t.getLetter() + ".png";
                Image img = new Image(getClass().getResourceAsStream(path));
                if (img.isError()) throw new Exception();
                ImageView bgImage = new ImageView(img);
                bgImage.setFitWidth(cardW);
                bgImage.setFitHeight(cardH);
                bgImage.setPreserveRatio(false);
                tilePane.getChildren().add(bgImage);
            } catch (Exception e) {
                Rectangle rect = new Rectangle(cardW, cardH, Color.web("#1E8449"));
                tilePane.getChildren().add(rect);
            }

            Pane totemOverlay = new Pane();
            totemOverlay.setPrefSize(cardW, cardH);

            if (t.isOccupied()) {
                ImageView totemImg = getTotemImageView(t.getCurrentTotemColor());
                if (totemImg != null) {
                    totemImg.setLayoutX((cardW - totemW) / 2.0);
                    totemImg.setLayoutY(whiteBoxCenterY - (totemH / 2.0));
                    totemOverlay.getChildren().add(totemImg);
                }
            }

            tilePane.getChildren().add(totemOverlay);
            offeringTrackBox.getChildren().add(tilePane);
        }
    }

    // =========================================================================================
    // USER PROMPT & INTERACTION LOGIC
    // =========================================================================================

    /**
     * Activates the UI state allowing the local player to select an Offering Tile.
     * Initializes the interaction memory state and delegates visual application.
     *
     * @param trackSize The maximum index of tiles available for selection.
     * @param future The CompletableFuture to complete with the chosen tile index.
     */
    public void enableOfferingTileSelection(int trackSize, CompletableFuture<Integer> future) {
        this.isOfferingPromptActive = true;
        this.activeOfferingTrackSize = trackSize;
        this.activeOfferingFuture = future;
        applyOfferingTileSelectionVisuals();
    }

    /**
     * Applies glowing highlights and mouse-click listeners to selectable offering tiles.
     * Completes the asynchronous future upon user click, waking up the network thread.
     */
    private void applyOfferingTileSelectionVisuals() {
        if (!isOfferingPromptActive || offeringTrackBox == null) return;
        if (phaseLabel != null) {
            phaseLabel.setText("IT'S YOUR TURN! Click an Offering Tile to place your totem.");
            phaseLabel.setTextFill(Color.web("#EFE4CB"));
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

    /**
     * Activates the UI state allowing the local player to pick multiple cards from a specific row.
     *
     * @param rowName "UPPER" or "LOWER".
     * @param targetType "CHARACTER", "BUILDING", or "EVENT".
     * @param budget The maximum number of cards the player is allowed to pick.
     * @param future The CompletableFuture to complete with the list of chosen card IDs.
     */
    public void enableCardSelection(String rowName, String targetType, int budget, CompletableFuture<List<Integer>> future) {
        this.isCardPromptActive = true;
        this.activeCardRowName = rowName;
        this.activeCardTargetType = targetType;
        this.activeCardBudget = budget;
        this.activeCardFuture = future;
        this.currentSelectedCardIds.clear();
        applyCardSelectionVisuals();
    }

    /**
     * Renders card selection visuals, including dynamic "Skip" and "Confirm" action buttons.
     * <p>
     * The "Confirm" button remains disabled until at least one
     * valid card is picked. The "Skip" button is provided to explicitly bypass the selection
     * (e.g., passing on a turn or saving food budget for building purchases).
     * </p>
     */
    private void applyCardSelectionVisuals() {
        if (!isCardPromptActive) return;

        if (phaseLabel != null) {
            String hint = "CHARACTER".equals(activeCardTargetType) ?
                    " (Click Skip to save picks for Buildings)." : " (Click Skip to pass).";
            String instruction;
            if (activeCardBudget > 1) {
                instruction = "YOUR TURN! Select up to " + activeCardBudget + " " + activeCardTargetType + "S, THEN click CONFIRM" + hint;
            } else {
                instruction = "YOUR TURN! Select 1 " + activeCardTargetType + ", then click CONFIRM" + hint;
            }

            phaseLabel.setText(instruction);
            phaseLabel.setTextFill(Color.web("#EFE4CB"));
        }

        HBox targetBox = activeCardRowName.equals("UPPER") ? upperRowBox : lowerRowBox;
        if (targetBox == null) return;

        if (myStatusBox != null) {
            myStatusBox.getChildren().removeIf(n -> "actionButtonsBox".equals(n.getId()) || "confirmSelectionBtn".equals(n.getId()));

            HBox actionButtonsBox = new HBox(15);
            actionButtonsBox.setId("actionButtonsBox");
            actionButtonsBox.setAlignment(Pos.CENTER);

            // Skip Button logic: completes future with an empty list.
            Button skipBtn = new Button("SKIP");
            skipBtn.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-family: 'Tempus Sans ITC'; -fx-font-weight: bold; -fx-font-size: 14; -fx-cursor: hand;");
            skipBtn.setOnAction(e -> {
                CompletableFuture<List<Integer>> f = activeCardFuture;
                disableSelectionStates();
                if (f != null) f.complete(new ArrayList<>());
            });

            // Confirm Button logic: verifies user selection and completes the future.
            Button confirmBtn = new Button("CONFIRM SELECTION");
            confirmBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-family: 'Tempus Sans ITC'; -fx-font-weight: bold; -fx-font-size: 14; -fx-cursor: hand;");
            confirmBtn.setDisable(true);
            confirmBtn.setOnAction(e -> {
                List<Integer> result = new ArrayList<>(currentSelectedCardIds);
                CompletableFuture<List<Integer>> f = activeCardFuture;
                disableSelectionStates();
                if (f != null) f.complete(result);
            });

            actionButtonsBox.getChildren().addAll(skipBtn, confirmBtn);
            myStatusBox.getChildren().add(actionButtonsBox);

            // Toggles the confirm button state based on selection emptiness.
            Runnable updateButtonsState = () -> {
                confirmBtn.setDisable(currentSelectedCardIds.isEmpty());
            };

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
                            // Enforce selection budget limit visually.
                            if (currentSelectedCardIds.size() < activeCardBudget) {
                                currentSelectedCardIds.add(cardId);
                                node.setStyle("-fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, #00ff00, 20, 0, 0, 0);");
                            }
                        }
                        updateButtonsState.run();
                    });
                }
            }
        }
    }

    /**
     * Activates the UI state allowing the local player to select a single extra card
     * (triggered by a specific building).
     *
     * @param targetType The card type allowed for selection.
     * @param future The CompletableFuture to complete with the single chosen card ID.
     */
    public void enableSingleCardSelection(String targetType, CompletableFuture<Integer> future) {
        this.isSingleCardPromptActive = true;
        this.activeSingleCardTargetType = targetType;
        this.activeSingleCardFuture = future;
        applySingleCardSelectionVisuals();
    }

    /**
     * Applies interaction listeners for a single card pick scenario.
     * Completes the future immediately upon card click, bypassing the need for a confirm button.
     */
    private void applySingleCardSelectionVisuals() {
        if (!isSingleCardPromptActive) return;
        if (phaseLabel != null) {
            phaseLabel.setText("SELECT YOUR EXTRA CARD: Click on the card you want to pick.");
            phaseLabel.setTextFill(Color.web("#EFE4CB"));
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

    /**
     * Clears all active interaction states, removes glow effects, and hides action buttons.
     * This is called automatically after a user fulfills an interaction prompt to reset the UI.
     */
    private void disableSelectionStates() {
        isOfferingPromptActive = false;
        isCardPromptActive = false;
        isSingleCardPromptActive = false;

        if (phaseLabel != null) {
            phaseLabel.setText("Waiting for other players...");
            phaseLabel.setTextFill(Color.web("#EFE4CB"));
        }

        if (myStatusBox != null) {
            myStatusBox.getChildren().removeIf(n ->
                    "actionButtonsBox".equals(n.getId()) || "confirmSelectionBtn".equals(n.getId())
            );
        }

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

    // =========================================================================================
    // UTILITY & HELPER METHODS
    // =========================================================================================

    /**
     * Retrieves the graphical asset for a specific Totem Color.
     *
     * @param color The TotemColor enum value.
     * @return The ImageView containing the totem image, or null if loading fails.
     */
    private ImageView getTotemImageView(TotemColor color) {
        if (color == null) return null;
        try {
            String path = "/images/" + color.name().toLowerCase() + "_totem.png";
            ImageView iv = new ImageView(new Image(getClass().getResourceAsStream(path)));
            iv.setFitWidth(40); iv.setFitHeight(40); iv.setPreserveRatio(true);
            return iv;
        } catch (Exception e) { return null; }
    }

    /**
     * Translates a game model TotemColor into a JavaFX paint Color for UI text/border styling.
     *
     * @param color The TotemColor to translate.
     * @return The corresponding JavaFX Color.
     */
    private Color getJavaFXColor(TotemColor color) {
        if (color == null) return Color.WHITE;
        return switch (color) {
            case RED -> Color.web("#EC7063"); case BLUE -> Color.web("#5DADE2");
            case YELLOW -> Color.web("#F4D03F"); case BLACK -> Color.web("#566573");
            case WHITE -> Color.web("#FBFCFC");
        };
    }

    /**
     * Formats a JavaFX Color to its standard Hexadecimal String representation.
     * * @param color The JavaFX Color object.
     * @return The hexadecimal string.
     */
    private String toHexString(Color color) {
        return String.format("#%02X%02X%02X", (int)(color.getRed()*255), (int)(color.getGreen()*255), (int)(color.getBlue()*255));
    }

    /**
     * Creates a standardized UI element pairing a graphical icon with a numerical value.
     * Includes a tooltip with a bold title and a detailed description.
     * If the icon image file is not found, it falls back to a text-only representation.
     *
     * @param iconName    The filename of the icon (must reside in /images/icons/).
     * @param value       The numerical statistic to display.
     * @param iconSize    The squared size constraint for the icon in pixels.
     * @param title       The bold title for the tooltip.
     * @param description The detailed description for the tooltip.
     * @return An HBox containing the formatted icon and text, complete with a hover tooltip.
     */
    private HBox createIconStat(String iconName, int value, int iconSize, String title, String description) {
        HBox box = new HBox(5);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPickOnBounds(true);

        if (title != null && description != null) {
            String tooltipContent = title.toUpperCase() + "\n" + description;
            javafx.scene.control.Tooltip tooltip = new javafx.scene.control.Tooltip(tooltipContent);
            tooltip.setFont(Font.font("Tempus Sans ITC", FontWeight.BOLD, 14));
            tooltip.setStyle("-fx-background-color: #4B1419ff; -fx-text-fill: #EFE4CBff; -fx-border-color: #EFE4CBff; -fx-border-width: 1px;");
            tooltip.setShowDelay(javafx.util.Duration.seconds(0.05));
            javafx.scene.control.Tooltip.install(box, tooltip);
        }

        try {
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
            String cleanName = iconName.split("\\.")[0].toUpperCase();
            Label lbl = new Label(cleanName + ": " + value);
            lbl.setTextFill(Color.WHITE);
            box.getChildren().add(lbl);
        }
        return box;
    }

    /**
     * Translates the raw game Phase object into a user-friendly string for the UI banner.
     *
     * @param phase The current phase of the match.
     * @return A readable string description of the current ongoing event.
     */
    private String getPhaseDescription(it.polimi.ingsw.common.dto.Phase phase) {
        if (phase == null) return "Waiting...";

        if (phase instanceof it.polimi.ingsw.common.dto.Phase.WaitingForNickname) return "Waiting for players to connect";
        if (phase instanceof it.polimi.ingsw.common.dto.Phase.WaitingForPlayerCount) return "Waiting for lobby size selection";
        if (phase instanceof it.polimi.ingsw.common.dto.Phase.WaitingForPlayers) return "Waiting for players to join";
        if (phase instanceof it.polimi.ingsw.common.dto.Phase.ColorPicking) return "Picking totem colors";
        if (phase instanceof it.polimi.ingsw.common.dto.Phase.PickOfferingTile) return "Placing totems on the Offering Track";
        if (phase instanceof it.polimi.ingsw.common.dto.Phase.TurnPickCardsCharacter) return "Drawing Character and Event cards";
        if (phase instanceof it.polimi.ingsw.common.dto.Phase.TurnPickCardsBuilding) return "Constructing Building cards";
        if (phase instanceof it.polimi.ingsw.common.dto.Phase.ResetTurnOrderAndPickExtraBuilding) return "Choosing a bonus extra card";
        if (phase instanceof it.polimi.ingsw.common.dto.Phase.ResolveBuildingsAndEvents) return "Resolving round effects...";
        if (phase instanceof it.polimi.ingsw.common.dto.Phase.PausedWaitingReconnect) return "Match paused (Waiting for reconnect)";
        if (phase instanceof it.polimi.ingsw.common.dto.Phase.GameEnded) return "Match is Over";

        return "Waiting...";
    }

    /**
     * Applies a scaling and glowing drop-shadow hover effect to the specified button.
     *
     * @param btn The button to apply the interactive hover effect to.
     */
    private void addHoverEffect(Button btn) {
        javafx.scene.effect.DropShadow glow = new javafx.scene.effect.DropShadow();
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
     * Handles the "Leave Match" action button click.
     * Disconnects the client from the server and safely aborts the active session returning to the lobby.
     */
    @FXML
    public void onLeaveMatchClicked() {
        GuiView.abortLobbyAndDisconnect();
    }
}