<p align="center">
  <img src="assets/branding/icons/MathosCoinLogo-180.png" alt="MathosCoin logo" width="180">
</p>

# MathosCoin · $MATH

MathosCoin je edukacijska Java implementacija blockchain mreže koja može raditi kao lokalna simulacija na jednom računalu ili kao stvarna peer-to-peer mreža između više računala na istom WLAN-u.

Završna verzija aplikacije koristi Swing GUI, TCP za pouzdanu razmjenu blockchain poruka, UDP broadcast za automatsko pronalaženje peerova i SQLite za lokalno spremanje stanja svakog noda.

> Projekt je napravljen za učenje i demonstraciju blockchain koncepata. Nije namijenjen čuvanju stvarne vrijednosti niti korištenju kao produkcijska kriptovaluta.

## Brand

- puni naziv: `MathosCoin`
- oznaka coina: `$MATH`
- glavni transparentni logo: `assets/branding/MathosCoinLogo.png`

## Glavne mogućnosti

- FULL, MINER i LIGHT nodeovi
- digitalno potpisane transakcije i double-spend patch
- SHA-256 hashiranje, ECDSA potpisi i Base64-kodirane adrese
- Proof-of-Work rudarenje i natjecanje minera
- odabir jačeg lanca prema cumulative worku
- promjenjiva difficulty vrijednost spremljena u svakom bloku
- Merkle root i provjera Merkle proofa na LIGHT nodu
- login lokalnog walleta
- lokalna SQLite baza za blockchain, mempool, wallete, peerove i headere
- automatsko ponovno povezivanje na zapamćene peerove
- Auto Mode za generiranje mrežnog prometa
- peer-to-peer komunikacija preko JSON poruka
- automatsko pronalaženje nodova preko UDP broadcasta
- ručno povezivanje na poznatu IP adresu kao fallback
- WLAN GUI s blockchain explorerom, pretraživanjem transakcija i prikazom najvećih walleta

## Vrste nodova

| Node | Uloga |
| --- | --- |
| `FULL` | Čuva cijele blokove, transakcije, mempool i stanje walleta te provjerava primljene podatke. |
| `MINER` | Ima mogućnosti FULL noda i dodatno rudari novi blok kada postoje valjane transakcije. |
| `LIGHT` | Wallet za slabije uređaje. Čuva headere i vlastite transakcije, potpisuje i šalje transakcije te njihovo uključenje potvrđuje Merkle proofom od FULL/MINER noda. |

## Potrebno za pokretanje

- JDK 20 ili noviji (optimalno 25)
- Windows PowerShell za naredbe iz ovog README-ja
- mrežna veza ako se koristi više računala

Sve Java biblioteke već se nalaze u `lib` folderu:

- `gson-2.14.0.jar` za JSON poruke
- `sqlite-jdbc-3.53.4.0.jar` za SQLite bazu

**Nije potrebno instalirati poseban SQLite program niti preuzimati dodatne JAR datoteke.**

## Prvo postavljanje projekta

Projekt sadrži `WLAN/Ipconfig_ex.java` kao predložak. Stvarna datoteka `WLAN/Ipconfig.java` namjerno je ignorirana u Gitu kako svaki uređaj može imati vlastite razvojne postavke.

Nakon prvog kloniranja:

1. Kopirati `WLAN/Ipconfig_ex.java` u `WLAN/Ipconfig.java`.
2. U kopiji treba  promijeni:

```java
public interface Ipconfig_ex {
```

u:

```java
public interface Ipconfig {
```

Za završni WLAN GUI IP adrese, node ID, node type i TCP port biraju se u početnom prozoru. Iz `Ipconfig.java` koristi se `DISCOVERY_PORT`, koji treba ostati `4999` na svim računalima.

## Kompiliranje

PowerShell otvori u korijenu projekta, odnosno u folderu u kojem se nalaze `blockchain`, `WLAN`, `data` i `lib`.

```powershell
New-Item -ItemType Directory -Force tmp\classes | Out-Null
Get-ChildItem blockchain,WLAN -Recurse -Filter *.java |
    ForEach-Object FullName |
    Set-Content tmp\sources.txt
javac --release 20 -encoding UTF-8 -cp "lib/*" -d tmp\classes "@tmp/sources.txt"
```

