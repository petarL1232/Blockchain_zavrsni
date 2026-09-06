package WLAN;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server implements AutoCloseable{
    private Socket socket = null;
    private final ServerSocket serverSocket;


    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("Server je pokrenut na "+port);
    }

    public PeerConnection acceptConnection() throws IOException {
        Socket socket = serverSocket.accept();
        System.out.println("Prihvaćen peer " + socket.getInetAddress().getHostAddress() + "  |  " + socket.getPort());

        return new PeerConnection(socket);
    }

    @Override
    public void close() throws IOException {
        // TODO Auto-generated method stub
        serverSocket.close();
    }

}
