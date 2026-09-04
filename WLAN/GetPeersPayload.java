package WLAN;

public class GetPeersPayload {

    private int maxPeers;

    public GetPeersPayload(int maxPeers) {
        this.maxPeers = maxPeers;
    }

    public int getMaxPeers() {
        return maxPeers;
    }
}