## Pokretanje WLAN GUI-ja

Iz korijena projekta pokreni:

```powershell
java -cp "tmp/classes;lib/*" WlanGUI_Main
```

Program se mora pokretati iz korijena projekta jer se SQL shema učitava iz `data/Blockchain.sql`.

Na Linuxu ili macOS-u separator classpatha je `:` umjesto `;`:

```bash
java -cp "tmp/classes:lib/*" WlanGUI_Main
```

## Alternativa za pokretanje
Projekt se može jednostavnije pokrenuti izravno kroz klasu `WlanGUI_Main.java`, primjerice pomoću popularne ekstenzije Code Runner.

## Pokretanje više čvorova na različitim računalima

1. Na svim računalima koristi istu verziju projekta i protokola.
2. Spoji računala na isti Wi-Fi, hotspot ili drugu lokalnu mrežu.
3. Pokreni WLAN GUI na svakom računalu.
4. Svakom nodu zadaj jedinstven `Node ID`.
5. Odaberi `FULL`, `MINER` ili `LIGHT`.
6. TCP listen port može biti `5000` na svakom računalu jer svako ima vlastitu IP adresu.
7. `Peer IP` ostavi prazan za automatski UDP discovery.
8. Ako discovery ne pronađe peer, ručno upiši IP adresu jednog aktivnog noda i njegov listen port.

Svaki uspješno spojeni peer sprema se u lokalnu bazu pa mu se node nakon ponovnog pokretanja ili prekida veze pokušava ponovno povezati.

## Mrežni portovi

| Protokol | Zadani port | Namjena |
| --- | ---: | --- |
| UDP | `4999` | Automatsko pronalaženje nodova na lokalnoj mreži. |
| TCP | `5000` ili port iz GUI-ja | HELLO handshake i sva daljnja blockchain komunikacija. |

Windows Firewall mora dopustiti Javi dolazni i odlazni promet za ove portove na **privatnoj mreži**.

**Napomena**: UDP broadcast uglavnom radi samo unutar iste podmreže. Primjerice, uređaji `192.168.30.x` i `192.168.20.x` mogu biti na različitim fakultetskim VLAN-ovima pa se neće automatski pronaći. Tada se može pokušati ručno povezivanje, ali i ono ovisi o pravilima mreže i dopuštenom prometu između VLAN-ova.

## Korištenje GUI-ja

### Početni ekran

- `Display name` je naziv prikazan u lokalnom GUI-ju.
- `Unique node ID` mora biti jedinstven na mreži.
- `Node role` određuje FULL, MINER ili LIGHT ponašanje.
- `Listen port` je TCP port na kojem node prihvaća peerove.
- `Peer IP` i `Peer port` nisu obavezni i služe kao ručni bootstrap.

### Live

Prikazuje broj direktno spojenih peerova, visinu lanca, mempool, cumulative work, lokalno rudarenje i posljednje prihvaćene blokove.

### Blockchain

Blockchain explorer prikazuje povezane blokove. Blok se može pronaći prema visini ili hashu i otvoriti kako bi se vidjeli njegov header i transakcije. LIGHT node na ovom ekranu prikazuje headere umjesto cijelih blokova.

### Transactions

Prikazuje potvrđene i pending transakcije. Popis se može pretraživati prema sender ili receiver adresi.

### Wallets

Zoomable Wallet Universe prikazuje poznate wallete i njihove balance odnose. LIGHT za svoj wallet prikazuje stanje sinkronizirano s FULL/MINER nodom, dok ne preuzima cijeli account state mreže.

### Node

Ovdje se nalaze:

- lokalni identitet i login
- ručno povezivanje na peer
- provjera lokalnog chaina ili LIGHT headera
- event stream mrežnih i lokalnih događaja
- zahtjev za Merkle proof na LIGHT nodu

Public i private login hash pripadaju samo lokalnom walletu. Login je lokalna zaštita potpisivanja u GUI-ju, a **nije mrežna prijava na centralni server** zato što se onda gubi smisao blockchaina.

## Slanje transakcije

