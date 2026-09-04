package WLAN;

import java.util.List;

public class ChainResponsePayload {

    private int startHeight;
    private List<BlockPayload> blocks;
    private String cumulativeWork; // (suma difficulty-ja po svakom bloku (+ koliko blokova je tu)) gleda se ukupan rad sto ga je netko napravio i daje se prednost lancu gdje je rad veći jer je sigurniji

    public ChainResponsePayload(int startHeight, List<BlockPayload> blocks, String cumulativeWork) {
        this.startHeight = startHeight;
        this.blocks = blocks;
        this.cumulativeWork = cumulativeWork;
    }

    public int getStartHeight() {
        return startHeight;
    }

    public List<BlockPayload> getBlocks() {
        return blocks;
    }

    public String getCumulativeWork() {
        return cumulativeWork;
    }
}