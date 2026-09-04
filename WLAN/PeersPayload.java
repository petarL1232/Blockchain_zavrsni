package WLAN;

import java.util.List;

public class PeersPayload {

    private List<PeerPayload> peers;

    public PeersPayload(List<PeerPayload> peers) {
        this.peers = peers;
    }

    public List<PeerPayload> getPeers() {
        return peers;
    }
}