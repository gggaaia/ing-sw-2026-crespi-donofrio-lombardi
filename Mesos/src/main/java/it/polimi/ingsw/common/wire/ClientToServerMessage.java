package it.polimi.ingsw.common.wire;

import it.polimi.ingsw.model.enums.TotemColor;

import java.io.Serializable;
import java.util.List;

/**
 * Base wire message for the client -> server direction (socket
 * transport). One nested subclass per {@code VirtualServer} method.
 *
 * Dispatch happens via the Visitor pattern: the concrete subclass
 * knows which {@code visitXxx} to call. The visitor is implemented
 * by {@code Socket_ClientHandler} on the server side.
 */
public abstract class ClientToServerMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    public abstract void accept(Visitor v);

    public interface Visitor {
        void visitConnect(String nickname);
        void visitDisconnect(String nickname);
        void visitSetPlayerCount(String nickname, int count);
        void visitPickColor(String nickname, TotemColor color);
        void visitPickOfferingTile(String nickname, int tileIndex);
        void visitPickCharacterCards(String nickname, List<Integer> upper, List<Integer> lower);
        void visitPickBuildingCards(String nickname, List<Integer> upper, List<Integer> lower);
        void visitPickExtraCard(String nickname, Integer cardId, boolean isCharacter);
    }

    public static class Connect extends ClientToServerMessage {
        private static final long serialVersionUID = 1L;
        private final String nickname;
        public Connect(String nickname) { this.nickname = nickname; }
        @Override public void accept(Visitor v) { v.visitConnect(nickname); }
    }

    public static class Disconnect extends ClientToServerMessage {
        private static final long serialVersionUID = 1L;
        private final String nickname;
        public Disconnect(String nickname) { this.nickname = nickname; }
        @Override public void accept(Visitor v) { v.visitDisconnect(nickname); }
    }

    public static class SetPlayerCount extends ClientToServerMessage {
        private static final long serialVersionUID = 1L;
        private final String nickname;
        private final int count;
        public SetPlayerCount(String nickname, int count) {
            this.nickname = nickname; this.count = count;
        }
        @Override public void accept(Visitor v) { v.visitSetPlayerCount(nickname, count); }
    }

    public static class PickColor extends ClientToServerMessage {
        private static final long serialVersionUID = 1L;
        private final String nickname;
        private final TotemColor color;
        public PickColor(String nickname, TotemColor color) {
            this.nickname = nickname; this.color = color;
        }
        @Override public void accept(Visitor v) { v.visitPickColor(nickname, color); }
    }

    public static class PickOfferingTile extends ClientToServerMessage {
        private static final long serialVersionUID = 1L;
        private final String nickname;
        private final int tileIndex;
        public PickOfferingTile(String nickname, int tileIndex) {
            this.nickname = nickname; this.tileIndex = tileIndex;
        }
        @Override public void accept(Visitor v) { v.visitPickOfferingTile(nickname, tileIndex); }
    }

    public static class PickCharacterCards extends ClientToServerMessage {
        private static final long serialVersionUID = 1L;
        private final String nickname;
        private final List<Integer> upper;
        private final List<Integer> lower;
        public PickCharacterCards(String nickname, List<Integer> upper, List<Integer> lower) {
            this.nickname = nickname; this.upper = upper; this.lower = lower;
        }
        @Override public void accept(Visitor v) { v.visitPickCharacterCards(nickname, upper, lower); }
    }

    public static class PickBuildingCards extends ClientToServerMessage {
        private static final long serialVersionUID = 1L;
        private final String nickname;
        private final List<Integer> upper;
        private final List<Integer> lower;
        public PickBuildingCards(String nickname, List<Integer> upper, List<Integer> lower) {
            this.nickname = nickname; this.upper = upper; this.lower = lower;
        }
        @Override public void accept(Visitor v) { v.visitPickBuildingCards(nickname, upper, lower); }
    }

    public static class PickExtraCard extends ClientToServerMessage {
        private static final long serialVersionUID = 1L;
        private final String nickname;
        private final Integer cardId;
        private final boolean isCharacter;
        public PickExtraCard(String nickname, Integer cardId, boolean isCharacter) {
            this.nickname = nickname; this.cardId = cardId; this.isCharacter = isCharacter;
        }
        @Override public void accept(Visitor v) { v.visitPickExtraCard(nickname, cardId, isCharacter); }
    }
}
