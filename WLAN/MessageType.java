package WLAN;

public enum MessageType {
    HELLO, // tu je port, verzija protokola i tako DONE
    HELLO_ACK, // odgovor na hello (Prihvaćam konekciju) DONE
    PING, // provjera je li drugo node još ziv DONE 
    PONG, // odgovor na ping ako se ne odgovori na odeženo vrijeme mora ponovno raditi hello te ga mičemo iz aktivnih konekcija / ne računamo na njega DONE
    TRANSACTION, // get transaction DONE
    BLOCK, // šalje novi blok peeru - "Evo novog bloka koji sam dobio/izmajnao" DONE
    GET_CHAIN, // "pošalji mi svoj blockchain cijeli" - ovo moze biti veliko u terabajtima/gigabajtima dok se ne update protokol to neću raditi za sada. DONE
    CHAIN_RESPONSE, // odgovor na getchain - šalje nodeu blockchain DONE
    GET_PEERS, // pitamo koje još nodeove poznaješ. u idealnom svijetu će ovo biti povezani graf pa će se na ovaj način lagano moći naš node sprijateljiti sa svim nodeovima na mreži
    PEERS, // odgovor na get peers - vraća nodeove koje još poznaje
    GET_MERKLE_PROOF, // dokaži mi da je ova transakcija u određenom bloku 
    MERKLE_PROOF, // odgovor na get merkle proof - vraca hasheove potrebne za merkle stablo
    WALLET, // javni novčanik se šalje (trebaju svi poslati da bih se napravile transakcije) DONE
    REJECT, // poruka je primljena, ali odbijena DONE
    DISCOVER, // poruka da se otkriju drugi čvorovi bez da se ručno unosi ip (UDP broadcast) DONE
    DISCOVER_REPLY // odgovor na discover DONE
}
