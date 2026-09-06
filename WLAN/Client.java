package WLAN;

import java.io.DataInputStream;
import java.io.DataOutputStream;
//import java.io.DataInputStream; nesigurni su pa ih necemo koristiti ipak
//import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.InetSocketAddress;

public class Client implements AutoCloseable{

    private Socket socket = null;
    private final PeerConnection connection; // bitno socket je nebitan osim da napravi ovo

    private static final int CONNECT_TIMEOUT = 5000;

    public Client(String adresa, int port) throws IOException {
        socket = new Socket();
        try {

            socket.connect(new InetSocketAddress(adresa, port), CONNECT_TIMEOUT);
            System.out.println("Spojen socket");
            
            connection = new PeerConnection(socket);
            System.out.println("Spojena PeerConnection");
        }
        catch(IOException e) {
            socket.close();
            throw e;
        }
    }   

    public void send(NetworkMessage message) throws IOException {
        connection.send(message);
    }

    public NetworkMessage receive() throws IOException {
        return connection.receive();
    }

    @Override
    public void close() throws IOException { 
        // TODO Auto-generated method stub
        connection.close();
    }



};