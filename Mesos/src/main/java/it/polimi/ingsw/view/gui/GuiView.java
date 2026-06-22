package it.polimi.ingsw.view.gui;

import it.polimi.ingsw.common.dto.Phase;
import it.polimi.ingsw.controller.client.ClientController;
import it.polimi.ingsw.view.View;
import it.polimi.ingsw.model.enums.TotemColor;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Main Graphical User Interface (GUI) implementation of the View.
 * This class acts as the bridge between the client's network/logic controllers
 * and the JavaFX UI. It implements the {@link View} interface to handle asynchronous
 * prompts, popups, scene changes, and UI refreshes based on the game state.
 */
public class GuiView implements View {

    private static GuiView instance;

    private static CompletableFuture<String> nicknameFuture;
    private static CompletableFuture<Void> startClickedFuture = new CompletableFuture<>();
    private static CompletableFuture<Integer> playerCountFuture;
    private static Stage mainStage;
    private static Runnable onStageReady;
    private static boolean isReconnecting = false;
    private static ClientController clientController;

    // Switch to "blind" the graphics during the exit/abort process
    private boolean isAborting = false;

    private Stage currentPopupStage;
    private Stage lobbyStage;
    private NicknameController nicknameController;
    private LobbyController lobbyController;
    private TotemSelectionController totemController;
    private boolean isGameBoardLoaded = false;
    private GameBoardController boardController;

    // References to game over UI controllers
    private GameOverController gameOverController;
    private LeaderboardController leaderboardController;

    /**
     * Constructor for GuiView.
     * Saves the current instance to a static variable, acting as a Singleton bridge
     * to allow static methods to access instance-level UI components.
     */
    public GuiView() {
        instance = this;
    }

    /**
     * Sets a callback to be executed once the primary JavaFX stage is ready.
     *
     * @param runnable The action to execute when the stage is set.
     */
    public static void setOnStageReady(Runnable runnable) {
        onStageReady = runnable;
    }

    /**
     * Injects the main JavaFX Stage into the view and triggers the ready callback.
     *
     * @param stage The primary stage of the application.
     */
    public static void setMainStage(Stage stage) {
        mainStage = stage;
        if (onStageReady != null) {
            onStageReady.run();
        }
    }

    /**
     * Instance method to handle the "Start" button click event from the Welcome screen.
     * Manages popups based on whether the user is reconnecting or starting a fresh game.
     */
    public void notifyStartClickedInstance() {
        isAborting = false;

        if (isReconnecting) {
            Platform.runLater(this::openNicknamePopup);
        } else if (!startClickedFuture.isDone()) {
            startClickedFuture.complete(null);
        } else if (currentPopupStage != null && !currentPopupStage.isShowing()) {
            Platform.runLater(() -> currentPopupStage.show());
        }
    }

    /**
     * Static wrapper to handle the "Start" button click event.
     * Delegates the action to the active GuiView instance.
     */
    public static void notifyStartClicked() {
        if (instance != null) {
            instance.notifyStartClickedInstance();
        } else if (!startClickedFuture.isDone()) {
            startClickedFuture.complete(null);
        }
    }

    /**
     * Instance method to process the submitted nickname.
     *
     * @param nickname The chosen nickname.
     */
    public void submitNicknameInstance(String nickname) {
        if (isReconnecting && clientController != null) {
            if (currentPopupStage != null && currentPopupStage.isShowing()) {
                Platform.runLater(() -> currentPopupStage.close());
            }
            new Thread(() -> clientController.reconnectNewGame(nickname)).start();
        } else if (nicknameFuture != null) {
            nicknameFuture.complete(nickname);
        }
    }

    /**
     * Static wrapper to process the submitted nickname.
     * Delegates the action to the active GuiView instance.
     *
     * @param nickname The chosen nickname.
     */
    public static void submitNickname(String nickname) {
        if (instance != null) {
            instance.submitNicknameInstance(nickname);
        } else if (nicknameFuture != null) {
            nicknameFuture.complete(nickname);
        }
    }

