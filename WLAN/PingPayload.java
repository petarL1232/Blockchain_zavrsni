package WLAN;

public class PingPayload {

    private long sentAt;

    public PingPayload(long sentAt) {
        this.sentAt = sentAt;
    }

    public long getSentAt() {
        return sentAt;
    }
}