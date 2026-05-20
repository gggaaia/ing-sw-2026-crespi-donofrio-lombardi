package it.polimi.ingsw.view.TUI;

import it.polimi.ingsw.common.dto.Phase;
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
 * Text-based view. Writes to System.out and reads from System.in
 * through a shared Scanner. All the actual ASCII art / formatting
 * (banner, boxed errors, board overview) lives in {@link TuiPrinter}.
 *
 * Threading note: display* / refresh are called from the network
 * thread, prompt* from the ClientController thread. To avoid display
 * messages cutting through a prompt that's currently waiting for
 * input, every write to System.out goes through {@code outLock} and
 * re-prints the prompt if one is active.
 */
public class TuiView implements View {

    private final Scanner scan;
    private final Object outLock = new Object();
    private String activePrompt;

    /**
     * Reuses an existing Scanner on {@code System.in}. Required: a
     * Scanner read-aheads from the underlying stream into its own
     * buffer, so two Scanners on the same {@code System.in} would
     * steal input from each other. The caller (typically
     * {@code Client}) creates the single JVM-wide Scanner and hands
     * it in here.
     */
    public TuiView(Scanner scan) {
        this.scan = scan;
    }

    /** Convenience for tests / standalone use. */
    public TuiView() {
        this(new Scanner(System.in));
    }

    /**
     * Long visual separator printed at the start and end of every
     * {@code refresh} dump.
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
        // Pre-match: during lobby (waiting nickname/count/players) and
        // color picking, server-side the Player objects don't exist
        // yet (they're built in the server during acceptColorPick), 
        // so food/prestige aren't known on the client and the dump 
        // would just say "Food: ? | PP: ?". 
        // User still sees INFO logs and prompts in these phases; 
        // the first useful dump shows up on the first *Turn of the actual game, 
        // with the initial zeros that startMatch broadcasts.
        
        if (isPreMatchPhase(state.getCurrentPhase())) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        String nl = System.lineSeparator();
        // Opening frame: long line + centred banner. Helps tell
        // consecutive game-state dumps apart at a glance.
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
        // NB: pendingError is NOT printed here. That's handled by
        // ClientController#commit(), which calls view.displayError(err)
        // after the dump if needed. This way the on-screen order stays
        // consistent: dump, log lines, error, prompt.
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

    // === internal helpers ===

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
     * Prints the prompt and reads a line from the shared Scanner.
     *
     * Note: the prompt is printed with {@code println} (so it ends
     * with a newline) and input is read from the next line, after a
     * small "{@code > }".
     *
     * Reason: some consoles (IntelliJ's run console in particular)
     * buffer stdout line by line and don't always honor a
     * {@code System.out.flush()} on a {@code print()} without a
     * trailing newline. The symptom was that the second back-to-back
     * prompt (e.g. the lower row right after the upper row) didn't
     * show up until the next Enter, which then got eaten as empty
     * input. Ending the prompt with a newline forces the line to
     * flush on any console.
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

    /**
     * True if the current phase is a pre-match one where the GAME
     * STATE dump doesn't make sense (no Player yet, no board, no
     * food/prestige). The {@code null} case is included too, which
     * is the client's initial state before the first event from the
     * server.
     */
    private static boolean isPreMatchPhase(Phase p) {
        if (p == null) return true;
        return p instanceof Phase.WaitingForNickname
                || p instanceof Phase.WaitingForPlayerCount
                || p instanceof Phase.WaitingForPlayers
                || p instanceof Phase.ColorPicking;
    }

    // === board / players overview (called from refresh) ===

    /**
     * Appends a readable, multi-line summary of the board to {@code sb}:
     * the turn order tile (food/PP per slot and current occupant),
     * the upper/lower rows (id and class of every card + cost/PP for
     * buildings), and the offering track (letter, top/bottom, food,
     * current occupant). The boxed formatters live in
     * {@link TuiPrinter}; here we just decide the order of the blocks.
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
     * Appends the block for a single CardRow (upper or lower):
     * characters, buildings, events. If the list is empty it prints
     * "(none)" so the user can clearly tell the row is empty.
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
