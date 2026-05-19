package it.polimi.ingsw.view.TUI;

import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.model.OfferingTile;
import it.polimi.ingsw.model.TurnOrderSlot;
import it.polimi.ingsw.model.TurnOrderTile;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.TotemColor;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class TuiPrinter {
    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    public static final String YELLOW = "\u001B[33m";
    private static final String nl = System.lineSeparator();

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

    public static String formatActionResult(ActionResultDto result) {
        if (result.isSuccess()) {
            // Se ha successo: [OK] in verde, il resto normale
            return "[" + GREEN + "OK" + RESET + " | " + result.getLabel() + "] " + result.getMessage();
        } else {
            // Se fallisce: [REJECTED] in rosso, il resto normale
            return "[" + RED + "REJECTED" + RESET + " | " + result.getLabel() + "] " + result.getMessage();
        }
    }

    public static String formatPlayerCompact(String nickname, TotemColor color, Integer food, Integer pp,
                                             List<CharacterCard> hand, List<BuildingCard> builds) {
        StringBuilder sb = new StringBuilder();

        String info = String.format(" %s | %s | Food: %s | PP: %s ",
                nickname, color, (food == null ? "?" : food), (pp == null ? "?" : pp));

        sb.append("+").append("-".repeat(info.length())).append("+").append(System.lineSeparator());
        sb.append("|").append(info).append("|").append(System.lineSeparator());
        sb.append("+").append("-".repeat(info.length())).append("+").append(System.lineSeparator());

        sb.append(" Hand: ");
        if (hand == null || hand.isEmpty()) sb.append(" - ");
        else sb.append(hand.stream().map(c -> c.getClass().getSimpleName()).collect(Collectors.joining(" | ")));

        sb.append(System.lineSeparator()).append(" Buildings: ");
        if (builds == null || builds.isEmpty()) sb.append(" - ");
        else sb.append(builds.stream().map(b -> b.getClass().getSimpleName()).collect(Collectors.joining(" | ")));

        sb.append(System.lineSeparator());
        return sb.toString();
    }

    public static String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    public static String formatOfferingTrack(List<OfferingTile> track) {
        String title = " offering track ";
        int numTiles = track.size();

        int colWidth = 22;
        StringBuilder sb = new StringBuilder();

        sb.append("---").append(YELLOW).append(title).append(RESET).append("-".repeat(numTiles * (colWidth + 1) - title.length() - 3)).append(nl);

        String[] rows = new String[6];
        for (int i = 0; i < 6; i++) rows[i] = "";

        for (OfferingTile t : track) {
            int idx = track.indexOf(t);
            // Nota: rimuovi il padRight precedente e usa fitToWidth per tutto
            rows[0] += TuiPrinter.fitToWidth(String.format("| [%d] tile %s ", idx, t.getLetter()), colWidth);
            rows[1] += TuiPrinter.fitToWidth("|--------------------", colWidth);
            rows[2] += TuiPrinter.fitToWidth(String.format("| top=%d", t.getTopCardsToGet()), colWidth);
            rows[3] += TuiPrinter.fitToWidth(String.format("| bottom=%d", t.getBottomCardsToGet()), colWidth);
            rows[4] += TuiPrinter.fitToWidth(String.format("| food=%d", t.getFoodGained()), colWidth);
            rows[5] += TuiPrinter.fitToWidth(String.format("| totem=%s", (t.isOccupied() ? t.getCurrentTotemColor() : "empty")), colWidth);
        }

        for (int i = 0; i < 6; i++) sb.append(rows[i]).append("|").append(nl);

        sb.append("-".repeat(numTiles * (colWidth + 1) + 1)).append(nl);

        return sb.toString();
    }

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
                .append("-".repeat(maxWidth - ((maxWidth - title.length()) / 2) - title.length())).append("+").append(nl);


        for (String line : lines) {
            sb.append("|").append(line).append(" ".repeat(maxWidth - line.length())).append("|").append(nl);
        }


        sb.append("+").append("-".repeat(maxWidth)).append("+").append(nl);

        return sb.toString();
    }

    public static String fitToWidth(String s, int n) {
        if (s.length() > n) {
            return s.substring(0, n); // Tronca se troppo lunga
        } else {
            return String.format("%-" + n + "s", s); // Aggiunge spazi se troppo corta
        }
    }
}
