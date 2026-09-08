package WLAN;

public enum MessageType {
    HELLO, // tu je port, verzija protokola i tako
    HELLO_ACK, // odgovor na hello (Prihvaćam konekciju)
    PING, // provjera je li drugo node još ziv
    PONG, // odgovor na ping ako se ne odgovori na odeženo vrijeme mora ponovno raditi hello te ga mičemo iz aktivnih konekcija / ne računamo na njega
    TRANSACTION, // get transaction
    BLOCK, // šalje novi blok peeru - "Evo novog bloka koji sam dobio/izmajnao"
    GET_CHAIN, // "pošalji mi svoj blockchain cijeli" - ovo moze biti veliko u terabajtima/gigabajtima dok se ne update protokol to neću raditi za sada.
    CHAIN_RESPONSE, // odgovor na getchain - šalje nodeu blockchain
    GET_PEERS, // pitamo koje još nodeove poznaješ. u idealnom svijetu će ovo biti povezani graf pa će se na ovaj način lagano moći naš node sprijateljiti sa svim nodeovima na mreži
    PEERS, // odgovor na get peers - vraća nodeove koje još poznaje
    GET_MERKLE_PROOF, // dokaži mi da je ova transakcija u određenom bloku
    MERKLE_PROOF, // odgovor na get merkle proof - vraca hasheove potrebne za merkle stablo
    WALLET, // javni novčanik se šalje (trebaju svi poslati da bih se napravile transakcije)
    REJECT // poruka je primljena, ali odbijena
}
