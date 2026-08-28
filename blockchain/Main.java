import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static int counter = 0;

    public static void posalji_coins(ArrayList<String> adresa_walleta, BlockChain blockChain, int i, int j,
            Double money) {
        Transactions t1 = new Transactions(adresa_walleta.get(i), adresa_walleta.get(j), money,
                blockChain.getWalletRegistry().get(adresa_walleta.get(i))
                        .signData(adresa_walleta.get(i) + adresa_walleta.get(j) + new String(money+"")));
        blockChain.addPendingTransaction(t1);
        System.out.println("Dodana transakcija " + counter);
        counter++;
    }

    public static void main(String[] args) {

        BlockChain blockChain = new BlockChain();
        List<Transactions> transakcije = new ArrayList<>();

        blockChain.registerWallet();
        blockChain.registerWallet();
        blockChain.registerWallet();
        blockChain.registerWallet();
        blockChain.registerWallet();
        blockChain.registerWallet();
        blockChain.registerWallet();
        // sutra skontati zašto ovaj system uzima random index u walletima i neka prestane po pitanju toga
        // light node implementirati
        ArrayList<String> adresa_walleta = new ArrayList<>();
        for (String adresa : blockChain.getWalletRegistry().keySet()) {
            System.out.println(adresa);
            adresa_walleta.add(adresa);
        }
        /*
         * Adrese walleta su:
         * [0] System
         * [1] Alice
         * [2] Bob
         * [3] Oliver
         * [4] JA
         */
        adresa_walleta = blockChain.getAdreseWalleta();
        blockChain.getWalletRegistry().get(adresa_walleta.get(0)).increaseBalance(500); // sender
        blockChain.getWalletRegistry().get(adresa_walleta.get(1)); // primač
        blockChain.getWalletRegistry().get(adresa_walleta.get(2)).increaseBalance(200);
        blockChain.getWalletRegistry().get(adresa_walleta.get(3));
        blockChain.getWalletRegistry().get(adresa_walleta.get(4)).increaseBalance(100);;
        blockChain.getWalletRegistry().get(adresa_walleta.get(5)).increaseBalance(2000);;
        blockChain.getWalletRegistry().get(adresa_walleta.get(6)).increaseBalance(1000);;

        Computer c1_System_vise_nije_xD = new Computer(Computer.NodeType.FULL, adresa_walleta.get(0), blockChain);
        Computer c2_Alice = new Computer(Computer.NodeType.MINER, adresa_walleta.get(1), blockChain);
        Computer c3_Bob = new Computer(Computer.NodeType.FULL, adresa_walleta.get(2), blockChain);
        Computer c4_Oliver = new Computer(Computer.NodeType.FULL, adresa_walleta.get(3), blockChain);
        Computer c5_JA = new Computer(Computer.NodeType.MINER, adresa_walleta.get(4), blockChain);
        Computer c6_samo_za_test_light_node = new Computer(Computer.NodeType.LIGHT, adresa_walleta.get(5), blockChain);
        Computer c7_samo_za_test_light_node = new Computer(Computer.NodeType.LIGHT, adresa_walleta.get(6), blockChain);

        // blockChain.addValidatorNode(c1_System); ovo ne smije biti nema smisla onda
        // nema decentralizacije
        blockChain.addValidatorNode(c2_Alice);
        blockChain.addValidatorNode(c3_Bob);
        blockChain.addValidatorNode(c4_Oliver);
        blockChain.addValidatorNode(c6_samo_za_test_light_node);
        blockChain.addValidatorNode(c7_samo_za_test_light_node);

        Thread minerThread = new Thread(c2_Alice);
        Thread minerThread2 = new Thread(c5_JA);
        minerThread.start();
        minerThread2.start();
        blockChain.printAllWallets_DETAL();
        try {
            blockChain.printAllWallets();
            Thread.sleep(5000); // pričekaj malo prije prve transakcije

            posalji_coins(adresa_walleta, blockChain, 2, 1, 50.0);

            Thread.sleep(10000); // čekaj 10 sekundi

            posalji_coins(adresa_walleta, blockChain, 0, 1, 100000.0);
            posalji_coins(adresa_walleta, blockChain, 0, 1, 1.2);
            posalji_coins(adresa_walleta, blockChain, 0, 1, 1.0);

            blockChain.printBlockchain();

            Thread.sleep(20000); // čekaj još 20 sekundi

            posalji_coins(adresa_walleta, blockChain, 2, 1, 50.0);

            Thread.sleep(30000);

            blockChain.printBlockchain();
            blockChain.printAllWallets();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
