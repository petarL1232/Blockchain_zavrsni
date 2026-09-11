import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
/*Repository sada može:
- spremiti lokalni i mrežni wallet
- spremiti pending transakciju
- atomarno spremiti blok i trenutno stanje mempoola
- potpuno zamijeniti chain prilikom forka
- rekonstruirati blokove i transakcije
- spremiti i učitati peerove*/
public class BlockchainRepository {

    private final Connection connection;

    public BlockchainRepository(DatabaseManager databaseManager) {
        if(databaseManager == null) {
            throw new IllegalArgumentException("DatabaseManager ne smije biti null.");
        }

        this.connection = databaseManager.getConnection();
    }

    public synchronized void saveLocalWallet(Wallet wallet,long initialBalance) throws SQLException {
        saveWallet(
                wallet.getAddress(),
                wallet.getPublicKeyString(),
                wallet.getPrivateKeyString(),
                initialBalance,
                true);
    }

    public synchronized void saveNetworkWallet(
            String address,
            String publicKey,
            long initialBalance) throws SQLException {

        saveWallet(address,publicKey,null,initialBalance,false);
    }

    private void saveWallet(
            String address,
            String publicKey,
            String privateKey,
            long initialBalance,
            boolean local) throws SQLException {

        String sql = """
                INSERT INTO wallets(
                    address,
                    public_key,
                    private_key,
                    initial_balance_units,
                    is_local
                )
                VALUES(?, ?, ?, ?, ?)
                ON CONFLICT(address) DO UPDATE SET
                    public_key = excluded.public_key,
                    private_key = COALESCE(wallets.private_key, excluded.private_key),
                    initial_balance_units = excluded.initial_balance_units,
                    is_local = CASE
                        WHEN wallets.is_local = 1 OR excluded.is_local = 1 THEN 1
                        ELSE 0
                    END
                """;

        try(PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1,address);
            statement.setString(2,publicKey);

            if(privateKey == null) {
                statement.setNull(3,Types.VARCHAR);
            } else {
                statement.setString(3,privateKey);
            }

            statement.setLong(4,initialBalance);
            statement.setInt(5,local ? 1 : 0);
            statement.executeUpdate();
        }
    }

    public synchronized ArrayList<StoredWallet> loadWallets() throws SQLException {

        ArrayList<StoredWallet> wallets = new ArrayList<>();

        String sql = """
                SELECT
                    address,
                    public_key,
                    private_key,
                    initial_balance_units,
                    is_local
                FROM wallets
                ORDER BY id
                """;

        try(PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {

            while(result.next()) {
                wallets.add(new StoredWallet(
                        result.getString("address"),
                        result.getString("public_key"),
                        result.getString("private_key"),
                        result.getLong("initial_balance_units"),
                        result.getInt("is_local") == 1));
            }
        }

        return wallets;
    }

    public synchronized void savePendingTransaction(Transactions transaction) throws SQLException {

        if(transaction == null || transaction.isSystemTransaction()) {
            throw new IllegalArgumentException("Samo regularna transakcija moze biti u mempoolu.");
        }

        insertPendingTransaction(transaction);
    }

    private void insertPendingTransaction(Transactions transaction) throws SQLException {

        String sql = """
                INSERT INTO transactions(
                    tx_hash,
                    block_id,
                    position_in_block,
                    transaction_type,
                    sender,
                    sender_public_key,
                    receiver,
                    amount_units,
                    nonce,
                    signature,
                    received_on
                )
                VALUES(?, NULL, NULL, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(tx_hash) DO NOTHING
                """;

        try(PreparedStatement statement = connection.prepareStatement(sql)) {
            fillTransactionStatement(
                    statement,
                    transaction,
                    System.currentTimeMillis());

            statement.executeUpdate();
        }
    }

    public synchronized void saveAcceptedBlock(
            Block block,
            List<Transactions> currentMempool) throws SQLException {

        boolean oldAutoCommit = connection.getAutoCommit();

        try {
            connection.setAutoCommit(false);

            int blockId = insertBlock(block);

            for(int i = 0; i < block.getTransactions().size(); i++) {
                insertConfirmedTransaction(
                        block.getTransactions().get(i),
                        blockId,
                        i);
            }

            try(Statement statement = connection.createStatement()) {
                statement.executeUpdate(
                        "DELETE FROM transactions WHERE block_id IS NULL");
            }

            for(Transactions transaction : currentMempool) {
                insertPendingTransaction(transaction);
            }

            connection.commit();

        } catch(SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    public synchronized void replaceChain(
            List<Block> chain,
            List<Transactions> currentMempool) throws SQLException {

        boolean oldAutoCommit = connection.getAutoCommit();

        try {
            connection.setAutoCommit(false);

            try(Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM transactions");
                statement.executeUpdate("DELETE FROM blocks");
            }

            for(Block block : chain) {

                int blockId = insertBlock(block);

                for(int i = 0; i < block.getTransactions().size(); i++) {
                    insertConfirmedTransaction(
                            block.getTransactions().get(i),
                            blockId,
                            i);
                }
            }

            for(Transactions transaction : currentMempool) {
                insertPendingTransaction(transaction);
            }

            connection.commit();

        } catch(SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    private int insertBlock(Block block) throws SQLException {

        String sql = """
                INSERT OR IGNORE INTO blocks(
                    block_index,
                    previous_hash,
                    current_hash,
                    created_on,
                    merkle_root,
                    miner_address,
                    nonce,
                    difficulty
                )
                VALUES(?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try(PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1,block.index);
            statement.setString(2,block.previousHash);
            statement.setString(3,block.hash);
            statement.setLong(4,block.timestamp);
            statement.setString(5,block.getMerkleRoot());

            String minerAddress = getMinerAddress(block);

            if(minerAddress == null) {
                statement.setNull(6,Types.VARCHAR);
            } else {
                statement.setString(6,minerAddress);
            }

            statement.setLong(7,block.nonce);
            statement.setInt(8,block.getDifficulty());
            statement.executeUpdate();
        }

        String findId = """
                SELECT id
                FROM blocks
                WHERE current_hash = ?
                """;

        try(PreparedStatement statement = connection.prepareStatement(findId)) {
            statement.setString(1,block.hash);

            try(ResultSet result = statement.executeQuery()) {
                if(result.next()) {
                    return result.getInt("id");
                }
            }
        }

        throw new SQLException("Block nije moguce pronaci nakon spremanja.");
    }

    private void insertConfirmedTransaction(
            Transactions transaction,
            int blockId,
            int position) throws SQLException {

        String sql = """
                INSERT INTO transactions(
                    tx_hash,
                    block_id,
                    position_in_block,
                    transaction_type,
                    sender,
                    sender_public_key,
                    receiver,
                    amount_units,
                    nonce,
                    signature,
                    received_on
                )
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(tx_hash) DO UPDATE SET
                    block_id = excluded.block_id,
                    position_in_block = excluded.position_in_block
                """;

        try(PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1,transaction.getHash());
            statement.setInt(2,blockId);
            statement.setInt(3,position);
            statement.setString(4,transaction.isSystemTransaction() ? "SYSTEM" : "REGULAR");
            statement.setString(5,transaction.getSender());
            statement.setString(6,transaction.getSenderPublicKey());
            statement.setString(7,transaction.getReceiver());
            statement.setLong(8,transaction.getAmount());
            statement.setLong(9,transaction.getNonce());
            statement.setString(10,transaction.getSignature());
            statement.setLong(11,System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    private void fillTransactionStatement(
            PreparedStatement statement,
            Transactions transaction,
            long receivedOn) throws SQLException {

        statement.setString(1,transaction.getHash());
        statement.setString(2,transaction.isSystemTransaction() ? "SYSTEM" : "REGULAR");
        statement.setString(3,transaction.getSender());
        statement.setString(4,transaction.getSenderPublicKey());
        statement.setString(5,transaction.getReceiver());
        statement.setLong(6,transaction.getAmount());
        statement.setLong(7,transaction.getNonce());
        statement.setString(8,transaction.getSignature());
        statement.setLong(9,receivedOn);
    }

    public synchronized ArrayList<Block> loadChain() throws SQLException {

        ArrayList<Block> chain = new ArrayList<>();

        String sql = """
                SELECT
                    id,
                    block_index,
                    previous_hash,
                    current_hash,
                    created_on,
                    merkle_root,
                    nonce,
                    difficulty
                FROM blocks
                ORDER BY block_index
                """;

        try(PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {

            while(result.next()) {

                int blockId = result.getInt("id");

                ArrayList<Transactions> transactions =
                        loadBlockTransactions(blockId);

                Block block = new Block(
                        result.getInt("block_index"),
                        result.getString("previous_hash"),
                        result.getLong("created_on"),
                        transactions,
                        result.getLong("nonce"),
                        result.getInt("difficulty"));

                String storedHash = result.getString("current_hash");
                String storedMerkleRoot = result.getString("merkle_root");

                if(!block.hash.equals(storedHash)
                        || !block.getMerkleRoot().equals(storedMerkleRoot)) {

                    throw new SQLException(
                            "Block #" + block.index + " u bazi nije valjan.");
                }

                chain.add(block);
            }
        }

        return chain;
    }

    private ArrayList<Transactions> loadBlockTransactions(int blockId) throws SQLException {

        ArrayList<Transactions> transactions = new ArrayList<>();

        String sql = """
                SELECT *
                FROM transactions
                WHERE block_id = ?
                ORDER BY position_in_block
                """;

        try(PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1,blockId);

            try(ResultSet result = statement.executeQuery()) {
                while(result.next()) {
                    transactions.add(readTransaction(result));
                }
            }
        }

        return transactions;
    }

    public synchronized ArrayList<Transactions> loadMempool() throws SQLException {

        ArrayList<Transactions> transactions = new ArrayList<>();

        String sql = """
                SELECT *
                FROM transactions
                WHERE block_id IS NULL
                ORDER BY received_on, id
                """;

        try(PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {

            while(result.next()) {

                Transactions transaction = readTransaction(result);

                if(!transaction.isSystemTransaction()) {
                    transactions.add(transaction);
                }
            }
        }

        return transactions;
    }

    private Transactions readTransaction(ResultSet result) throws SQLException {

        String type = result.getString("transaction_type");
        String receiver = result.getString("receiver");
        long amount = result.getLong("amount_units");
        long nonce = result.getLong("nonce");

        if("SYSTEM".equals(type)) {
            return Transactions.createSystemTransaction(
                    receiver,
                    amount,
                    nonce);
        }

        if(!"REGULAR".equals(type)) {
            throw new SQLException("Nepoznat transaction type u bazi: " + type);
        }

        return new Transactions(
                result.getString("sender"),
                result.getString("sender_public_key"),
                receiver,
                amount,
                result.getString("signature"),
                nonce);
    }

    public synchronized void savePeer(
            String nodeId,
            String host,
            int port,
            String nodeType) throws SQLException {

        String sql = """
            INSERT INTO peers(
                node_id,
                host,
                port,
                node_type,
                last_seen
            )
            VALUES(?, ?, ?, ?, ?)
                
            ON CONFLICT(node_id) DO UPDATE SET
                host = excluded.host,
                port = excluded.port,
                node_type = excluded.node_type,
                last_seen = excluded.last_seen
                
            ON CONFLICT(host, port) DO UPDATE SET
                node_id = excluded.node_id,
                node_type = excluded.node_type,
                last_seen = excluded.last_seen
            """;

        try(PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1,nodeId);
            statement.setString(2,host);
            statement.setInt(3,port);
            statement.setString(4,nodeType);
            statement.setLong(5,System.currentTimeMillis());
            statement.executeUpdate();
        }
    }

    public synchronized ArrayList<StoredPeer> loadPeers() throws SQLException {

        ArrayList<StoredPeer> peers = new ArrayList<>();

        String sql = """
                SELECT node_id, host, port, node_type, last_seen
                FROM peers
                ORDER BY last_seen DESC
                """;

        try(PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {

            while(result.next()) {
                peers.add(new StoredPeer(
                        result.getString("node_id"),
                        result.getString("host"),
                        result.getInt("port"),
                        result.getString("node_type"),
                        result.getLong("last_seen")));
            }
        }

        return peers;
    }

    private String getMinerAddress(Block block) {

        if(block.index == 0 || block.getTransactions().isEmpty()) {
            return null;
        }

        Transactions firstTransaction = block.getTransactions().get(0);

        if(!firstTransaction.isSystemTransaction()) {
            return null;
        }

        return firstTransaction.getReceiver();
    }

    public static class StoredWallet {

        public final String address;
        public final String publicKey;
        public final String privateKey;
        public final long initialBalance;
        public final boolean local;

        public StoredWallet(
                String address,
                String publicKey,
                String privateKey,
                long initialBalance,
                boolean local) {

            this.address = address;
            this.publicKey = publicKey;
            this.privateKey = privateKey;
            this.initialBalance = initialBalance;
            this.local = local;
        }
    }

    public static class StoredPeer {

        public final String nodeId;
        public final String host;
        public final int port;
        public final String nodeType;
        public final long lastSeen;

        public StoredPeer(
                String nodeId,
                String host,
                int port,
                String nodeType,
                long lastSeen) {

            this.nodeId = nodeId;
            this.host = host;
            this.port = port;
            this.nodeType = nodeType;
            this.lastSeen = lastSeen;
        }
    }
}