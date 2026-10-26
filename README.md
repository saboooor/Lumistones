# Lumistones

Paper waystones with craftable items, display-entity monuments, per-player discovery, XP and money costs, warp scrolls, and native Minecraft dialogs. Includes owner-controlled private access, Bedrock forms, command travel, configurable crafting and spacing, and flight zones.

## Install and build

Requires **Paper 1.21.10 and Java 21**. Run `./gradlew build`, put `build/libs/Lumistones-1.3.0.jar` in `plugins/`, and restart. The plugin compiles against Paper API with compileOnly scope; the server supplies it at runtime. Other Minecraft versions have not been tested. The Gradle wrapper supports running the build with Java 26; compilation and tests use the Java 21 toolchain. If your IDE still reports `Unsupported class file major version 70`, reload the Gradle project and select the project wrapper as its Gradle distribution.

The plugin creates `plugins/Lumistones/config.yml`, `stones.yml`, and `flight-zones.yml`. Back up the two data files alongside your worlds. They contain stone ownership, discovery records, flight areas, and outstanding flight grants. Existing Lumistones stone data stays compatible. New settings fall back to defaults if your existing config lacks them; add the relevant entries from `src/main/resources/config.yml` to customize them.

For Bedrock clients, install Floodgate alongside Geyser. Lumistones detects Floodgate players and converts its menus into native Bedrock forms. Java players keep native Minecraft dialogs. Without Floodgate, use the command fallback. Floodgate remains optional; its libraries are not bundled in the plugin JAR.

## Waystones

Craft a Lumistone using `S` = stone bricks, `E` = ender pearl, and `O` = obsidian:

```text
 S 
SES
OOO
```

Place it on solid ground with three empty blocks above that ground. New stones receive a random fantasy name. The name/management dialog opens after placement, letting you rename them. Naturally generated stones use the same naming system; existing saved names are preserved. New stones must be at least **64 blocks apart** within the same world; set `minimum-distance-blocks: 0` to disable spacing. Existing stones are not moved by a spacing change.

Right-click a stone to activate it for your player and open the teleport menu. Destination buttons show the world and travel cost. Local stones are automatically discovered by the player placing them; other players must visit and click them before they appear as destinations. Global stones appear for everyone without activation. Private stones can only be activated and used by their owner; other players cannot click or travel to them, even if they activated them earlier. Owners can toggle private access in the manage dialog or with `/lumistones private <id> <true|false>`. Administrators retain management permissions but cannot travel through another player’s private stone. Turning private access off restores access for players who previously activated it.

`waystones.bypass-claim-protection: true` (the default, including existing configs) allows discovery and the travel menu when GriefPrevention or another protection plugin denies a stone click. Set it to `false` to respect denied clicks. Bypassed clicks still enforce private access and use permissions, and cannot edit the stone or open management. Breaking remains protected.

Sneak-right-click your stone to rename it, change private access, travel, or retrieve it. `require-sneak-to-manage: false` opens management on a normal click for owners. Administrators can manage anyone's stones. Break the polished tuff wall at the base to retrieve the stone, or use `/lumistones remove <id>`. Only owners and administrators can retrieve stones, and protection plugins can cancel breaking. Mining in survival drops one waystone item at the base; creative-mode breaking removes the stone without dropping an item; command retrieval adds it to your inventory, dropping overflow nearby. Retrieved items retain the stone's identity, owner, name, discoveries, global/private access, body material, anchor height, owner head, and menu icon across restarts. Replacing one restores these settings at its new location without charging the global cost again.

Craft a single-use Warp Scroll using `P` = paper and `E` = ender pearl:

```text
 P 
PEP
 P 
```

Hold the scroll in your main hand and right-click the air to choose an activated destination. Clicking a stone directly uses stone travel instead. Scrolls are consumed only after successful travel; creative players retain them.

Command travel is disabled by default: click a waystone or use a scroll to travel. Set `teleport.command-travel: true` to enable the `/lumistones` teleport menu and `/lumistones tp <id>` from anywhere. These paths still use regular XP costs, warmup, cooldown, discovery, and destination safety checks. Administration and management commands remain available. Existing configurations with `command-travel: true` retain that explicit setting; change it to `false` and reload to disable command travel.

