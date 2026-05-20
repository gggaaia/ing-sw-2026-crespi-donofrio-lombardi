package it.polimi.ingsw.view.TUI;

import it.polimi.ingsw.model.CardRow;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.TurnOrderSlot;
import it.polimi.ingsw.model.TurnOrderTile;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.Card;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.card.EventCard;
import it.polimi.ingsw.model.enums.TotemColor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Formatting helpers for the TUI. They build strings ready to print;
 * they don't write to System.out themselves.
 */
public class TuiPrinter {

    public static final String RED = "\u001B[31m";
    public static final String RESET = "\u001B[0m";
    public static final String GREEN = "\u001B[32m";
    public static final String BLUE = "\u001B[34m";
    public static final String YELLOW = "\u001B[33m";
    /** Bold (ANSI SGR 1). Can be combined with the colours above. */
    public static final String BOLD = "\u001B[1m";
    /** {@code [INFO]} tag with bright-green background and bold black
     *  text, so it really stands out even in the middle of a wall of
     *  output. SGR: 1 = bold, 30 = fg black, 102 = bg bright green. */
    private static final String INFO_TAG = "\u001B[1;30;102m INFO \u001B[0m";

    private static final String nl = System.lineSeparator();

    /** Welcome banner, printed when the client starts. */
    public static String formatBeginGame() {
        String banner = ""
                + "  \u2588\u2588\u2588\u2588\u2588\u2588   \u2588\u2588\u2588\u2588\u2588\u2588 \u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588  \u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588     \u2588\u2588\u2588\u2588\u2588\u2588\u2588     \u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588 " + nl
                + "\u2592\u2592\u2588\u2588\u2588\u2588\u2588\u2588 \u2588\u2588\u2588\u2588\u2588\u2588 \u2592\u2592\u2588\u2588\u2588\u2592\u2592\u2592\u2592\u2592\u2588 \u2588\u2588\u2588\u2592\u2592\u2592\u2592\u2592\u2588\u2588\u2588  \u2588\u2588\u2588\u2592\u2592\u2592\u2592\u2592\u2588\u2588\u2588\u2592\u2588\u2588\u2588\u2592\u2592\u2592\u2592\u2592\u2588\u2588\u2588" + nl
                + " \u2592\u2588\u2588\u2588\u2592\u2588\u2588\u2588\u2588\u2588\u2592\u2588\u2588\u2588  \u2592\u2588\u2588\u2588  \u2588 \u2592 \u2592\u2588\u2588\u2588   \u2592\u2592\u2592  \u2588\u2588\u2588     \u2592\u2592\u2588\u2588\u2588\u2592\u2588\u2588\u2588   \u2592\u2592\u2592 " + nl
                + " \u2592\u2588\u2588\u2588\u2592\u2592\u2588\u2588\u2588 \u2592\u2588\u2588\u2588  \u2592\u2588\u2588\u2588\u2588\u2588\u2588   \u2592\u2592\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588 \u2592\u2588\u2588\u2588     \u2592\u2588\u2588\u2588\u2592\u2592\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588 " + nl
                + " \u2592\u2588\u2588\u2588 \u2592\u2592\u2592  \u2592\u2588\u2588\u2588  \u2592\u2588\u2588\u2588\u2592\u2592\u2588    \u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2588\u2588\u2588\u2592\u2588\u2588\u2588     \u2592\u2588\u2588\u2588 \u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2588\u2588\u2588" + nl
                + " \u2592\u2588\u2588\u2588      \u2592\u2588\u2588\u2588  \u2592\u2588\u2588\u2588 \u2592   \u2588 \u2588\u2588\u2588   \u2592\u2588\u2588\u2588\u2592\u2592\u2588\u2588\u2588    \u2588\u2588\u2588  \u2588\u2588\u2588   \u2592\u2588\u2588\u2588" + nl
                + " \u2588\u2588\u2588\u2588\u2588     \u2588\u2588\u2588\u2588\u2588 \u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2592\u2592\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588  \u2592\u2592\u2592\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2592  \u2592\u2592\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588\u2588 " + nl
                + "\u2592\u2592\u2592\u2592\u2592     \u2592\u2592\u2592\u2592\u2592 \u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592  \u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592     \u2592\u2592\u2592\u2592\u2592\u2592\u2592    \u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592\u2592  " + nl;

        return  RED + "Welcome to" + nl
                 + banner
                + "a Cranio Creations Game" + RESET + nl;
    }

    /** Red, boxed error message. */
    public static String formatError(String message) {
        String fullMessage = "[ERROR] " + message;
        int length = fullMessage.length();
        StringBuilder sb = new StringBuilder();
        sb.append("+");
        for (int i = 0; i < length + 2; i++) sb.append("-");
        sb.append("+").append(nl);
        sb.append("| ").append(RED).append(fullMessage).append(RESET).append(" |").append(nl);
        sb.append("+");
        for (int i = 0; i < length + 2; i++) sb.append("-");
        sb.append("+");
        return sb.toString();
    }