1. Otvori login i unesi public i private login hash prikazane za lokalni wallet.
2. U `Receiver address` unesi cijelu adresu primatelja.
3. Upiši `$MATH` iznos s najviše osam decimalnih mjesta.
4. Klikni `SIGN + BROADCAST`.

Transakcija dobiva nonce i potpisuje se privatnim ključem lokalnog walleta. FULL/MINER je lokalno provjerava i stavlja u mempool, dok je LIGHT šalje FULL/MINER nodu, vodi kao vlastitu pending transakciju i čeka Merkle proof.

Minimalna transakcija iznosi `0.0001 $MATH`. Naknada za transakcije iznosi `0.1%`.

## Auto Mode

Auto Mode se može uključiti nakon logina na FULL, MINER i LIGHT nodu te radi isključivo na računalu na kojem je uključen:

- nasumično šalje transakcije poznatim peer walletima
- povremeno šalje nekoliko transakcija u kratkom valu
- otprilike 10% pokušaja namjerno je nevaljano radi demonstracije validacije
- automatski se gasi prilikom logouta ili zatvaranja aplikacije

# Tehničke pojedinosti i česti problemi

## Rudarenje i consensus

- MINER automatski počinje rudarenje kada u mempoolu postoji posao.
- Prvi valjani blok koji node prihvati postaje kandidat za nastavak lanca.
- Ostali mineri nastavljaju s transakcijama koje nisu završile u prihvaćenom bloku.
- Mining reward iznosi `10 $MATH` i u GUI-ju se prikazuje kao `SYSTEM REWARD`.
- Kod forka ne pobjeđuje samo dulji lanac, nego valjani lanac s većim cumulative workom.

## LIGHT node i Merkle proof

LIGHT node nakon pokretanja ima samo genesis header `#0` dok se ne poveže s FULL ili MINER nodom i ne završi header sync.

LIGHT može koristiti login, transakcije i Auto Mode kao i ostali walleti. Sam potpisuje transakciju svojim lokalnim private keyem, a FULL/MINER node mu daje trenutno raspoloživi balance i idući transaction nonce.

Nakon što pošalje transakciju i primi novi header, LIGHT automatski traži Merkle proof za svoju pending transakciju. FULL ili MINER vraća potrebnu Merkle granu, a LIGHT samostalno provjerava vodi li ona do spremljenog Merkle roota. Ručni Merkle proof i dalje se može zatražiti u Node prikazu.

## SQLite baza

Svaki GUI node koristi vlastitu datoteku:

```text
data/node-<LISTEN_PORT>.db
```

Primjer: node na portu `5001` koristi `data/node-5001.db`.

Shema se automatski stvara iz `data/Blockchain.sql`. Glavne tablice su:

| Tablica | Sadržaj |
| --- | --- |
| `blocks` | Header i Proof-of-Work podatci cijelih blokova. |
| `transactions` | Potvrđene transakcije i lokalni mempool. |
| `wallets` | Javni walleti te privatni ključ isključivo lokalnog walleta. |
| `peers` | Poznati peerovi za ponovno povezivanje. |
| `light_headers` | Headeri koje sprema LIGHT node. |

SQLite baza nije centralna baza mreže. Svaki node sprema vlastito potvrđeno stanje, a međusobno se usklađuju blockchain protokolom.

Uz `.db` se tijekom rada mogu pojaviti `.db-wal` i `.db-shm` datoteke. To su normalne SQLite WAL datoteke i ignorirane su u Gitu.

### Reset jednog noda

1. Zatvori njegov GUI.
2. Obriši samo `node-PORT.db`, `node-PORT.db-wal` i `node-PORT.db-shm` za taj port.
3. Ponovno pokreni node.

Ovakav reset briše lokalni wallet, privatni ključ, poznate peerove i spremljeni chain tog noda. Nemoj brisati bazu FULL/MINER noda ako je to jedina preostala kopija željenog blockchaina jer će se informacija izgubiti.

## Još načina za pokretanje

Završna aplikacija je `WlanGUI_Main`. Projekt još sadrži ranije faze razvoja koje mogu pomoći razumijevanju i vrijedne su pogleda:

