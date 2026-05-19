package it.polimi.ingsw.view.TUI;

import it.polimi.ingsw.controller.client.ClientController;
import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.CardRow;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.TurnOrderTile;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.Card;
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
 * Scanner condiviso. I formattatori grafici (banner, errori boxati,
 * board overview) sono in {@link TuiPrinter}.
 *
 * Concorrenza: i metodi display* / refresh sono chiamati dal thread di
 * rete; i prompt* dal thread del ClientController. Per evitare che i
 * messaggi di display "spezzino" un prompt in corso, ogni write a
 * System.out passa da {@code outLock} e re-stampa il prompt se
 * presente.
 */
public class TuiView implements View {

    private final Scanner scan = new Scanner(System.in);
    private final Object outLock = new Object();
    private String activePrompt;

    /**
     * Separatore visivo lungo, stampato all'inizio e alla fine di ogni
     * dump di {@code refresh}. Una linea lunga rende molto pi\u00f9
     * evidente il passaggio da una fase di gioco alla successiva
     * rispetto al vecchio "==================" da 18 caratteri.
     */
    private static final String LONG_SEP =
            "================================================================";
    private static final String GAME_STATE_HEADER =
            "==================== GAME STATE ====================";

    @Override
    public void displayConnected(String host, int port) {
        emitLine(TuiPrinter.formatBeginGame());
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
        // Cornice di apertura: linea lunga + banner centrato. Aiuta a
        // separare visivamente i dump di game state successivi.
        sb.append(TuiPrinter.BLUE).append(LONG_SEP).append(TuiPrinter.RESET).append(nl);
        sb.append(TuiPrinter.BLUE).append(GAME_STATE_HEADER).append(TuiPrinter.RESET).append(nl);
        sb.append("phase:    ").append(state.getCurrentPhase()).append(nl);
        sb.append("turn:     ").append(state.getCurrentPlayer()).append(nl);
        sb.append("you:      ").append(state.getMyNickname()).append(nl);
        if (state.getCurrentEra() != null) {
            sb.append("era:      ").append(state.getCurrentEra())
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
        sb.append(TuiPrinter.BLUE).append(LONG_SEP).append(TuiPrinter.RESET);
        // Errore deferred: se il server ha appena rifiutato un'azione
        // ({@code actionRejected}), il messaggio non viene stampato in
        // quel momento ma stashato sul controller. Lo tiriamo fuori qui,
        // in fondo al refresh, cos\u00ec appare vicino al prompt invece
        // che "sopra" il game state aggiornato.
        String err = state.consumePendingError();
        if (err != null) {
            sb.append(nl).append(TuiPrinter.formatError(err));
            sb.append(nl).append(TuiPrinter.BLUE).append(LONG_SEP).append(TuiPrinter.RESET);
        }
        emitLine(sb.toString());
    }

    @Override
    public String promptNickname() {
        return promptLine("Enter your nickname:");
    }

    @Override
    public int promptPlayerCount() {
        while (true) {
            int n = promptInt("How many players will join this match? (2-5)");
            if (n >= 2 && n <= 5) return n;
            emitLine("Number must be between 2 and 5. Try again.");
        }
    }

    @Override
    public TotemColor promptColor(Set<TotemColor> available) {
        while (true) {
            String s = promptLine("Pick your totem color. Available: " + available)
                    .trim().toUpperCase();
            try {
                TotemColor c = TotemColor.valueOf(s);
                if (available.contains(c)) return c;
                emitLine("That color is already taken. Try again.");
            } catch (IllegalArgumentException e) {
                emitLine("Unknown color name. Try again (e.g. RED).");
            }
        }
    }

    @Override
    public int promptOfferingTile(int trackSize) {
        while (true) {
            int i = promptInt("Pick an offering tile to claim. Enter its index (0-"
                    + (trackSize - 1) + "), see 'offering track' above");
            if (i >= 0 && i < trackSize) return i;
            emitLine("Index out of range. Try again.");
        }
    }

    @Override
    public List<Integer> promptCharacterUpperIds(int budget) {
        return promptIntList("Pick character cards from the UPPER row (up to "
                + budget + "). Enter card IDs comma-separated, or leave blank to skip");
    }

    @Override
    public List<Integer> promptCharacterLowerIds(int budget) {
        return promptIntList("Pick character cards from the LOWER row (up to "
                + budget + "). Enter card IDs comma-separated, or leave blank to skip");
    }

    @Override
    public List<Integer> promptBuildingUpperIds(int remaining) {
        return promptIntList("Buy buildings from the UPPER row (up to "
                + remaining + " left). Enter card IDs comma-separated, or leave blank to skip");
    }

    @Override
    public List<Integer> promptBuildingLowerIds(int remaining) {
        return promptIntList("Buy buildings from the LOWER row (up to "
                + remaining + " left). Enter card IDs comma-separated, or leave blank to skip");
    }

    @Override
    public ExtraPickChoice promptExtraCard() {
        String input = promptLine(
                "Bonus extra pick! Type 'skip', 'char <id>' to take a character, "
                        + "or 'build <id>' to take a building")
                .trim().toLowerCase();
        if (input.startsWith("skip")) {
            return new ExtraPickChoice();
        }
        String[] parts = input.split("\\s+");
        if (parts.length != 2) {
            emitLine("Expected: skip | char <id> | build <id>. Treating as skip.");
            return new ExtraPickChoice();
        }
        boolean isChar = parts[0].startsWith("char");
        try {
            int id = Integer.parseInt(parts[1]);
            return new ExtraPickChoice(id, isChar);
        } catch (NumberFormatException e) {
            emitLine("Card id must be a number. Treating as skip.");
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
                emitLine("That's not a number. Try again.");
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
                emitLine("Ignoring '" + p + "' (not a valid card id).");
            }
        }
        return result;
    }

