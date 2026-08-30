import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MerkleTree {
    public static final String EMPTY_MERKLE_ROOT = Cryptography.applySHA256("");



    // ovu operaciju može izvršiti samo full node
    public void printMerkleTree(List<String> transactions) {
        List<String> hashes = new ArrayList<>();
        for (String tx : transactions) {
            hashes.add(Cryptography.applySHA256(tx));
        }

        if(hashes.size() == transactions.size()) {
                System.out.print("Merkle Listovi: ");
            }
        for (String hash : hashes) {
            System.out.print(hash + " ");
        }
        System.out.println();

        while (hashes.size() > 1) { // vrti dok nema samo jedan element
            List<String> newLevel = new ArrayList<>();

            for (int i = 0; i < hashes.size(); i += 2) {
                if (i + 1 < hashes.size()) {
                    newLevel.add(Cryptography.applySHA256(hashes.get(i) + hashes.get(i + 1)));
                } else {
                    newLevel.add(Cryptography.applySHA256(hashes.get(i) + hashes.get(i)));
                }
            }
            hashes = newLevel;
            if(hashes.size() == 1) {
                System.out.print("Merkle Root: ");
            } else {
                System.out.print("Merkle layer: ");
            }

            for (String hash : hashes) {
                System.out.print(hash + " ");
            }
            System.out.println();


        }

    }

    // ovu operaciju može izvršiti samo full node
    public String getMerkleRoot(List<String> transactions) {
        if(transactions == null || transactions.isEmpty()) {
            return EMPTY_MERKLE_ROOT; // jasno dati do znanja da su transakcije prazne
        }
        
        List<String> hashes = new ArrayList<>();
        for (String tx : transactions) {
            hashes.add(Cryptography.applySHA256(tx)); // listovi su samo hashovi
        }

        while (hashes.size() > 1) { // vrti dok nema samo jedan element
            List<String> newLevel = new ArrayList<>();
            for (int i = 0; i < hashes.size(); i += 2) {
                if (i + 1 < hashes.size()) {
                    newLevel.add(Cryptography.applySHA256(hashes.get(i) + hashes.get(i + 1)));
                } else {
                    // ovo će se okinuti samo ako je broj lisitova neparan
                    // ideja je tada duplicirati samo zadnji list
                    newLevel.add(Cryptography.applySHA256(hashes.get(i) + hashes.get(i)));
                }
            } // example -> {"t1","t2","t3","t4", "t5"} -> {"t1t2","t3t4","t5t5"} -> {"t1t2t3t4","t5t5t5t5"} -> {"t1t2t3t4t5t5t5t5"} = root
            hashes = newLevel;
        }
        return hashes.get(0); // vratiti hash od korijena i ovo se sprema u blockchainu
    }

    // vraća Merkle proof za određenu transakciju
    // ovu operaciju može izvršiti samo full node

    public List<String> getMerkleProof(List<String> transactions, int index_of_transaction) {
        List<String> proof = new ArrayList<>();
        List<String> currentLevel = new ArrayList<>();

        // Početna razina — hashirane transakcije
        for (String tx : transactions) {
            currentLevel.add(Cryptography.applySHA256(tx));
        }

        int idx = index_of_transaction;
        while (currentLevel.size() > 1) {
            List<String> newLevel = new ArrayList<>();
            for (int i = 0; i < currentLevel.size(); i += 2) {
                String left = currentLevel.get(i);
                String right = (i + 1 < currentLevel.size()) ? currentLevel.get(i + 1) : left;

                if (i == idx || i + 1 == idx) {
                    proof.add((i == idx) ? right : left); // Dodaj "sibling" hash
                    idx = newLevel.size(); // Novi indeks u sljedećoj razini
                }

                newLevel.add(Cryptography.applySHA256(left + right));
            }
            currentLevel = newLevel;
        }
        return proof;
    }

    // Provjera transakcije pomoću Merkle proof-a (light node)
    // ovu operaciju može izvršiti samo light node
    public boolean verifyMerkleProof(String transaction, List<String> proof, String merkleRoot, int index) {
        String hash = Cryptography.applySHA256(transaction);
        int idx = index;

        for (String sibling : proof) {
            if (idx % 2 == 0) {
                hash = Cryptography.applySHA256(hash + sibling);
            } else {
                hash = Cryptography.applySHA256(sibling + hash);
            }
            idx /= 2;
        }
        return hash.equals(merkleRoot);
    }

    // Test
    public void main(String[] args) {
        List<String> txs = Arrays.asList("transakcija1", "transakcija2", "transakcija3", "transakcija4");

        String root = getMerkleRoot(txs);
        printMerkleTree(txs);
        System.out.println("Merkle Root: " + root);

        // Light node provjera tx3
        int txIndex = 2; // "tx3"
        List<String> proof = getMerkleProof(txs, txIndex);
        System.out.println("Merkle Proof: " + proof);

        boolean valid = verifyMerkleProof("transakcija3", proof, root, txIndex);
        System.out.println("Validno? " + valid);
        valid = verifyMerkleProof("transakcija3", proof, root.replace("a", "c"), txIndex);
        System.out.println("Validno nakon izmjene roota? "+ valid);
    }
}

