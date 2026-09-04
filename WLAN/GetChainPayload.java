package WLAN;

public class GetChainPayload {

    private int fromHeight; //od kojeg bloka da se pošalje chain ipak da ne bude presporo već ovako internet je koma
    //fromHeight= 0 za cijeli chain
    public GetChainPayload(int fromHeight) {
        this.fromHeight = fromHeight;
    }

    public int getFromHeight() {
        return fromHeight;
    }
}