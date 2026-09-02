package WLAN_test;
import java.net.*;
import WLAN_test.Ipconfig;


public class Main_peer_to_peer {
    public static void main(String[] args) throws Exception {
        /*
        IP adresa = koje računalo?
        Port      = koji program/usluga na tom računalu? 
        TCP       = način na koji ta dva programa pouzdano komuniciraju

        TCP - server i client side ima - u našem slučaju svaki node ce biti i jedno i drugo
        samo sto ce imati kao client i server dugacije ovlasti u ovisnosti koja je vrsta čvora.
        */


        //Client client = new Client(Ipconfig.IP_OF_MY_PC, Ipconfig.PORT);
        Server server = new Server(Ipconfig.PORT);
    }
}
