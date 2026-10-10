package ca.saboor.lumistones;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BodyCraftingTest {
    private final ItemStack waystone = stack(Material.LODESTONE, 16);
    private final ItemStack block = stack(Material.BRICKS, 32);
    private static ItemStack stack(Material type, int amount) {
        var stack = mock(ItemStack.class); when(stack.getType()).thenReturn(type); when(stack.getAmount()).thenReturn(amount); return stack;
    }
    private BodyCrafting.Ingredients match(ItemStack... matrix) {
        return BodyCrafting.match(matrix, item -> item == waystone, item -> item == block);
    }
    @Test void shapelessMatchingPreservesSourceMetadataAndStackAmounts() {
        var first = match(null, waystone, null, block);
        var swapped = match(block, null, waystone, null);
        assertNotNull(first); assertEquals(first, swapped);
        assertSame(waystone, first.waystone()); assertEquals(Material.BRICKS, first.block());
        verify(waystone, never()).setAmount(anyInt()); verify(block, never()).setAmount(anyInt());
    }
    @Test void ordinaryBlocksCannotSubstituteForTaggedWaystones() {
        assertNull(match(stack(Material.LODESTONE, 1), block));
        assertNull(match(block)); assertNull(match(waystone));
    }
    @Test void extraIngredientsAndMultipleWaystonesAreRejected() {
        assertNull(match(waystone, waystone)); assertNull(match(waystone, block, block));
        assertNull(match(waystone, stack(Material.PAPER, 1)));
    }
}
