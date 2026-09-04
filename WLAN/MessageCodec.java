package WLAN;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

// klasa koja služi za pretvaranje poruka u JSON i nazad da bih se mogle koristiti kako spada ovo je -> encoder + decoder
public class MessageCodec {
    private static final Gson gson = new GsonBuilder().serializeNulls().create();

    private MessageCodec() {}

    public static NetworkMessage createMessage(MessageType type, String senderNodeId, String replyToId, Object payload)  {

        if(type == null || senderNodeId == null || senderNodeId.isBlank()) {
            throw new IllegalArgumentException("Message type je null ili senderNode nije zadan");
        }

        return new NetworkMessage(type, senderNodeId, replyToId, gson.toJsonTree(payload));
    }

    public static String toJson(NetworkMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("Message je prazan");
        }

        return gson.toJson(message);
    }

    public static NetworkMessage fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON je prazan.");
        }

        try{
            NetworkMessage message = gson.fromJson(json, NetworkMessage.class);

            if (message == null || message.getType() == null || message.getMessageId() == null || message.getMessageId().isBlank() || message.getSenderNodeId() == null || message.getSenderNodeId().isBlank()) {
                throw new IllegalArgumentException("Primljena poruka je null. Negdje xD");
            }

            if (message.getProtocolVersion() <= 0) {
                throw new IllegalArgumentException("Protocol version nije valjan.");
            }

            return message;
        }catch(JsonParseException e){
            throw new IllegalArgumentException("JSON nije valjan.", e);
        }
    }

    public static <T> T payloadAsPayloadTypeIWant(NetworkMessage message, Class<T> payloadType) {

        if (message == null || message.getPayload() == null || message.getPayload().isJsonNull()) {
            throw new IllegalArgumentException("Network message ne smije biti null ili nema payload.");
        }

        if (payloadType == null) {
            throw new IllegalArgumentException("Payload type ne smije biti null.");
        }

        try {
            return gson.fromJson(message.getPayload(), payloadType);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Payload nije ispravnog formata za pretvaranje u taj oblik", e);
        }


    }

}
