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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class GuiView implements View {

    private static CompletableFuture<String> nicknameFuture;
    private static CompletableFuture<Void> startClickedFuture = new CompletableFuture<>();
    private static CompletableFuture<Integer> playerCountFuture;
    private static Stage mainStage;
    private static Runnable onStageReady;
    private static boolean isReconnecting = false;
    private static ClientController clientController;

    private Stage currentPopupStage;
    private Stage lobbyStage;
    private NicknameController nicknameController;
    private LobbyController lobbyController;
    private TotemSelectionController totemController;
    private boolean isGameBoardLoaded = false;
    private GameBoardController boardController;

    public static void setOnStageReady(Runnable runnable) {
        onStageReady = runnable;
    }

    public static void setMainStage(Stage stage) {
        mainStage = stage;
        if (onStageReady != null) {
            onStageReady.run();
        }
    }

    public void notifyStartClickedInstance() {
        if (isReconnecting) {
            Platform.runLater(this::openNicknamePopup);
        } else if (!startClickedFuture.isDone()) {
            startClickedFuture.complete(null);
        } else if (currentPopupStage != null && !currentPopupStage.isShowing()) {
            Platform.runLater(() -> currentPopupStage.show());
        }
    }

    public static void notifyStartClicked() {
        if (!startClickedFuture.isDone()) {
            startClickedFuture.complete(null);
        }
    }

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

    public static void submitNickname(String nickname) {
        if (nicknameFuture != null) nicknameFuture.complete(nickname);
    }

    public static void submitPlayerCount(int count) {
        if (playerCountFuture != null) {
            playerCountFuture.complete(count);
        }
    }

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

            if (currentPhase == Phase.WAITING_FOR_PLAYERS) {
                handleLobbyPhase(state);
            } else {
                closeLobbyIfOpen();
            }

            if (currentPhase == Phase.COLOR_PICKING) {
                if (currentPopupStage != null && currentPopupStage.isShowing() && totemController == null) {
                    currentPopupStage.close();
                }
                showTotemPopup(state);
            } else {
                closeTotemPopupIfOpen();
            }

            boolean isGameStarted = (currentPhase != Phase.WAITING_FOR_NICKNAME &&
                    currentPhase != Phase.WAITING_FOR_PLAYER_COUNT &&
                    currentPhase != Phase.WAITING_FOR_PLAYERS &&
                    currentPhase != Phase.COLOR_PICKING);

            if (isGameStarted) {
                ensureGameBoardLoaded();

                if (isGameBoardLoaded && boardController != null) {
                    boardController.updateBoard(state);
                }
            }
        });
    }

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

    private void handleLobbyPhase(ClientController state) {
        if (lobbyController == null) setupLobbyScreen();
        if (lobbyController != null) lobbyController.updateLobbyData(state.getJoinedPlayers(), state.getExpectedPlayers());
    }

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
            totemController.updateState(currentPlayer, myName, state.getAvailableColors(), new CompletableFuture<>());

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
        } catch (Exception e) { return 2; }
    }

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

    public static void abortPlayerCount() {
        if (playerCountFuture != null) playerCountFuture.cancel(true);
    }

    public void abortLobbyAndDisconnectInstance() {
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

    public static void abortLobbyAndDisconnect() {}


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
        // SE IL BUDGET E' ZERO, SALTA COMPLETAMENTE LA GUI E RITORNA UNA LISTA VUOTA
        if (budget <= 0) return new ArrayList<>();

        CompletableFuture<List<Integer>> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            if (boardController != null) boardController.enableCardSelection("UPPER", "CHARACTER", budget, future);
        });
        try { return future.get(); } catch (Exception e) { return new ArrayList<>(); }
    }

    @Override
    public List<Integer> promptCharacterLowerIds(int budget) {
        // SE IL BUDGET E' ZERO, SALTA COMPLETAMENTE LA GUI E RITORNA UNA LISTA VUOTA
        if (budget <= 0) return new ArrayList<>();

        CompletableFuture<List<Integer>> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            if (boardController != null) boardController.enableCardSelection("LOWER", "CHARACTER", budget, future);
        });
        try { return future.get(); } catch (Exception e) { return new ArrayList<>(); }
    }

    @Override
    public List<Integer> promptBuildingUpperIds(int remaining) {
        // SE NON PUOI PRENDERE EDIFICI, SALTA COMPLETAMENTE LA GUI E RITORNA UNA LISTA VUOTA
        if (remaining <= 0) return new ArrayList<>();

        CompletableFuture<List<Integer>> future = new CompletableFuture<>();
        Platform.runLater(() -> {
            if (boardController != null) boardController.enableCardSelection("UPPER", "BUILDING", remaining, future);
        });
        try { return future.get(); } catch (Exception e) { return new ArrayList<>(); }
    }

    @Override
    public List<Integer> promptBuildingLowerIds(int remaining) {
        // SE NON PUOI PRENDERE EDIFICI, SALTA COMPLETAMENTE LA GUI E RITORNA UNA LISTA VUOTA
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
}