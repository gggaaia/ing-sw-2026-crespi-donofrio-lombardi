package it.polimi.ingsw.view;

import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.common.dto.GameStateDto;
import it.polimi.ingsw.model.Board;
import it.polimi.ingsw.model.CardRow;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.TurnOrderSlot;
import it.polimi.ingsw.model.TurnOrderTile;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.card.EventCard;
import it.polimi.ingsw.model.enums.TotemColor;

import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Text-only View. Reads from System.in via a single Scanner instance
 * and writes to System.out. Suitable for first-year-style command
 * line interaction.
 *
 * Threading: the prompt methods are called from the main thread (by
 * ClientController.runInputLoop and once at start-up by the network
 * adapters). The display methods are called from RMI worker threads
 * (when a remote notifyXxx callback fires) or the socket reader
 * thread, concurrently with a main thread that may be blocked inside
 * a prompt waiting for the user to press Enter.
 *
 * To avoid the symptom where a server-pushed state dump appears
 * BETWEEN the prompt label and the cursor (so the user no longer
 * sees what they are supposed to type), every write to System.out
 * goes through outLock and is wrapped by emitWithPromptRedraw():
 *
 *   - if there is no active prompt, just print the message;
 *   - if a prompt is active, drop a newline first (so the typed-but-
 *     not-yet-Entered prompt line is committed to its own row),
 *     print the message, then re-print the prompt at the bottom of
 *     the screen so the cursor is always sitting after the most
 *     recent prompt.
 *
 * The Scanner.nextLine() call inside the prompt methods runs OUTSIDE
 * the lock, so background notifications can still arrive while the
 * user is typing. Characters the user has already typed remain
 * buffered by the terminal's line discipline and are committed when
 * Enter is finally pressed (they may not be visible on the redrawn
 * prompt line, which is an accepted limitation of a basic TUI).
 */
public class TuiView implements View {

    private final Scanner scan = new Scanner(System.in);

    /**
     * Guards every System.out write performed by this class so the
     * background reader thread cannot tear apart a prompt or a state
     * dump. NOT held during scan.nextLine() (that would block all
     * background output until the user presses Enter).
     */
    private final Object outLock = new Object();

    /**
     * Text of the prompt currently awaiting user input, or null when
     * no prompt is on screen. Set by the prompt methods before they
     * release outLock and block on Scanner; cleared after Scanner
     * returns. Guarded by outLock.
     */
    private String activePrompt;

    public TuiView() {
        super();
    }

    @Override
    public void displayConnected(String host, int port) {
        emitLine("Connected to server at " + host + ":" + port);
    }

