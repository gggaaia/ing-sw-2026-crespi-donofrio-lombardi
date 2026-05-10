package it.polimi.ingsw.view;

import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.network.shared.GameStateDto;

import java.util.List;

public interface GameView {
    void displayGameState(GameStateDto state);
    void displayError(String message);
    void displayInfo(String message);
    void displayActionResult(String action, boolean success, String message);
    void displayEndOfGame(GameStateDto state);


    void askNickname();
    void askPlayerCount();
    void askColor(List<TotemColor> available);
    void askOfferingTile(List<OfferingTile> track);
    void askCharacterCards(GameStateDto gameState);
    void askBuildingCards(Board board, int upperBudget, int lowerBudget);
    void askExtraPick(Board board);
}
