package WLAN;

public class DiscoveryPayload {

    private String networkId;
    private String genesisHash;
    private String nodeType;
    private int listenPort;

    public DiscoveryPayload(String networkId, String genesisHash, String nodeType, int listenPort) {

        this.networkId = networkId;
        this.genesisHash = genesisHash;
        this.nodeType = nodeType;
        this.listenPort = listenPort;
    }

    public String getNetworkId() {
        return networkId;
    }

    public String getGenesisHash() {
        return genesisHash;
    }

    public String getNodeType() {
        return nodeType;
    }

    public int getListenPort() {
        return listenPort;
    }
}