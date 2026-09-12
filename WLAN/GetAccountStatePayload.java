package WLAN;

public class GetAccountStatePayload {

    private String address;

    public GetAccountStatePayload(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }
}
