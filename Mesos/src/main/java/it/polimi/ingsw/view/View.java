package it.polimi.ingsw.view;

import it.polimi.ingsw.controller.client.ClientController;
import it.polimi.ingsw.model.enums.TotemColor;

import java.util.List;
import java.util.Set;

/**
 * Interfaccia che il {@code ClientController} usa per parlare con
 * l'utente. Due gruppi di metodi:
 *
 *   - display* / refresh: il controller dice alla view "qui c'\u00e8
 *     un evento, mostra qualcosa". Non bloccano.
 *
 *   - prompt*: il controller chiede all'utente di scegliere. BLOCCANO
 *     finch\u00e9 l'utente non sceglie. Vengono chiamati solo nei
 *     metodi *Turn del ClientController quando il turno \u00e8 quello
 *     dell'utente locale.
 *
 * Implementazioni: {@code TuiView} (testo) e {@code GuiView} (JavaFX).
 */
public interface View {

    /** Chiamato all'apertura della connessione. */
    void displayConnected(String host, int port);

    /** Mostra un messaggio informativo generico. */
    void displayInfo(String message);

    /** Mostra un messaggio di errore. */
    void displayError(String message);

    /**
     * Re-renderizza lo schermo dal mini-stato corrente del controller.
     * Chiamato dopo ogni aggiornamento del mini-stato.
     */
    void refresh(ClientController state);

    /** Prompt iniziale del nickname. Blocca. */
    String promptNickname();

    /** Prompt del numero di giocatori (2-5). Blocca. */
    int promptPlayerCount();

    /** Prompt del colore. Blocca. */
    TotemColor promptColor(Set<TotemColor> available);

    /** Prompt dell'offering tile (indice 0..trackSize-1). Blocca. */
    int promptOfferingTile(int trackSize);

    /** Prompt degli id delle carte personaggio della upper row. Blocca. */
    List<Integer> promptCharacterUpperIds(int budget);

    /** Prompt degli id delle carte personaggio della lower row. Blocca. */
    List<Integer> promptCharacterLowerIds(int budget);

    /** Prompt degli id degli edifici della upper row. Blocca. */
    List<Integer> promptBuildingUpperIds(int remaining);

    /** Prompt degli id degli edifici della lower row. Blocca. */
    List<Integer> promptBuildingLowerIds(int remaining);

    /** Prompt della pesca extra: skip, una carta personaggio o una edificio. Blocca. */
    ExtraPickChoice promptExtraCard();

    /**
     * Risultato del prompt extra-pick. Tre forme: skip, character con
     * id, building con id. Per evitare di esporre tipi complessi,
     * usiamo un piccolo record-like.
     */
    final class ExtraPickChoice {
        public final Integer cardId;
        public final boolean isCharacter;

        /** Skip: {@code cardId == null}. */
        public ExtraPickChoice() {
            this.cardId = null;
            this.isCharacter = true;
        }

        /** Pesca una carta con dato id. */
        public ExtraPickChoice(Integer cardId, boolean isCharacter) {
            this.cardId = cardId;
            this.isCharacter = isCharacter;
        }
    }
}
