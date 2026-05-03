package it.polimi.ingsw.network.shared.messages.client;

import it.polimi.ingsw.controller.GameController;
import it.polimi.ingsw.network.shared.ClientCallback;

/**
 * Sent by a client during {@code PICK_OFFERING_TILE} to place its totem
 * on the offering track at the given index. The server validates bounds,
 * occupancy and {@code minPlayers} before applying.
 */
public class PickOfferingTileMessage extends ClientMessage {
    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /** Index of the offering tile, in the order returned by
     *  {@code Board.getOfferingTrack()} (so the same indexing the client
     *  saw in the latest GameStateDto). */
    private final int tileIndex;

    /**
     * @param tileIndex the chosen offering-tile index
     */
    public PickOfferingTileMessage(int tileIndex) {
        this.tileIndex = tileIndex;
    }

    @Override
    public void handle(GameController controller, ClientCallback client) {
        controller.handlePickOfferingTile(client, tileIndex);
    }
}