    @Override
    public String promptNickname() {
        return promptLine("Pick a nickname: ");
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
    public void displayActionResult(ActionResultDto result) {
        String tag = result.isSuccess() ? "OK" : "REJECTED";
        emitLine("[" + tag + " | " + result.getLabel() + "] " + result.getMessage());
    }

    @Override
    public void displayState(GameStateDto state) {
        // Build the whole dump first, then emit it as one block, so
        // the prompt-redraw logic only fires once for the entire
        // state notification (instead of after every single line).
        StringBuilder sb = new StringBuilder();
        String nl = System.lineSeparator();
        sb.append("=== game state ===").append(nl);
        sb.append("phase: ").append(state.getPhase()).append(nl);
        sb.append("connected: ").append(state.getConnectedNicknames()).append(nl);
        sb.append("colors:    ").append(state.getNicknameToColor()).append(nl);
        sb.append("turn:      ").append(state.getCurrentPlayerNickname()).append(nl);
        sb.append("round:     ").append(state.getCurrentRound())
                .append(" (era ").append(state.getCurrentEra()).append(")").append(nl);
        sb.append("food:      ").append(state.getFoodByNickname()).append(nl);
        sb.append("prestige:  ").append(state.getPrestigeByNickname()).append(nl);
        // Append the board overview (offering track, card rows, turn order
        // tile) when the match is in progress, so the user can SEE the
        // tiles and cards they are about to pick from. The Board class
        // does not have a useful toString() and the prompts only ask for
        // an index, so without this block the player would be picking
        // blindly. Skipped during lobby phases where board is null.
        if (state.getBoard() != null) {
            appendBoardOverview(sb, state.getBoard(), nl);
            // Per-player info (hand of character cards drafted so far,
            // buildings bought so far) only makes sense once players
            // exist. Same gating as the board overview.
            appendPlayersOverview(sb, state, nl);
        }
        if (state.getLeaderboard() != null) {
            sb.append("leaderboard: ").append(state.getLeaderboard()).append(nl);
            sb.append("winners:     ").append(state.getWinners()).append(nl);
        }
        sb.append("desc:      ").append(state.getDescription()).append(nl);
        sb.append("==================");
        emitLine(sb.toString());
    }

    /**
     * Appends a multi-line, human-readable summary of the board to {@code sb}:
     * the offering track (with each tile's letter, top/bottom card counts,
     * food gain and current occupant), the upper / lower card rows (with
     * each card's id and concrete class name and, for buildings, food cost
     * and end-of-game prestige), and the turn order tile (with each slot's
     * food/PP rewards and current occupant).
     */
    private void appendBoardOverview(StringBuilder sb, Board board, String nl) {
        List<OfferingTile> track = board.getOfferingTrack();
        if (track != null) {
            sb.append("offering track:").append(nl);
            for (int i = 0; i < track.size(); i++) {
                OfferingTile t = track.get(i);
                sb.append("  [").append(i).append("] tile ").append(t.getLetter())
                        .append(" - top=").append(t.getTopCardsToGet())
                        .append(", bottom=").append(t.getBottomCardsToGet())
                        .append(", food=").append(t.getFoodGained())
                        .append(", totem=")
                        .append(t.isOccupied() ? t.getCurrentTotemColor() : "empty")
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
                        .append(" (food=").append(s.getFood())
                        .append(", PP=").append(s.getPP()).append(")")
                        .append(" totem=")
                        .append(s.isOccupied() ? s.getTotemColor() : "empty")
                        .append(nl);
            }
        }
    }

    /**
     * Appends a per-player block listing, for each connected nickname,
     * its color, food and prestige (so they line up with what the user
     * is reading), the character cards currently in the hand and the
     * building cards already bought. Players that have not been
     * created yet (no Player object server-side) are skipped silently
     * so the lobby flow stays unaffected.
     */
    private void appendPlayersOverview(StringBuilder sb, GameStateDto state, String nl) {
        Map<String, List<CharacterCard>> hands = state.getHandsByNickname();
        Map<String, List<BuildingCard>> buildings = state.getBuildingsByNickname();
        Map<String, TotemColor> colors = state.getNicknameToColor();
        Map<String, Integer> foods = state.getFoodByNickname();
        Map<String, Integer> prestiges = state.getPrestigeByNickname();

        sb.append("players:").append(nl);
        for (String nickname : state.getConnectedNicknames()) {
            TotemColor color = colors == null ? null : colors.get(nickname);
            Integer food = foods == null ? null : foods.get(nickname);
            Integer prestige = prestiges == null ? null : prestiges.get(nickname);
            sb.append("  ").append(nickname)
                    .append(" (color=").append(color == null ? "?" : color)
                    .append(", food=").append(food == null ? "?" : food)
                    .append(", PP=").append(prestige == null ? "?" : prestige)
                    .append(")").append(nl);

            List<CharacterCard> hand = hands == null ? null : hands.get(nickname);
            sb.append("    hand:");
            if (hand == null || hand.isEmpty()) {
                sb.append(" (empty)").append(nl);
            } else {
                sb.append(nl);
                for (CharacterCard c : hand) {
                    sb.append("      id=").append(c.getCardID())
                            .append(" ").append(c.getClass().getSimpleName())
                            .append(" (era ").append(c.getEra()).append(")")
                            .append(nl);
                }
            }

            List<BuildingCard> owned = buildings == null ? null : buildings.get(nickname);
            sb.append("    buildings:");
            if (owned == null || owned.isEmpty()) {
                sb.append(" (none)").append(nl);
            } else {
                sb.append(nl);
                for (BuildingCard b : owned) {
                    sb.append("      id=").append(b.getCardID())
                            .append(" ").append(b.getClass().getSimpleName())
                            .append(" (era ").append(b.getEra())
                            .append(", cost=").append(b.getFoodCost())
                            .append(", endPP=").append(b.getEndOfGamePP())
                            .append(")")
                            .append(nl);
                }
            }
        }
    }

    private void appendRow(StringBuilder sb, String label, CardRow row, String nl) {
        if (row == null) {
            return;
        }
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

    @Override
    public int promptInt(String prompt) {
        while (true) {
            String line = promptLine(prompt);
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                emitLine("Not a valid integer, try again.");
            }
        }
    }

    @Override
    public String promptLine(String prompt) {
        synchronized (outLock) {
            activePrompt = prompt;
            System.out.print(prompt);
            System.out.flush();
        }
        // Released the lock so the reader thread can still post
        // notifications while we wait for the user. If a notification
        // arrives, emitLine will newline + print + redraw the prompt.
        String line = scan.nextLine();
        synchronized (outLock) {
            activePrompt = null;
        }
        return line.trim();
    }

    /**
     * Atomically prints a line of output. If a prompt is currently
     * awaiting input, breaks out of its line first and re-prints it
     * at the bottom so the user always sees the most recent prompt
     * as the last thing on screen.
     */
    private void emitLine(String message) {
        synchronized (outLock) {
            if (activePrompt != null) {
                // Move off the in-flight prompt line before writing.
                System.out.println();
            }
            System.out.println(message);
            if (activePrompt != null) {
                System.out.print(activePrompt);
                System.out.flush();
            }
        }
    }
}
