package ca.saboor.lumistones;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Admin-placed blocks that grant temporary creative-style flight to everyone standing inside a
 * configurable box around them. Used to hand out flight on build plots.
 */
final class FlightZones implements Listener {
    static final String PERMISSION_ADMIN = "lumistones.flightzone.admin";
    static final String PERMISSION_FLY = "lumistones.flightzone.fly";

    private static final String INPUT_NAME = "name";
    private static final String INPUT_WIDTH = "width";
    private static final String INPUT_HEIGHT = "height";
    /** Used when an older config.yml has no flight-zone section, so every lookup falls back to its default. */
    private static final ConfigurationSection EMPTY_SECTION = new MemoryConfiguration();

    private final Lumistones plugin;
    private final Map<String, FlightZone> zones = new ConcurrentHashMap<>();
    private final Map<Lumistones.BlockKey, String> byBlock = new ConcurrentHashMap<>();
    /** Players whose flight this plugin turned on, mapped to the zone they are currently inside. */
    private final Map<UUID, String> granted = new ConcurrentHashMap<>();
    /** Grants that survived a crash or reload and still need to be taken back. */
    private final Set<UUID> pendingRevoke = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> fallGrace = new ConcurrentHashMap<>();
    private final File dataFile;
    private final NamespacedKey itemKey;
    private Material material;
    private BukkitTask task;
    private volatile boolean grantsDirty;
    private boolean loaded;
    private final Set<UUID> removing = new java.util.HashSet<>();

