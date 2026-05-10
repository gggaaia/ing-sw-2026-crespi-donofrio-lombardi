package it.polimi.ingsw.controller.servercontroller.phase;

/**
 * RESOLVE_BUILDINGS_AND_EVENTS phase. Step 6c will fill in the body
 * (call match.resolveBuildings + match.resolveEvents, then either
 * end the match or move to the next round). For now every input
 * defaults to rejection from PhaseStrategy, which is the correct
 * behavior since this phase awaits no player input.
 */
public class ResolveBuildingsAndEventsStrategy extends PhaseStrategy {
    // All inputs use the default rejection from PhaseStrategy.
}
