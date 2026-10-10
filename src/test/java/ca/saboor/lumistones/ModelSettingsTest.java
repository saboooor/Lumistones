package ca.saboor.lumistones;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ModelSettingsTest {
    @Test void bundledModelPreservesTheEightDisplayParts() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/model.yml"))) {
            var yaml = new YamlConfiguration(); yaml.load(reader);
            var model = ModelSettings.read(yaml);
            assertEquals(8, model.parts().size());
            assertEquals(Material.POLISHED_ANDESITE, model.parts().getFirst().material());
            assertEquals(new ModelSettings.Vector(0, 0, 0), model.parts().getFirst().rotation());
            assertTrue(model.parts().getFirst().bodyBlock());
            assertTrue(model.runesEnabled()); assertTrue(model.runeText().contains("ᚠ"));
            assertTrue(model.labelEnabled()); assertEquals(.4f, model.labelScale());
        }
    }
    @Test void rimNamesFitThinFacesAndLongUnicodeNames() {
        var zero = new ModelSettings.Vector(0, 0, 0);
        var rim = new ModelSettings.Part("rim", true, false, Material.POLISHED_ANDESITE, zero, zero, new ModelSettings.Vector(.875f, .125f, .5f), 0);
        assertTrue(StoneModel.nameScale(rim, "Home", .4f, false) <= .4f);
        float longName = StoneModel.nameScale(rim, "界".repeat(32), .4f, false);
        assertTrue(longName > 0); assertTrue(longName * 32 * .4f <= .875f);
        assertTrue(StoneModel.nameScale(rim, "界".repeat(32), .4f, true) < longName);
    }
    @Test void itemPartsSupportCustomModelsAndDegrees() {
        var yaml = new YamlConfiguration();
        yaml.set("parts.crystal.type", "item"); yaml.set("parts.crystal.material", "AMETHYST_SHARD");
        yaml.set("parts.crystal.custom-model-data", 42); yaml.set("parts.crystal.rotation", List.of(0, 90, 0));
        var part = ModelSettings.read(yaml).parts().getFirst();
        assertTrue(part.item()); assertEquals(42, part.customModelData());
        assertEquals(new ModelSettings.Vector(0, 90, 0), part.rotation());
        assertEquals(new ModelSettings.Vector(1, 1, 1), part.scale());
    }
    @Test void invalidModelsFailBeforeSpawningEntities() {
        var yaml = new YamlConfiguration();
        assertThrows(IllegalArgumentException.class, () -> ModelSettings.read(yaml));
        yaml.set("parts.base.material", "STONE"); yaml.set("parts.base.scale", List.of(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> ModelSettings.read(yaml));
        yaml.set("parts.base.scale", List.of(1, 1, 1)); yaml.set("parts.base.translation", List.of(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> ModelSettings.read(yaml));
        yaml.set("parts.base.translation", List.of(0, 0));
        assertThrows(IllegalArgumentException.class, () -> ModelSettings.read(yaml));
        yaml.set("parts.base.translation", List.of(0, 0, 0)); yaml.set("parts.base.type", "armorstand");
        assertThrows(IllegalArgumentException.class, () -> ModelSettings.read(yaml));
    }
}
