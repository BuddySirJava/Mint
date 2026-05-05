package ir.buddy.mint.gui;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ModuleToggleGui implements Listener {

    private final MintPlugin plugin;
    private final NamespacedKey openerKey;
    private final NamespacedKey virtualItemKey;

    public ModuleToggleGui(MintPlugin plugin) {
        this.plugin = plugin;
        this.openerKey = new NamespacedKey(plugin, "module_gui_opener");
        this.virtualItemKey = new NamespacedKey(plugin, "module_gui_virtual");
    }

    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            FoliaScheduler.runEntity(plugin, player, () -> updateCraftingOpener(player));
        }
    }

    public void reload() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            FoliaScheduler.runEntity(plugin, player, () -> updateCraftingOpener(player));
        }
    }

    /**
     * Opens the paginated module GUI (same layout as the crafting-table opener).
     */
    public void openFor(Player player) {
        openFor(player, 0);
    }

    public void openFor(Player player, int page) {
        if (player == null || !player.isOnline()) {
            return;
        }
        openGui(player, page);
    }

    public void stop() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            FoliaScheduler.runEntity(plugin, player, () -> {
                clearCraftingOpener(player);
                if (isVirtualGuiItem(player.getItemOnCursor())) {
                    player.setItemOnCursor(null);
                }
                player.updateInventory();
            });
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        FoliaScheduler.runEntityLater(plugin, event.getPlayer(), 2L, () -> updateCraftingOpener(event.getPlayer()));
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        FoliaScheduler.runEntity(plugin, player, () -> updateCraftingOpener(player));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();

        if (isVirtualGuiItem(cursor)) {
            event.setCancelled(true);
            player.setItemOnCursor(null);
            syncInventory(player);
            return;
        }

        if (isModuleGui(top)) {
            handleModuleGuiClick(event, player);
            return;
        }

        // Crafting-table opener uses client quirks that we only support outside creative.
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (isVirtualGuiItem(current) && !isOpenerItem(current)) {
            event.setCancelled(true);
            event.setCurrentItem(null);
            syncInventory(player);
            FoliaScheduler.runEntity(plugin, player, () -> updateCraftingOpener(player));
            return;
        }

        if (!isCraftingOpenerEnabled()) {
            clearCraftingOpener(player);
            return;
        }

        if (view.getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            return;
        }

        if (event.getRawSlot() != 0) {
            return;
        }

        ItemStack resultSlot = top.getItem(0);
        ItemStack clicked = resultSlot != null ? resultSlot : current;

        if (isOpenerItem(clicked)) {
            event.setCancelled(true);

            if (!player.hasPermission(getString("crafting-opener.permission", "mint.gui"))) {
                clearCraftingOpener(player);
                syncInventory(player);
                return;
            }

            player.setItemOnCursor(null);
            openGui(player, 0);
            syncInventory(player);
            return;
        }

        if (isVirtualGuiItem(clicked)) {
            event.setCancelled(true);
            FoliaScheduler.runEntity(plugin, player, () -> updateCraftingOpener(player));
            syncInventory(player);
        }

    }

    private void handleModuleGuiClick(InventoryClickEvent event, Player player) {
        InventoryView view = event.getView();
        Inventory top = view.getTopInventory();
        int topSize = top.getSize();
        int rawSlot = event.getRawSlot();

        ClickType click = event.getClick();
        InventoryAction action = event.getAction();

        if (event.isShiftClick()
                || click == ClickType.NUMBER_KEY
                || click == ClickType.DOUBLE_CLICK
                || click == ClickType.DROP
                || click == ClickType.CONTROL_DROP
                || action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.HOTBAR_MOVE_AND_READD
                || action == InventoryAction.HOTBAR_SWAP) {
            event.setCancelled(true);
            syncInventory(player);
            return;
        }

        if (rawSlot < 0 || rawSlot >= topSize) {
            if (isVirtualGuiItem(event.getCurrentItem())) {
                event.setCancelled(true);
                event.setCurrentItem(null);
                syncInventory(player);
            }
            return;
        }

        event.setCancelled(true);

        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof ModuleGuiHolder guiHolder)) {
            syncInventory(player);
            return;
        }

        int previousSlot = getInt("gui.pagination.previous-slot", 45);
        int nextSlot = getInt("gui.pagination.next-slot", 53);
        int closeSlot = getInt("gui.pagination.close-slot", 49);

        if (rawSlot == previousSlot) {
            if (guiHolder.page > 0) {
                openGui(player, guiHolder.page - 1);
            } else {
                syncInventory(player);
            }
            return;
        }

        if (rawSlot == nextSlot) {
            List<Module> modules = getModules();
            List<Integer> moduleSlots = getModuleSlots();
            int pageSize = Math.max(1, moduleSlots.size());
            int maxPage = Math.max(0, (int) Math.ceil((double) modules.size() / pageSize) - 1);
            if (guiHolder.page < maxPage) {
                openGui(player, guiHolder.page + 1);
            } else {
                syncInventory(player);
            }
            return;
        }

        if (rawSlot == closeSlot) {
            player.closeInventory();
            return;
        }

        List<Integer> moduleSlots = getModuleSlots();
        int idxInPage = moduleSlots.indexOf(rawSlot);
        if (idxInPage < 0) {
            syncInventory(player);
            return;
        }

        int moduleIndex = guiHolder.page * moduleSlots.size() + idxInPage;
        List<Module> modules = getModules();
        if (moduleIndex < 0 || moduleIndex >= modules.size()) {
            syncInventory(player);
            return;
        }

        Module module = modules.get(moduleIndex);
        boolean enabled = plugin.getPlayerModulePreferences().toggleFor(player, module);

        String msg = color(getString("messages.toggle", "&e%module% &7-> %state%"))
                .replace("%module%", toTitleCase(module.getName()))
                .replace("%state%", enabled
                        ? color(getString("messages.state-enabled", "&aenabled"))
                        : color(getString("messages.state-disabled", "&cdisabled")));
        player.sendMessage(msg);

        openGui(player, guiHolder.page);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (isVirtualGuiItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            refreshOpenerAfterClose(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClickMonitor(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (event.getView().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            return;
        }
        FoliaScheduler.runEntity(plugin, player, () -> updateCraftingOpener(player));
    }


    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (isVirtualGuiItem(event.getOldCursor())) {
            event.setCancelled(true);
            player.setItemOnCursor(null);
            syncInventory(player);
            return;
        }

        if (isModuleGui(event.getView().getTopInventory())) {
            int topSize = event.getView().getTopInventory().getSize();
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot >= 0 && rawSlot < topSize) {
                    event.setCancelled(true);
                    syncInventory(player);
                    return;
                }
            }
            return;
        }

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        if (!isCraftingOpenerEnabled()) {
            clearCraftingOpener(player);
            return;
        }

        if (event.getView().getType() == org.bukkit.event.inventory.InventoryType.CRAFTING) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot == 0) {
                    ItemStack result = event.getView().getTopInventory().getItem(0);
                    if (isOpenerItem(result) || isVirtualGuiItem(result)) {
                        event.setCancelled(true);
                        syncInventory(player);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryDragMonitor(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (event.getView().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            return;
        }
        FoliaScheduler.runEntity(plugin, player, () -> updateCraftingOpener(player));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (!isVirtualGuiItem(stack)) {
            return;
        }
        event.setCancelled(true);
        event.getItemDrop().remove();
    }

    private void openGui(Player player, int requestedPage) {
        List<Module> modules = getModules();
        List<Integer> moduleSlots = getModuleSlots();
        int pageSize = Math.max(1, moduleSlots.size());

        int rows = clamp(getInt("gui.rows", 6), 1, 6);
        int size = rows * 9;

        int totalPages = Math.max(1, (int) Math.ceil((double) modules.size() / pageSize));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        String titleTemplate = color(getString("gui.title", "&8Mint Modules &7(%page%/%pages%)"));
        String title = titleTemplate
                .replace("%page%", String.valueOf(page + 1))
                .replace("%pages%", String.valueOf(totalPages));

        ModuleGuiHolder holder = new ModuleGuiHolder(page);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        if (getBoolean("gui.background.enabled", true)) {
            fillBackground(
                    inv,
                    parseMaterial(getString("gui.background.material", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE),
                    color(getString("gui.background.name", " "))
            );
        }

        int start = page * pageSize;
        for (int i = 0; i < pageSize; i++) {
            int moduleIndex = start + i;
            if (moduleIndex >= modules.size()) {
                break;
            }

            int slot = moduleSlots.get(i);
            if (slot < 0 || slot >= inv.getSize()) {
                continue;
            }

            inv.setItem(slot, buildModuleItem(player, modules.get(moduleIndex)));
        }

        placePagination(inv, page, totalPages);
        player.openInventory(inv);
    }

    private void placePagination(Inventory inv, int page, int totalPages) {
        int previousSlot = getInt("gui.pagination.previous-slot", 45);
        int nextSlot = getInt("gui.pagination.next-slot", 53);
        int closeSlot = getInt("gui.pagination.close-slot", 49);

        if (previousSlot >= 0 && previousSlot < inv.getSize()) {
            inv.setItem(previousSlot, simpleItem(
                    parseMaterial(getString("gui.pagination.previous.material", "ARROW"), Material.ARROW),
                    color(getString("gui.pagination.previous.name", "&ePrevious Page")),
                    List.of(color(getString("gui.pagination.previous.lore", "&7Go to page &e%page%"))
                            .replace("%page%", String.valueOf(Math.max(1, page))))
            ));
        }

        if (nextSlot >= 0 && nextSlot < inv.getSize()) {
            inv.setItem(nextSlot, simpleItem(
                    parseMaterial(getString("gui.pagination.next.material", "ARROW"), Material.ARROW),
                    color(getString("gui.pagination.next.name", "&eNext Page")),
                    List.of(color(getString("gui.pagination.next.lore", "&7Go to page &e%page%"))
                            .replace("%page%", String.valueOf(Math.min(totalPages, page + 2))))
            ));
        }

        if (closeSlot >= 0 && closeSlot < inv.getSize()) {
            inv.setItem(closeSlot, simpleItem(
                    parseMaterial(getString("gui.pagination.close.material", "BARRIER"), Material.BARRIER),
                    color(getString("gui.pagination.close.name", "&cClose")),
                    loreList("gui.pagination.close.lore")
            ));
        }
    }

    private ItemStack buildModuleItem(Player player, Module module) {
        boolean enabled = plugin.getPlayerModulePreferences().isEnabledFor(player, module);
        String moduleKey = plugin.getModuleManager().getModuleKey(module);

        String base = enabled ? "gui.module-item.enabled" : "gui.module-item.disabled";
        Material fallbackMaterial = parseMaterial(
                getString(base + ".material", enabled ? "LIME_DYE" : "GRAY_DYE"),
                enabled ? Material.LIME_DYE : Material.GRAY_DYE
        );
        Material material = parseMaterial(
                getString("gui.module-icons." + moduleKey, fallbackMaterial.name()),
                fallbackMaterial
        );

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String state = enabled
                ? getString("messages.state-enabled", "&aenabled")
                : getString("messages.state-disabled", "&cdisabled");

        String name = color(getString(base + ".name", enabled ? "&a%module%" : "&c%module%"))
                .replace("%module%", toTitleCase(module.getName()))
                .replace("%description%", module.getDescription() == null ? "" : module.getDescription())
                .replace("%state%", color(state));

        meta.setDisplayName(name);

        List<String> loreTemplate = loreList(base + ".lore");
        List<String> lore = new ArrayList<>();

        if (getBoolean("gui.module-item.description.enabled", true)) {
            int maxChars = Math.max(10, getInt("gui.module-item.description.max-chars-per-line", 36));
            List<String> wrapped = wrapDescription(module.getDescription(), maxChars);
            String firstPrefix = color(getString("gui.module-item.description.first-line-prefix", "&7Description: &f"));
            String nextPrefix = color(getString("gui.module-item.description.next-line-prefix", "&8- &f"));
            for (int i = 0; i < wrapped.size(); i++) {
                lore.add((i == 0 ? firstPrefix : nextPrefix) + wrapped.get(i));
            }
        }

        for (String line : loreTemplate) {
            lore.add(color(line)
                    .replace("%module%", toTitleCase(module.getName()))
                    .replace("%description%", module.getDescription() == null ? "" : module.getDescription())
                    .replace("%state%", color(state)));
        }

        meta.setLore(lore);
        markVirtual(meta);

        if (enabled && getBoolean("gui.module-item.enabled.glow", true)) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
            item.addUnsafeEnchantment(Enchantment.LUCK_OF_THE_SEA, 1);
            ItemMeta newMeta = item.getItemMeta();
            if (newMeta != null) {
                newMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                markVirtual(newMeta);
                item.setItemMeta(newMeta);
            }
            return item;
        }

        item.setItemMeta(meta);
        return item;
    }

    private String toTitleCase(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String[] words = value.trim().split("\\s+");
        List<String> normalized = new ArrayList<>(words.length);
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            normalized.add(Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT));
        }
        return String.join(" ", normalized);
    }

    private void fillBackground(Inventory inv, Material material, String name) {
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            markVirtual(meta);
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    private ItemStack simpleItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            markVirtual(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void updateCraftingOpener(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        boolean enabled = isCraftingOpenerEnabled();
        String permission = getString("crafting-opener.permission", "mint.gui");
        if (!enabled || !player.hasPermission(permission) || player.getGameMode() == GameMode.CREATIVE) {
            clearCraftingOpener(player);
            return;
        }

        InventoryView view = player.getOpenInventory();
        if (view == null || view.getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            return;
        }

        Inventory top = view.getTopInventory();
        if (top == null || top.getSize() < 5) {
            return;
        }

        ItemStack current = top.getItem(0);
        boolean onlyWhenEmpty = getBoolean("crafting-opener.inject-only-when-result-empty", true);

        if (hasCraftingInputsOrCursor(player, top)) {
            clearCraftingOpener(player);
            return;
        }

        if (current != null && !current.getType().isAir() && !isOpenerItem(current)) {
            if (onlyWhenEmpty) {
                return;
            }
            return;
        }

        if (isOpenerItem(current)) {
            return;
        }

        top.setItem(0, buildOpenerItem());
    }

    private void clearCraftingOpener(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        InventoryView view = player.getOpenInventory();
        if (view == null || view.getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) {
            return;
        }

        Inventory top = view.getTopInventory();
        if (top == null || top.getSize() < 1) {
            return;
        }

        ItemStack current = top.getItem(0);
        if (isOpenerItem(current)) {
            top.setItem(0, null);
        }
    }

    private boolean hasCraftingInputsOrCursor(Player player, Inventory top) {
        for (int slot = 1; slot <= 4; slot++) {
            ItemStack stack = top.getItem(slot);
            if (stack != null && !stack.getType().isAir()) {
                return true;
            }
        }

        ItemStack cursor = player.getItemOnCursor();
        return cursor != null && !cursor.getType().isAir();
    }

    private ItemStack buildOpenerItem() {
        Material material = parseMaterial(getString("crafting-opener.item.material", "BOOK"), Material.BOOK);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName(color(getString("crafting-opener.item.name", "&6Mint Modules")));
        meta.setLore(loreList("crafting-opener.item.lore"));
        meta.getPersistentDataContainer().set(openerKey, PersistentDataType.BYTE, (byte) 1);
        markVirtual(meta);

        if (getBoolean("crafting-opener.item.glow", true)) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
            item.addUnsafeEnchantment(Enchantment.LUCK_OF_THE_SEA, 1);
            ItemMeta newMeta = item.getItemMeta();
            if (newMeta != null) {
                newMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                newMeta.getPersistentDataContainer().set(openerKey, PersistentDataType.BYTE, (byte) 1);
                markVirtual(newMeta);
                item.setItemMeta(newMeta);
            }
            return item;
        }

        item.setItemMeta(meta);
        return item;
    }

    private void markVirtual(ItemMeta meta) {
        meta.getPersistentDataContainer().set(virtualItemKey, PersistentDataType.BYTE, (byte) 1);
    }

    private boolean isOpenerItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(openerKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private boolean isVirtualGuiItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte marker = meta.getPersistentDataContainer().get(virtualItemKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private boolean isModuleGui(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof ModuleGuiHolder;
    }

    private List<Module> getModules() {
        return plugin.getModuleManager().getModules().stream()
                .filter(module -> module.isEnabledByConfig(plugin.getConfig()))
                .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private List<String> wrapDescription(String input, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (input == null || input.isBlank()) {
            return lines;
        }

        String[] words = input.trim().split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (current.length() == 0) {
                current.append(word);
                continue;
            }

            if (current.length() + 1 + word.length() <= maxChars) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }

        if (current.length() > 0) {
            lines.add(current.toString());
        }

        return lines;
    }

    private List<Integer> getModuleSlots() {
        int rows = clamp(getInt("gui.rows", 6), 1, 6);
        int size = rows * 9;

        int previousSlot = getInt("gui.pagination.previous-slot", 45);
        int nextSlot = getInt("gui.pagination.next-slot", 53);
        int closeSlot = getInt("gui.pagination.close-slot", 49);

        List<Integer> configured = new ArrayList<>();
        for (Integer slot : plugin.getGuiConfig().getIntegerList("gui.module-slots")) {
            if (slot == null) {
                continue;
            }
            if (slot < 0 || slot >= size) {
                continue;
            }
            if (slot == previousSlot || slot == nextSlot || slot == closeSlot) {
                continue;
            }
            if (!configured.contains(slot)) {
                configured.add(slot);
            }
        }

        if (!configured.isEmpty()) {
            return configured;
        }

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (i == previousSlot || i == nextSlot || i == closeSlot) {
                continue;
            }
            slots.add(i);
        }

        return slots;
    }

    private boolean isCraftingOpenerEnabled() {
        return getBoolean("crafting-opener.enabled", true);
    }

    private FileConfiguration gui() {
        return plugin.getGuiConfig();
    }

    private String getString(String path, String def) {
        return gui().getString(path, def);
    }

    private boolean getBoolean(String path, boolean def) {
        return gui().getBoolean(path, def);
    }

    private int getInt(String path, int def) {
        return gui().getInt(path, def);
    }

    private List<String> loreList(String path) {
        List<String> lines = gui().getStringList(path);
        if (lines.isEmpty()) {
            return List.of();
        }
        return lines.stream().map(this::color).collect(Collectors.toList());
    }

    private String color(String input) {
        return input == null ? "" : ChatColor.translateAlternateColorCodes('&', input);
    }

    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return material != null ? material : fallback;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void syncInventory(Player player) {
        FoliaScheduler.runEntity(plugin, player, player::updateInventory);
    }

    private void refreshOpenerAfterClose(Player player) {
        if (!isCraftingOpenerEnabled()) {
            return;
        }

        FoliaScheduler.runEntityLater(plugin, player, 1L, () -> {
            updateCraftingOpener(player);
            player.updateInventory();
        });

        FoliaScheduler.runEntityLater(plugin, player, 5L, () -> {
            updateCraftingOpener(player);
            player.updateInventory();
        });
    }

    private static final class ModuleGuiHolder implements InventoryHolder {
        private final int page;
        private Inventory inventory;

        private ModuleGuiHolder(int page) {
            this.page = page;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
