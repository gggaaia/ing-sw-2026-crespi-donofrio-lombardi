package it.polimi.ingsw.view.TUI;

import it.polimi.ingsw.controller.client.ClientController;
import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.CardRow;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.TurnOrderTile;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.card.EventCard;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.view.View;

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
        emitLine(TuiPrinter.formatInfo(message));
    }

    @Override
    public void displayError(String message) {
        emitLine(TuiPrinter.formatError(message));
    }

    @Override
    public void refresh(ClientController state) {
        StringBuilder sb = new StringBuilder();
        String nl = System.lineSeparator();
        sb.append("=== GAME STATE ===").append(nl);
        sb.append("phase:   ").append(state.getCurrentPhase()).append(nl);
        sb.append("turn:    ").append(state.getCurrentPlayer()).append(nl);
        sb.append("you:     ").append(state.getMyNickname()).append(nl);
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
        emitLine(TuiPrinter.formatBeginGame());
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
    public View.ExtraPickChoice promptExtraCard() {
        String input = promptLine("Extra pick: 'skip', 'char <id>', or 'build <id>': ")
                .trim().toLowerCase();
        if (input.startsWith("skip")) {
            return new View.ExtraPickChoice();
        }
        String[] parts = input.split("\\s+");
        if (parts.length != 2) {
            emitLine("Format: skip | char <id> | build <id>. Treating as skip.");
            return new View.ExtraPickChoice();
        }
        boolean isChar = parts[0].startsWith("char");
        try {
            int id = Integer.parseInt(parts[1]);
            return new View.ExtraPickChoice(id, isChar);
        } catch (NumberFormatException e) {
            emitLine("Invalid id. Treating as skip.");
            return new View.ExtraPickChoice();
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
        TurnOrderTile tot = board.getTurnOrderTile();
        if (tot != null) {
            sb.append(TuiPrinter.formatSectionHeader("BOARD")).append(nl);
            sb.append(TuiPrinter.formatTurnOrder(tot));
        }

        appendRow(sb, "upper row", board.getUpperRow(), nl);
        appendRow(sb, "lower row", board.getLowerRow(), nl);

        List<OfferingTile> track = board.getOfferingTrack();
        if (track != null && !track.isEmpty()) {
            sb.append(TuiPrinter.formatOfferingTrack(track));
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
        List<String> nicks = state.getJoinedPlayers();
        if (nicks == null || nicks.isEmpty()) return;

        sb.append(TuiPrinter.formatSectionHeader("PLAYERS STATUS")).append(nl);

        Map<String, List<CharacterCard>> hands = state.getHandsByPlayer();
        Map<String, List<BuildingCard>> buildings = state.getBuildingsByPlayer();
        Map<String, TotemColor> colors = state.getColorsByPlayer();
        Map<String, Integer> food = state.getFood();
        Map<String, Integer> prestige = state.getPrestige();

        for (String nick : nicks) {
            sb.append(TuiPrinter.formatPlayerCompact(
                    nick,
                    colors == null ? null : colors.get(nick),
                    food == null ? null : food.get(nick),
                    prestige == null ? null : prestige.get(nick),
                    hands == null ? null : hands.get(nick),
                    buildings == null ? null : buildings.get(nick)));
            sb.append(nl);
        }
    }
}
