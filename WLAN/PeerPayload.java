package WLAN;
public class PeerPayload {

    private String nodeId;
    private String ipAddress;
    private int listenPort;
    private String nodeType;

    public PeerPayload(String nodeId, String ipAddress, int listenPort, String nodeType) {
        this.nodeId = nodeId;
        this.ipAddress = ipAddress;
        this.listenPort = listenPort;
        this.nodeType = nodeType;
    }

    public String getNodeId() {
        return nodeId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getListenPort() {
        return listenPort;
    }

    public String getNodeType() {
        return nodeType;
    }
}