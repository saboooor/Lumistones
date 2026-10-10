package ca.saboor.lumistones;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextStylesTest {
    @Test void configuredColorsAndLegacyTagsRemainCompatible() {
        assertEquals("§d[Lumistones] §7", TextStyles.prefix("&d[Lumistones] &7"));
        assertEquals("§cError§r", TextStyles.prefix("<red>Error</red>"));
        assertEquals("§lName§r", TextStyles.prefix("<bold>Name</bold>"));
        assertEquals("ᚠ ᚢ\nᚦ ᚨ", TextStyles.plain(TextStyles.legacy("&fᚠ ᚢ\nᚦ ᚨ")));
    }
}
