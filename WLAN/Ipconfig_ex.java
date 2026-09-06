package WLAN;

import java.util.ArrayList;

public interface Ipconfig_ex {

    String IP_OF_MY_PC = "YOUR_LOCAL_IP"; // ovo ce bit server uvijek
    String IP_OF_TEST_PC = "OTHER_NODE_IP";

    int PORT = 5000;

    boolean SERVER_MODE = true;

    String NODE_ID = "YOUR_UNIQUE_NODE_ID";
    String NODE_TYPE = "FULL";

    String PEER_IP = IP_OF_TEST_PC;

    ArrayList<String> IPS = new ArrayList<>();
}