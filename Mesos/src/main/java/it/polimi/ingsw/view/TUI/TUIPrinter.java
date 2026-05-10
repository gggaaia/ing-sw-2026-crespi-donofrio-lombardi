package it.polimi.ingsw.view.TUI;

import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.CardRow;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.network.client.Client;
import it.polimi.ingsw.network.shared.GameStateDto;

import java.util.List;
import java.util.Map;

public class TUIPrinter {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_YELLOW = "\u001B[33m";

    private static void printLogo() {
        String logo = """
             ██████  ██████ ██████████  █████████     ███████     █████████ 
            ▒▒██████ ██████ ▒▒███▒▒▒▒▒█ ███▒▒▒▒▒███  ███▒▒▒▒▒███  ███▒▒▒▒▒███
             ▒███▒█████▒███  ▒███  █ ▒ ▒███    ▒▒▒  ███     ▒▒███▒███    ▒▒▒ 
             ▒███▒▒███ ▒███  ▒██████   ▒▒█████████ ▒███      ▒███▒▒█████████ 
             ▒███ ▒▒▒  ▒███  ▒███▒▒█    ▒▒▒▒▒▒▒▒███▒███      ▒███ ▒▒▒▒▒▒▒▒███
             ▒███      ▒███  ▒███ ▒   █ ███    ▒███▒▒███     ███  ███    ▒███
             █████     █████ ██████████▒▒█████████  ▒▒▒███████▒  ▒▒█████████ 
            ▒▒▒▒▒     ▒▒▒▒▒ ▒▒▒▒▒▒▒▒▒▒  ▒▒▒▒▒▒▒▒▒     ▒▒▒▒▒▒▒     ▒▒▒▒▒▒▒▒▒  
            """;
        System.out.println(ANSI_RED + logo + ANSI_RESET);
    }

    public static void printBeginGame(){
        System.out.print("Welcome to:\n");
        printLogo();
        System.out.println("A CranioCreation Game");
        System.out.println("================================");
        System.out.println("\nChoose a nickname: ");
    }

    public static void printTotemColor(List<TotemColor> available){
        System.out.print("\nNow choose your totem's color!");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < available.size(); i++) {
            sb.append(available.get(i));
            if (i < available.size() - 1) {
                sb.append(" | ");
            }
        }
        String content = sb.toString();

        int frameWidth = content.length() + 4;
        String horizontalEdge = "-".repeat(frameWidth);

        System.out.println(horizontalEdge);
        System.out.println("| " + content + " |");
        System.out.println(horizontalEdge);

