package WLAN;

import com.google.gson.JsonElement;
import java.util.UUID;

public class NetworkMessage {

    public static final int CURRENT_PROTOCOL_VERSION = 1;

    private int protocolVersion;
    private MessageType type;
    private String messageId; // UUID
    private String senderNodeId; // UUID koji ostaje isti nakon restarta nodea
    private String replyToId;
    private JsonElement payload; // stvarni sadržaj poruke

    private NetworkMessage() {

    }

    public NetworkMessage(MessageType type, String senderNodeId, String replyToId, JsonElement payload) {
        this.protocolVersion = CURRENT_PROTOCOL_VERSION;
        this.type = type;
        this.messageId = UUID.randomUUID().toString();
        this.senderNodeId = senderNodeId;
        this.replyToId = replyToId;
        this.payload = payload;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public MessageType getType() {
        return type;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getSenderNodeId() {
        return senderNodeId;
    }

    public String getReplyToId() {
        return replyToId;
    }

    public JsonElement getPayload() {
        return payload;
    }

}
