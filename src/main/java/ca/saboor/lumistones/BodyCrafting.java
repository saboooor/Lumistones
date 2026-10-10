package ca.saboor.lumistones;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import java.util.function.Predicate;

final class BodyCrafting {
    record Ingredients(ItemStack waystone, Material block) {}
    static Ingredients match(ItemStack[] matrix, Predicate<ItemStack> waystone, Predicate<ItemStack> block) {
        ItemStack source = null;
        Material material = null;
        int count = 0;
        for (ItemStack stack : matrix) {
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) continue;
            count++;
            if (waystone.test(stack)) {
                if (source != null) return null;
                source = stack;
            } else {
                if (material != null || !block.test(stack)) return null;
                material = stack.getType();
            }
        }
        return count == 2 && source != null && material != null ? new Ingredients(source, material) : null;
    }
    private BodyCrafting() {}
}