        System.out.print("Choose color: ");
    }

    public static void printOfferingTileList(List<OfferingTile> track){
        System.out.println("Offering track:");
        for (int i = 0; i < track.size(); i++) {
            OfferingTile t = track.get(i);
            String occupiedStatus;
            if (t.isOccupied()) {
                occupiedStatus = ANSI_RED + "OCCUPIED by " + t.getCurrentTotemColor() + ANSI_RESET;
            } else {
                occupiedStatus = ANSI_GREEN + "FREE" + ANSI_RESET;
            }
            System.out.println("  [" + i + "] " + t.getLetter()
                    + " top=" + t.getTopCardsToGet()
                    + " bottom=" + t.getBottomCardsToGet()
                    + " minPlayers=" + t.getMinPlayers()
                    + " food+=" + t.getFoodGained()
                    + " (" + occupiedStatus + ")");
        }
        System.out.print("Pick a tile index: ");
    }

    public static void printCharacterDrawingPhase(Board board, int upperBudget, int lowerBudget){
        System.out.println(ANSI_BLUE + "CHARACTER DRAWING PHASE:" + ANSI_RESET +
                " pick up to " + ANSI_YELLOW + upperBudget + ANSI_RESET +
                " upper-row character(s) and up to " +
                ANSI_YELLOW + lowerBudget + ANSI_RESET +
                " lower-row character(s).");
        printCharactersInRow("Upper", board.getUpperRow());
        printCharactersInRow("Lower", board.getLowerRow());
    }

    //TODO: stampare anche gli attributi delle carte
    public static void printCharactersInRow(String label, CardRow row) {
        if (row == null) {
            System.out.println("  " + label + " row not available");
            return;
        }
        List<CharacterCard> chars = row.getCharacterCards();
        System.out.println("  " + label + " row characters (" + chars.size() + "):");
        for (int i = 0; i < chars.size(); i++) {
            CharacterCard c = chars.get(i);
            System.out.println("    ID " + c.getCardID() + " :: " + c.getClass().getSimpleName());
        }
    }

    public static void printBuildingDrawingPhase(Board board, int upperBudget, int lowerBudget){

    }

    public void printBuildingsInRow(String label, CardRow row) {
        if (row == null) {
            System.out.println("  " + label + " row not available");
            return;
        }
        List<BuildingCard> buildings = row.getBuildingCards();
        System.out.println("  " + label + " row buildings (" + buildings.size() + "):");
        for (int i = 0; i < buildings.size(); i++) {
            BuildingCard b = buildings.get(i);
            System.out.println("    ID " + b.getCardID()
                    + " cost=" + b.getFoodCost()
                    + " :: " + b.getClass().getSimpleName());
        }
    }

    public void printGameState(GameStateDto state) {
        System.out.println();
        System.out.println("========== GAME STATE ==========");
        System.out.println("Phase:          " + state.getPhase());
        System.out.println("Round / Era:    " + state.getRound() + " / " + state.getEra());
        System.out.println("Players:        " + state.getPlayers());
        System.out.println("Player colors:  " + state.getPlayerColors());
        if (state.getColorPickTurnOrder() != null
                && !state.getColorPickTurnOrder().isEmpty()) {
            System.out.println("Color pick order: " + state.getColorPickTurnOrder());
        }
        System.out.println("Turn order:     " + state.getPlayersTurnOrderTile());
        System.out.println("Current player: " + state.getCurrentPlayerNickname());
        System.out.println("Available colors: " + state.getAvailableColors());

        // Per-phase extras (e.g. per-turn budgets during the pick phases).
        // Polymorphic dispatch via the strategy registry — no if-else
        // chain needed.
        Client.ClientPhaseAction action = PHASE_ACTIONS.get(state.getPhase());
        if (action != null) {
            action.printExtras(this, state);
        }

        // Resources: only useful once the match has started.
        if (state.getPlayerFood() != null && !state.getPlayerFood().isEmpty()) {
            System.out.println("Food:     " + state.getPlayerFood());
        }
        if (state.getPlayerPrestige() != null && !state.getPlayerPrestige().isEmpty()) {
            System.out.println("Prestige: " + state.getPlayerPrestige());
        }

        System.out.println("Message: " + state.getMessage());
        System.out.println("================================");
        System.out.println();
    }

    public void printPickBudgets(GameStateDto state) {
        System.out.println("Char budget:    upper=" + state.getUpperRowCardsToDraw()
                + " lower=" + state.getLowerRowCardsToDraw());
        System.out.println("Build leftover: upper=" + state.getUpperRowCardsRemainingForBuilding()
                + " lower=" + state.getLowerRowCardsRemainingForBuilding());
    }

    public void printEndOfGame(GameStateDto state) {
        System.out.println("=========== GAME OVER ===========");
        Map<String, Integer> food = state.getPlayerFood();
        Map<String, Integer> prestige = state.getPlayerPrestige();
        List<String> leaderboard = state.getLeaderboard();
        List<String> winners = state.getWinners();
        System.out.println("Leaderboard:");
        for (int i = 0; i < leaderboard.size(); i++) {
            String nick = leaderboard.get(i);
            Integer pp = (prestige == null) ? null : prestige.get(nick);
            Integer fd = (food == null) ? null : food.get(nick);
            System.out.println("  " + (i + 1) + ". " + nick
                    + "  PP=" + pp + "  Food=" + fd);
        }
        if (winners.size() == 1) {
            System.out.println("Winner: " + winners.get(0));
        } else {
            System.out.println("Shared victory between: " + winners);
        }
        System.out.println("=================================");
    }
}
