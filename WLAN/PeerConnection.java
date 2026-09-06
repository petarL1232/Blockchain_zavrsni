package WLAN;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

public class PeerConnection implements AutoCloseable{

    private static final int MAX_MESSAGE_LENGTH = 10_000_000;

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;

    public PeerConnection(Socket socket) throws IOException{
        if(socket == null) {
            throw new IllegalArgumentException("Socket mora biti != null");
        }

        this.socket = socket;
        //this.reader = null;
        //this.writer = null;
        try {
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        this.reader = new BufferedReader(
            new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8)
        );

        this.writer = new BufferedWriter(
            new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8)
        );

    }
    public synchronized void send(NetworkMessage message) throws IOException {
        String json = MessageCodec.toJson(message);

        if(json.length() > MAX_MESSAGE_LENGTH) {
            throw new IOException("Poruka je prevelika.");
        }

        writer.write(json);
        writer.newLine();
        writer.flush();
    }
    public NetworkMessage receive() throws IOException{

        String json = reader.readLine();

        if(json == null || json.isBlank()) {
            return null;
        }

        if(json.length() > MAX_MESSAGE_LENGTH) {
            throw new IOException("Predugacko - ne citam");
        }

        try {
            return MessageCodec.fromJson(json); // nesigurna metoda zbog toga se stavlja u dupli try catch
        } catch (IllegalArgumentException e) {
            throw new IOException("Primljena mrežna poruka nije valjana.", e);
        }


    }

    public String getRemoteAddress() {
        return socket.getInetAddress().getHostAddress();
    }

    public int getRemotePort() {
        return socket.getPort();
    }

    @Override
    public void close() throws IOException {
        socket.close();
        // TODO Auto-generated method stub
        //throw new UnsupportedOperationException("Unimplemented method 'close'");
    }
        
}
