package it.polimi.ingsw.view;

import it.polimi.ingsw.controller.client.ClientController;
import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.CardRow;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.TurnOrderSlot;
import it.polimi.ingsw.model.TurnOrderTile;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.card.EventCard;
import it.polimi.ingsw.model.enums.TotemColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

/**
 * View testuale. Stampa su System.out, legge da System.in via uno
 * Scanner condiviso.
 *
 * Concorrenza: i metodi display* / refresh sono chiamati dal thread di
 * rete; i prompt* dal thread del ClientController (che blocca il
 * thread di rete fino al ritorno dell'input). Per evitare che i
 * messaggi di display "spezzino" un prompt in corso, ogni write a
 * System.out passa da {@code outLock} e re-stampa il prompt se
 * presente.
 */
public class TuiView implements View {

    private final Scanner scan = new Scanner(System.in);
    private final Object outLock = new Object();
    private String activePrompt;

    @Override
    public void displayConnected(String host, int port) {
        emitLine("Connected to server at " + host + ":" + port);
    }

    @Override
    public void displayInfo(String message) {
        emitLine("[INFO]  " + message);
    }

    @Override
    public void displayError(String message) {
        emitLine("[ERROR] " + message);
    }

    @Override
    public void refresh(ClientController state) {
        StringBuilder sb = new StringBuilder();
        String nl = System.lineSeparator();
        sb.append("=== game state ===").append(nl);
        sb.append("phase:   ").append(state.getCurrentPhase()).append(nl);
        sb.append("turn:    ").append(state.getCurrentPlayer()).append(nl);
        sb.append("you:     ").append(state.getMyNickname()).append(nl);
        sb.append("players: ").append(state.getJoinedPlayers()).append(nl);
        sb.append("colors:  ").append(state.getColorsByPlayer()).append(nl);
        sb.append("food:    ").append(state.getFood()).append(nl);
        sb.append("prestige:").append(state.getPrestige()).append(nl);
        if (state.getCurrentEra() != null) {
            sb.append("era:     ").append(state.getCurrentEra())
              .append(" (round ").append(state.getCurrentRound()).append(")").append(nl);
        }
        if (state.getCurrentBoard() != null) {
            appendBoardOverview(sb, state.getCurrentBoard(), nl);
        }
        appendPlayersOverview(sb, state, nl);
        if (state.getFinalLeaderboard() != null) {
            sb.append("leaderboard: ").append(state.getFinalLeaderboard()).append(nl);
            sb.append("winners:     ").append(state.getFinalWinners()).append(nl);
        }
        sb.append("==================");
        emitLine(sb.toString());
    }

    @Override
    public String promptNickname() {
        return promptLine("Pick a nickname: ");
    }

    @Override
    public int promptPlayerCount() {
        while (true) {
            int n = promptInt("How many participants? (2..5): ");
            if (n >= 2 && n <= 5) return n;
            emitLine("Must be between 2 and 5.");
        }
    }

    @Override
    public TotemColor promptColor(Set<TotemColor> available) {
        while (true) {
            String s = promptLine("Pick a color " + available + ": ").trim().toUpperCase();
            try {
                TotemColor c = TotemColor.valueOf(s);
                if (available.contains(c)) return c;
                emitLine("Color not available, try again.");
            } catch (IllegalArgumentException e) {
                emitLine("Unknown color name, try again.");
            }
        }
    }

    @Override
    public int promptOfferingTile(int trackSize) {
        while (true) {
            int i = promptInt("Offering tile index (0.." + (trackSize - 1) + "): ");
            if (i >= 0 && i < trackSize) return i;
            emitLine("Out of range, try again.");
        }
    }

    @Override
    public List<Integer> promptCharacterUpperIds(int budget) {
        return promptIntList("Upper-row character ids (comma-separated, blank to skip; budget "
                + budget + "): ");
    }

    @Override
    public List<Integer> promptCharacterLowerIds(int budget) {
        return promptIntList("Lower-row character ids (comma-separated, blank to skip; budget "
                + budget + "): ");
    }

    @Override
    public List<Integer> promptBuildingUpperIds(int remaining) {
        return promptIntList("Upper-row building ids (comma-separated, blank to skip; residual "
                + remaining + "): ");
    }

    @Override
    public List<Integer> promptBuildingLowerIds(int remaining) {
        return promptIntList("Lower-row building ids (comma-separated, blank to skip; residual "
                + remaining + "): ");
    }

    @Override
    public ExtraPickChoice promptExtraCard() {
        String input = promptLine("Extra pick: 'skip', 'char <id>', or 'build <id>': ")
                .trim().toLowerCase();
        if (input.startsWith("skip")) {
            return new ExtraPickChoice();
        }
        String[] parts = input.split("\\s+");
        if (parts.length != 2) {
            emitLine("Format: skip | char <id> | build <id>. Treating as skip.");
            return new ExtraPickChoice();
        }
        boolean isChar = parts[0].startsWith("char");
        try {
            int id = Integer.parseInt(parts[1]);
            return new ExtraPickChoice(id, isChar);
        } catch (NumberFormatException e) {
            emitLine("Invalid id. Treating as skip.");
            return new ExtraPickChoice();
        }
    }

