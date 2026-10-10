package ca.saboor.lumistones;

final class TravelRules {
    private TravelRules() {}
    static int cost(double distance, boolean crossWorld, double blocksPerLevel, int minimum, int maximum, int crossCost) {
        int max = Math.max(0, maximum);
        int min = Math.min(max, Math.max(0, minimum));
        return crossWorld ? Math.max(0, crossCost) : (int) Math.min(max, Math.max(min, Math.ceil(distance / Math.max(1, blocksPerLevel))));
    }
    static boolean tooClose(java.util.UUID world, int x, int y, int z, java.util.UUID otherWorld, int otherX, int otherY, int otherZ, int minimum) {
        if (minimum <= 0 || !world.equals(otherWorld)) return false;
        double dx = (double)x - otherX, dy = (double)y - otherY, dz = (double)z - otherZ;
        return dx * dx + dy * dy + dz * dz < (double)minimum * minimum;
    }
    static String cleanName(String value) {
        String name = value.replaceAll("[^\\p{L}\\p{N} _.-]", "").strip();
        return name.substring(0, Math.min(32, name.length()));
    }
}