    /**
     * Stampa il prompt e legge una riga dallo Scanner condiviso.
     *
     * Importante: il prompt viene stampato con {@code println} (quindi
     * con newline finale) e l'input \u00e8 letto dalla riga successiva
     * dopo un piccolo "{@code > }".
     *
     * Motivo: alcune console (in particolare la run-console di IntelliJ)
     * bufferizzano stdout per riga e NON sempre rispettano un
     * {@code System.out.flush()} su un {@code print()} senza newline
     * finale. Il sintomo era che il secondo prompt back-to-back (es. la
     * lower row dopo la upper row) non comparisse fino al primo Enter
     * successivo, che veniva nel frattempo consumato come input vuoto.
     * Terminare il prompt con newline forza il flush della riga in
     * qualunque console.
     */
    private String promptLine(String prompt) {
        synchronized (outLock) {
            activePrompt = prompt;
            System.out.println(prompt);
            System.out.print("> ");
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
                System.out.println(activePrompt);
                System.out.print("> ");
                System.out.flush();
            }
        }
    }

    // === board / players overview (chiamate da refresh) ===

    /**
     * Appende un riassunto multiriga e leggibile della board a {@code sb}:
     * la turn order tile (con food/PP per slot e occupante corrente),
     * la upper / lower row (id e classe di ogni carta + costo/PP per
     * gli edifici), e l'offering track (lettera, top/bottom, food,
     * occupante corrente). I formattatori boxati vivono in
     * {@link TuiPrinter}; qui sopra c'\u00e8 la sequenza di blocchi.
     */
    private void appendBoardOverview(StringBuilder sb, Board board, String nl) {
        TurnOrderTile tot = board.getTurnOrderTile();
        if (tot != null) {
            sb.append(TuiPrinter.BLUE).append("--- BOARD ---").append(TuiPrinter.RESET).append(nl);
            sb.append(TuiPrinter.formatTurnOrder(tot));
        }

        appendRow(sb, "upper row", board.getUpperRow(), nl);
        appendRow(sb, "lower row", board.getLowerRow(), nl);

        List<OfferingTile> track = board.getOfferingTrack();
        if (track != null) {
            sb.append(TuiPrinter.formatOfferingTrack(track));
        }
    }

    /**
     * Appende il blocco di una singola CardRow (upper o lower):
     * personaggi, edifici, eventi. Se la lista \u00e8 vuota stampa
     * "(none)" cos\u00ec l'utente vede esplicitamente che la riga
     * \u00e8 vuota.
     */
    private void appendRow(StringBuilder sb, String label, CardRow row, String nl) {
        if (row == null) return;
        sb.append(label).append(":").append(nl);

        List<CharacterCard> chars = row.getCharacterCards();
        sb.append("  characters:");
        if (chars.isEmpty()) {
            sb.append(" (none)").append(nl);
        } else {
            sb.append(nl);
            for (CharacterCard c : chars) {
                sb.append("    id=").append(c.getCardID())
                        .append(" ").append(c.getClass().getSimpleName())
                        .append(" (era ").append(c.getEra()).append(")")
                        .append(nl);
            }
        }

        List<BuildingCard> builds = row.getBuildingCards();
        sb.append("  buildings:");
        if (builds.isEmpty()) {
            sb.append(" (none)").append(nl);
        } else {
            sb.append(nl);
            for (BuildingCard b : builds) {
                sb.append("    id=").append(b.getCardID())
                        .append(" ").append(b.getClass().getSimpleName())
                        .append(" (era ").append(b.getEra())
                        .append(", cost=").append(b.getFoodCost())
                        .append(", endPP=").append(b.getEndOfGamePP())
                        .append(")")
                        .append(nl);
            }
        }

        List<EventCard> events = row.getEventCards();
        sb.append("  events:");
        if (events.isEmpty()) {
            sb.append(" (none)").append(nl);
        } else {
            sb.append(nl);
            for (Card e : events) {
                sb.append("    id=").append(e.getCardID())
                        .append(" ").append(e.getClass().getSimpleName())
                        .append(" (era ").append(e.getEra()).append(")")
                        .append(nl);
            }
        }
    }

    private void appendPlayersOverview(StringBuilder sb, ClientController state, String nl) {
        sb.append(TuiPrinter.BLUE).append("--- PLAYERS STATUS ---").append(TuiPrinter.RESET).append(nl);
        Map<String, List<CharacterCard>> hands = state.getHandsByPlayer();
        Map<String, List<BuildingCard>> buildings = state.getBuildingsByPlayer();
        Map<String, TotemColor> colors = state.getColorsByPlayer();
        Map<String, Integer> food = state.getFood();
        Map<String, Integer> prestige = state.getPrestige();
        for (String nick : state.getJoinedPlayers()) {
            sb.append(TuiPrinter.formatPlayerCompact(
                    nick,
                    colors.get(nick),
                    food.get(nick),
                    prestige.get(nick),
                    hands.get(nick),
                    buildings.get(nick)
            ));
            sb.append(nl);
        }
    }
}
