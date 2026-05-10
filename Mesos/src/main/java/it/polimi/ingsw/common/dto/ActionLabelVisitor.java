package it.polimi.ingsw.common.dto;

/**
 * Visitor for ActionLabel. Anyone whose behavior depends on which player
 * input an ActionResultDto refers to (e.g. the client-side display of a
 * positive or negative result) implements this interface and puts the
 * label-dependent logic inside the visit methods.
 *
 * Discrimination among labels never uses ==, switch, instanceof or
 * if/else: each ActionLabel subclass overrides accept(ActionLabelVisitor)
 * so it calls back the matching visitXxx, polymorphically.
 */
public interface ActionLabelVisitor {

    void visitNickname();

    void visitPlayerCount();

    void visitPickColor();

    void visitPickOfferingTile();

    void visitPickCharacters();

    void visitPickBuildings();

    void visitPickExtra();
}
