
import WLAN.HelloPayload;

import java.math.BigInteger;
import java.util.ArrayList;
public class NetworkNode {

    private final String nodeId;
    private final Computer.NodeType nodeType;
    private final int listenPort;
    private final String networkId;

    private final BlockChain blockchain;

    public NetworkNode(
            String nodeId,
            Computer.NodeType nodeType,
            int listenPort,
            String networkId,
            BlockChain blockchain
    ) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.listenPort = listenPort;
        this.networkId = networkId;
        this.blockchain = blockchain;
    }

    public HelloPayload createHelloPayload() {

        ArrayList<Block> chain = blockchain.getChain();

        Block genesisBlock = chain.get(0);
        Block latestBlock = chain.get(chain.size() - 1);

        return new HelloPayload(
                networkId,
                genesisBlock.hash,
                nodeType.name(),
                listenPort,
                latestBlock.index,
                latestBlock.hash,
                calculateCumulativeWork()
        );
    }

    private String calculateCumulativeWork() {

        int minedBlocks = Math.max(0, blockchain.getChain().size() - 1); // genesis se ne racuna
        BigInteger workPerBlock = BigInteger.ONE.shiftLeft(blockchain.getDifficulty() * 4); // * 4 zato što jedna hex 0 predstavlja 4 bita da su točno postavljena
        // npr. difficulty = 3, ukupno 3 * 4 = 12 bitova mora biti postavljeno na 0 kako bi prva 3 chara bila stavljena na 0
        // procjenjeno koliko ce pokusaja za to trebati je 4096 = 2^12 (shiftleft to radi na brz način)

        return workPerBlock.multiply(BigInteger.valueOf(minedBlocks)).toString();
    }

    public String getNodeId() {
        return nodeId;
    }

    public Computer.NodeType getNodeType() {
        return nodeType;
    }

    public BlockChain getBlockchain() {
        return blockchain;
    }
}