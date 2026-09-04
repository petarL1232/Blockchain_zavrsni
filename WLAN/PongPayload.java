package WLAN;

public class PongPayload {

    private long pingSentAt;
    private long respondedAt;

    public PongPayload(long pingSentAt, long respondedAt) {
        this.pingSentAt = pingSentAt;
        this.respondedAt = respondedAt;
    }

    public long getPingSentAt() {
        return pingSentAt;
    }

    public long getRespondedAt() {
        return respondedAt;
    }
}