import java.math.BigDecimal;

public final class Money {
    public static final long UNITS_PER_COIN = 100_000_000L;

    private Money() {
    }

    public static long coins(long wholeCoins) {
        return Math.multiplyExact(wholeCoins, UNITS_PER_COIN);
    }

    public static long fromCoins(String amount) {
        if(amount == null || amount.isBlank()) {
            throw new IllegalArgumentException("Iznos nije unesen.");
        }
        BigDecimal value = new BigDecimal(amount.trim());
        return value.movePointRight(8).longValueExact();
    }

    public static String format(long amount) {
        return BigDecimal.valueOf(amount, 8).stripTrailingZeros().toPlainString();
    }
}