    /**
     * Completes the future for the selected player count.
     *
     * @param count The number of players chosen.
     */
    public static void submitPlayerCount(int count) {
        if (playerCountFuture != null) {
            playerCountFuture.complete(count);
        }
    }

    /**
     * Resets the asynchronous futures to prepare the View for a new game session.
     */
    public static void resetForNewGame() {
        startClickedFuture = new CompletableFuture<>();
        nicknameFuture = null;
        playerCountFuture = null;
    }

    @Override
    public String promptNickname() {
        nicknameFuture = new CompletableFuture<>();

        if (currentPopupStage != null && currentPopupStage.isShowing()) {
            try { return nicknameFuture.get(); } catch (Exception e) { return null; }
        }

        new Thread(() -> {
            try {
                startClickedFuture.get();
                Platform.runLater(this::openNicknamePopup);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        try {
            return nicknameFuture.get();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void displayError(String message) {
        Platform.runLater(() -> {
            if (currentPopupStage != null && currentPopupStage.isShowing() && nicknameController != null) {
                nicknameController.showError("This nickname is already taken!");
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText("Connection Error");
                alert.setContentText(message);
                alert.showAndWait();
            }
        });
    }

    @Override
    public void displayConnected(String host, int port) {}

    @Override
    public void displayInfo(String message) {}

    @Override
    public void refresh(ClientController state) {
        Platform.runLater(() -> {
            clientController = state;
            Phase currentPhase = state.getCurrentPhase();

            // Close the Nickname popup as soon as the server accepts us and updates the phase
            if (currentPopupStage != null && currentPopupStage.isShowing() && "Enter Nickname".equals(currentPopupStage.getTitle())) {
                if (currentPhase != null && !(currentPhase instanceof Phase.WaitingForNickname)) {
                    currentPopupStage.close();
                    nicknameController = null;
                }
            }

            boolean isPreGame = (currentPhase == null ||
                    currentPhase instanceof Phase.WaitingForNickname ||
                    currentPhase instanceof Phase.WaitingForPlayerCount ||
                    currentPhase instanceof Phase.WaitingForPlayers);

            // We ONLY show the color picking popup if the server explicitly tells us we are in that phase.
            boolean isColorPicking = (currentPhase instanceof Phase.ColorPicking);

            // Keep the Lobby open even during COLOR_PICKING as a background
            if ((currentPhase instanceof Phase.WaitingForPlayers || isColorPicking) && !isAborting) {
                handleLobbyPhase(state);
            } else {
                closeLobbyIfOpen();
            }

            // Handle Totem Selection Popup
            if (isColorPicking) {
                if (currentPopupStage != null && currentPopupStage.isShowing() && totemController == null) {
                    currentPopupStage.close();
                }
                showTotemPopup(state);
            } else {
                closeTotemPopupIfOpen();
            }

            boolean isGameStarted = !isPreGame && !isColorPicking && !(currentPhase instanceof Phase.GameEnded);

            // Handle Game Ended Phase
            if (currentPhase instanceof Phase.GameEnded) {
                if (currentPopupStage != null && currentPopupStage.isShowing()) {
                    currentPopupStage.close();
                    currentPopupStage = null;
                }
                // Call the match game over screen
                showMatchGameOverScreen(state);
            }
            // If the game is started and NOT ended, show the normal Game Board
            else if (isGameStarted) {
                if (currentPopupStage != null && currentPopupStage.isShowing()) {
                    currentPopupStage.close();
                    currentPopupStage = null;
                }
                ensureGameBoardLoaded();

                if (isGameBoardLoaded && boardController != null) {
                    boardController.updateBoard(state);
                }
            }
        });
    }

    /**
     * Ensures that the main Game Board FXML is loaded and displayed on the primary stage.
     */
    private void ensureGameBoardLoaded() {
        if (!isGameBoardLoaded) {
            try {
                FXMLLoader boardLoader = new FXMLLoader(getClass().getResource("/fxml/game_board.fxml"));
                Parent boardRoot = boardLoader.load();
                boardController = boardLoader.getController();

                mainStage.setScene(new Scene(boardRoot));
                mainStage.centerOnScreen();

                isGameBoardLoaded = true;
            } catch (Exception e) {
                System.err.println("GRAPHIC ERROR: Loading failed for 'game_board.fxml'.");
                e.printStackTrace();
            }
        }
    }

    /**
     * Handles the logic for rendering the Lobby phase.
     *
     * @param state The current client controller state.
     */
    private void handleLobbyPhase(ClientController state) {
        if (lobbyController == null) setupLobbyScreen();
        if (lobbyController != null) lobbyController.updateLobbyData(state.getJoinedPlayers(), state.getExpectedPlayers());
    }

    /**
     * Loads and displays the Lobby popup window.
     */
    private void setupLobbyScreen() {
        try {
            FXMLLoader lobbyLoader = new FXMLLoader(getClass().getResource("/fxml/lobby.fxml"));
            Parent lobbyRoot = lobbyLoader.load();
            lobbyController = lobbyLoader.getController();

            lobbyStage = new Stage();
            lobbyStage.setTitle("Lobby");
            lobbyStage.initModality(Modality.NONE);
            lobbyStage.initOwner(mainStage);
            lobbyStage.initStyle(StageStyle.TRANSPARENT);

            Scene lobbyScene = new Scene(lobbyRoot);
            lobbyScene.setFill(Color.TRANSPARENT);
            lobbyStage.setScene(lobbyScene);
            lobbyStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Closes the Lobby popup if it is currently open.
     */
    private void closeLobbyIfOpen() {
        if (lobbyController != null) {
            if (lobbyStage != null && lobbyStage.isShowing()) lobbyStage.close();
            lobbyController = null;
        }
    }

    /**
     * Loads and displays the Totem Selection popup window.
     *
     * @param state The current client controller state containing available colors.
     */
    private void showTotemPopup(ClientController state) {
        try {
            if (totemController == null) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/totem_selection.fxml"));
                Parent root = loader.load();
                totemController = loader.getController();

                currentPopupStage = new Stage();
                currentPopupStage.initModality(Modality.APPLICATION_MODAL);
                if (mainStage != null) currentPopupStage.initOwner(mainStage);
                currentPopupStage.initStyle(StageStyle.TRANSPARENT);

                Scene scene = new Scene(root);
                scene.setFill(Color.TRANSPARENT);
                currentPopupStage.setScene(scene);
                currentPopupStage.show();
            }

            String myName = state.getMyNickname() != null ? state.getMyNickname() : "";
            String currentPlayer = state.getCurrentPlayer() != null ? state.getCurrentPlayer() : "";

            Set<TotemColor> available = state.getAvailableColors();
            if (available == null) available = new HashSet<>();

            totemController.updateState(currentPlayer, myName, available, new CompletableFuture<>());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Closes the Totem Selection popup if it is currently open.
     */
    private void closeTotemPopupIfOpen() {
        if (totemController != null && currentPopupStage != null && currentPopupStage.isShowing()) {
            currentPopupStage.close();
            totemController = null;
        }
    }

    @Override
    public TotemColor promptColor(Set<TotemColor> available) {
        CompletableFuture<TotemColor> colorFuture = new CompletableFuture<>();
        Platform.runLater(() -> {
            if (totemController == null && clientController != null) {
                showTotemPopup(clientController);
            }
            if (totemController != null && clientController != null) {
                totemController.updateState(clientController.getCurrentPlayer(), clientController.getMyNickname(), available, colorFuture);
            }
        });
        try { return colorFuture.get(); } catch (Exception e) { return null; }
    }

    @Override
    public int promptPlayerCount() {
        playerCountFuture = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                if (currentPopupStage != null && currentPopupStage.isShowing()) currentPopupStage.close();
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/player_count.fxml"));
                Parent root = loader.load();

                currentPopupStage = new Stage();
                currentPopupStage.setTitle("Select Number of Players");
                currentPopupStage.initModality(Modality.APPLICATION_MODAL);
                if (mainStage != null) currentPopupStage.initOwner(mainStage);
                currentPopupStage.initStyle(StageStyle.TRANSPARENT);

                Scene scene = new Scene(root);
                scene.setFill(Color.TRANSPARENT);
                currentPopupStage.setScene(scene);
                currentPopupStage.setResizable(false);
                currentPopupStage.show();
            } catch (Exception e) { e.printStackTrace(); }
        });

        try {
            int count = playerCountFuture.get();
            Platform.runLater(() -> { if (currentPopupStage != null) currentPopupStage.close(); });
            return count;
        } catch (Exception e) {
            return 2;
        }
    }

    /**
     * Opens the popup to prompt the user for their Nickname.
     */
    private void openNicknamePopup() {
        try {
            FXMLLoader loader = new FXMLLoader(GuiView.class.getResource("/fxml/nickname_popup.fxml"));
            Parent root = loader.load();
            nicknameController = loader.getController();

            currentPopupStage = new Stage();
            currentPopupStage.setTitle("Enter Nickname");
            currentPopupStage.initModality(Modality.APPLICATION_MODAL);
            if (mainStage != null) currentPopupStage.initOwner(mainStage);
            currentPopupStage.initStyle(StageStyle.TRANSPARENT);

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            currentPopupStage.setScene(scene);
            currentPopupStage.setResizable(false);
            currentPopupStage.show();
        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Aborts the player count selection process.
     */
    public static void abortPlayerCount() {
        if (playerCountFuture != null) playerCountFuture.cancel(true);
    }

    /**
     * Instance method to handle a clean exit from the lobby
     * and return the user to the Welcome screen.
     */
    public void abortLobbyAndDisconnectInstance() {
        isAborting = true;

        if (clientController != null) clientController.leaveLobbySoft();
        Platform.runLater(() -> {
            try {
                if (currentPopupStage != null && currentPopupStage.isShowing()) currentPopupStage.close();
                if (lobbyStage != null && lobbyStage.isShowing()) lobbyStage.close();

                FXMLLoader loader = new FXMLLoader(GuiView.class.getResource("/fxml/welcome.fxml"));
                Parent root = loader.load();
                mainStage.setScene(new Scene(root));
                mainStage.centerOnScreen();

                isReconnecting = true;
                lobbyController = null;
                isGameBoardLoaded = false;
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    /**
     * Static wrapper to safely disconnect from the lobby.
     * Delegates the action to the active GuiView instance.
     */
    public static void abortLobbyAndDisconnect() {
        if (instance != null) {
            instance.abortLobbyAndDisconnectInstance();
        }
    }

    @Override
    public int promptOfferingTile(int trackSize) {
        CompletableFuture<Integer> tileFuture = new CompletableFuture<>();
        Platform.runLater(() -> {
            if (boardController != null) boardController.enableOfferingTileSelection(trackSize, tileFuture);
        });
        try { return tileFuture.get(); } catch (Exception e) { return 0; }
    }

    @Override
    public List<Integer> promptCharacterUpperIds(int budget) {
        if (budget <= 0) return new ArrayList<>();

        CompletableFuture<List<Integer>> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            if (boardController != null) boardController.enableCardSelection("UPPER", "CHARACTER", budget, future);
        });
        try { return future.get(); } catch (Exception e) { return new ArrayList<>(); }
    }

    @Override
    public List<Integer> promptCharacterLowerIds(int budget) {
        if (budget <= 0) return new ArrayList<>();

        CompletableFuture<List<Integer>> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            if (boardController != null) boardController.enableCardSelection("LOWER", "CHARACTER", budget, future);
        });
        try { return future.get(); } catch (Exception e) { return new ArrayList<>(); }
    }

    @Override
    public List<Integer> promptBuildingUpperIds(int remaining) {
        if (remaining <= 0) return new ArrayList<>();

        CompletableFuture<List<Integer>> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            if (boardController != null) boardController.enableCardSelection("UPPER", "BUILDING", remaining, future);
        });
        try { return future.get(); } catch (Exception e) { return new ArrayList<>(); }
    }

    @Override
    public List<Integer> promptBuildingLowerIds(int remaining) {
        if (remaining <= 0) return new ArrayList<>();

        CompletableFuture<List<Integer>> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            if (boardController != null) boardController.enableCardSelection("LOWER", "BUILDING", remaining, future);
        });
        try { return future.get(); } catch (Exception e) { return new ArrayList<>(); }
    }

    @Override
    public View.ExtraPickChoice promptExtraCard() {
        CompletableFuture<Integer> idFuture = new CompletableFuture<>();
        CompletableFuture<Boolean> isCharFuture = new CompletableFuture<>();

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Extra Pick");
            alert.setHeaderText("Bonus from special card");
            alert.setContentText("Which card do you want to pick?");

            ButtonType btnChar = new ButtonType("Character");
            ButtonType btnBuild = new ButtonType("Building");
            ButtonType btnSkip = new ButtonType("Skip", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(btnChar, btnBuild, btnSkip);

            alert.showAndWait().ifPresent(res -> {
                if (res == btnSkip) {
                    idFuture.complete(null);
                    isCharFuture.complete(true);
                } else {
                    boolean isChar = (res == btnChar);
                    isCharFuture.complete(isChar);
                    String type = isChar ? "CHARACTER" : "BUILDING";
                    if (boardController != null) boardController.enableSingleCardSelection(type, idFuture);
                }
            });
        });

        try {
            Integer id = idFuture.get();
            Boolean isChar = isCharFuture.get();
            return new View.ExtraPickChoice(id, isChar);
        } catch (Exception e) {
            return new View.ExtraPickChoice();
        }
    }

    // =========================================================================================
    // END OF GAME SCREEN MANAGEMENT METHODS
    // =========================================================================================

    /**
     * Displays the match results on the main stage once the game has concluded.
     */
    private void showMatchGameOverScreen(ClientController state) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/game_over.fxml"));
            Parent root = loader.load();
            gameOverController = loader.getController();

            mainStage.setScene(new Scene(root));
            mainStage.centerOnScreen();

            isGameBoardLoaded = false;
            if (gameOverController != null) {
                gameOverController.updateMatchResults(state);
            }
        } catch (Exception e) {
            System.err.println("GRAPHIC ERROR: Loading failed for 'game_over.fxml'.");
            e.printStackTrace();
        }
    }

