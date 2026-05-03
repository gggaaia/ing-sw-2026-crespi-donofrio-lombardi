package it.polimi.ingsw.network.shared.messages.client;

import it.polimi.ingsw.controller.GameController;
import it.polimi.ingsw.model.enums.TotemColor;
import it.polimi.ingsw.network.shared.ClientCallback;

/**
 * Sent by a client during {@code COLOR_PICKING} to claim a totem color.
 * <p>
 * The color is sent as a typed {@link TotemColor} (per Q2 = "use TotemColor
 * end-to-end") instead of a raw string, so the server doesn't have to do
 * any string-to-enum parsing.
 */
public class PickColorMessage extends ClientMessage {
    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /** The color the client wants to claim. */
    private final TotemColor color;

    /**
     * @param color the desired totem color (server checks availability)
     */
    public PickColorMessage(TotemColor color) {
        this.color = color;
    }

    @Override
    public void handle(GameController controller, ClientCallback client) {
        controller.handlePickColor(client, color);
    }
}