Travel has configurable warmup and cooldown. Movement or damage interrupts warmup. Mounted/gliding players cannot travel. A destination needs an empty two-block-high landing area, solid non-hazardous ground within three blocks, and space inside the world border. Missing worlds, blocked landings, and cancelled Bukkit teleport events do not charge XP or consume scrolls.

## Commands and permissions

Aliases: `/lumistone`, `/ls`.

| Command | Purpose | Permission |
| --- | --- | --- |
| `/lumistones` | Open available destinations | `lumistones.use` |
| `/lumistones list` | List available IDs, names, privacy | `lumistones.use` |
| `/lumistones tp <id>` | Travel to an available destination | `lumistones.use` |
| `/lumistones rename <name>` | Rename the stone you are looking at | Owner or `lumistones.admin` |
| `/lumistones private <id> <true\|false>` | Toggle owner-only access | Owner or `lumistones.admin` |
| `/lumistones remove <id>` | Retrieve a stone and its item | Owner or `lumistones.admin` |
| `/lumistones give [player] [waystone\|scroll]` | Give custom items; omit player to give yourself a stone | `lumistones.give` |
| `/lumistones reload` | Reload configuration, recipes, loaded models, flight checks, and Floodgate detection | `lumistones.reload` |
| `/lumistones flightzone give\|list\|remove <id>\|tp <id>` | Admin flight markers | `lumistones.flightzone.admin` |

`lumistones.use`, `lumistones.place`, and `lumistones.flightzone.fly` default to true. Administration permissions default to operators. `lumistones.admin` grants administration and ownership overrides unless an individual permission is explicitly denied.

ID/name resolution accepts full UUIDs, unique UUID prefixes, and unique names. Ambiguous matches are rejected. Menu callbacks use player-specific expiring tokens, and ownership/access is rechecked server-side. Internal `choose`, `edit`, `close`, and `flightzone edit` commands support the menus. Vanilla clients may show their standard command-confirmation prompt for Java dialog actions.

## Configuration

| Setting | Default | Effect |
| --- | --- | --- |
| `xp.enabled` | `true` | Enable or disable travel costs |
| `xp.blocks-per-level` | `1000` | Distance per XP level, rounded up |
| `xp.minimum-cost` / `xp.maximum-cost` | `{levels: 1}` / `{levels: 3}` | Same-world bounds for each currency |
| `xp.cross-dimension-cost` | `{levels: 3}` | Flat cost for travel between worlds/dimensions |
| `scroll.enabled` | `true` | Enable/disable scroll recipe and existing scroll usage |
| `scroll.uses-cost` | `false` | Charge travel costs for scrolls when costs are enabled |
| `teleport.command-travel` | `false` | Allow menu/command travel from anywhere |
| `teleport.cross-world` | `true` | Permit travel between worlds |
| `teleport.cooldown-seconds` | `5` | Recharge after successful travel |
| `teleport.warmup-seconds` | `2` | Delay before teleport |
| `teleport-sound` | `ENTITY_ENDERMAN_TELEPORT` | Bukkit sound name; plays at the destination lumistone for nearby players after successful travel; `NONE` disables it |
| `minimum-distance-blocks` | `64` | Minimum three-dimensional spacing within a world; zero disables |
| `require-sneak-to-manage` | `true` | Owners must sneak-click to manage |
| `waystones.private-by-default` | `false` | Newly placed stones allow only their owner to activate/use them |
| `waystone-material` | `LODESTONE` | Craftable item material, converted to a polished tuff wall anchor on placement |
| `recipe.enabled` | `true` | Enable/disable the waystone recipe |
| `recipe.shape` / `recipe.ingredients` | Grid above | Fully configurable crafting pattern and materials |
| `give-amount` | `1` | Amount given by the waystone give command, capped at 64 |
| `messages.prefix` | `&d[Lumistones] &7` | Chat prefix; supports `&` colors and simple named color tags |
| `model.custom-model-data` | `0` | Zero uses built-in monument; positive values use your resource-pack item model |
| `model.item-material` | `AMETHYST_SHARD` | Material for the optional item model |

