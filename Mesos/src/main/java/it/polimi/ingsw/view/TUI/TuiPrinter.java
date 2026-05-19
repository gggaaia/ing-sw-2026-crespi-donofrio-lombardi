package it.polimi.ingsw.view.TUI;

import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.TurnOrderSlot;
import it.polimi.ingsw.model.TurnOrderTile;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.TotemColor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility di formattazione per la {@link TuiView}: produce stringhe
 * pronte da stampare (banner ASCII, box per gli errori, tabelle
 * formattate per offering track, turn order tile e player).
 *
 * Tutti i metodi sono statici e senza stato. Le stringhe restituite
 * contengono escape ANSI per i colori, che la maggior parte dei
 * terminali interpreta correttamente.
 */
public class TuiPrinter {

    private static final String RED    = "\u001B[31m";
    private static final String RESET  = "\u001B[0m";
    private static final String GREEN  = "\u001B[32m";
    private static final String BLUE   = "\u001B[34m";
    public  static final String YELLOW = "\u001B[33m";
    private static final String nl     = System.lineSeparator();

    /** Banner di benvenuto stampato all'avvio del client. */
    public static String formatBeginGame() {
        String banner = """
          ██████   ██████ ██████████  █████████     ███████     █████████
        ▒▒██████ ██████ ▒▒███▒▒▒▒▒█ ███▒▒▒▒▒███  ███▒▒▒▒▒███  ███▒▒▒▒▒███
         ▒███▒█████▒███  ▒███  █ ▒ ▒███   ▒▒▒  ███     ▒▒███▒███   ▒▒▒
         ▒███▒▒███ ▒███  ▒██████   ▒▒█████████ ▒███     ▒███▒▒█████████
         ▒███ ▒▒▒  ▒███  ▒███▒▒█    ▒▒▒▒▒▒▒▒███▒███     ▒███ ▒▒▒▒▒▒▒▒███
         ▒███      ▒███  ▒███ ▒   █ ███   ▒███▒▒███    ███  ███   ▒███
         █████     █████ ██████████▒▒█████████  ▒▒▒███████▒  ▒▒█████████
        ▒▒▒▒▒     ▒▒▒▒▒ ▒▒▒▒▒▒▒▒▒▒  ▒▒▒▒▒▒▒▒     ▒▒▒▒▒▒▒    ▒▒▒▒▒▒▒▒▒
        """;

        return "Welcome to " + nl +
                RED + banner + RESET +
                "a Cranio Creations Game" + nl;
    }

    /** Formatta un messaggio di errore dentro un riquadro rosso. */
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

    /** Formatta un messaggio informativo dentro un riquadro verde. */
    public static String formatInfo(String message) {
        String fullMessage = "[INFO] " + message;
        int length = fullMessage.length();

        StringBuilder sb = new StringBuilder();

        sb.append("+");
        for (int i = 0; i < length + 2; i++) sb.append("-");
        sb.append("+").append(nl);

        sb.append("| ").append(GREEN).append(fullMessage).append(RESET).append(" |").append(nl);

        sb.append("+");
        for (int i = 0; i < length + 2; i++) sb.append("-");
        sb.append("+");

        return sb.toString();
    }

    /**
     * Riga compatta con info di un giocatore: nickname, colore, risorse,
     * hand di personaggi e edifici in proprio.
     */
    public static String formatPlayerCompact(String nickname, TotemColor color, Integer food, Integer pp,
                                             List<CharacterCard> hand, List<BuildingCard> builds) {
        StringBuilder sb = new StringBuilder();

        String info = String.format(" %s | %s | Food: %s | PP: %s ",
                nickname, color, (food == null ? "?" : food), (pp == null ? "?" : pp));

        sb.append("+").append("-".repeat(info.length())).append("+").append(nl);
        sb.append("|").append(info).append("|").append(nl);
        sb.append("+").append("-".repeat(info.length())).append("+").append(nl);

        sb.append(" Hand: ");
        if (hand == null || hand.isEmpty()) sb.append(" - ");
        else sb.append(hand.stream().map(c -> c.getClass().getSimpleName()).collect(Collectors.joining(" | ")));

        sb.append(nl).append(" Buildings: ");
        if (builds == null || builds.isEmpty()) sb.append(" - ");
        else sb.append(builds.stream().map(b -> b.getClass().getSimpleName()).collect(Collectors.joining(" | ")));

        sb.append(nl);
        return sb.toString();
    }

    /** Tabella orizzontale dell'offering track. */
    public static String formatOfferingTrack(List<OfferingTile> track) {
        String title = " offering track ";
        int numTiles = track.size();

        int colWidth = 22;
        StringBuilder sb = new StringBuilder();

        sb.append("---").append(YELLOW).append(title).append(RESET)
                .append("-".repeat(Math.max(0, numTiles * (colWidth + 1) - title.length() - 3))).append(nl);

        String[] rows = new String[6];
        for (int i = 0; i < 6; i++) rows[i] = "";

        for (OfferingTile t : track) {
            int idx = track.indexOf(t);
            rows[0] += fitToWidth(String.format("| [%d] tile %s ", idx, t.getLetter()), colWidth);
            rows[1] += fitToWidth("|--------------------", colWidth);
            rows[2] += fitToWidth(String.format("| top=%d", t.getTopCardsToGet()), colWidth);
            rows[3] += fitToWidth(String.format("| bottom=%d", t.getBottomCardsToGet()), colWidth);
            rows[4] += fitToWidth(String.format("| food=%d", t.getFoodGained()), colWidth);
            rows[5] += fitToWidth(String.format("| totem=%s",
                    (t.isOccupied() ? t.getCurrentTotemColor() : "empty")), colWidth);
        }

        for (int i = 0; i < 6; i++) sb.append(rows[i]).append("|").append(nl);

        sb.append("-".repeat(numTiles * (colWidth + 1) + 1)).append(nl);

        return sb.toString();
    }

    /** Tabella verticale del turn order tile. */
    public static String formatTurnOrder(TurnOrderTile tot) {
        String title = " turn order tile ";
        TurnOrderSlot[] slots = tot.getTurnOrderSlots();

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            TurnOrderSlot s = slots[i];
            lines.add(String.format(" slot [%d]: food=%d | PP=%d | totem=%s ",
                    i, s.getFood(), s.getPP(),
                    (s.isOccupied() ? s.getTotemColor() : "empty")));
        }

        int maxWidth = lines.stream().mapToInt(String::length).max().orElse(20);
        maxWidth = Math.max(maxWidth, title.length() + 2);

        StringBuilder sb = new StringBuilder();

        sb.append("+").append("-".repeat((maxWidth - title.length()) / 2))
                .append(YELLOW).append(title).append(RESET)
                .append("-".repeat(maxWidth - ((maxWidth - title.length()) / 2) - title.length()))
                .append("+").append(nl);

        for (String line : lines) {
            sb.append("|").append(line).append(" ".repeat(maxWidth - line.length())).append("|").append(nl);
        }

        sb.append("+").append("-".repeat(maxWidth)).append("+").append(nl);

        return sb.toString();
    }

    /** Banner section header in blu. */
    public static String formatSectionHeader(String label) {
        return BLUE + "--- " + label + " ---" + RESET;
    }

    /** Tronca o pad-a-destra una stringa per portarla esattamente a {@code n} caratteri. */
    public static String fitToWidth(String s, int n) {
        if (s.length() > n) {
            return s.substring(0, n);
        } else {
            return String.format("%-" + n + "s", s);
        }
    }
}
