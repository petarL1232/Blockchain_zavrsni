package WLAN;

import java.util.List;

public class HeadersPayload {

    private int startHeight;
    private List<HeaderPayload> headers;
    private String cumulativeWork;

    public HeadersPayload(int startHeight, List<HeaderPayload> headers, String cumulativeWork) {
        this.startHeight = startHeight;
        this.headers = headers;
        this.cumulativeWork = cumulativeWork;
    }

    public int getStartHeight() {
        return startHeight;
    }

    public List<HeaderPayload> getHeaders() {
        return headers;
    }

    public String getCumulativeWork() {
        return cumulativeWork;
    }
}
