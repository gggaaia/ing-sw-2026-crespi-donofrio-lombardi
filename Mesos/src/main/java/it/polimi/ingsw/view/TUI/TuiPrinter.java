package it.polimi.ingsw.view.TUI;

import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.TurnOrderSlot;
import it.polimi.ingsw.model.TurnOrderTile;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.TotemColor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Formattatori per la TUI. Producono stringhe gi\u00e0 pronte da
 * stampare; non scrivono direttamente su System.out.
 */
public class TuiPrinter {

    public static final String RED = "\u001B[31m";
    public static final String RESET = "\u001B[0m";
    public static final String GREEN = "\u001B[32m";
    public static final String BLUE = "\u001B[34m";
    public static final String YELLOW = "\u001B[33m";
    /** Bold (ANSI SGR 1). Combinabile con i colori sopra. */
    public static final String BOLD = "\u001B[1m";
    /** Tag {@code [INFO]} con sfondo verde brillante e testo nero in
     *  bold, cos\u00ec il marker salta all'occhio anche in mezzo a un
     *  blocco di output. SGR: 1 = bold, 30 = fg black, 102 = bg bright
     *  green. */
    private static final String INFO_TAG = "\u001B[1;30;102m INFO \u001B[0m";

    private static final String nl = System.lineSeparator();

    /** Banner di benvenuto, stampato all'apertura del client. */
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

        return "Welcome to" + nl
                + RED + banner + RESET
                + "a Cranio Creations Game" + nl;
    }

    /** Errore boxato in rosso. */
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
     * Formatter ms-precision (HH:mm:ss.SSS). Statico per non
     * ricostruirlo a ogni messaggio. Il timestamp \u00e8 quello del
     * momento in cui il client renderizza la riga di INFO; serve
     * tipicamente per debug di ordine/batching, vedi commento sotto.
     */
    private static final DateTimeFormatter INFO_TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * Messaggio informativo (es. "Alice piazza il totem 0"). Il tag
     * {@code  INFO } \u00e8 stampato con sfondo verde brillante e testo
     * nero bold; il messaggio stesso \u00e8 in bold. Cos\u00ec
     * l'utente individua subito gli eventi di gioco anche quando
     * scorrono velocemente.
     *
     * Timestamp di rendering: ogni riga viene prefissata con l'orario
     * locale (HH:mm:ss.SSS) calcolato nel momento in cui questa
     * funzione viene chiamata, cio\u00e8 quando il client sta per
     * stampare l'evento. Serve per diagnosticare se due righe che
     * appaiono "insieme" sull'output siano davvero arrivate insieme
     * (timestamps molto vicini) oppure se la console stia bufferizzando
     * e renderizzando a blocchi (timestamps distanti). Pu\u00f2 essere
     * tolto in seguito senza impatto funzionale.
     */
    public static String formatInfo(String message) {
        String ts = LocalTime.now().format(INFO_TIME_FMT);
        return INFO_TAG + " " + ts + " " + BOLD + message + RESET;
    }

    /** Riga compatta per un giocatore. */
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
                food == null ? "?" : food,
                pp == null ? "?" : pp);
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

    /** Offering track formattato in colonne. */
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

    /** Turn order tile formattata. */
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
}