    /**
     * ms-precision formatter (HH:mm:ss.SSS). Static so we don't
     * rebuild it on every message. The timestamp is taken when the
     * client is about to render the INFO line; mostly handy for
     * debugging ordering / batching issues, see comment below.
     */
    private static final DateTimeFormatter INFO_TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * Informational message (e.g. "Alice places totem 0"). The
     * {@code  INFO } tag is drawn with a bright green background and
     * bold black text; the message itself is bold too. Makes game
     * events easy to spot even when the output is scrolling fast.
     *
     * Render timestamp: every line is prefixed with the local time
     * (HH:mm:ss.SSS) taken when this function is called, i.e. right
     * before the client prints the event. Useful to tell whether two
     * lines that look "together" on screen actually arrived together
     * (timestamps very close) or if the console is just buffering and
     * flushing in chunks (timestamps far apart). Can be dropped later
     * with no functional impact.
     */
    public static String formatInfo(String message) {
        String ts = LocalTime.now().format(INFO_TIME_FMT);
        return INFO_TAG + " " + ts + " " + BOLD + message + RESET;
    }

    /** Compact one-block summary for a player. */
    public static String formatPlayerCompact(String nickname,
                                             TotemColor color,
                                             Integer food,
                                             Integer pp,
                                             List<CharacterCard> hand,
                                             List<BuildingCard> builds) {
        StringBuilder sb = new StringBuilder();
        String info = String.format(" %s | %s | Food: %s | PP: %s ",
                nickname,
                color == null ? "-" : color,
                food == null ? "0" : food,
                pp == null ? "0" : pp);
        sb.append("+").append("-".repeat(info.length())).append("+").append(nl);
        sb.append("|").append(info).append("|").append(nl);
        sb.append("+").append("-".repeat(info.length())).append("+").append(nl);
        sb.append(" Hand: ");
        if (hand == null || hand.isEmpty()) {
            sb.append(" - ");
        } else {
            sb.append(hand.stream()
                    .map(c -> c.getClass().getSimpleName())
                    .collect(Collectors.joining(" | ")));
        }
        sb.append(nl).append(" Buildings: ");
        if (builds == null || builds.isEmpty()) {
            sb.append(" - ");
        } else {
            sb.append(builds.stream()
                    .map(b -> b.getClass().getSimpleName())
                    .collect(Collectors.joining(" | ")));
        }
        sb.append(nl);
        return sb.toString();
    }

    /** Offering track formatted in columns. */
    public static String formatOfferingTrack(List<OfferingTile> track) {
        String title = " offering track ";
        int numTiles = track.size();
        int colWidth = 22;
        StringBuilder sb = new StringBuilder();
        sb.append("---")
                .append(YELLOW).append(title).append(RESET)
                .append("-".repeat(Math.max(0, numTiles * (colWidth + 1) - title.length() - 3)))
                .append(nl);

        String[] rows = new String[6];
        for (int i = 0; i < 6; i++) rows[i] = "";
        for (int idx = 0; idx < track.size(); idx++) {
            OfferingTile t = track.get(idx);
            rows[0] += fitToWidth(String.format("| [%d] tile %s ", idx, t.getLetter()), colWidth);
            rows[1] += fitToWidth("|--------------------", colWidth);
            rows[2] += fitToWidth(String.format("| top=%d", t.getTopCardsToGet()), colWidth);
            rows[3] += fitToWidth(String.format("| bottom=%d", t.getBottomCardsToGet()), colWidth);
            rows[4] += fitToWidth(String.format("| food=%d", t.getFoodGained()), colWidth);
            rows[5] += fitToWidth(String.format("| totem=%s",
                    t.isOccupied() ? t.getCurrentTotemColor() : "empty"), colWidth);
        }
        for (int i = 0; i < 6; i++) {
            sb.append(rows[i]).append("|").append(nl);
        }
        sb.append("-".repeat(numTiles * (colWidth + 1) + 1)).append(nl);
        return sb.toString();
    }

