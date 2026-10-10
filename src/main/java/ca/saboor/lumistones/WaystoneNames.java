package ca.saboor.lumistones;

import java.util.Random;
import java.util.UUID;

/** Pronounceable fantasy place names, stable for a stone's identity. */
final class WaystoneNames {
    private static final String[] START = {"Aer", "Al", "Ar", "Bel", "Bri", "Cal", "Cor", "Dor", "Dun", "El", "Fa", "Fen", "Gal", "Glen", "Hal", "Ith", "Ka", "Kel", "Lor", "Lum", "Mor", "Nor", "Or", "Quel", "Ra", "Riv", "Sil", "Tal", "Thal", "Val", "Vor", "Zan"};
    private static final String[] MIDDLE = {"a", "ae", "an", "ar", "e", "en", "er", "i", "ia", "in", "ir", "o", "on", "or", "u", "ur"};
    private static final String[] END = {"a", "an", "ara", "as", "ath", "dale", "dor", "en", "era", "fall", "ford", "haven", "ia", "ion", "is", "mere", "ora", "orn", "os", "rest", "rin", "thor", "un", "wyn"};

    private WaystoneNames() {}
    static String forStone(UUID id) {
        Random random = new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits());
        return START[random.nextInt(START.length)] + MIDDLE[random.nextInt(MIDDLE.length)] + END[random.nextInt(END.length)];
    }
}