    // === helper interni ===

    private int promptInt(String prompt) {
        while (true) {
            String line = promptLine(prompt);
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                emitLine("Not a valid integer, try again.");
            }
        }
    }

    private List<Integer> promptIntList(String prompt) {
        String line = promptLine(prompt).trim();
        List<Integer> result = new ArrayList<>();
        if (line.isEmpty()) return result;
        String[] parts = line.split("\\s*,\\s*");
        for (String p : parts) {
            try {
                result.add(Integer.parseInt(p.trim()));
            } catch (NumberFormatException e) {
                emitLine("Skipping invalid id: " + p);
            }
        }
        return result;
    }

    private String promptLine(String prompt) {
        synchronized (outLock) {
            activePrompt = prompt;
            System.out.print(prompt);
            System.out.flush();
        }
        String line = scan.nextLine();
        synchronized (outLock) {
            activePrompt = null;
        }
        return line.trim();
    }

    private void emitLine(String message) {
        synchronized (outLock) {
            if (activePrompt != null) System.out.println();
            System.out.println(message);
            if (activePrompt != null) {
                System.out.print(activePrompt);
                System.out.flush();
            }
        }
    }

    // === overview helpers (riusati dalla refresh) ===

    private void appendBoardOverview(StringBuilder sb, Board board, String nl) {
        List<OfferingTile> track = board.getOfferingTrack();
        if (track != null) {
            sb.append("offering track:").append(nl);
            for (int i = 0; i < track.size(); i++) {
                OfferingTile t = track.get(i);
                sb.append("  [").append(i).append("] ").append(t.getLetter())
                  .append(" top=").append(t.getTopCardsToGet())
                  .append(" bot=").append(t.getBottomCardsToGet())
                  .append(" food=").append(t.getFoodGained())
                  .append(" totem=").append(t.isOccupied() ? t.getCurrentTotemColor() : "empty")
                  .append(nl);
            }
        }
        appendRow(sb, "upper row", board.getUpperRow(), nl);
        appendRow(sb, "lower row", board.getLowerRow(), nl);
        TurnOrderTile tot = board.getTurnOrderTile();
        if (tot != null) {
            sb.append("turn order tile:").append(nl);
            TurnOrderSlot[] slots = tot.getTurnOrderSlots();
            for (int i = 0; i < slots.length; i++) {
                TurnOrderSlot s = slots[i];
                sb.append("  slot ").append(i)
                  .append(" food=").append(s.getFood()).append(" PP=").append(s.getPP())
                  .append(" totem=").append(s.isOccupied() ? s.getTotemColor() : "empty")
                  .append(nl);
            }
        }
    }

    private void appendRow(StringBuilder sb, String label, CardRow row, String nl) {
        if (row == null) return;
        sb.append(label).append(":").append(nl);
        for (CharacterCard c : row.getCharacterCards()) {
            sb.append("  char id=").append(c.getCardID()).append(" ")
              .append(c.getClass().getSimpleName()).append(" era=").append(c.getEra()).append(nl);
        }
        for (BuildingCard b : row.getBuildingCards()) {
            sb.append("  bld  id=").append(b.getCardID()).append(" ")
              .append(b.getClass().getSimpleName()).append(" era=").append(b.getEra())
              .append(" cost=").append(b.getFoodCost())
              .append(" endPP=").append(b.getEndOfGamePP()).append(nl);
        }
        for (EventCard e : row.getEventCards()) {
            sb.append("  evt  id=").append(e.getCardID()).append(" ")
              .append(e.getClass().getSimpleName()).append(" era=").append(e.getEra()).append(nl);
        }
    }

    private void appendPlayersOverview(StringBuilder sb, ClientController state, String nl) {
        Map<String, List<CharacterCard>> hands = state.getHandsByPlayer();
        Map<String, List<BuildingCard>> buildings = state.getBuildingsByPlayer();
        Map<String, TotemColor> colors = state.getColorsByPlayer();
        for (String nick : state.getJoinedPlayers()) {
            sb.append("  ").append(nick)
              .append(" (color=").append(colors.get(nick))
              .append(", food=").append(state.getFood().get(nick))
              .append(", PP=").append(state.getPrestige().get(nick))
              .append(")").append(nl);
            List<CharacterCard> hand = hands.get(nick);
            if (hand != null && !hand.isEmpty()) {
                sb.append("    hand:").append(nl);
                for (CharacterCard c : hand) {
                    sb.append("      id=").append(c.getCardID()).append(" ")
                      .append(c.getClass().getSimpleName()).append(nl);
                }
            }
            List<BuildingCard> bld = buildings.get(nick);
            if (bld != null && !bld.isEmpty()) {
                sb.append("    buildings:").append(nl);
                for (BuildingCard b : bld) {
                    sb.append("      id=").append(b.getCardID()).append(" ")
                      .append(b.getClass().getSimpleName()).append(nl);
                }
            }
        }
    }
}
