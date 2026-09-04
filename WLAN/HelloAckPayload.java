package WLAN;

public class HelloAckPayload {

    private boolean accepted;
    private String reason;
    private HelloPayload nodeInfo;

    public HelloAckPayload(boolean accepted, String reason, HelloPayload nodeInfo) {
        this.accepted = accepted;
        this.reason = reason;
        this.nodeInfo = nodeInfo;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getReason() {
        return reason;
    }

    public HelloPayload getNodeInfo() {
        return nodeInfo;
    }
}