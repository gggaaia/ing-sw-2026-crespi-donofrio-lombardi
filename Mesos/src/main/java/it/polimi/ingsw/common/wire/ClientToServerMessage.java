package it.polimi.ingsw.common.wire;

import it.polimi.ingsw.model.enums.TotemColor;

import java.io.Serializable;
import java.util.List;

/**
 * Wire-message abstract base for every call going from a socket client
 * to the server. Mirrors VirtualServer one-to-one: one concrete inner
 * subclass per VirtualServer method.
 *
 * Discrimination on the receiving side (Socket_ClientHandler) is done
 * via the visitor pattern (double dispatch): no instanceof, no switch,
 * no if/else. Each subclass overrides accept(Visitor) to call the
 * matching visitXxx method.
 *
 * Note: the nickname does not appear in wire bodies after Connect.
 * The server-side handler binds itself to the nickname on the first
 * Connect and uses that binding for every subsequent message. The
 * VirtualView "client" parameter from VirtualServer is never on the
 * wire either: on socket the server-side handler already holds its
 * own Socket_VirtualClient as the back-channel.
 */
public abstract class ClientToServerMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Receives the polymorphic dispatch back from the visitor. Each
     * concrete subclass implements this with one line that calls the
     * matching visitXxx on the visitor.
     */
    public abstract void accept(Visitor visitor);

    /**
     * Server-side dispatcher contract. Implemented by
     * Socket_ClientHandler.
     */
    public interface Visitor {
        void visitConnect(String nickname);

        void visitSetPlayerCount(int count);

        void visitPickColor(TotemColor color);

        void visitPickOfferingTile(int tileIndex);

        void visitPickCharacterCards(List<Integer> upperRowIds, List<Integer> lowerRowIds);

        void visitPickBuildingCards(List<Integer> upperRowIds, List<Integer> lowerRowIds);

        void visitPickExtraCard(Integer cardId, boolean isCharacter);
    }

    public static class Connect extends ClientToServerMessage {
        private static final long serialVersionUID = 1L;
        private final String nickname;

        public Connect(String nickname) {
            this.nickname = nickname;
        }

        public String getNickname() {
            return nickname;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitConnect(nickname);
        }
    }

    public static class SetPlayerCount extends ClientToServerMessage {
        private static final long serialVersionUID = 1L;
        private final int count;

        public SetPlayerCount(int count) {
            this.count = count;
        }

        public int getCount() {
            return count;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitSetPlayerCount(count);
        }
    }

    public static class PickColor extends ClientToServerMessage {
        private static final long serialVersionUID = 1L;
        private final TotemColor color;

        public PickColor(TotemColor color) {
            this.color = color;
        }

        public TotemColor getColor() {
            return color;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitPickColor(color);
        }
    }

    public static class PickOfferingTile extends ClientToServerMessage {
        private static final long serialVersionUID = 1L;
        private final int tileIndex;

        public PickOfferingTile(int tileIndex) {
            this.tileIndex = tileIndex;
        }

        public int getTileIndex() {
            return tileIndex;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitPickOfferingTile(tileIndex);
        }
    }

    public static class PickCharacterCards extends ClientToServerMessage {
        private static final long serialVersionUID = 1L;
        private final List<Integer> upperRowIds;
        private final List<Integer> lowerRowIds;

        public PickCharacterCards(List<Integer> upperRowIds, List<Integer> lowerRowIds) {
            this.upperRowIds = upperRowIds;
            this.lowerRowIds = lowerRowIds;
        }

        public List<Integer> getUpperRowIds() {
            return upperRowIds;
        }

        public List<Integer> getLowerRowIds() {
            return lowerRowIds;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitPickCharacterCards(upperRowIds, lowerRowIds);
        }
    }

    public static class PickBuildingCards extends ClientToServerMessage {
        private static final long serialVersionUID = 1L;
        private final List<Integer> upperRowIds;
        private final List<Integer> lowerRowIds;

        public PickBuildingCards(List<Integer> upperRowIds, List<Integer> lowerRowIds) {
            this.upperRowIds = upperRowIds;
            this.lowerRowIds = lowerRowIds;
        }

        public List<Integer> getUpperRowIds() {
            return upperRowIds;
        }

        public List<Integer> getLowerRowIds() {
            return lowerRowIds;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitPickBuildingCards(upperRowIds, lowerRowIds);
        }
    }

    public static class PickExtraCard extends ClientToServerMessage {
        private static final long serialVersionUID = 1L;
        private final Integer cardId;
        private final boolean isCharacter;

        public PickExtraCard(Integer cardId, boolean isCharacter) {
            this.cardId = cardId;
            this.isCharacter = isCharacter;
        }

        public Integer getCardId() {
            return cardId;
        }

        public boolean isCharacter() {
            return isCharacter;
        }

        @Override
        public void accept(Visitor visitor) {
            visitor.visitPickExtraCard(cardId, isCharacter);
        }
    }
}