    FlightZones(Lumistones plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "flight-zones.yml");
        this.itemKey = new NamespacedKey(plugin, "flight_zone_item");
    }

    void enable() {
        material = readMaterial();
        load(); loaded = true;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startTask();
    }

    void reload() {
        material = readMaterial();
        startTask();
    }

    void disable() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean revoke = granted.remove(player.getUniqueId()) != null;
            revoke |= pendingRevoke.remove(player.getUniqueId());
            if (revoke) revokeFlight(player);
        }
        save();
    }

    private void startTask() {
        if (task != null) {
            task.cancel();
        }
        long interval = Math.max(1, section().getInt("check-interval-ticks", 10));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    // --- Events -------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemMeta meta = event.getItemInHand().getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE)) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.permission(player, PERMISSION_ADMIN)) {
            event.setCancelled(true);
            player.sendMessage(plugin.error("You do not have permission to place flight zones."));
            return;
        }
        Block block = event.getBlockPlaced();
        Material placedMaterial = event.getItemInHand().getType();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled() || block.getType() != placedMaterial) return;
            FlightZone zone = createZone(block, player);
            player.sendMessage(plugin.success("Created flight zone " + zone.name + ". Right-click it to edit."));
            if (player.isOnline()) showEditDialog(player, zone);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        FlightZone zone = zoneAtBlock(event.getBlock());
        if (zone == null) {
            return;
        }
        if (removing.contains(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!plugin.permission(player, PERMISSION_ADMIN)) {
            player.sendMessage(plugin.error("You do not have permission to break flight zones.")); return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> pickup(player, zone));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        FlightZone zone = zoneAtBlock(event.getClickedBlock());
        if (zone == null || !event.getPlayer().hasPermission(PERMISSION_ADMIN)) {
            return;
        }
        event.setCancelled(true);
        showEditDialog(event.getPlayer(), zone);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> zoneAtBlock(block) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> zoneAtBlock(block) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> zoneAtBlock(block) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> zoneAtBlock(block) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (zoneAtBlock(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (zoneAtBlock(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFromTo(BlockFromToEvent event) {
        if (zoneAtBlock(event.getBlock()) != null || zoneAtBlock(event.getToBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        editSessions.remove(id);
        fallGrace.remove(id);
        boolean recovered = pendingRevoke.remove(id);
        if (granted.remove(id) != null || recovered) {
            revokeFlight(event.getPlayer());
            grantsDirty = true;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Long expiry = fallGrace.remove(player.getUniqueId());
        if (expiry != null && expiry >= System.currentTimeMillis()) {
            event.setCancelled(true);
        }
    }

    // --- Flight bookkeeping -------------------------------------------------------------------

    private void tick() {
        long now = System.currentTimeMillis();
        fallGrace.values().removeIf(expiry -> expiry < now);
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
        if (grantsDirty) {
            grantsDirty = false;
            save();
        }
    }

    private void updatePlayer(Player player) {
        UUID id = player.getUniqueId();
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            // These modes fly on their own; stop tracking so we never take away vanilla flight.
            if (granted.remove(id) != null) {
                grantsDirty = true;
            }
            if (pendingRevoke.remove(id)) grantsDirty = true;
            return;
        }

        FlightZone zone = zoneAt(player.getLocation());
        if (zone != null && plugin.permission(player, PERMISSION_FLY)) {
            boolean recovered = pendingRevoke.remove(id);
            if (recovered) grantsDirty = true;
            String current = granted.get(id);
            if (current == null) {
                if (!recovered && player.getAllowFlight()) {
                    // Flight from another source (rank perk, /fly). Leave it alone.
                    return;
                }
                granted.put(id, zone.id);
                grantsDirty = true;
                announce(player, plugin.success("Flight enabled inside " + zone.name + "."));
            } else if (!current.equals(zone.id)) {
                granted.put(id, zone.id);
                grantsDirty = true;
            }
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
            return;
        }

        if (granted.remove(id) != null) {
            grantsDirty = true;
            revokeFlight(player);
            announce(player, plugin.info("Flight disabled; you left the build plot."));
        } else if (pendingRevoke.remove(id)) {
            grantsDirty = true;
            revokeFlight(player);
        }
    }

    private void revokeFlight(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.isFlying()) {
            player.setFlying(false);
            int seconds = Math.max(0, section().getInt("fall-damage-grace-seconds", 5));
            if (seconds > 0) {
                fallGrace.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
            }
        }
        player.setAllowFlight(false);
    }

    private void announce(Player player, String message) {
        if (section().getBoolean("announce", true)) {
            player.sendMessage(message);
        }
    }

    // --- Zones --------------------------------------------------------------------------------

    private FlightZone createZone(Block block, Player owner) {
        FlightZone zone = new FlightZone(
            UUID.randomUUID().toString().substring(0, 8),
            "Flight Plot",
            owner.getUniqueId(),
            owner.getName(),
            Lumistones.BlockKey.from(block),
            clampWidth(section().getInt("default-width", 32)),
            clampHeight(section().getInt("default-height", 32))
        );
        zones.put(zone.id, zone);
        byBlock.put(zone.block, zone.id);
        save();
        return zone;
    }

    private void removeZone(FlightZone zone) {
        zones.remove(zone.id);
        byBlock.remove(zone.block);
        save();
    }

    private void pickup(Player player, FlightZone zone) {
        if (!plugin.permission(player, PERMISSION_ADMIN) || !zones.containsKey(zone.id)) return;
        World world = Bukkit.getWorld(zone.block.worldId());
        if (world == null) {
            player.sendMessage(plugin.error("That flight zone's world is not loaded."));
            return;
        }
        Block block = world.getBlockAt(zone.block.x(), zone.block.y(), zone.block.z());
        BlockBreakEvent event = new BlockBreakEvent(block, player);
        removing.add(player.getUniqueId());
        try { Bukkit.getPluginManager().callEvent(event); } finally { removing.remove(player.getUniqueId()); }
        if (event.isCancelled()) { player.sendMessage(plugin.error("You cannot remove a flight zone here.")); return; }
        removeZone(zone);
        block.setType(Material.AIR, false);

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(createItem(1));
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
        player.sendMessage(plugin.success("Removed flight zone " + zone.name + "."));
    }

    private FlightZone zoneAtBlock(Block block) {
        String id = byBlock.get(Lumistones.BlockKey.from(block));
        return id == null ? null : zones.get(id);
    }

    private FlightZone zoneAt(Location location) {
        for (FlightZone zone : zones.values()) {
            if (zone.contains(location)) {
                return zone;
            }
        }
        return null;
    }

    private Optional<FlightZone> findZone(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return zones.values().stream()
            .filter(zone -> zone.id.equalsIgnoreCase(input) || zone.name.toLowerCase(Locale.ROOT).contains(lower))
            .findFirst();
    }

    private List<FlightZone> sortedZones() {
        return zones.values().stream()
            .sorted(Comparator.comparing(zone -> zone.name.toLowerCase(Locale.ROOT)))
            .toList();
    }

    ItemStack createItem(int amount) {
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(org.bukkit.ChatColor.AQUA + "Flight Zone");
        meta.setLore(List.of("Place to create a flight build plot.", "Right-click after placing to set the size."));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // --- Dialog / forms -----------------------------------------------------------------------

    private final Map<UUID, EditSession> editSessions = new java.util.HashMap<>();
    private record EditSession(UUID token, String zone, long expires) {}

    private void showEditDialog(Player player, FlightZone zone) {
        EditSession session = new EditSession(UUID.randomUUID(), zone.id, System.currentTimeMillis() + 120_000);
        editSessions.put(player.getUniqueId(), session);
        var base = Dialogs.base("Flight Zone", "Everyone with flight permission inside this area gets flight until they leave. " + zone.describeBounds());
        base.inputs(List.of(
            new DialogView.TextInput(INPUT_NAME, new DialogView.TextComponent("Name")).initial(zone.name).maxLength(40),
            new DialogView.NumberRangeInput(INPUT_WIDTH, new DialogView.TextComponent("Width"), 1, Math.max(2, maxWidthValue())).initial((float)Math.min(zone.width, maxWidthValue())).step(1f),
            new DialogView.NumberRangeInput(INPUT_HEIGHT, new DialogView.TextComponent("Height"), 1, Math.max(2, maxHeightValue())).initial((float)Math.min(zone.height, maxHeightValue())).step(1f)
        ));
        String command = "/lumistones flightzone edit " + session.token() + " ";
        plugin.showDialog(player, Dialogs.menu(base, List.of(
            Dialogs.button("Save", command + "save $(width) $(height) $(name)", true, DialogView.Icon.SAVE),
            Dialogs.button("Remove", command + "remove", false, DialogView.Icon.RETRIEVE)
        )));
    }

    private void editCommand(Player player, String[] args) {
        if (args.length < 4) return;
        EditSession session = editSessions.get(player.getUniqueId());
        if (session == null || !session.token.toString().equals(args[2]) || session.expires < System.currentTimeMillis()) {
            player.sendMessage(plugin.error("This flight zone dialog has expired.")); return;
        }
        FlightZone zone = zones.get(session.zone);
        if (zone == null) return;
        if (args[3].equals("remove")) removeFromForm(player, zone);
        else if (args[3].equals("save") && args.length >= 7) {
            try { applyEdit(player, zone, String.join(" ", java.util.Arrays.copyOfRange(args, 6, args.length)),
                Math.round(Float.parseFloat(args[4])), Math.round(Float.parseFloat(args[5]))); }
            catch (NumberFormatException e) { player.sendMessage(plugin.error("Invalid zone dimensions.")); }
        }
        editSessions.remove(player.getUniqueId());
    }

    /** Applies edits coming from the Bedrock form. */
    void applyEdit(Player player, FlightZone zone, String name, int width, int height) {
        if (!plugin.permission(player, PERMISSION_ADMIN)) {
            player.sendMessage(plugin.error("You do not have permission to edit flight zones."));
            return;
        }
        if (!zones.containsKey(zone.id)) {
            player.sendMessage(plugin.error("That flight zone no longer exists."));
            return;
        }
        zone.name = sanitizeZoneName(name);
        zone.width = clampWidth(width);
        zone.height = clampHeight(height);
        save();
        player.sendMessage(plugin.success("Updated " + zone.name + ". " + zone.describeBounds()));
    }

    void removeFromForm(Player player, FlightZone zone) {
        if (!plugin.permission(player, PERMISSION_ADMIN)) {
            player.sendMessage(plugin.error("You do not have permission to remove flight zones."));
            return;
        }
        pickup(player, zone);
    }

    int maxWidthValue() {
        return Math.max(1, section().getInt("max-width", 256));
    }

    int maxHeightValue() {
        return Math.max(1, section().getInt("max-height", 320));
    }

    // --- Commands -----------------------------------------------------------------------------

    void handleCommand(CommandSender sender, String[] args) {
        if (!plugin.permission(sender, PERMISSION_ADMIN)) {
            sender.sendMessage(plugin.error("You do not have permission to manage flight zones."));
            return;
        }
        String sub = args.length < 2 ? "help" : args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "edit" -> { if (sender instanceof Player player) editCommand(player, args); }
            case "give" -> giveCommand(sender);
            case "list" -> listCommand(sender);
            case "remove", "delete" -> removeCommand(sender, args);
            case "tp", "teleport" -> teleportCommand(sender, args);
            default -> sender.sendMessage(plugin.error("Usage: /lumistones flightzone <give|list|remove|tp>"));
        }
    }

    List<String> tabComplete(String[] args) {
        if (args.length == 2) {
            return Stream.of("give", "list", "remove", "tp")
                .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT)))
                .toList();
        }
        if (args.length == 3 && Set.of("remove", "delete", "tp", "teleport").contains(args[1].toLowerCase(Locale.ROOT))) {
            return zones.values().stream().map(zone -> zone.id).filter(id -> id.startsWith(args[2])).toList();
        }
        return List.of();
    }

    private void giveCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.error("Only players can receive flight zone blocks."));
            return;
        }
        player.getInventory().addItem(createItem(1)).values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(plugin.success("Added a Flight Zone block to your inventory."));
    }

    private void listCommand(CommandSender sender) {
        List<FlightZone> all = sortedZones();
        if (all.isEmpty()) {
            sender.sendMessage(plugin.error("No flight zones exist."));
            return;
        }
        sender.sendMessage(plugin.info("Flight zones:"));
        for (FlightZone zone : all) {
            World world = Bukkit.getWorld(zone.block.worldId());
            String worldName = world == null ? "unloaded world" : world.getName();
            sender.sendMessage(
                zone.id + " - " + zone.name + " (" + zone.width + "x" + zone.height + ") at "
                    + zone.block.x() + ", " + zone.block.y() + ", " + zone.block.z() + " in " + worldName
                );
        }
    }

    private void removeCommand(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.error("Usage: /lumistones flightzone remove <id>"));
            return;
        }
        FlightZone zone = findZone(args[2]).orElse(null);
        if (zone == null) {
            sender.sendMessage(plugin.error("No flight zone matched " + args[2] + "."));
            return;
        }
        if (sender instanceof Player player) {
            pickup(player, zone);
            return;
        }
        World world = Bukkit.getWorld(zone.block.worldId());
        removeZone(zone);
        if (world != null) {
            world.getBlockAt(zone.block.x(), zone.block.y(), zone.block.z()).setType(Material.AIR, false);
        }
        sender.sendMessage(plugin.success("Removed flight zone " + zone.name + "."));
    }

    private void teleportCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.error("Only players can teleport to flight zones."));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(plugin.error("Usage: /lumistones flightzone tp <id>"));
            return;
        }
        FlightZone zone = findZone(args[2]).orElse(null);
        World world = zone == null ? null : Bukkit.getWorld(zone.block.worldId());
        if (zone == null || world == null) {
            player.sendMessage(plugin.error("That flight zone is not available."));
            return;
        }
        player.teleport(new Location(world, zone.block.x() + 0.5, zone.block.y() + 1.0, zone.block.z() + 0.5,
            player.getLocation().getYaw(), player.getLocation().getPitch()));
    }

    // --- Config / storage ---------------------------------------------------------------------

    private ConfigurationSection section() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("flight-zone");
        return section == null ? EMPTY_SECTION : section;
    }

    private Material readMaterial() {
        return plugin.parseMaterial(section().getString("material", "SEA_LANTERN"), Material.SEA_LANTERN, "flight-zone.material");
    }

    private String sanitizeZoneName(String input) {
        String trimmed = input == null ? "" : TravelRules.cleanName(input);
        if (trimmed.isEmpty()) {
            return "Flight Plot";
        }
        return trimmed.length() > 40 ? trimmed.substring(0, 40) : trimmed;
    }

    private int clampWidth(int value) {
        return Math.max(1, Math.min(maxWidthValue(), value));
    }

    private int clampHeight(int value) {
        return Math.max(1, Math.min(maxHeightValue(), value));
    }

    private void load() {
        zones.clear();
        byBlock.clear();
        pendingRevoke.clear();
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration yaml = DataFiles.read(dataFile);
        ConfigurationSection root = yaml.getConfigurationSection("zones");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection zoneSection = root.getConfigurationSection(id);
                if (zoneSection == null) {
                    continue;
                }
                try {
                    Lumistones.BlockKey block = new Lumistones.BlockKey(
                        UUID.fromString(zoneSection.getString("world")),
                        zoneSection.getInt("x"),
                        zoneSection.getInt("y"),
                        zoneSection.getInt("z")
                    );
                    FlightZone zone = new FlightZone(
                        id,
                        zoneSection.getString("name", "Flight Plot"),
                        UUID.fromString(zoneSection.getString("owner")),
                        zoneSection.getString("owner-name", "Unknown"),
                        block,
                        clampWidth(zoneSection.getInt("width", 32)),
                        clampHeight(zoneSection.getInt("height", 32))
                    );
                    zones.put(id, zone);
                    byBlock.put(block, id);
                } catch (IllegalArgumentException exception) {
                    throw new IllegalStateException("Invalid flight zone " + id + "; refusing to overwrite data", exception);
                }
            }
        }
        for (String id : yaml.getStringList("granted-flight")) {
            try {
                pendingRevoke.add(UUID.fromString(id));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid flight grant " + id + "; refusing to overwrite data", exception);
            }
        }
    }

    private void save() {
        if (!loaded) return;
        YamlConfiguration yaml = new YamlConfiguration();
        for (FlightZone zone : zones.values()) {
            String path = "zones." + zone.id + ".";
            yaml.set(path + "name", zone.name);
            yaml.set(path + "owner", zone.owner.toString());
            yaml.set(path + "owner-name", zone.ownerName);
            yaml.set(path + "world", zone.block.worldId().toString());
            yaml.set(path + "x", zone.block.x());
            yaml.set(path + "y", zone.block.y());
            yaml.set(path + "z", zone.block.z());
            yaml.set(path + "width", zone.width);
            yaml.set(path + "height", zone.height);
        }
        List<String> outstanding = new ArrayList<>();
        granted.keySet().forEach(id -> outstanding.add(id.toString()));
        pendingRevoke.forEach(id -> outstanding.add(id.toString()));
        yaml.set("granted-flight", outstanding.stream().distinct().sorted().toList());
        DataFiles.write(plugin, dataFile.toPath(), yaml);
    }

    static final class FlightZone {
        final String id;
        String name;
        final UUID owner;
        final String ownerName;
        final Lumistones.BlockKey block;
        int width;
        int height;

        FlightZone(String id, String name, UUID owner, String ownerName, Lumistones.BlockKey block, int width, int height) {
            this.id = id;
            this.name = name;
            this.owner = owner;
            this.ownerName = ownerName;
            this.block = block;
            this.width = width;
            this.height = height;
        }

        /** Lowest block layer: the block directly below the placed marker. */
        int minY() {
            return block.y() - 1;
        }

        /** Highest block layer that is still inside the zone. */
        int topBlockY() {
            return minY() + height - 1;
        }

        int minX() {
            return block.x() - (width - 1) / 2;
        }

        int minZ() {
            return block.z() - (width - 1) / 2;
        }

        boolean contains(Location location) {
            World world = location.getWorld();
            if (world == null || !world.getUID().equals(block.worldId())) {
                return false;
            }
            double x = location.getX();
            double z = location.getZ();
            double y = location.getY();
            return x >= minX() && x < minX() + width
                && z >= minZ() && z < minZ() + width
                && y >= minY() && y < (double)minY() + height;
        }

        String describeBounds() {
            return width + "x" + width + " blocks wide, Y " + minY() + " to " + topBlockY() + ".";
        }
    }
}
