package it.polimi.ingsw.common;

import it.polimi.ingsw.common.dto.GameSnapshot;
import it.polimi.ingsw.common.dto.Phase;
import it.polimi.ingsw.model.card.BuildingCard;
import it.polimi.ingsw.model.card.CharacterCard;
import it.polimi.ingsw.model.enums.Era;
import it.polimi.ingsw.model.enums.TotemColor;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Set;

/**
 * Contratto delle chiamate dal server al client. Un metodo per ogni
 * evento di gioco. I parametri portano i dati tipati che il client
 * deve aggiornare nel suo mini-stato locale.
 *
 * Implementato da {@code Rmi_Client} (lato client RMI) e da
 * {@code Socket_VirtualClient} (lato server, per i client socket).
 * Il {@code ServerController} chiama i metodi su istanze di questa
 * interfaccia: non sa quale dei due implementatori sta sotto.
 *
 * I metodi sono di due tipi:
 *   - broadcast (la maggior parte): mandati a tutti i client connessi
 *   - single-player (3): {@code onActionRejected}, {@code onGameSnapshot},
 *     {@code ping} -- destinati solo a un singolo client
 *
 * Il client, ricevendo un evento, aggiorna il proprio mini-stato e,
 * se l'evento contiene il proprio nickname e richiede input, fa
 * partire il prompt per l'utente.
 */
public interface VirtualView extends Remote {

    // === EVENTI LOBBY (broadcast) ===

    /** Un nuovo giocatore si \u00e8 unito alla lobby. */
    void onPlayerJoined(String nickname) throws RemoteException;

    /** Il primo giocatore (con il nickname dato) deve impostare il numero di partecipanti. */
    void onAwaitPlayerCount(String firstPlayerNickname) throws RemoteException;

    /** Il numero di partecipanti \u00e8 stato fissato. */
    void onPlayerCountSet(int count) throws RemoteException;

    /** Lobby in attesa di altri giocatori. */
    void onAwaitMorePlayers(int joined, int total) throws RemoteException;

    // === EVENTI COLOR PICKING (broadcast) ===

    /** Tocca al giocatore con questo nickname scegliere un colore. */
    void onColorTurn(String nickname, Set<TotemColor> available) throws RemoteException;

    /** Il giocatore ha scelto un colore. */
    void onColorPicked(String nickname, TotemColor color) throws RemoteException;

    // === EVENTI INIZIO/PROGRESSO MATCH (broadcast) ===

    /** La partita \u00e8 partita. */
    void onMatchStarted(List<String> playerOrder, Era startingEra) throws RemoteException;

    /** L'era \u00e8 cambiata. */
    void onEraChanged(Era newEra) throws RemoteException;

    /** Un nuovo round \u00e8 iniziato. */
    void onRoundStarted(int round) throws RemoteException;

    // === EVENTI TURN FLOW (broadcast) ===

    /** Tocca al giocatore piazzare il totem sull'offering tile. */
    void onOfferingTileTurn(String nickname, int trackSize) throws RemoteException;

    /** Il giocatore ha piazzato il totem sulla tile. */
    void onOfferingTilePicked(String nickname, int tileIndex) throws RemoteException;

    /** Tocca al giocatore scegliere carte personaggio. */
    void onCharacterPickTurn(String nickname, int upperBudget, int lowerBudget) throws RemoteException;

    /** Il giocatore ha scelto carte personaggio. */
    void onCharacterCardsPicked(String nickname,
                                List<CharacterCard> upper,
                                List<CharacterCard> lower) throws RemoteException;

    /** Tocca al giocatore comprare edifici. */
    void onBuildingPickTurn(String nickname, int upperRemaining, int lowerRemaining) throws RemoteException;

    /** Il giocatore ha comprato edifici. */
    void onBuildingCardsBought(String nickname,
                               List<BuildingCard> upper,
                               List<BuildingCard> lower) throws RemoteException;

    /** Tocca al giocatore fare la pesca extra (special building). */
    void onExtraPickTurn(String nickname) throws RemoteException;

    /** Il giocatore ha fatto la pesca extra (o skippato se cardId=null). */
    void onExtraCardPicked(String nickname, Integer cardId, boolean isCharacter) throws RemoteException;

    // === EVENTI RISORSE (broadcast) ===

    /** Il prestigio del giocatore \u00e8 cambiato. */
    void onPrestigeChanged(String nickname, int newPrestige) throws RemoteException;

    /** Il cibo del giocatore \u00e8 cambiato. */
    void onFoodChanged(String nickname, int newFood) throws RemoteException;

    // === EVENTI FINE ROUND / FINE PARTITA (broadcast) ===

    /** Il round corrente \u00e8 terminato. */
    void onRoundEnded() throws RemoteException;

    /** La partita \u00e8 finita. */
    void onGameEnded(List<String> leaderboard, List<String> winners) throws RemoteException;

    // === EVENTI PAUSE / RECONNECT (broadcast) ===

    /** La partita \u00e8 in pausa (es. un solo giocatore connesso). */
    void onMatchPaused(String reason) throws RemoteException;

    /** La partita \u00e8 ripresa. */
    void onMatchResumed() throws RemoteException;

    /** Un giocatore si \u00e8 disconnesso. */
    void onPlayerDisconnected(String nickname) throws RemoteException;

    /** Un giocatore si \u00e8 riconnesso. */
    void onPlayerReconnected(String nickname) throws RemoteException;

    // === EVENTI SINGLE-PLAYER ===

    /** L'azione del chiamante \u00e8 stata rifiutata dal server. */
    void onActionRejected(String reason) throws RemoteException;

    /** Snapshot completo dello stato del gioco (per join iniziale o riconnessione). */
    void onGameSnapshot(GameSnapshot snapshot) throws RemoteException;

    /**
     * Heartbeat. Body vuoto: serve solo a verificare che il client
     * sia raggiungibile. Una {@code RemoteException} dice al server
     * "questo client \u00e8 morto".
     */
    void ping() throws RemoteException;
}
