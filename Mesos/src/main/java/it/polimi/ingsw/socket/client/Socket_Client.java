package it.polimi.ingsw.socket.client;

import it.polimi.ingsw.common.dto.ActionResultDto;
import it.polimi.ingsw.common.dto.GameStateDto;
import it.polimi.ingsw.common.wire.ServerToClientMessage;
import it.polimi.ingsw.controller.clientcontroller.ClientController;
import it.polimi.ingsw.view.View;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;

/**
 * Client-side socket adapter. Two threads:
 *
 *   - main thread: prompts the user for a nickname, sends the very
 *     first ClientToServerMessage.Connect, and then runs the
 *     interactive input loop via ClientController.runInputLoop().
 *
 *   - reader thread (a daemon spawned by run()): reads serialized
 *     ServerToClientMessage instances from the server and discriminates
 *     among them via the visitor pattern (msg.accept(this) routes to
 *     visitGameState / visitActionResult / visitInfo / visitError).
 *     Each visit method hands the deserialized payload to the
 *     ClientController, which displays it via the View AND wakes up
 *     the input loop on the main thread.
 *
 * The class implements ServerToClientMessage.Visitor so the read
 * loop can dispatch with no instanceof, no switch, no if/else for
 * type discrimination.
 */
public class Socket_Client implements ServerToClientMessage.Visitor {

    private final ObjectInputStream input;
    private final Socket_VirtualServer socket_virtual_server;
    private final ClientController client_controller;

    public Socket_Client(ObjectInputStream input, ObjectOutputStream output, View view) {
        this.input = input;
        this.socket_virtual_server = new Socket_VirtualServer(output);
        this.client_controller = new ClientController(view);
    }

    public void run() {
        String nickname = client_controller.promptNickname();
        client_controller.setNickname(nickname);
        client_controller.setServerStub(socket_virtual_server);

        // Spawn the reader thread BEFORE sending the connect. Daemon so
        // it does not keep the JVM alive once main returns.
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                readLoop();
            }
        });
        reader.setDaemon(true);
        reader.setName("socket-client-reader");
        reader.start();

        try {
            // The 'view' arg of connect is unused on socket: the
            // server-side handler holds its own Socket_VirtualClient
            // as the back-channel.
            socket_virtual_server.connect(nickname, null);
        } catch (RemoteException e) {
            client_controller.onError("Connect failed: " + e.getMessage());
            return;
        }

        // Run the interactive input loop on the main thread. Returns
        // when the input prompter sees a GAME_ENDED state.
        client_controller.runInputLoop();
    }

    /**
     * Body of the daemon reader thread. Reads serialized messages
     * from the input stream until EOF, a network error, or a
     * malformed message terminates it; updates ClientController on
     * each successful read.
     */
    private void readLoop() {
        try {
            while (true) {
                ServerToClientMessage msg = (ServerToClientMessage) input.readObject();
                msg.accept(this);
            }
        } catch (EOFException eof) {
            client_controller.onInfo("Server disconnected.");
        } catch (RemoteException re) {
            client_controller.onError("Network error: " + re.getMessage());
        } catch (IOException io) {
            client_controller.onError("Socket read error: " + io.getMessage());
        } catch (ClassNotFoundException | ClassCastException malformed) {
            client_controller.onError("Malformed wire message: " + malformed.getMessage());
        }
    }

    @Override
    public void visitGameState(GameStateDto state) {
        client_controller.onGameState(state);
    }

    @Override
    public void visitActionResult(ActionResultDto result) {
        client_controller.onActionResult(result);
    }

    @Override
    public void visitInfo(String message) {
        client_controller.onInfo(message);
    }

    @Override
    public void visitError(String message) {
        client_controller.onError(message);
    }
}