The manage menu's **Visibility** button opens a separate menu with **Private**, **Public**, and **Global** options and marks the current selection. Private restricts use to the owner; Public requires each player to discover the stone; Global allows everyone to use it without discovery. Enable `waystones.public.enabled` (default `false`) to allow Global selection. `waystones.public.cost` defaults to `{levels: 3, money: 0}` and is charged each time a stone becomes global. Switching a global stone to Public or Private requires confirmation that the global cost will not be refunded; conversion is free. Disabling global waystones restores activation requirements without deleting their saved global setting.

Every cost section accepts `levels`, `money`, or both; omitted currencies are free. Money requires [Vault](https://github.com/MilkBowl/VaultAPI/blob/master/src/main/java/net/milkbowl/vault/economy/Economy.java) and an economy provider; unavailable or failed payments block the action. Same-world money costs use `xp.blocks-per-money` (default `1000`) and the money minimum/maximum. For example:

```yaml
xp:
  minimum-cost:
    levels: 1
    money: 10
  maximum-cost:
    levels: 3
    money: 100
  cross-dimension-cost:
    money: 50
waystones:
  public:
    enabled: true
    cost:
      levels: 5
      money: 250
```

Legacy `minimum-levels`, `maximum-levels`, `cross-world-levels`, and `scroll.uses-xp` remain supported when their replacement is absent. `xp.enabled` controls travel costs; the public conversion cost is independent.

XP costs are **levels**, preserve progress toward the next level, and are retained only for successful travel. Creative travel is free.

For example, a custom crafting recipe can be configured as:

```yaml
recipe:
  enabled: true
  shape: [" E ", "OLO", " D "]
  ingredients:
    E: ENDER_PEARL
    O: OBSIDIAN
    L: LODESTONE
    D: DIAMOND
```

Recipe shapes may be rectangular grids between 1×1 and 3×3. Every non-space symbol needs an item material. Invalid recipes are disabled with a server log message. Recipe book unlocks are updated on join/reload.

## Flight zones

Use `/lumistones flightzone give` to receive an admin-only marker. It has no crafting recipe. Placement opens its edit menu; right-click to reopen. Java uses dialogs, and Floodgate clients use Bedrock forms. Edit the name, width, and height, or remove the zone to retrieve its item. Only flight-zone administrators can place, edit, remove, or break markers.

Width is the full square footprint centered on the marker. The bottom layer is the block directly below the marker. Height counts upward from that layer; the upper boundary is excluded. Players with `lumistones.flightzone.fly` receive flight inside a zone and lose only the flight this plugin granted when they leave. Pre-existing flight from another plugin/rank is not taken over. Creative and spectator flight is preserved. Overlapping zones keep flight active while a player remains in an eligible zone.

Outstanding grants are persisted, allowing restart/crash recovery. When flight is taken away mid-air, fall damage is ignored for the configured grace interval. Markers are protected from explosions, pistons, burning, fading, and fluids. Player retrieval uses Bukkit block-break events so protection plugins can reject it.

```yaml
flight-zone:
  material: SEA_LANTERN
  default-width: 32
  default-height: 32
  max-width: 256
  max-height: 320
  check-interval-ticks: 10
  fall-damage-grace-seconds: 5
  announce: true
```


## Models and storage

The built-in monument combines `BlockDisplay` parts, a fixed `TextDisplay` names on the rim, and a breakable polished tuff wall anchor. Click or mine the wall at the base; there is no interaction entity intercepting mining. Saved barrier anchors from previous Lumistones builds become walls when their models load. Placement still fires the normal block placement event; retrieval fires a block break event. The anchor and model space are protected from explosions, pistons, burning, fading, and fluids.

A positive `model.custom-model-data` replaces monument parts with an `ItemDisplay` centered one block above the base in the `FIXED` transform. Supply the corresponding resource-pack model yourself; no resource pack is distributed by the plugin.

The model follows the native display/persistent-tag approach from the supplied Vehicles example. Chunk loads rebuild models from authoritative saved records, preventing duplicate displays. Data saves use atomic file replacement where supported, and unreadable data stops startup without overwriting it.

Lumistones implements the waystone/warp-scroll gameplay and flight-zone features. Warp stones, bound/return scrolls, sharestones, warp plates, inventory buttons, and client map integrations remain outside this plugin.

## Validation

`./gradlew build` runs 46 tests covering editable model validation, Paper dialog construction and sprite labels, real Cumulus form builders and response decoding, server-thread form callbacks, closed/invalid forms, spacing, deterministic structure spawning chances and identities, configurable recipes, discovery and owner-only access, permission checks, ownership and protection checks for mining, XP/scroll charging, flight ownership, geometry, fall grace, persisted grant recovery.

Live Paper/Floodgate/client gameplay has not been exercised in this workspace. Check crafting, discovery, XP/scroll settings, privacy, protection-plugin interaction, command travel settings, cross-world travel, flight entry/exit, server restarts, and chunk reloads in a test world before deployment.

Behavior references: [Waystones](https://mods.twelveiterations.com/minecraft/waystones/), [Minecraft dialogs](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-6), and the official [Floodgate](https://github.com/GeyserMC/Floodgate) / [Cumulus](https://github.com/GeyserMC/Cumulus) APIs.

Natural waystones can spawn in newly populated chunks. `natural-generation.enabled` controls this feature; the `structures` map uses Minecraft structure keys and percentages. Defaults are 25% for each village type, 15% for desert/jungle temples, and 10% for outposts, ancient cities, and end cities. Set a percentage to `0` to disable that type or `100` to always attempt placement. Other vanilla or datapack structure keys can be added.

An empty `natural-generation.worlds` list allows all worlds; otherwise list world folder names. Natural stones always require discovery by clicking them. Natural stones have no player owner, so only administrators can rename, change private access, or retrieve them.

Placement requires solid, nonhazardous support, three clear blocks, a neighboring landing spot, world-border clearance, and the configured minimum distance. It never overwrites structure blocks or loads neighboring chunks. A successful chance roll can still yield no stone if no suitable space exists. Each structure has a stable identity; completed attempts are saved in `natural-generation.yml`, preventing rerolls and respawning removed stones. Previously generated chunks are not retrofitted. Configuration reload applies to future attempts; already recorded outcomes stay fixed.

Java dialog buttons include native Minecraft sprite icons for travel, saving, retrieval, private access, and closing. These use vanilla item-atlas textures and require no resource pack. Bedrock forms keep readable text labels. The icon picker also includes crafting table, barrel, furnace, anvil, lantern, and enchanting table sprites. Choose **Custom** to enter an atlas ID and sprite ID, then select **Preview** and **Apply** to save the displayed sprite. For example, use `minecraft:blocks` with `minecraft:block/crafting_table_front`; item sprites use `minecraft:blocks` through 1.21.10 and `minecraft:items` from 1.21.11 onward. Resource pack sprites work when the client has the matching atlas and texture. Bedrock forms show the sprite ID instead of a graphical preview. Custom icons persist across restarts and retrieval. Stone names are treated as literal text rather than markup.

Existing saved stones without the new `private` flag become discoverable stones; previous global visibility no longer applies. Existing individual activation records are preserved. Successful placement automatically discovers the stone for the player placing it, before saving and opening management.

## Editing the waystone model

Edit `plugins/Lumistones/model.yml`, then run `/lumistones reload`. All loaded stones rebuild immediately; unloaded stones use the new model when loaded. Invalid files retain the last successfully loaded model and report the problem in the console. The bundled file reproduces the current monument.

Each named entry under `parts` creates a native `BlockDisplay` or `ItemDisplay`, similar to Vehicles display parts. Change `material`, `translation: [x, y, z]`, `rotation: [x, y, z]`, and `scale: [x, y, z]`, or add/remove parts. Translation uses blocks from the center of the wall’s bottom face. Rotation uses degrees around the part origin, in XYZ order; scaling applies in the part’s local axes. A block’s origin is its lower corner; an item’s origin is its center. Item parts use the fixed display transform and support `custom-model-data` for resource-pack models.

```yaml
parts:
  crystal:
    type: block
    material: AMETHYST_BLOCK
    translation: [-0.18, 1.8, -0.18]
    rotation: [0, 45, 0]
    scale: [0.36, 0.3, 0.36]
```

`label.enabled`, `label.scale`, and `label.view-range` control the floating name. Keep `model.custom-model-data: 0` in config.yml to show these native parts; a positive value still selects the single resource-pack item model. This edits the shared model for all stones. Parts are visual: the polished tuff wall remains the click/mining target, and reserved placement space remains one block wide and three blocks high. Keep the model within that footprint to avoid visual overlaps. Model files are YAML, not Vehicles `.schem` files.

## Editing in Blockbench

Open `models/Lumistone.bbmodel` in Blockbench as a **Generic Model**. Move, resize, rotate, add, and delete cubes, then save the `.bbmodel`. One block equals 16 Blockbench units; the world anchor is at `[0, 0, 0]`. The bundled model embeds vanilla Minecraft textures, so it opens with textured previews.

Keep cube names in the form `pillar [block:CHISELED_STONE_BRICKS]`. To change its material, edit the material in the name. New cubes can use `crystal [block:AMETHYST_BLOCK]`. Item-display parts use `crystal [item:AMETHYST_SHARD:123]`, where `123` is custom model data; these are bounding-cube previews, not the actual item geometry. Renaming or painting preview textures does not change the in-game material.

Install the converter dependency, then convert your saved model:

```sh
python3 -m pip install -r scripts/requirements.txt
python3 scripts/blockbench_model.py import models/Lumistone.bbmodel src/main/resources/model.yml
```

For a running server, use `plugins/Lumistones/model.yml` as the output path, then run `/lumistones reload`. Keep `model.custom-model-data: 0` to display the converted parts. The converter preserves label settings from an existing output file.

To open your current YAML model in Blockbench:

```sh
python3 scripts/blockbench_model.py export src/main/resources/model.yml models/Lumistone.bbmodel
```

Cube pivot rotations are converted into native display transforms. Rotated groups, meshes, cube inflation, and zero-size cubes are rejected; bake group rotations into individual cubes and inflation into dimensions first. Per-face UV artwork and animations are not converted: native block parts use Minecraft's block textures. Conversion checks run with `python3 -m unittest discover -s scripts -p 'test_blockbench_model.py'`.

Texture-aware exports read your installed Minecraft client JAR or a folder of PNGs, using the same embedded-texture approach as Vehicles:

```sh
python3 scripts/blockbench_model.py export src/main/resources/model.yml models/Lumistone.bbmodel --client-jar /path/to/minecraft-client.jar
# Or supply material PNG files such as polished_deepslate.png:
python3 scripts/blockbench_model.py export src/main/resources/model.yml models/Lumistone.bbmodel --textures /path/to/textures
```

Textures from `--textures` take precedence over client assets. Missing textures fall back to color previews and print a notice. Textures are embedded in the saved `.bbmodel`; Blockbench does not need access to the original files afterward. Preview texture edits still do not change native Minecraft display materials.

To download the client automatically from Mojang, pass `--client-jar` without a path:

```sh
python3 scripts/blockbench_model.py export src/main/resources/model.yml models/Lumistone.bbmodel --client-jar
# Choose a different Minecraft version:
python3 scripts/blockbench_model.py export src/main/resources/model.yml models/Lumistone.bbmodel --client-jar --minecraft-version 1.21.11
```

The default download version is `1.21.10`. Downloads use Mojang's version manifest, verify the expected file size and SHA-1, and cache the client under `$XDG_CACHE_HOME/lumistones` (or `~/.cache/lumistones`). Verified cached clients work without network access. Supplying a path after `--client-jar` continues to use that local JAR instead.

## Body materials and rune overlays

To change a placed waystone’s body material, **shift-right-click it with a block in your main hand**. Only the owner or an administrator can change it. This consumes one block in survival; creative players keep their block. Applying the current material consumes nothing. The stone keeps its name, owner, visibility, icon, and activations. Shift-right-click with an empty hand to manage it. Placement, mining retrieval, and command retrieval preserve the material. Polished andesite remains the default; the mining anchor stays a polished tuff wall. Material-changing crafting recipes are no longer available.

Parts with `material-placeholder: body_block` use the stone’s chosen material instead of their fallback `material`. The default body is polished andesite. In Blockbench, preserve `{body_block}` in cube names, such as `pillar {body_block} [block:POLISHED_ANDESITE]`, to keep this substitution when converting.

Runes are four fixed `TextDisplay` overlays just outside the faces of the part named `pillar`; they follow its dimensions and rotation. Configure `runes.enabled`, `runes.text` (supports `&` colors and newlines), and `runes.scale` in model.yml. Their backgrounds are transparent and brightness is fixed at maximum. The converter preserves rune settings from the output YAML or its embedded metadata; the text overlays are generated in Minecraft and do not appear as cubes in Blockbench. A single resource-pack item override does not generate these pillar overlays.

The waystone management dialog saves the edited name when you press **Close**, without a separate Save name button. Escape dismisses the dialog without saving the draft name. Bedrock players select **Close** in the Action dropdown and submit; dismissing a Bedrock form does not submit changes. Other management actions retain their existing behavior. Default white rune overlays use two columns and three rows.

`waystones.anchor-height: 2` controls how many polished tuff walls are stacked for new stones (1–16). Either anchor can be clicked or mined. Breaking any anchor retrieves one item and clears the other anchors; ownership and break cancellation checks still apply. The height is saved per stone, so changing the config affects new stones rather than leaving old stacks behind. Older records without a stored height adopt this setting when loaded; extra anchors are added only in empty space. Placement needs at least three clear blocks, or the configured height if larger.

Management shows **Travel** and a **Visibility** submenu with **Private**, **Public**, and **Global** choices. The Retrieve button is removed; mine an anchor to retrieve the waystone. Close still saves the name, and Escape dismisses without saving.

Name labels and rune overlays are hidden until that player discovers the stone by placing it or visiting and clicking it. The physical monument remains visible. Private access also hides overlays from other players who previously activated it; allowing access restores them. Paper per-player entity visibility handles spawning, activation, joins, chunk/model reloads, and privacy changes; PacketEvents is not required.

Waystone names now appear as fixed overlays on all four faces of the part named `rim` (previously `top2`). They follow the rim’s dimensions and rotation and scale down to fit long names. `label.scale: 0.4` sets the maximum text scale; `label.enabled` and `label.view-range` still apply. Names and runes remain hidden until activation. For existing model.yml files, rename the part to `rim` and replace `label.height` with `label.scale`. Single resource-pack model overrides have no native rim and do not create these labels.

`waystones.item-glint: true` adds an enchantment shimmer to Lumistone items without adding an enchantment. Set it to `false` and reload to disable the shimmer on newly crafted, given, recolored, or retrieved items. Existing inventory items keep their current appearance until recolored or placed and retrieved.

Currency amounts in menus use the Vault economy provider’s formatting, with `$` as a fallback.

Destination rows mark global Lumistones with the vanilla globe GUI icon (Global text on Bedrock). Cross-world destinations show their world icon and name after the owner head. `xp.cross-dimension-cost` accepts levels and money; existing `xp.cross-world-cost` and `xp.cross-world-levels` settings remain supported when the new setting is absent. Creative travel and the configured free-scroll rules still apply.

Owners and administrators can open **Manage → Discovered by** and select **Remove · player** to remove that player’s discovery. The list refreshes after removal, and online players immediately lose the stone’s name and rune overlays. They can rediscover it by visiting and activating it again. Global stones remain available to everyone.

Destination menus list your own Lumistones first, followed by other global Lumistones, then other discovered stones. Within the owned group, global stones come first; each group is sorted by name.

When more than 16 destinations are available, the travel menu shows **Own Lumistones**, **Global Lumistones**, and **Others’ Lumistones** buttons. Own includes all your stones; Global includes all global stones, including yours; Others includes discoverable stones owned by other players or naturally generated. Each filtered list has a **Back** button. Lists of 16 or fewer destinations open directly.