```powershell
# Moderna lokalna simulacija na jednom računalu
java -cp "tmp/classes;lib/*" NewGUI_Main

# Lokalna konzolna blockchain simulacija
java -cp "tmp/classes;lib/*" Main

# Razvojni WLAN konzolni program koji koristi WLAN/Ipconfig.java
java -cp "tmp/classes;lib/*" Main_WLAN
```

Lokalna simulacija i WLAN aplikacija koriste iste core blockchain klase, ali WLAN GUI dodatno povezuje mrežu i SQLite spremanje.

## Struktura projekta

```text
blockchain/
├── assets/                 službeni MathosCoin logo i brand asseti
├── blockchain/             core blockchain, consensus, baza i mrežni node
│   ├── WLAN_gui/           završni WLAN Swing GUI
│   ├── new_gui/            GUI lokalne CPU simualcije
│   └── old_gui/            ranija GUI implementacija CPU simualcije
├── WLAN/                   mrežne poruke, payloadi, TCP i JSON komunikacija
├── WLAN_test/              rani razvojni mrežni testovi
├── data/
│   └── Blockchain.sql      SQLite shema
├── lib/                    Gson i SQLite JDBC
├── tmp/                    lokalni build i privremeni testovi, ignorirano u Gitu
└── README.md
```

## Najčešći problemi

### `Connection refused`

Na ciljnoj IP adresi i portu nitko ne sluša. Prvo pokreni drugi node, provjeri njegov listen port i zatim pokušaj povezivanje.

### `Connect timed out`

IP nije dostupan, uređaji nisu u istoj/routable mreži ili **firewall** blokira promet.

### UDP discovery ne pronalazi node

Provjeri da su uređaji u istoj podmreži i da UDP port `4999` nije blokiran. Na fakultetskim i poslovnim mrežama broadcast ili komunikacija između klijenata često su onemogućeni. Koristi manual bootstrap ili **mobilni hotspot**.

### `Protocol version nije podržan`

Računala koriste različite verzije projekta. Povuci isti commit na sve uređaje, ponovno kompajliraj i ponovno pokreni sve nodove. Trenutna verzija protokola je `4`.

### Port je zauzet

Drugi proces već koristi isti TCP port. Odaberi drugi listen port. Ako je zauzet UDP `4999`, zatvori staru instancu aplikacije koja se nije pravilno ugasila.

### LIGHT prikazuje samo `#0`

Nije bug. To je očekivano prije header synca. Spoji LIGHT na aktivni FULL ili MINER node koji ima ostatak lanca. Ako je peer povezan, a visina se ne mijenja, provjeri event stream i jesu li svi uređaji na istoj verziji protokola.

### Baza se ne može otvoriti ili je zaključana

Provjeri da nema druge instance s istim listen portom i bazom. Nakon urednog zatvaranja GUI-ja pokušaj ponovno.

### Gson `AccessDeniedException` nakon kompilacije na JDK-u 25

Na nekim Windows/JDK 25 kombinacijama compiler pri zatvaranju može ispisati `AccessDeniedException` za Gson JAR i svejedno završiti s exit kodom `0`. Projekt je ciljan na Javu 20; ako poruka smeta, kompajliraj JDK-om 20 i provjeri je li `JAVAC_EXIT` uspješan.

## Trenutna ograničenja

- UDP discovery ne prelazi routere i odvojene VLAN-ove.
- Nema NAT traversal ni javnog internet bootstrap servera.
- **Početno usklađivanje FULL nodova može slati cijeli chain i nije optimizirano za vrlo velike produkcijske lance.**
- Login hash u GUI-ju je demonstracijska lokalna zaštita, ne password sustav.
- Implementacija nije sigurnosno testirana u produkciji.

## Brza provjera prije demonstracije

1. Svi uređaji koriste isti commit i protokol `4`.
2. `lib` sadrži oba potrebna JAR-a.
3. Svaki uređaj ima svoj `WLAN/Ipconfig.java`.
4. Node ID-jevi su različiti.
5. Na istom računalu koriste se različiti TCP portovi.
6. Firewall dopušta UDP `4999` i odabrane TCP portove.
7. Pokrenut je barem jedan FULL i MINER node.
8. LIGHT node se uspješno spojio na FULL node i sinkronizirao headere, pa njegova visina više nije `#0`
