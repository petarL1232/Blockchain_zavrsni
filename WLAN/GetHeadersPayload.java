package WLAN;

public class GetHeadersPayload {

    private int fromHeight;

    public GetHeadersPayload(int fromHeight) {
        this.fromHeight = fromHeight;
    }

    public int getFromHeight() {
        return fromHeight;
    }
}
