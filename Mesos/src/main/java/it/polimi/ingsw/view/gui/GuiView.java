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
 * Main Graphical User Interface (GUI) implementation of the {@link View}.
 * <p>
 * This class acts as the bridge between the client's network/logic controllers and the JavaFX UI.
 * Since network events arrive on background threads and JavaFX requires UI updates to happen on the
 * Application Thread, this class makes extensive use of {@code Platform.runLater()} for rendering
 * and {@link CompletableFuture} to block the network thread while waiting for asynchronous user inputs.
 * </p>
 */
public class GuiView implements View {

    // --- Singleton-like Instance ---
    /** * Static reference to the active instance. Allows UI controllers (which are instantiated
     * by JavaFX FXML loaders) to easily communicate back to this main view manager.
     */
    private static GuiView instance;

    // --- Asynchronous Input Futures ---
    private static CompletableFuture<String> nicknameFuture;
    private static CompletableFuture<Void> startClickedFuture = new CompletableFuture<>();
    private static CompletableFuture<Integer> playerCountFuture;

    // --- Core UI State ---
    private static Stage mainStage;
    private static Runnable onStageReady;
    private static boolean isReconnecting = false;
    private static ClientController clientController;
    private boolean isAborting = false;

    // --- Active Controllers & Stages ---
    private Stage currentPopupStage;
    private Stage lobbyStage;
    private NicknameController nicknameController;
    private LobbyController lobbyController;
    private TotemSelectionController totemController;

    private boolean isGameBoardLoaded = false;
    private GameBoardController boardController;
    private GameOverController gameOverController;
    private LeaderboardController leaderboardController;

    /**
     * Constructs the GuiView and registers it as the active instance.
     */
    public GuiView() {
        instance = this;
    }

    /**
     * Sets a callback to be executed once the primary JavaFX Stage is initialized.
     *
     * @param runnable The action to execute when the stage is ready.
     */
    public static void setOnStageReady(Runnable runnable) {
        onStageReady = runnable;
    }

    /**
     * Injects the main JavaFX Stage into the view and triggers the initialization callback.
     *
     * @param stage The primary stage of the application provided by JavaFX.
     */
    public static void setMainStage(Stage stage) {
        mainStage = stage;
        if (onStageReady != null) {
            onStageReady.run();
        }
    }

    /**
     * Handles the logical progression when the user clicks the initial "Start" button.
     * Depending on the state, it either opens the nickname prompt or completes the pending future.
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
     * Static wrapper to handle the "Start" button click event from external UI controllers.
     */
    public static void notifyStartClicked() {
        if (instance != null) {
            instance.notifyStartClickedInstance();
        } else if (!startClickedFuture.isDone()) {
            startClickedFuture.complete(null);
        }
    }

    /**
     * Processes the nickname submitted by the user.
     * If reconnecting, it spins up a new thread to handle the network reconnect.
     * Otherwise, it completes the pending future allowing the login sequence to continue.
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
     * Static wrapper to submit the chosen nickname from external UI controllers.
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
     * Completes the future for the selected player count, unblocking the server setup sequence.
     *
     * @param count The selected number of players.
     */
    public static void submitPlayerCount(int count) {
        if (playerCountFuture != null) {
            playerCountFuture.complete(count);
        }
    }

    /**
     * Resets all asynchronous futures, readying the client for a completely new session.
     */
    public static void resetForNewGame() {
        startClickedFuture = new CompletableFuture<>();
        nicknameFuture = null;
        playerCountFuture = null;
    }

    // =========================================================================================
    // VIEW INTERFACE IMPLEMENTATION (Synchronous blocking calls for the network thread)
    // =========================================================================================

