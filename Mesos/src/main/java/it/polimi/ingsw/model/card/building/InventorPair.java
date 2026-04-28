package it.polimi.ingsw.model.card.building;

import it.polimi.ingsw.model.Player;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.enums.Era;

import java.util.stream.IntStream;

// TODO: implement effect logic
public class InventorPair extends BuildingCard {
    public InventorPair(int cardID, Era era, int minPlayers, int foodCost, int endOfGamePP, Player owner) {
        super(cardID, era, minPlayers, foodCost, endOfGamePP, owner);
    }
    @Override
    public void applyEndRoundEffect(Player player) {
        if (player == getOwner()) {
           //avrebbe senso avere una lista di ICON che viene aggiornata quando peschi l'inventore, conto quante icon ci
            //sono nella lista, poi faccio la filter e prendo solo le istanze singole senza duplicati e faccio la count
            //divido il numero di icone totali per il numero di icone singole
        }
    }
}
