package it.polimi.ingsw;

import it.polimi.ingsw.common.VirtualServer;
import it.polimi.ingsw.rmi.client.Rmi_Client;
import it.polimi.ingsw.socket.client.Socket_Client;
import it.polimi.ingsw.view.gui.GuiLauncher;
import it.polimi.ingsw.view.gui.GuiView;
import it.polimi.ingsw.view.TUI.TuiView;
import it.polimi.ingsw.view.View;

import java.io.*;
import java.net.Socket;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;

public class Client {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_RMI_PORT = 52345;
    private static final int DEFAULT_SOCKET_PORT = 52346;

    public static void main(String[] args) throws Exception {
        Scanner scan = new Scanner(System.in);

        String protocol = askChoice(scan,
                "Protocol? [socket/rmi]: ",
                "socket", "rmi");

        String viewType = askChoice(scan,
                "View?     [tui/gui]:    ",
                "tui", "gui");

        // Server IP address: optional first CLI arg. With no args we
        // default to localhost (single-machine run). The port is NOT
        // taken from args: it is always the protocol's default.
        String host = args.length > 0 ? args[0] : DEFAULT_HOST;
        int port = protocol.equals("rmi") ? DEFAULT_RMI_PORT : DEFAULT_SOCKET_PORT;

        System.out.println("Connecting to " + host + ":" + port + " via " + protocol);

        // Reuse the same Scanner inside TuiView.
        View view = viewType.equals("gui") ? new GuiView() : new TuiView(scan);

        if (viewType.equals("gui")) {
            // Latch che si sblocca quando JavaFX ha registrato il mainStage.
            // Il network thread DEVE aspettare che la finestra sia aperta prima
            // di chiamare promptNickname(), altrimenti GuiView.mainStage è null
            // e tutti i popup vengono creati senza owner.
            CountDownLatch fxReady = new CountDownLatch(1);
            GuiView.setOnStageReady(fxReady::countDown);

            // 1. Avviamo il thread di rete in background.
            // È un daemon thread: quando JavaFX chiude la finestra, la JVM
            // termina automaticamente anche questo thread.
            Thread networkThread = new Thread(() -> {
                try {
                    // Aspetta che GuiLauncher.start() abbia chiamato
                    // GuiView.setMainStage() prima di procedere con la rete.
                    fxReady.await();

                    if (protocol.equals("socket")) {
                        startSocketClient(host, port, view);
                    } else {
                        startRmiClient(host, port, view);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            networkThread.setDaemon(true);
            networkThread.start();

            // 2. Avviamo la grafica sul thread principale (bloccante).
            GuiLauncher.main(new String[0]);

        } else {
            // TUI: rete sul thread principale, bloccante su Scanner.
            if (protocol.equals("socket")) {
                startSocketClient(host, port, view);
            } else if (protocol.equals("rmi")) {
                startRmiClient(host, port, view);
            }
        }
    }

    private static String askChoice(Scanner scan, String prompt, String... allowed) {
        while (true) {
            System.out.print(prompt);
            String answer = scan.nextLine().trim().toLowerCase();
            for (String a : allowed) {
                if (a.equals(answer))
                    return answer;
            }
            System.out.println("Invalid choice. Allowed: " + String.join(", ", allowed));
        }
    }

    private static void startSocketClient(String host, int port, View view) throws Exception {
        Socket serverSocket = new Socket(host, port);
        view.displayConnected(host, port);

        ObjectOutputStream socketTx = new ObjectOutputStream(serverSocket.getOutputStream());
        socketTx.flush();
        ObjectInputStream socketRx = new ObjectInputStream(serverSocket.getInputStream());

        new Socket_Client(socketRx, socketTx, view).run();
    }

    private static void startRmiClient(String host, int port, View view) throws Exception {
        final String serverName = "AdderServer";
        Registry registry = LocateRegistry.getRegistry(host, port);
        VirtualServer rmi_server_stub = (VirtualServer) registry
                .lookup(serverName);
        view.displayConnected(host, port);
        new Rmi_Client(rmi_server_stub, view).run();
    }
}