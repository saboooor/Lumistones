package ca.saboor.lumistones;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class Lumistones extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    final Map<UUID, Stone> stones = new LinkedHashMap<>();
    final Map<UUID, Set<UUID>> discoveries = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> pending = new HashMap<>();
    NamespacedKey entityKey, itemKey;
    private boolean dataLoaded;
    private StoneModel model;
    private Dialogs dialogs;
    private WaystoneEffects effects;
    private FlightZones flightZones;
    private BedrockForms bedrockForms;
    private final Set<UUID> retrieving = new HashSet<>();
    private final Map<UUID, Stone> placing = new HashMap<>();
    record BlockKey(UUID worldId, int x, int y, int z) {
        static BlockKey from(Block block) { return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()); }
    }
    record Session(UUID token, UUID source, boolean scroll, boolean remote, long expires) {}

    @Override public void onEnable() {
        saveDefaultConfig(); entityKey = new NamespacedKey(this, "stone"); itemKey = new NamespacedKey(this, "item");
        model = new StoneModel(this); dialogs = new Dialogs(this); load(); dataLoaded = true; recipes();
        bedrockForms = new BedrockForms(this); bedrockForms.refresh();
        flightZones = new FlightZones(this); flightZones.enable();
        new NaturalWaystones(this).enable();
        effects = new WaystoneEffects(this); effects.enable();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("lumistones")).setExecutor(this); getCommand("lumistones").setTabCompleter(this);
        for (World world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks()) model.reconcile(chunk);
    }
    @Override public void onDisable() {
        pending.values().forEach(BukkitTask::cancel); pending.clear();
        save();
        if (flightZones != null) flightZones.disable();
        if (model != null) for (World world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks())
            for (Entity entity : chunk.getEntities()) if (model.stoneId(entity) != null) entity.remove();
    }
    private void load() {
        File file = new File(getDataFolder(), "stones.yml"); YamlConfiguration data = new YamlConfiguration();
        if (file.exists()) try { data.load(file); } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) { throw new IllegalStateException("Cannot read stones.yml; refusing to overwrite data", e); }
        var section = data.getConfigurationSection("stones");
        if (section != null) for (String id : section.getKeys(false)) try {
            String root = "stones." + id + ".";
            Stone stone = new Stone(UUID.fromString(id), UUID.fromString(data.getString(root + "owner")), UUID.fromString(data.getString(root + "world")), data.getInt(root + "x"), data.getInt(root + "y"), data.getInt(root + "z"), data.getString(root + "name", "Waystone"), data.getBoolean(root + "private"), bodyMaterial(data.getString(root + "body-block", "POLISHED_ANDESITE")), Math.max(1, Math.min(16, data.getInt(root + "anchor-height", anchorHeight()))), data.getBoolean(root + "owner-head", false)); stones.put(stone.id(), stone);
        } catch (RuntimeException e) { throw new IllegalStateException("Invalid waystone record " + id + "; refusing to overwrite data", e); }
        section = data.getConfigurationSection("discoveries");
        if (section != null) for (String player : section.getKeys(false)) {
            Set<UUID> ids = new HashSet<>(); for (String id : data.getStringList("discoveries." + player)) ids.add(UUID.fromString(id));
            discoveries.put(UUID.fromString(player), ids);
        }
    }
    private void save() {
        if (!dataLoaded) return;
        YamlConfiguration data = new YamlConfiguration();
        for (Stone stone : stones.values()) {
            String root = "stones." + stone.id() + ".";
            data.set(root + "owner", stone.owner().toString()); data.set(root + "world", stone.world().toString());
            data.set(root + "x", stone.x()); data.set(root + "y", stone.y()); data.set(root + "z", stone.z());
            data.set(root + "owner-head", stone.ownerHead()); data.set(root + "anchor-height", stone.anchorHeight()); data.set(root + "body-block", stone.bodyBlock().name()); data.set(root + "name", stone.name()); data.set(root + "private", stone.privateAccess());
        }
        discoveries.forEach((player, ids) -> data.set("discoveries." + player, ids.stream().map(UUID::toString).toList()));
        DataFiles.write(this, getDataFolder().toPath().resolve("stones.yml"), data);
    }
    private ItemStack item(String type) {
        ItemStack item = new ItemStack(type.equals("waystone") ? parseMaterial(getConfig().getString("waystone-material", "LODESTONE"), Material.LODESTONE, "waystone-material") : Material.PAPER);
        var meta = item.getItemMeta(); meta.displayName(TextStyles.component("\u00a7d" + (type.equals("waystone") ? "Lumistone" : "Warp Scroll")));
        meta.lore((type.equals("waystone") ? List.of("\u00a77" + "Place, activate, and travel.", "\u00a77" + "Break to retrieve; sneak-click to manage.") : List.of("\u00a77" + "Travel to an activated waystone.", "\u00a77" + "Consumed after successful travel.")).stream().map(TextStyles::component).toList());
        if (type.equals("waystone")) meta.setEnchantmentGlintOverride(getConfig().getBoolean("waystones.item-glint", true));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, type); item.setItemMeta(meta); return item;
    }
    private Material itemBody(ItemStack stack) {
        String value = stack.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(this, "body_block"), PersistentDataType.STRING);
        return value == null ? Material.POLISHED_ANDESITE : bodyMaterial(value);
    }
    private ItemStack bodyItem(Material material) { return colorItem(item("waystone"), material); }
    private ItemStack colorItem(ItemStack original, Material material) {
        ItemStack result = original.clone(); result.setAmount(1);
        var meta = result.getItemMeta();
        meta.setEnchantmentGlintOverride(getConfig().getBoolean("waystones.item-glint", true));
        meta.getPersistentDataContainer().set(new NamespacedKey(this, "body_block"), PersistentDataType.STRING, material.name());
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
        lore.removeIf(line -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line).startsWith("Body block: "));
        lore.add(TextStyles.component("\u00a77Body block: " + material.name())); meta.lore(lore); result.setItemMeta(meta);
        return result;
    }
    private boolean bodyRecipe(Recipe recipe) {
        return recipe instanceof Keyed keyed && keyed.getKey().equals(new NamespacedKey(this, "body_block"));
    }
    private ItemStack bodyResult(CraftingInventory inventory) {
        var ingredients = BodyCrafting.match(inventory.getMatrix(), stack -> isItem(stack, "waystone"),
                stack -> stack.getType().isBlock() && !isItem(stack, "scroll"));
        return ingredients == null ? null : colorItem(ingredients.waystone(), ingredients.block());
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepareBody(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
        if (bodyRecipe(event.getRecipe())) event.getInventory().setResult(bodyResult(event.getInventory()));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craftBody(org.bukkit.event.inventory.CraftItemEvent event) {
        if (!bodyRecipe(event.getRecipe())) return;
        ItemStack result = bodyResult(event.getInventory());
        if (result == null) { event.setCancelled(true); return; }
        event.setCurrentItem(result);
    }
    private boolean isItem(ItemStack item, String type) {
        if (item == null || !item.hasItemMeta()) return false;
        var data = item.getItemMeta().getPersistentDataContainer();
        return type.equals(data.get(itemKey, PersistentDataType.STRING));
    }
    private void recipes() {
        NamespacedKey stoneKey = new NamespacedKey(this, "waystone"), scrollKey = new NamespacedKey(this, "scroll");
        Bukkit.removeRecipe(stoneKey); Bukkit.removeRecipe(scrollKey);
        NamespacedKey bodyKey = new NamespacedKey(this, "body_block");
        Bukkit.removeRecipe(bodyKey);
        Material stoneMaterial = item("waystone").getType();
        List<Material> blocks = Arrays.stream(Material.values()).filter(material -> !material.isLegacy() && material.isBlock() && material.isItem() && !material.isAir()).toList();
        ShapelessRecipe body = new ShapelessRecipe(bodyKey, item("waystone"));
        body.addIngredient(new RecipeChoice.MaterialChoice(stoneMaterial));
        body.addIngredient(new RecipeChoice.MaterialChoice(blocks)); Bukkit.addRecipe(body);
        if (getConfig().getBoolean("recipe.enabled", true)) {
            try {
                RecipeSettings settings = RecipeSettings.read(getConfig());
                if (settings.ingredients().values().stream().anyMatch(material -> !material.isItem())) throw new IllegalArgumentException("Recipe ingredients must be obtainable items");
                ShapedRecipe stone = new ShapedRecipe(stoneKey, item("waystone")); stone.shape(settings.shape().toArray(String[]::new));
                settings.ingredients().forEach(stone::setIngredient); Bukkit.addRecipe(stone);
            } catch (IllegalArgumentException e) { getLogger().warning("Waystone recipe disabled: " + e.getMessage()); }
        }
        if (getConfig().getBoolean("scroll.enabled")) {
            ShapedRecipe scroll = new ShapedRecipe(scrollKey, item("scroll")); scroll.shape(" P ", "PEP", " P ");
            scroll.setIngredient('P', Material.PAPER); scroll.setIngredient('E', Material.ENDER_PEARL); Bukkit.addRecipe(scroll);
        }
        for (Player player : Bukkit.getOnlinePlayers()) { if (Bukkit.getRecipe(stoneKey) != null) player.discoverRecipe(stoneKey); else player.undiscoverRecipe(stoneKey); if (getConfig().getBoolean("scroll.enabled")) player.discoverRecipe(scrollKey); }
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        model.refresh(event.getPlayer());
        if (Bukkit.getRecipe(new NamespacedKey(this, "waystone")) != null) event.getPlayer().discoverRecipe(new NamespacedKey(this, "waystone"));
        if (getConfig().getBoolean("scroll.enabled")) event.getPlayer().discoverRecipe(new NamespacedKey(this, "scroll"));
    }
    static boolean worldEnabled(org.bukkit.configuration.Configuration config, String world) {
        var worlds = config.getConfigurationSection("enabled-worlds");
        return worlds != null && worlds.getValues(true).containsKey(world);
    }
    boolean worldEnabled(World world) { return world != null && worldEnabled(getConfig(), world.getName()); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void place(BlockPlaceEvent event) {
        if (!isItem(event.getItemInHand(), "waystone")) return;
        Player player = event.getPlayer(); Block block = event.getBlockPlaced();
        if (!worldEnabled(block.getWorld())) { event.setCancelled(true); tell(player, "Waystones are disabled in this world."); return; }
        if (block.getY() + clearanceHeight() - 1 >= block.getWorld().getMaxHeight() || !permission(player, "lumistones.place") || !block.getRelative(0, -1, 0).getType().isSolid() || !clearAbove(block) || occupied(block) != null) {
            event.setCancelled(true); tell(player, "You need solid ground and " + clearanceHeight() + " clear blocks of space."); return;
        }
        if (tooClose(block.getLocation())) { event.setCancelled(true); tell(player, "Waystones must be at least " + minimumDistance() + " blocks apart."); return; }
        // Placement uses the item's real block event; a mineable wall supports the display model.
        UUID id = UUID.randomUUID();
        Stone stone = new Stone(id, player.getUniqueId(), block.getWorld().getUID(), block.getX(), block.getY(), block.getZ(), WaystoneNames.forStone(id), getConfig().getBoolean("waystones.private-by-default", false), itemBody(event.getItemInHand()), anchorHeight());
        placing.put(stone.id(), stone);
        Material placedMaterial = event.getItemInHand().getType();
        Bukkit.getScheduler().runTask(this, () -> {
            placing.remove(stone.id());
            if (event.isCancelled() || block.getType() != placedMaterial) return;
            createAnchors(stone);
            stones.put(stone.id(), stone); save();
            model.spawn(stone); if (player.isOnline()) dialogs.manage(player, stone);
        });
    }
    private Stone at(Location location) {
        for (Stone stone : stones.values()) if (stone.world().equals(location.getWorld().getUID()) && stone.x() == location.getBlockX() && stone.y() == location.getBlockY() && stone.z() == location.getBlockZ()) return stone;
        return null;
    }
    private Stone occupied(Block block) {
        for (Stone stone : stones.values()) if (stone.world().equals(block.getWorld().getUID()) && stone.x() == block.getX() && stone.z() == block.getZ()
                && block.getY() >= stone.y() && block.getY() < stone.y() + Math.max(3, stone.anchorHeight())) return stone;
        return null;
    }
    private Stone anchorAt(Location location) {
        for (Stone stone : stones.values()) if (stone.world().equals(location.getWorld().getUID()) && stone.x() == location.getBlockX() && stone.z() == location.getBlockZ()
                && location.getBlockY() >= stone.y() && location.getBlockY() < stone.y() + stone.anchorHeight()) return stone;
        return null;
    }
    int anchorHeight() { return Math.max(1, Math.min(16, getConfig().getInt("waystones.anchor-height", 2))); }
    int clearanceHeight() { return Math.max(3, anchorHeight()); }
    private boolean clearAbove(Block block) {
        for (int dy = 1; dy < clearanceHeight(); dy++) if (!block.getRelative(0, dy, 0).isEmpty()) return false;
        return true;
    }
    void createAnchors(Stone stone) {
        Block base = stone.location().getBlock();
        for (int dy = 0; dy < stone.anchorHeight() && base.getY() + dy < base.getWorld().getMaxHeight(); dy++) {
            Block block = base.getRelative(0, dy, 0);
            if (dy == 0 || block.getType().isAir()) block.setType(Material.POLISHED_TUFF_WALL, false);
        }
    }
    private void clearAnchors(Stone stone) {
        Block base = stone.location().getBlock();
        for (int dy = 0; dy < stone.anchorHeight() && base.getY() + dy < base.getWorld().getMaxHeight(); dy++) {
            Block block = base.getRelative(0, dy, 0);
            if (block.getType() == Material.POLISHED_TUFF_WALL || block.getType() == Material.BARRIER) block.setType(Material.AIR, false);
        }
    }
    // Air interactions can be pre-cancelled because paper has no vanilla right-click action.
    @EventHandler(priority = EventPriority.HIGHEST) public void blockClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        Stone stone = event.getClickedBlock() == null ? null : anchorAt(event.getClickedBlock().getLocation());
        if (stone != null) { event.setCancelled(true); click(event.getPlayer(), stone); }
        else if (isItem(event.getItem(), "scroll")) { event.setCancelled(true); openScroll(event.getPlayer()); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void entityClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Stone stone = stones.get(model.stoneId(event.getRightClicked()));
        if (stone != null) { event.setCancelled(true); click(event.getPlayer(), stone); }
    }
    private void click(Player player, Stone stone) {
        if (!worldEnabled(player.getWorld())) { tell(player, "Waystones are disabled in this world."); return; }
        if (!permission(player, "lumistones.use")) return;
        if (!near(player, stone)) return;
        if (!canUse(player, stone)) { tell(player, "This waystone is private to its owner."); return; }
        activate(player, stone);
        if ((!getConfig().getBoolean("require-sneak-to-manage", true) || player.isSneaking()) && owner(player, stone)) dialogs.manage(player, stone); else dialogs.destinations(player, stone, false);
    }
    private void openScroll(Player player) {
        if (!getConfig().getBoolean("scroll.enabled")) { tell(player, "Warp scrolls are disabled."); return; }
        if (permission(player, "lumistones.use")) dialogs.destinations(player, null, true);
    }
    private void activate(Player player, Stone stone) {
        if (discoveries.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(stone.id())) {
            save(); model.refresh(player); tell(player, "Activated " + stone.name() + "."); player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1, 1);
        }
    }
    boolean canUse(Player player, Stone stone) { return !stone.privateAccess() || stone.owner().equals(player.getUniqueId()); }
    boolean known(Player player, Stone stone) { return canUse(player, stone) && discoveries.getOrDefault(player.getUniqueId(), Set.of()).contains(stone.id()); }
    boolean owner(Player player, Stone stone) { return stone.owner().equals(player.getUniqueId()) || permission(player, "lumistones.admin"); }
    private boolean near(Player player, Stone stone) { return stone.location().getWorld() == player.getWorld() && player.getLocation().distanceSquared(stone.location().add(.5, .5, .5)) <= 36; }
    Session session(Player player, Stone source, boolean scroll) {
        Session session = new Session(UUID.randomUUID(), source == null ? null : source.id(), scroll, source == null && !scroll, System.currentTimeMillis() + 120_000); sessions.put(player.getUniqueId(), session); return session;
    }
    int cost(Player player, Stone target, boolean scroll) {
        if (!getConfig().getBoolean("xp.enabled") || scroll && !getConfig().getBoolean("scroll.uses-xp") || player.getGameMode() == GameMode.CREATIVE) return 0;
        boolean cross = !target.world().equals(player.getWorld().getUID());
        return TravelRules.cost(cross ? 0 : player.getLocation().distance(target.location()), cross, getConfig().getDouble("xp.blocks-per-level"), getConfig().getInt("xp.minimum-levels"), getConfig().getInt("xp.maximum-levels"), getConfig().getInt("xp.cross-world-levels"));
    }
    private Session validSession(Player player, String token) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.token().toString().equals(token) && session.expires() >= System.currentTimeMillis() ? session : null;
    }
    private boolean validSource(Player player, Session session) {
        if (!worldEnabled(player.getWorld())) return false;
        if (session.remote()) return getConfig().getBoolean("teleport.command-travel", false);
        if (session.scroll()) return getConfig().getBoolean("scroll.enabled") && isItem(player.getInventory().getItemInMainHand(), "scroll");
        Stone source = stones.get(session.source()); return source != null && known(player, source) && near(player, source) && source.location().getBlock().getType() == Material.POLISHED_TUFF_WALL;
    }
    private void travel(Player player, Session session, Stone target) {
        if (!worldEnabled(player.getWorld()) || !worldEnabled(target.location().getWorld())) { tell(player, "Waystones are disabled in this world."); return; }
        if (!permission(player, "lumistones.use") || !known(player, target) || !validSource(player, session) || target.id().equals(session.source())) { tell(player, "This travel selection is no longer available."); return; }
        if (pending.containsKey(player.getUniqueId())) { tell(player, "A teleport is already in progress."); return; }
        if (cooldowns.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) { tell(player, "Your waystone is still recharging."); return; }
        if (!getConfig().getBoolean("teleport.cross-world") && !target.world().equals(player.getWorld().getUID())) { tell(player, "Cross-world travel is disabled."); return; }
        if (target.location().getWorld() == null) { tell(player, "The destination world is unavailable."); return; }
        if (player.isInsideVehicle() || player.isGliding()) { tell(player, "Dismount and stop gliding before travelling."); return; }
        if (player.getLevel() < cost(player, target, session.scroll())) { tell(player, "You do not have enough XP levels."); return; }
        Location start = player.getLocation(); sessions.remove(player.getUniqueId());
        if (getConfig().getLong("teleport.warmup-seconds") > 0) tell(player, "Travelling to " + target.name() + "… Stay still.");
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            pending.remove(player.getUniqueId());
            if (!player.isOnline() || player.isInsideVehicle() || player.isGliding() || !stones.containsKey(target.id()) || !permission(player, "lumistones.use") || !validSource(player, session) || player.getWorld() != start.getWorld() || player.getLocation().distanceSquared(start) > .04 || !known(player, stones.get(target.id()))) { tell(player, "Teleport cancelled."); return; }
            if (!getConfig().getBoolean("teleport.cross-world") && !target.world().equals(player.getWorld().getUID())) { tell(player, "Cross-world travel is disabled."); return; }
            if (!worldEnabled(target.location().getWorld())) { tell(player, "The destination world is disabled."); return; }
            Location destination = safeLanding(target, player);
            if (destination == null) { tell(player, "The destination has no safe landing space."); return; }
            int cost = cost(player, target, session.scroll());
            if (player.getLevel() < cost) { tell(player, "You do not have enough XP levels."); return; }
            // Bukkit teleport is cancellable; charge only after it succeeds.
            ItemStack paymentScroll = session.scroll() ? player.getInventory().getItemInMainHand() : null;
            if (TravelPayment.complete(player, destination, cost, paymentScroll)) {
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + Math.max(0, getConfig().getLong("teleport.cooldown-seconds")) * 1000);
                effects.arrived(player, start, target.name());
                playTeleportSound(player, destination); tell(player, "Arrived at " + target.name() + ".");
            } else tell(player, "Teleport was cancelled.");
        }, Math.min(1200, Math.max(1, getConfig().getLong("teleport.warmup-seconds") * 20)));
        pending.put(player.getUniqueId(), task);
    }
    private Location safeLanding(Stone stone, Player player) {
        World world = stone.location().getWorld(); if (world == null) return null;
        world.getChunkAt(stone.x() >> 4, stone.z() >> 4).load();
        if (stone.location().getBlock().getType() != Material.POLISHED_TUFF_WALL) return null;
        for (int radius = 1; radius <= 3; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            for (int dy : new int[]{0, 1, -1, 2, -2}) {
                Location location = stone.location().add(dx + .5, dy, dz + .5);
                if (location.getY() <= world.getMinHeight() || location.getY() + 1 >= world.getMaxHeight() || !world.getWorldBorder().isInside(location)) continue;
                Block feet = location.getBlock(), head = feet.getRelative(0, 1, 0), floor = feet.getRelative(0, -1, 0);
                if (!feet.isEmpty() || !head.isEmpty() || !floor.getType().isSolid() || hazardous(floor.getType()) || occupied(feet) != null || occupied(head) != null) continue;
                location.setYaw(player.getLocation().getYaw()); location.setPitch(player.getLocation().getPitch()); return location;
            }
        }
        return null;
    }
    private boolean hazardous(Material material) { return material == Material.MAGMA_BLOCK || material == Material.CACTUS || material == Material.CAMPFIRE || material == Material.SOUL_CAMPFIRE || material == Material.POWDER_SNOW; }
    private void cancel(Player player) { BukkitTask task = pending.remove(player.getUniqueId()); if (task != null) { task.cancel(); tell(player, "Teleport interrupted."); } }
    @EventHandler(ignoreCancelled = true) public void move(PlayerMoveEvent event) {
        if (event.getTo() != null && (event.getFrom().getWorld() != event.getTo().getWorld() || event.getFrom().distanceSquared(event.getTo()) > .0001)) cancel(event.getPlayer());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void hurt(EntityDamageEvent event) { if (event.getEntity() instanceof Player player) cancel(player); }
    @EventHandler public void quit(PlayerQuitEvent event) { cancel(event.getPlayer()); sessions.remove(event.getPlayer().getUniqueId());  }
    @EventHandler public void death(PlayerDeathEvent event) { cancel(event.getEntity()); sessions.remove(event.getEntity().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR) public void chunkLoad(ChunkLoadEvent event) { model.reconcile(event.getChunk()); }
    @EventHandler public void worldLoad(WorldLoadEvent event) { for (Chunk chunk : event.getWorld().getLoadedChunks()) model.reconcile(chunk); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void breakStone(BlockBreakEvent event) {
        Stone stone = anchorAt(event.getBlock().getLocation()); if (stone == null) return;
        if (retrieving.contains(event.getPlayer().getUniqueId())) return;
        if (!owner(event.getPlayer(), stone)) {
            event.setCancelled(true); tell(event.getPlayer(), "You do not own this waystone."); return;
        }
        event.setDropItems(false); event.setExpToDrop(0);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void brokenStone(BlockBreakEvent event) {
        if (retrieving.contains(event.getPlayer().getUniqueId())) return;
        Stone stone = anchorAt(event.getBlock().getLocation());
        if (stone == null || !owner(event.getPlayer(), stone)) return;
        // Wait for vanilla to remove the block and every protection listener to finish.
        getServer().getScheduler().runTask(this, () -> {
            if (event.isCancelled() || !event.getBlock().getType().isAir() || !stone.equals(stones.get(stone.id()))) return;
            clearAnchors(stone); remove(stone);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(.5, .5, .5), bodyItem(stone.bodyBlock()));
        });
    }
    void remove(Stone stone) { model.remove(stone); stones.remove(stone.id()); discoveries.values().forEach(ids -> ids.remove(stone.id())); save(); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void protectSpace(BlockPlaceEvent event) { if (occupied(event.getBlockPlaced()) != null && !isItem(event.getItemInHand(), "waystone")) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void piston(BlockPistonExtendEvent event) { if (event.getBlocks().stream().anyMatch(block -> occupied(block) != null || occupied(block.getRelative(event.getDirection())) != null)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void piston(BlockPistonRetractEvent event) { if (event.getBlocks().stream().anyMatch(block -> occupied(block) != null || occupied(block.getRelative(event.getDirection())) != null)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void explode(EntityExplodeEvent event) { event.blockList().removeIf(block -> occupied(block) != null); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void explode(BlockExplodeEvent event) { event.blockList().removeIf(block -> occupied(block) != null); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void burn(BlockBurnEvent event) { if (occupied(event.getBlock()) != null) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void fade(BlockFadeEvent event) { if (occupied(event.getBlock()) != null) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void fluid(BlockFromToEvent event) { if (occupied(event.getBlock()) != null || occupied(event.getToBlock()) != null) event.setCancelled(true); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload") && permission(sender, "lumistones.reload")) {
            reloadConfig(); boolean modelReloaded = model.reload(); recipes(); flightZones.reload(); bedrockForms.refresh(); for (World world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks()) model.reconcile(chunk); sender.sendMessage(modelReloaded ? "Lumistones configuration and model reloaded." : "Configuration reloaded; model.yml is invalid, keeping the previous model. Check the console."); return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("give") && permission(sender, "lumistones.give")) {
            Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player player ? player : null; String type = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "waystone";
            if (target == null || !Set.of("waystone", "scroll").contains(type)) { sender.sendMessage("/lumistones give <player> [waystone|scroll]"); return true; }
            ItemStack gift = item(type); if (type.equals("waystone")) gift.setAmount(Math.max(1, Math.min(64, getConfig().getInt("give-amount", 1))));
            target.getInventory().addItem(gift).values().forEach(extra -> target.getWorld().dropItemNaturally(target.getLocation(), extra)); return true;
        }
        if (args.length > 0 && Set.of("flightzone", "fz").contains(args[0].toLowerCase(Locale.ROOT))) { flightZones.handleCommand(sender, args); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) { list(sender); return true; }
        if (!(sender instanceof Player player)) { sender.sendMessage("/lumistones give <player> [waystone|scroll] | reload"); return true; }
        if (args.length == 1 && args[0].equals("close")) { sessions.remove(player.getUniqueId()); return true; }
        if (args.length >= 3 && (args[0].equals("choose") || args[0].equals("edit"))) {
            Session session = validSession(player, args[1]); if (session == null) { tell(player, "This dialog has expired. Open the waystone again."); return true; }
            if (args[0].equals("choose")) {
                try { Stone target = stones.get(UUID.fromString(args[2])); if (target != null) travel(player, session, target); } catch (IllegalArgumentException ignored) { tell(player, "Invalid destination."); }
            } else {
                Stone stone = stones.get(session.source()); if (stone == null || !owner(player, stone) || !near(player, stone)) return true;
                if (args[2].equals("rename") && args.length > 3) {
                    String name = TravelRules.cleanName(String.join(" ", Arrays.copyOfRange(args, 3, args.length)));
                    if (name.isEmpty()) { tell(player, "Enter a name containing letters or numbers."); return true; }
                    model.remove(stone); Stone updated = stone.named(name); stones.put(stone.id(), updated); model.spawn(updated); save();
                } else if (args[2].equals("travel")) { dialogs.destinations(player, stone, false); return true; }
                else if (args[2].equals("private")) { Stone updated = stone.visibility(!stone.privateAccess()); stones.put(stone.id(), updated); model.refresh(updated); save(); }
                else if (args[2].equals("owner-head")) {
                    Stone updated = stone.withOwnerHead(!stone.ownerHead());
                    model.remove(stone); stones.put(stone.id(), updated); model.spawn(updated); save();
                    dialogs.manage(player, updated); return true;
                }
                else if (args[2].equals("remove")) {
                    pickup(player, stone);
                }
                sessions.remove(player.getUniqueId());
            }
            return true;
        }
        if (args.length == 0) {
            if (!getConfig().getBoolean("teleport.command-travel", false)) tell(player, "Click a waystone or use a warp scroll to travel.");
            else if (permission(player, "lumistones.use")) dialogs.destinations(player, null, false);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (Set.of("tp", "teleport").contains(sub) && args.length > 1) {
            if (!getConfig().getBoolean("teleport.command-travel", false)) { tell(player, "Command travel is disabled. Click a waystone or use a warp scroll."); return true; }
            Stone target = findStone(args[1]);
            if (target == null) tell(player, "No unique available waystone matched that ID or name.");
            else travel(player, session(player, null, false), target);
        } else if (sub.equals("rename") && args.length > 1) {
            Block block = player.getTargetBlockExact(8); Stone stone = block == null ? null : occupied(block);
            if (stone == null || !owner(player, stone)) tell(player, "Look at a waystone you own.");
            else rename(player, stone, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        } else if (sub.equals("private") && args.length > 2) {
            Stone stone = findStone(args[1]);
            if (stone == null || !owner(player, stone)) tell(player, "You cannot manage that waystone.");
            else if (!Set.of("true", "false").contains(args[2].toLowerCase(Locale.ROOT))) tell(player, "Use true or false for privacy.");
            else { Stone updated = stone.visibility(Boolean.parseBoolean(args[2])); stones.put(stone.id(), updated); model.refresh(updated); save(); }
        } else if (Set.of("remove", "delete").contains(sub) && args.length > 1) {
            Stone stone = findStone(args[1]); if (stone != null) pickup(player, stone);
        } else tell(player, "/lumistones list | tp <id> | rename <name> | private <id> <true|false> | remove <id> | flightzone | reload");
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return List.of();
        List<String> choices = new ArrayList<>();
        if (args.length == 1) {
            choices.addAll(List.of("list", "tp", "rename", "private", "remove"));
            for (String admin : List.of("give", "reload")) if (permission(sender, "lumistones." + admin)) choices.add(admin);
            if (permission(sender, "lumistones.flightzone.admin")) choices.add("flightzone");
        } else if (Set.of("flightzone", "fz").contains(args[0].toLowerCase(Locale.ROOT))) {
            return permission(sender, "lumistones.flightzone.admin") ? flightZones.tabComplete(args) : List.of();
        } else if (args.length == 2 && Set.of("tp", "teleport", "private", "remove", "delete").contains(args[0].toLowerCase(Locale.ROOT))) {
            stones.values().stream().filter(stone -> !(sender instanceof Player player) || known(player, stone)).map(stone -> stone.id().toString()).forEach(choices::add);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give") && permission(sender, "lumistones.give")) Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(choices::add);
        else if (args.length == 3 && args[0].equalsIgnoreCase("give") && permission(sender, "lumistones.give")) choices.addAll(List.of("waystone", "scroll"));
        else if (args.length == 3 && args[0].equalsIgnoreCase("private")) choices.addAll(List.of("true", "false"));
        return choices.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))).toList();
    }
    boolean permission(CommandSender sender, String permission) {
        if (sender.isPermissionSet(permission)) return sender.hasPermission(permission);
        return sender.hasPermission(permission) ||
                (!permission.equals("lumistones.flightzone.fly") && sender.hasPermission("lumistones.admin"));
    }

    String error(String message) { return prefix() + "\u00a7c" + message; }
    String success(String message) { return prefix() + "\u00a7a" + message; }
    String info(String message) { return prefix() + "\u00a77" + message; }
    private String prefix() {
        String value = getConfig().getString("messages.prefix", "&d[Lumistones] &7");
        return TextStyles.prefix(value);
    }
    Material parseMaterial(String name, Material fallback, String setting) {
        Material material = Material.matchMaterial(name == null ? "" : name);
        if (material == null || !material.isBlock() || !material.isItem() || material.isAir() || !material.isSolid()) {
            getLogger().warning("Invalid " + setting + "; using " + fallback); return fallback;
        }
        return material;
    }
    private Material bodyMaterial(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isBlock() || material.isAir()) throw new IllegalArgumentException("Invalid body block: " + name);
        return material;
    }
    void showDialog(Player player, DialogView.MultiActionDialog dialog) {
        if (bedrockForms != null && bedrockForms.isBedrock(player)) bedrockForms.show(player, dialog); else player.showDialog(DialogView.paper(dialog));
    }
    void executeUiCommand(Player player, String command) {
        String prefix = "/lumistones ";
        if (!command.startsWith(prefix)) return;
        onCommand(player, getCommand("lumistones"), "lumistones", command.substring(prefix.length()).split(" +"));
    }
    private void list(CommandSender sender) {
        if (!permission(sender, "lumistones.use")) return;
        for (Stone stone : stones.values().stream().sorted(Comparator.comparing(Stone::name)).toList())
            if (!(sender instanceof Player player) || known(player, stone)) sender.sendMessage(info(stone.id() + " - " + stone.name() + " (" + (stone.privateAccess() ? "private" : "discoverable") + ")"));
    }
    Stone findStone(String input) {
        List<Stone> matches = stones.values().stream().filter(stone -> stone.id().toString().equalsIgnoreCase(input) || stone.name().equalsIgnoreCase(input)).toList();
        if (matches.size() == 1) return matches.getFirst();
        matches = stones.values().stream().filter(stone -> stone.id().toString().startsWith(input.toLowerCase(Locale.ROOT)) || stone.name().toLowerCase(Locale.ROOT).contains(input.toLowerCase(Locale.ROOT))).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }
    void rename(Player player, Stone stone, String value) {
        if (!owner(player, stone)) return;
        String name = TravelRules.cleanName(value); if (name.isEmpty()) { tell(player, "Enter a name containing letters or numbers."); return; }
        model.remove(stone); Stone updated = stone.named(name); stones.put(stone.id(), updated);
        if (updated.location().getWorld() != null) model.spawn(updated); save();
    }
    void pickup(Player player, Stone stone) {
        if (!owner(player, stone) || !stones.containsKey(stone.id()) || stone.location().getWorld() == null) return;
        Block block = stone.location().getBlock(); BlockBreakEvent event = new BlockBreakEvent(block, player);
        retrieving.add(player.getUniqueId());
        try { Bukkit.getPluginManager().callEvent(event); } finally { retrieving.remove(player.getUniqueId()); }
        if (event.isCancelled()) { tell(player, "You cannot remove a waystone here."); return; }
        clearAnchors(stone); remove(stone);
        player.getInventory().addItem(bodyItem(stone.bodyBlock())).values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
    }
    int minimumDistance() { return Math.max(0, getConfig().getInt("minimum-distance-blocks", 64)); }
    boolean tooClose(Location location) {
        return java.util.stream.Stream.concat(stones.values().stream(), placing.values().stream()).anyMatch(stone ->
            TravelRules.tooClose(stone.world(), stone.x(), stone.y(), stone.z(), location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), minimumDistance()));
    }
    private void playTeleportSound(Player player, Location destination) {
        String configured = getConfig().getString("teleport-sound", "ENTITY_ENDERMAN_TELEPORT");
        if (configured == null || configured.isBlank() || configured.equalsIgnoreCase("NONE")) return;
        for (Sound sound : Registry.SOUNDS) {
            NamespacedKey key = Registry.SOUNDS.getKey(sound);
            if (key != null && (key.toString().equalsIgnoreCase(configured) || key.getKey().equalsIgnoreCase(configured)
                    || key.getKey().replace('.', '_').equalsIgnoreCase(configured))) {
                player.playSound(destination, sound, 1, 1); return;
            }
        }
        getLogger().warning("Unknown teleport-sound: " + configured);
    }
    void persist() { save(); }
    StoneModel models() { return model; }
    private void tell(Player player, String message) { player.sendMessage(info(message)); }
}
