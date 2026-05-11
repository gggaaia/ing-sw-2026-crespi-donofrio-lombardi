package it.polimi.ingsw.network.client;

import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.CardRow;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.network.shared.GameStateDto;
import it.polimi.ingsw.network.shared.messages.client.PickColorMessage;
import it.polimi.ingsw.view.GameView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ClientController {
    private final GameView view;
    private boolean waitingPlayerCountResponse = false;
    private boolean waitingNicknameResponse = false;
    private boolean waitingColorResponse = false;
    private boolean waitingOfferingTileResponse = false;
    private boolean waitingCharactersResponse  = false;
    private boolean waitingBuildingsResponse = false;
    private boolean waitingExtraResponse = false;
    private String pendingNickname;

    //TODO: aggiornare il builder quando inevitabilmente stabiliamo bene il ClientController
    public ClientController(GameView view) { this.view = view; }

    public void handleRequestPlayerNickName() { view.askNickname(); }

    //TODO: add call to virtualServer
    public void onPlayerNicknameInput(String pendingNickname) {
        if (waitingNicknameResponse) return;

        String sanitized = pendingNickname.trim();

        if (sanitized.isEmpty()) {
            view.displayError("The nickname cannot be empty!");
            view.askNickname();
            return;
        }

        if (sanitized.length() < 3 || sanitized.length() > 15) {
            view.displayError("The nickname length must be between 3 and 15 characters!");
            view.askNickname();
            return;
        }

        if (!sanitized.matches("^[a-zA-Z0-9_]*$")) {
            view.displayError("The nickname must only contain alphanumeric characters!");
            view.askNickname();
            return;
        }

        this.pendingNickname = sanitized;
        this.waitingNicknameResponse = true;

        try {
            virtualClient.setPlayerNickname(sanitized);
        } catch (RemoteException e) {
            handleNetworkError(e);
        }
    }

    public void handleRequestPlayerCount() {
        view.askPlayerCount();
    }

    //TODO: add call to virtual server
    public void onPlayerCountInput(String input) {
        try {
            int count = Integer.parseInt(input.trim());

            if (count >= 2 && count <= 5) {
                waitingPlayerCountResponse = true;
                virtualClient.setPlayerCount(count);
            } else {
                view.displayError("The number must be between 2 and 5!");
                view.askPlayerCount();
            }
        } catch (NumberFormatException e) {
            view.displayError("You must enter a number!");
            view.askPlayerCount();
        }
    }

    public void handleColorRequest(List<TotemColor> available){view.askColor(available);}

    public void onColorInput(String input, List<TotemColor> available) {
        if (waitingColorResponse) {
            return;
        }
        try {
            TotemColor parsed = TotemColor.valueOf(input.toUpperCase());
            if (available.contains(parsed)) {
                waitingColorResponse = true;
                virtualClient.setPlayerColor(parsed);
            } else {
                view.displayError("Color not available: " + parsed);
                view.askColor(available);
            }
        } catch (IllegalArgumentException e) {
            view.displayError("Invalid input, choose one of the listed colors!");
            view.askColor(available);
        }
        //send(new PickColorMessage(parsed));
    }

    //TODO: add board not null check?
    public void handleOfferingTileRequest(Board board){
        List<OfferingTile> track = board.getOfferingTrack();
        view.askOfferingTile(track);
    }


    public void onOfferingTileInput(String input, List<OfferingTile> track){
        if (waitingOfferingTileResponse) return;

        try {
            int idx = Integer.parseInt(input);
            if (idx >= 0 && idx < track.size()) {
                OfferingTile selected = track.get(idx);
                if (!selected.isOccupied()) {
                    waitingOfferingTileResponse = true;
                    virtualClient.pickOfferingTile(idx);
                } else {
                    view.displayError("Offering Tile already occupied!");
                    view.askOfferingTile(track);
                }
            } else {
                view.displayError("Invalid index, choose one of the listed offering tiles!");
                view.askOfferingTile(track);
            }
        } catch (NumberFormatException e) {
            view.displayError("You must enter a number!");
            view.askOfferingTile(track);
        }
    }

    public void handleCharacterCardsRequest(GameStateDto gameState){ view.askCharacterCards(gameState); }

    public void onCharacterCardsInput(GameStateDto gameState, List<Integer> upperIds, List<Integer> lowerIds) {
        if (waitingCharactersResponse) return;

        int upperBudget = gameState.getUpperRowCardsToDraw();
        int lowerBudget = gameState.getLowerRowCardsToDraw();
        try {
            if (upperIds.size() > upperBudget || lowerIds.size() > lowerBudget) {
                view.displayError("Budget exceeded! Max upper: " + upperBudget + ", Max lower: " + lowerBudget);
                view.askCharacterCards(gameState);
                return;
            }
            Board board=gameState.getBoard();
            if (!validateIdsOnBoard(upperIds, board.getUpperRow()) ||
                    !validateIdsOnBoard(lowerIds, board.getLowerRow())) {
                view.displayError("One or more selected IDs are not available on the board!");
                view.askCharacterCards(gameState);
                return;
            }
            waitingCharactersResponse = true;
            //TODO: implements call to virtualClient
            virtualClient.pickCharacterCards(upperIds, lowerIds);

        } catch (NumberFormatException e) {
            view.displayError("Invalid format! Please enter space-separated numbers.");
            view.askCharacterCards(gameState);
        }
    }

    //FIXME: viene aggiornato il contatore di carte che possono essere prese?
    public void handleBuildingCardsRequest(GameStateDto gameState) { view.askBuildingCards(gameState);}

    public void onBuildingCardsInput(GameStateDto gameState, List<Integer> upperIds, List<Integer> lowerIds) {
        if(waitingBuildingsResponse) return;

        int upperBudget = gameState.getUpperRowCardsToDraw();
        int lowerBudget = gameState.getLowerRowCardsToDraw();
        try {
            if (upperIds.size() > upperBudget || lowerIds.size() > lowerBudget) {
                view.displayError("Budget exceeded! Max upper: " + upperBudget + ", Max lower: " + lowerBudget);
                view.askBuildingCards(gameState);
                return;
            }
            Board board=gameState.getBoard();
            if (!validateIdsOnBoard(upperIds, board.getUpperRow()) ||
                    !validateIdsOnBoard(lowerIds, board.getLowerRow())) {
                view.displayError("One or more selected IDs are not available on the board!");
                view.askBuildingCards(gameState);
                return;
            }
            waitingBuildingsResponse = true;
            //TODO: implements call to virtualClient
            virtualClient.pickBuildingCards(upperIds, lowerIds);

        } catch (NumberFormatException e) {
            view.displayError("Invalid format! Please enter space-separated numbers.");
            view.askBuildingCards(gameState);
        }
    }

    public void handleExtraPickRequest(GameStateDto gameState){
        view.askExtraPick(gameState);
    }

    public void onExtraPickInput(GameStateDto gameState,Integer id) {
        if (waitingExtraResponse) return;

        List<Integer> list = new ArrayList<>();
        list.add(id);
        Board board=gameState.getBoard();

        if (!validateIdsOnBoard(list, board.getUpperRow())) {
            view.displayError("The selected IDs is not available on the board!");
            view.askCharacterCards(gameState);
            return;
        }

        /*boolean isCharacter;
        if (type.equalsIgnoreCase("character")) {
            isCharacter = true;
        } else if (type.equalsIgnoreCase("building")) {
            isCharacter = false;
        } else {
            view.displayError("Invalid category selected");
            return;
        }*/

        waitingExtraResponse = true;
        virtualClient.pickExtraCard(id);
    }

    public void skipExtraCard(){
        virtualClient.skipExtraCard();
    }


    /*private List<Integer> parseIds(String input) throws NumberFormatException {
        if (input == null || input.trim().isEmpty()) return new ArrayList<>();
        return Arrays.stream(input.trim().split("\\s+"))
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }*/

    private boolean validateIdsOnBoard(List<Integer> chosenIds, CardRow row) {
        List<Integer> availableIds = row.getCharacterCards().stream().map(Card::getCardID).toList();
        return availableIds.containsAll(chosenIds);
    }
}
