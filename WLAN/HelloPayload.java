package WLAN;

public class HelloPayload {

    private String networkId;
    private String genesisHash;
    private String nodeType;
    private int listenPort;
    private int tipHeight;
    private String tipHash;
    private String cumulativeWork;

    public HelloPayload(String networkId, String genesisHash, String nodeType, int listenPort, int tipHeight, String tipHash, String cumulativeWork) {
        this.networkId = networkId;
        this.genesisHash = genesisHash;
        this.nodeType = nodeType;
        this.listenPort = listenPort;
        this.tipHeight = tipHeight;
        this.tipHash = tipHash;
        this.cumulativeWork = cumulativeWork;
    }
    //getteri
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

    public int getTipHeight() {
        return tipHeight;
    }

    public String getTipHash() {
        return tipHash;
    }

    public String getCumulativeWork() {
        return cumulativeWork;
    }
}