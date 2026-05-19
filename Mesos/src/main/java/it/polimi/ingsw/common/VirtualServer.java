package it.polimi.ingsw.common;

import it.polimi.ingsw.model.enums.TotemColor;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Contratto delle chiamate dal client al server. Ogni metodo
 * corrisponde a un'azione del giocatore: ogni parametro \u00e8 un dato
 * tipato (numeri, enum, liste di id, stringhe), niente DTO grossi.
 *
 * Implementato da {@code Rmi_Server} e da {@code Socket_VirtualServer}.
 * I controller (client e server) parlano sempre attraverso questa
 * interfaccia: non sanno quale dei due implementatori sta sotto.
 *
 * Il server identifica il chiamante dal nickname passato in ogni
 * metodo. Non c'\u00e8 autenticazione: il modello di identit\u00e0
 * \u00e8 basato sul trust che il client non menta sul proprio nickname.
 */
public interface VirtualServer extends Remote {

    /**
     * Prima chiamata che ogni client fa. Porta il nickname richiesto e
     * la propria VirtualView (il back-channel server-to-client).
     */
    void connect(String nickname, VirtualView client) throws RemoteException;

    /**
     * Disconnessione esplicita. Il client comunica al server che sta
     * uscendo. Server e client si liberano delle risorse.
     */
    void disconnect(String nickname) throws RemoteException;

    /**
     * Il primo giocatore sceglie quanti partecipanti avr\u00e0 la
     * partita. Accettato solo durante {@code WAITING_FOR_PLAYER_COUNT}.
     */
    void setPlayerCount(String nickname, int count) throws RemoteException;

    /**
     * Sceglie il colore del totem per il chiamante. Accettato solo
     * durante {@code COLOR_PICKING}, dal giocatore al turno.
     */
    void pickColor(String nickname, TotemColor color) throws RemoteException;

    /**
     * Posiziona il totem sulla tile dell'offering track al dato indice.
     * Accettato solo durante {@code PICK_OFFERING_TILE}, dal giocatore
     * il cui turno \u00e8 corrente.
     */
    void pickOfferingTile(String nickname, int tileIndex) throws RemoteException;

    /**
     * Sceglie carte personaggio dalle file (upper / lower) per id,
     * entro i budget fissati dalla tile dell'offering. Accettato solo
     * durante {@code TURN_PICK_CARDS_CHARACTER}. Le liste possono
     * essere vuote.
     */
    void pickCharacterCards(String nickname,
                            List<Integer> upperRowIds,
                            List<Integer> lowerRowIds) throws RemoteException;

    /**
     * Compra carte edificio dalle file (upper / lower) per id, entro i
     * budget residui e il cibo. Accettato solo durante
     * {@code TURN_PICK_CARDS_BUILDING}.
     */
    void pickBuildingCards(String nickname,
                           List<Integer> upperRowIds,
                           List<Integer> lowerRowIds) throws RemoteException;

    /**
     * Esegue la pesca extra opzionale concessa dall'edificio speciale.
     * {@code cardId == null} significa "salta"; {@code isCharacter}
     * dice al server se la carta scelta \u00e8 personaggio o edificio.
     * Accettato solo durante {@code RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING}.
     */
    void pickExtraCard(String nickname,
                       Integer cardId,
                       boolean isCharacter) throws RemoteException;
}
