package it.polimi.ingsw.common.dto;

/**
 * Visitor for Phase. Anyone whose behavior depends on the current phase
 * (the client-side renderer, the server-side input router, etc.)
 * implements this interface and puts the phase-dependent logic inside the
 * visit methods.
 *
 * Discrimination among phases never uses ==, switch, instanceof or
 * if/else: each Phase subclass overrides accept(PhaseVisitor) so it calls
 * back the matching visitXxx, polymorphically.
 *
 * The visit methods are void: the visitor accumulates results inside its
 * own fields, not as return values, to keep the interface generic.
 */
public interface PhaseVisitor {

    void visitWaitingForNickname();

    void visitWaitingForPlayerCount();

    void visitWaitingForPlayers();

    void visitColorPicking();

    void visitPickOfferingTile();

    void visitTurnPickCardsCharacter();

    void visitTurnPickCardsBuilding();

    void visitResetTurnOrderAndPickExtraBuilding();

    void visitResolveBuildingsAndEvents();

    void visitPausedWaitingReconnect();

    void visitGameEnded();
}
