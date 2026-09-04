package WLAN;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class Client {

    private Socket socket = null;
    private DataInputStream in = null;
    private DataOutputStream out = null;

    public Client(String adresa, int port) throws IOException {
        try {
            socket = new Socket(adresa, port);
            System.out.println("Spojeno");

            in = new DataInputStream(System.in);
            out = new DataOutputStream(socket.getOutputStream()); 
        }
        catch(UnknownHostException e) {
            System.out.println("NE radi");
            return;
        }

        String message = new String();
        System.out.println("daoiwiodaw");
        //in = new DataInputStream(socket.getInputStream());
        while(!message.equals("ZAUSTAVI")) {
            message = in.readLine();
            out.writeUTF(message);
            System.out.println("daoiwiodaw");
        }

        in.close();
        out.close();
        socket.close();
    }   

};