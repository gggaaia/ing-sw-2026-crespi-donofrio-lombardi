package it.polimi.ingsw.socket.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

import it.polimi.ingsw.Server;

public class Socket_Server {

    final ServerSocket listenSocket;
    final Server java_server;

    public Socket_Server(ServerSocket listenSocket, Server java_server) {
        this.listenSocket = listenSocket;
        this.java_server = java_server;
    }


    public void run() throws IOException {
        Socket clientSocket;
        while ((clientSocket = this.listenSocket.accept()) != null) {
            // Order matters: the client side already wrote+flushed its
            // stream header before reading, so we read first (unblocks
            // instantly) then write our header and flush so the client's
            // ObjectInputStream constructor unblocks on the other side.
            ObjectInputStream socketRx = new ObjectInputStream(clientSocket.getInputStream());
            ObjectOutputStream socketTx = new ObjectOutputStream(clientSocket.getOutputStream());
            socketTx.flush();

            Socket_VirtualClient virtualClient = new Socket_VirtualClient(socketTx);

            final Socket_ClientHandler handler = new Socket_ClientHandler(
                    this.java_server,
                    socketRx,
                    virtualClient);

            // The slot is registered in Server.registry only when the
            // handler reads the first 'connect' message and learns the
            // nickname; see Socket_ClientHandler.visitConnect.

            Thread handlerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    handler.runVirtualServer();
                }
            });
            handlerThread.start();
        }
    }


}
