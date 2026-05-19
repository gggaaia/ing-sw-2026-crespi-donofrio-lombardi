package it.polimi.ingsw.controller.server;

import it.polimi.ingsw.common.VirtualView;
import it.polimi.ingsw.common.dto.Phase;
import it.polimi.ingsw.model.enums.TotemColor;

import java.util.List;

/**
 * Logica per fase del {@code ServerController}. Una classe astratta
 * base + 11 classi nested, una per ogni fase del gioco.
 *
 * Ogni fase concreta override solo le azioni che la fase accetta;
 * le altre ereditano il default "reject". Il dispatch per fase \u00e8
 * polimorfico, senza discriminazione esplicita sui tipi.
 *
 * Il {@code ServerController} tiene un riferimento {@code PhaseLogic
 * phase} che muta al cambio fase. Quando arriva un'azione, il
 * controller chiama {@code phase.onAzione(this, ...)} che fa il
 * dispatch alla classe concreta corrente.
 *
 * Le classi concrete NON fanno validazione di stato fine n\u00e9
 * mutazioni: delegano al {@code ServerController} via metodi
 * {@code accept*} che contengono la validazione vera e le mutazioni.
 * Le classi di fase rispondono solo alla domanda "questo metodo
 * \u00e8 ammesso in questa fase?".
 *
 * Ogni sottoclasse porta con s\u00e9 il proprio {@link Phase} DTO
 * (passato al costruttore della base), restituito da
 * {@link #getPhaseDto()} per popolare il campo phase di un
 * {@code GameSnapshot} senza dover ispezionare il tipo della
 * sottoclasse.
 */
public abstract class PhaseLogic {

    private final Phase phaseDto;

    protected PhaseLogic(Phase phaseDto) {
        this.phaseDto = phaseDto;
    }

    /** Il {@link Phase} corrispondente, usato dentro {@code GameSnapshot}. */
    public Phase getPhaseDto() {
        return phaseDto;
    }

    // === default: tutte le azioni rifiutate ===

    public void onConnect(ServerController c, String nick, VirtualView view) {
        c.rejectAction(nick, "cannot join right now");
    }

    public void onDisconnect(ServerController c, String nick) {
        // Disconnect \u00e8 sempre accettato; il controller gestisce la cascata.
        c.acceptDisconnect(nick);
    }

    public void onSetPlayerCount(ServerController c, String nick, int count) {
        c.rejectAction(nick, "cannot set player count right now");
    }

    public void onPickColor(ServerController c, String nick, TotemColor color) {
        c.rejectAction(nick, "cannot pick color right now");
    }

    public void onPickOfferingTile(ServerController c, String nick, int tileIndex) {
        c.rejectAction(nick, "cannot pick offering tile right now");
    }

    public void onPickCharacterCards(ServerController c, String nick,
                                     List<Integer> upper, List<Integer> lower) {
        c.rejectAction(nick, "cannot pick character cards right now");
    }

    public void onPickBuildingCards(ServerController c, String nick,
                                    List<Integer> upper, List<Integer> lower) {
        c.rejectAction(nick, "cannot buy buildings right now");
    }

    public void onPickExtraCard(ServerController c, String nick,
                                Integer cardId, boolean isCharacter) {
        c.rejectAction(nick, "cannot pick extra card right now");
    }

    /**
     * True solo su {@code PausedWaitingReconnect}. Serve al lone-player
     * timer per verificare se la fase \u00e8 ancora in pausa prima di
     * forzare la vittoria.
     */
    public boolean isPaused() {
        return false;
    }

    /**
     * True solo su {@code GameEnded}. Serve a {@code acceptDisconnect}
     * per non rimettere la fase in pausa dopo che la partita \u00e8
     * gi\u00e0 finita.
     */
    public boolean isFinished() {
        return false;
    }

    /**
     * Chiamato dopo che il giocatore di turno si \u00e8 disconnesso
     * (o subito dopo una resume da pausa). Le fasi che hanno un
     * "attore corrente" da saltare lo fanno qui; le altre lasciano
     * il default no-op.
     */
    public void advanceCurrent(ServerController c) {
        // default: no-op
    }

    /**
     * Re-invia al singolo client {@code nick} l'evento {@code *Turn}
     * corrispondente alla fase corrente, in modo che lui possa
     * (se \u00e8 il suo turno) ri-promptare l'utente. Chiamato dopo
     * {@code onActionRejected} per non lasciare il client appeso, e
     * dopo {@code resumeFromPause} / {@code onGameSnapshot} di
     * riconnessione perch\u00e9 chi torna in gioco con il turno in
     * mano possa essere subito prompttato.
     *
     * Le fasi senza un {@code *Turn} (lobby, resolve, paused, ended)
     * lasciano il default no-op.
     */
    public void emitCurrentTurnTo(ServerController c, String nick) {
        // default: no-op
    }

    // === fasi concrete ===

    /** Fase iniziale: si attende il primo connect. */
    public static final class WaitingForNickname extends PhaseLogic {
        public WaitingForNickname() { super(Phase.WAITING_FOR_NICKNAME); }
        @Override
        public void onConnect(ServerController c, String nick, VirtualView view) {
            c.acceptFirstPlayerJoin(nick, view);
        }
    }