    @Override
    public String promptNickname() {
        nicknameFuture = new CompletableFuture<>();

        if (currentPopupStage != null && currentPopupStage.isShowing()) {
            try { return nicknameFuture.get(); } catch (Exception e) { return null; }
        }

        // Wait for the user to click start, then show the nickname popup
        new Thread(() -> {
            try {
                startClickedFuture.get();
                Platform.runLater(this::openNicknamePopup);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        try {
            return nicknameFuture.get(); // Blocks caller until the user submits the form
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void displayError(String message) {
        Platform.runLater(() -> {
            // If the nickname popup is open, show the error inside it inline
            if (currentPopupStage != null && currentPopupStage.isShowing() && nicknameController != null) {
                nicknameController.showError("This nickname is already taken!");
            } else {
                // Otherwise show a generic OS-level alert
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

    /**
     * The core rendering engine of the GUI. Called by the ClientController whenever the model updates.
     * It evaluates the current game phase and delegates rendering to the appropriate FXML controller.
     *
     * @param state The updated client-side state of the game.
     */
    @Override
    public void refresh(ClientController state) {
        Platform.runLater(() -> {
            clientController = state;
            Phase currentPhase = state.getCurrentPhase();

            // 1. Clean up login popups if we successfully moved past the nickname phase
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

            boolean isColorPicking = (currentPhase instanceof Phase.ColorPicking);

            // 2. Manage the Lobby Screen
            if ((currentPhase instanceof Phase.WaitingForPlayers || isColorPicking) && !isAborting) {
                handleLobbyPhase(state);
            } else {
                closeLobbyIfOpen();
            }

            // 3. Manage the Totem Selection Popup
            if (isColorPicking) {
                if (currentPopupStage != null && currentPopupStage.isShowing() && totemController == null) {
                    currentPopupStage.close();
                }
                showTotemPopup(state);
            } else {
                closeTotemPopupIfOpen();
            }

            boolean isGameStarted = !isPreGame && !isColorPicking && !(currentPhase instanceof Phase.GameEnded);

            // 4. Manage Game Over and Leaderboards
            if (currentPhase instanceof Phase.GameEnded) {
                if (currentPopupStage != null && currentPopupStage.isShowing()) {
                    currentPopupStage.close();
                    currentPopupStage = null;
                }
                showMatchGameOverScreen(state);
            }
            // 5. Manage the Main Game Board rendering
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
     * Initializes the main Game Board scene on the primary stage if it hasn't been loaded yet.
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
     * Renders or updates the Lobby phase UI.
     *
     * @param state The current client controller state.
     */
    private void handleLobbyPhase(ClientController state) {
        if (lobbyController == null) setupLobbyScreen();
        if (lobbyController != null) lobbyController.updateLobbyData(state.getJoinedPlayers(), state.getExpectedPlayers());
    }

    /**
     * Instantiates the Lobby popup as a transparent, non-modal window over the main stage.
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

    private void closeLobbyIfOpen() {
        if (lobbyController != null) {
            if (lobbyStage != null && lobbyStage.isShowing()) lobbyStage.close();
            lobbyController = null;
        }
    }

    /**
     * Instantiates and configures the Totem Selection popup.
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
            return 2; // Safe default
        }
    }

    /**
     * Loads and displays the initial Nickname prompt popup.
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
     * Cancels the player count selection process if interrupted.
     */
    public static void abortPlayerCount() {
        if (playerCountFuture != null) playerCountFuture.cancel(true);
    }

    /**
     * Safely tears down the active lobby connection and returns the user to the Welcome screen.
     * Prevents GUI glitches by blinding rapid state updates during the transition.
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
     * Static wrapper for disconnecting from the lobby.
     */
    public static void abortLobbyAndDisconnect() {
        if (instance != null) {
            instance.abortLobbyAndDisconnectInstance();
        }
    }

    // =========================================================================================
    // IN-GAME PROMPTS (Delegated to GameBoardController)
    // =========================================================================================

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
    // END OF GAME SCREEN MANAGEMENT
    // =========================================================================================

    /**
     * Swaps the main scene to display the match results leaderboard once the game concludes.
     *
     * @param state The final state of the game.
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
     * Static method allowing internal GUI controllers to trigger the scene change
     * to the global historical leaderboard.
     */
    public static void showGlobalLeaderboardScreen() {
        if (instance != null) {
            instance.showGlobalLeaderboardScreenInstance();
        }
    }

    /**
     * Loads the global database history by swapping the scene on the mainStage.
     * Retrieves the necessary leaderboard data directly from the client controller.
     */
    private void showGlobalLeaderboardScreenInstance() {
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/game_leaderboard.fxml"));
                Parent root = loader.load();

                leaderboardController = loader.getController();

                mainStage.setScene(new Scene(root));
                mainStage.centerOnScreen();

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