    /** Boxed view of the turn order tile. */
    public static String formatTurnOrder(TurnOrderTile tot) {
        String title = " turn order tile ";
        TurnOrderSlot[] slots = tot.getTurnOrderSlots();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            TurnOrderSlot s = slots[i];
            lines.add(String.format(" slot [%d]: food=%d | PP=%d | totem=%s ",
                    i, s.getFood(), s.getPP(),
                    s.isOccupied() ? s.getTotemColor() : "empty"));
        }
        int maxWidth = lines.stream().mapToInt(String::length).max().orElse(20);
        maxWidth = Math.max(maxWidth, title.length() + 2);
        StringBuilder sb = new StringBuilder();
        sb.append("+")
                .append("-".repeat((maxWidth - title.length()) / 2))
                .append(YELLOW).append(title).append(RESET)
                .append("-".repeat(maxWidth - ((maxWidth - title.length()) / 2) - title.length()))
                .append("+").append(nl);
        for (String line : lines) {
            sb.append("|").append(line)
                    .append(" ".repeat(maxWidth - line.length()))
                    .append("|").append(nl);
        }
        sb.append("+").append("-".repeat(maxWidth)).append("+").append(nl);
        return sb.toString();
    }

    private static String fitToWidth(String s, int n) {
        if (s.length() > n) {
            return s.substring(0, n);
        }
        return String.format("%-" + n + "s", s);
    }

    /**
     * Genera la rappresentazione grafica affiancata delle carte contenute in una CardRow.
     */
    public static String formatCardRow(CardRow row) {
        if (row == null) return "";
        StringBuilder sb = new StringBuilder();

        sb.append("  characters:").append(nl);
        List<CharacterCard> chars = row.getCharacterCards();
        if (chars.isEmpty()) {
            sb.append("    (none)").append(nl);
        } else {
            List<List<String>> allCharBlocks = new ArrayList<>();
            for (CharacterCard c : chars) {
                List<String> info = new ArrayList<>();

                info.add("[" + c.getCardID() + "] " + c.getClass().getSimpleName());

                String cardDetails = c.toString();
                if (cardDetails != null && !cardDetails.isEmpty()) {
                    String[] lines = cardDetails.split(";");
                    for (String line : lines) {
                        info.add(line);
                    }
                }

                allCharBlocks.add(renderSingleCard(info));
            }
            sb.append(sideBySideBlocks(allCharBlocks));
        }

        sb.append("  events:").append(nl);
        List<EventCard> events = row.getEventCards();
        if (events.isEmpty()) {
            sb.append("    (none)").append(nl);
        } else {
            List<List<String>> allEventBlocks = new ArrayList<>();
            for (Card e : events) {
                List<String> info = new ArrayList<>();

                info.add("[" + e.getCardID() + "] " + e.getClass().getSimpleName());

                String cardDetails = e.toString();
                if (cardDetails != null && !cardDetails.isEmpty()) {
                    String[] lines = cardDetails.split(";");
                    for (String line : lines) {
                        info.add(line);
                    }
                }

                allEventBlocks.add(renderSingleCard(info));
            }
            sb.append(sideBySideBlocks(allEventBlocks));
        }

        sb.append("  buildings:").append(nl);
        List<BuildingCard> builds = row.getBuildingCards();
        if (builds.isEmpty()) {
            sb.append("    (none)").append(nl);
        } else {
            List<List<String>> allBuildBlocks = new ArrayList<>();
            for (BuildingCard b : builds) {
                List<String> info = new ArrayList<>();

                info.add("[" + b.getCardID() + "] " + b.getClass().getSimpleName());

                String cardDetails = b.toString();
                if (cardDetails != null && !cardDetails.isEmpty()) {
                    String[] lines = cardDetails.split(";");
                    for (String line : lines) {
                        info.add(line);
                    }
                }

                allBuildBlocks.add(renderSingleCard(info));
            }
            sb.append(sideBySideBlocks(allBuildBlocks));
        }

        return sb.toString();
    }

    /**
     * Crea i bordi usando '-' e '|' attorno alle informazioni verticali di una singola carta.
     */
    private static List<String> renderSingleCard(List<String> infoLines) {
        int maxTextLength = 0;
        for (String line : infoLines) {
            if (line.length() > maxTextLength) {
                maxTextLength = line.length();
            }
        }

        int internalWidth = maxTextLength + 2;
        List<String> cardImage = new ArrayList<>();

        String horizontalBorder = "+" + "-".repeat(internalWidth) + "+";
        cardImage.add(horizontalBorder);

        for (String line : infoLines) {
            int paddingNeeded = internalWidth - line.length() - 1;
            String formattedLine = "| " + line + " ".repeat(paddingNeeded) + "|";
            cardImage.add(formattedLine);
        }

        cardImage.add(horizontalBorder);
        return cardImage;
    }

    /**
     * Unisce orizzontalmente le righe dei vari blocchi di carte inserendo uno spazio.
     */
    private static String sideBySideBlocks(List<List<String>> blocks) {
        if (blocks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();

        int maxRows = 0;
        for (List<String> block : blocks) {
            if (block.size() > maxRows) {
                maxRows = block.size();
            }
        }

        for (int r = 0; r < maxRows; r++) {
            sb.append("    ");
            for (int b = 0; b < blocks.size(); b++) {
                List<String> currentCard = blocks.get(b);
                int lastIndex = currentCard.size() - 1;

                if (r < lastIndex) {
                    sb.append(currentCard.get(r));
                } else if (r == lastIndex) {
                    sb.append(currentCard.get(lastIndex));
                } else {
                    int cardWidth = currentCard.get(0).length();
                    sb.append(" ".repeat(cardWidth));
                }

                if (b < blocks.size() - 1) {
                    sb.append("   ");
                }
            }
            sb.append(nl);
        }
        return sb.toString();
    }


}