    /** Il primo giocatore deve impostare il numero di partecipanti. */
    public static final class WaitingForPlayerCount extends PhaseLogic {
        public WaitingForPlayerCount() { super(Phase.WAITING_FOR_PLAYER_COUNT); }
        @Override
        public void onConnect(ServerController c, String nick, VirtualView view) {
            // Eventuali altri non sono ammessi finch\u00e9 non \u00e8 fissato il numero.
            c.rejectAction(nick, "wait for the first player to set the participant count");
        }

        @Override
        public void onSetPlayerCount(ServerController c, String nick, int count) {
            c.acceptSetPlayerCount(nick, count);
        }
    }

    /** Si attendono altri giocatori. */
    public static final class WaitingForPlayers extends PhaseLogic {
        public WaitingForPlayers() { super(Phase.WAITING_FOR_PLAYERS); }
        @Override
        public void onConnect(ServerController c, String nick, VirtualView view) {
            c.acceptAdditionalPlayerJoin(nick, view);
        }
    }

    /** Color picking: a turno ognuno sceglie il colore. */
    public static final class ColorPicking extends PhaseLogic {
        public ColorPicking() { super(Phase.COLOR_PICKING); }
        @Override
        public void onPickColor(ServerController c, String nick, TotemColor color) {
            c.acceptColorPick(nick, color);
        }
        @Override
        public void emitCurrentTurnTo(ServerController c, String nick) {
            c.sendColorTurnTo(nick);
        }
    }

    /** Si piazza il totem sull'offering track. */
    public static final class PickOfferingTile extends PhaseLogic {
        public PickOfferingTile() { super(Phase.PICK_OFFERING_TILE); }
        @Override
        public void onPickOfferingTile(ServerController c, String nick, int tileIndex) {
            c.acceptOfferingTilePick(nick, tileIndex);
        }
        @Override
        public void advanceCurrent(ServerController c) {
            c.advanceOfferingPicker();
        }
        @Override
        public void emitCurrentTurnTo(ServerController c, String nick) {
            c.sendOfferingTileTurnTo(nick);
        }
    }

    /** Pesca delle carte personaggio. */
    public static final class TurnPickCardsCharacter extends PhaseLogic {
        public TurnPickCardsCharacter() { super(Phase.TURN_PICK_CARDS_CHARACTER); }
        @Override
        public void onPickCharacterCards(ServerController c, String nick,
                                         List<Integer> upper, List<Integer> lower) {
            c.acceptCharacterCardsPick(nick, upper, lower);
        }
        @Override
        public void advanceCurrent(ServerController c) {
            c.advanceResolver();
        }
        @Override
        public void emitCurrentTurnTo(ServerController c, String nick) {
            c.sendCharacterPickTurnTo(nick);
        }
    }

    /** Acquisto delle carte edificio. */
    public static final class TurnPickCardsBuilding extends PhaseLogic {
        public TurnPickCardsBuilding() { super(Phase.TURN_PICK_CARDS_BUILDING); }
        @Override
        public void onPickBuildingCards(ServerController c, String nick,
                                        List<Integer> upper, List<Integer> lower) {
            c.acceptBuildingCardsPick(nick, upper, lower);
        }
        @Override
        public void advanceCurrent(ServerController c) {
            c.advanceResolver();
        }
        @Override
        public void emitCurrentTurnTo(ServerController c, String nick) {
            c.sendBuildingPickTurnTo(nick);
        }
    }

    /** Pesca extra opzionale per il proprietario dell'edificio speciale. */
    public static final class ResetTurnOrderAndPickExtraBuilding extends PhaseLogic {
        public ResetTurnOrderAndPickExtraBuilding() { super(Phase.RESET_TURN_ORDER_AND_PICK_EXTRA_BUILDING); }
        @Override
        public void onPickExtraCard(ServerController c, String nick,
                                    Integer cardId, boolean isCharacter) {
            c.acceptExtraCardPick(nick, cardId, isCharacter);
        }
        @Override
        public void advanceCurrent(ServerController c) {
            c.skipExtraPick();
        }
        @Override
        public void emitCurrentTurnTo(ServerController c, String nick) {
            c.sendExtraPickTurnTo(nick);
        }
    }

    /** Risoluzione automatica di edifici ed eventi. Nessun input atteso. */
    public static final class ResolveBuildingsAndEvents extends PhaseLogic {
        public ResolveBuildingsAndEvents() { super(Phase.RESOLVE_BUILDINGS_AND_EVENTS); }
        // tutto default reject; questa fase \u00e8 attraversata senza
        // attendere input dei client
    }

    /** Match in pausa, in attesa di riconnessioni. */
    public static final class PausedWaitingReconnect extends PhaseLogic {
        public PausedWaitingReconnect() { super(Phase.PAUSED_WAITING_RECONNECT); }
        @Override
        public boolean isPaused() {
            return true;
        }

        @Override
        public void onConnect(ServerController c, String nick, VirtualView view) {
            // Durante la pausa accettiamo SOLO riconnessioni di nickname gi\u00e0 in partita.
            c.acceptReconnect(nick, view);
        }
    }

    /** Partita conclusa, terminale. */
    public static final class GameEnded extends PhaseLogic {
        public GameEnded() { super(Phase.GAME_ENDED); }
        @Override
        public boolean isFinished() {
            return true;
        }
        // tutto default reject; non si pu\u00f2 pi\u00f9 fare niente
    }
}
