package WLAN;

public class RejectPayload {

    private String code;
    private String reason;

    public RejectPayload(String code, String reason) {
        this.code = code; // dodati code kao type?
        this.reason = reason; // dodati reason kao type?
    }

    public String getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }
}