    /**
     * Static method allowing the GameOverController to trigger the scene change.
     */
    public static void showGlobalLeaderboardScreen() {
        if (instance != null) {
            instance.showGlobalLeaderboardScreenInstance();
        }
    }

    /**
     * Loads the global database history by swapping the scene on the mainStage.
     */
    private void showGlobalLeaderboardScreenInstance() {
        Platform.runLater(() -> {
            try {
                // 1. LOAD THE FXML AND INITIALIZE THE SCENE
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/game_leaderboard.fxml"));
                Parent root = loader.load();

                // Now that the page is fully loaded, the controller is successfully instantiated
                leaderboardController = loader.getController();

                mainStage.setScene(new Scene(root));
                mainStage.centerOnScreen();

                // 2. PASS THE DATA (No background thread needed, data is already stored in RAM!)
                if (clientController != null && leaderboardController != null) {
                    Map<String, Integer> globalStats = clientController.getGlobalLeaderboard();
                    String myName = clientController.getMyNickname();
                    int expectedCount = clientController.getExpectedPlayers();

                    leaderboardController.updateGlobalLeaderboard(globalStats, myName, expectedCount);
                } else {
                    System.err.println("ERROR: Null controller, unable to pass data to the leaderboard.");
                }

            } catch (Exception e) {
                System.err.println("GRAPHIC ERROR: Fatal failure while loading 'game_leaderboard.fxml'.");
                e.printStackTrace();
            }
        });
    }
}