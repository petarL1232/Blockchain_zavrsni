package WLAN_test;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private Socket socket = null;
    private ServerSocket serverSocket = null;
    private DataInputStream in = null;

    public Server(int port) {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Server je pokrenut na "+port);

            socket = serverSocket.accept();
            System.out.println("Client je prihvacen na " + socket.getLocalAddress() +" "+ socket.getInetAddress());

            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            String message = "";

            while(!message.equals("ZAUSTAVI")) {
                message = in.readUTF();
                System.out.println(message);
            }
            System.out.println("Gasim konekciju");

            socket.close();
            in.close();
            serverSocket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
