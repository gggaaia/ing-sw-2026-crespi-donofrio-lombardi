package it.polimi.ingsw.view.TUI;

import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.network.client.ClientController;
import it.polimi.ingsw.network.shared.GameStateDto;
import it.polimi.ingsw.network.shared.messages.client.*;
import it.polimi.ingsw.view.GameView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.util.Scanner;

public class TUI implements GameView {
    private final TUIPrinter printer= new TUIPrinter();
    private final ClientController controller;

    public TUI(ClientController controller) {
        this.controller = controller;
    }

    /** Reads the user's console input. */
    private final Scanner scanner = new Scanner(System.in);


    public void displayGameState(GameStateDto state){}
    @Override
    public void displayError(String message) {
        System.out.println("\u001B[31m" + ">>> ERROR: " + message + "\u001B[0m");
    }
    public void displayInfo(String message){}
    public void displayActionResult(String action, boolean success, String message){}
    public void displayEndOfGame(GameStateDto state){}

    @Override
    public void askNickname() {
        TUIPrinter.printBeginGame();
        String nickname = scanner.nextLine();
        controller.onPlayerNicknameInput(nickname);
        //send(new SetNicknameMessage(nickname));
    }

    @Override
    public void askPlayerCount() {
        System.out.print("Choose number of players (2..5): ");
        String input = scanner.nextLine();
        controller.onPlayerCountInput(input);
    }

    @Override
    public void askColor(List<TotemColor> available) {
        TUIPrinter.printTotemColor(available);
        String raw = scanner.nextLine().trim();
        controller.onColorInput(raw, available);
    }

    @Override
    public void askOfferingTile(List<OfferingTile> track) {
        TUIPrinter.printOfferingTileList(track);
        String rawInput = scanner.nextLine().trim();
        controller.onOfferingTileInput(rawInput, track);
    }

    @Override
    public void askCharacterCards(GameStateDto gameState) {
        Board board = gameState.getBoard();
        int upperBudget = gameState.getUpperRowCardsToDraw();
        int lowerBudget = gameState.getLowerRowCardsToDraw();
        TUIPrinter.printCharacterDrawingPhase(board, upperBudget, lowerBudget);
        List<Integer> upperIds = readCardIds(
                "Upper-row character IDs (space-separated, empty for none): ",
                upperBudget);
        List<Integer> lowerIds = readCardIds(
                "Lower-row character IDs (space-separated, empty for none): ",
                lowerBudget);
        if (upperIds == null || lowerIds == null) {
            // readCardIds already explained the issue; re-ask both.
            askCharacterCards(gameState);
            return;
        }
        controller.onCharacterCardsInput(gameState, upperIds, lowerIds);
    }

    @Override
    public void askBuildingCards(GameStateDto gameState) {
        /*Board board = currentGameState.getBoard();
        if (board == null) {
            System.out.println("Board not yet available.");
            return;
        } */
        Board board = gameState.getBoard();
        int upperBudget = gameState.getUpperRowCardsRemainingForBuilding();
        int lowerBudget = gameState.getLowerRowCardsRemainingForBuilding();

        TUIPrinter.printBuildingDrawingPhase(board, upperBudget, lowerBudget);

        List<Integer> upperIds = readCardIds(
                "Upper-row building IDs (space-separated, empty for none): ",
                upperBudget);
        List<Integer> lowerIds = readCardIds(
                "Lower-row building IDs (space-separated, empty for none): ",
                lowerBudget);
        if (upperIds == null || lowerIds == null) {
            askBuildingCards(gameState);
            return;
        }
        controller.onBuildingCardsInput(gameState, upperIds, lowerIds);
        //send(new PickBuildingCardsMessage(upperIds, lowerIds));
    }

    public void askExtraPick(GameStateDto gameState) {
        Board board = gameState.getBoard();
        TUIPrinter.printExtraPickPhase(board);
        String line = scanner.nextLine().trim();
        if (line.equalsIgnoreCase("skip") || line.isEmpty()) {
            controller.skipExtraCard();
            return;
        }

        if (line.split("\\s+").length > 1) {
            displayError("Invalid input: please enter only ONE ID.");
            askExtraPick(gameState);
            return;
        }

        try {
            int id = Integer.parseInt(line);
            controller.onExtraPickInput(gameState, id);

        } catch (NumberFormatException e) {
            displayError("Invalid input: '" + line + "' is not a valid ID or command.");
            askExtraPick(gameState);
        }

        /*String[] parts = line.split("\\s+");
        if (parts.length != 2) {
            displayError("Invalid input.");
            askExtraPick(gameState);
            return;
        }
        boolean isCharacter;
        if (parts[0].equalsIgnoreCase("character")) {
            isCharacter = true;
        } else if (parts[0].equalsIgnoreCase("building")) {
            isCharacter = false;
        } else {
            displayError("Invalid kind: " + parts[0]);
            askExtraPick();
            return;
        }
        try {
            int id = Integer.parseInt(parts[1]);
            waitingExtraResponse = true;
            send(new PickExtraCardMessage(Integer.valueOf(id), isCharacter));
        } catch (NumberFormatException e) {
            displayError("Invalid id: " + parts[1]);
            askExtraPick();
        }*/
    }

    public List<Integer> readCardIds(String prompt, int maxCount) {
        System.out.print(prompt);
        String line = scanner.nextLine();
        if (line == null) {
            return new ArrayList<Integer>();
        }
        line = line.trim();
        if (line.isEmpty()) {
            return new ArrayList<Integer>();
        }
        String[] tokens = line.split("\\s+");
        if (tokens.length > maxCount) {
            displayError("Too many IDs: at most " + maxCount + " allowed.");
            return null;
        }
        List<Integer> ids = new ArrayList<Integer>();
        Set<Integer> seen = new HashSet<Integer>();
        for (int i = 0; i < tokens.length; i++) {
            int id;
            try {
                id = Integer.parseInt(tokens[i]);
            } catch (NumberFormatException e) {
                displayError("Invalid ID: " + tokens[i]);
                return null;
            }
            if (seen.contains(id)) {
                displayError("Duplicate ID: " + id);
                return null;
            }
            seen.add(id);
            ids.add(id);
        }
        return ids;
    }
}
