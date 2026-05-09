package ir.buddy.mint.gui;

import ir.buddy.mint.MintPlugin;
import ir.buddy.mint.module.Module;
import ir.buddy.mint.util.FoliaScheduler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ModuleToggleGui implements Listener {

    private final MintPlugin plugin;
    private final NamespacedKey openerKey;
    private final NamespacedKey virtualItemKey;
    private final Map<UUID, String> playerLayoutChoices = new HashMap<>();

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

    


    public void openFor(Player player) {
        openFor(player, null, 0, GuiView.CATEGORIES);
    }

    public void openFor(Player player, int page) {
        openFor(player, null, page, isCategoriesEnabled() ? GuiView.CATEGORIES : GuiView.MODULES);
    }

    public void openFor(Player player, String category, int page, GuiView view) {
        if (player == null || !player.isOnline()) {
            return;
        }

        openGui(player, category, page, view);
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
            openFor(player);
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

        LayoutConfig layout = resolveLayout(player, top.getSize());
        int previousSlot = layout.previousSlot();
        int nextSlot = layout.nextSlot();
        int closeSlot = layout.closeSlot();
        int backSlot = layout.backSlot();
        int layoutSwitchSlot = layout.layoutSwitchSlot();

        boolean isSinglePage = !layout.categorySlots().isEmpty();

        if (rawSlot == previousSlot) {
            if (guiHolder.page > 0) {
                openGui(player, guiHolder.categoryKey, guiHolder.page - 1, guiHolder.view);
            } else {
                syncInventory(player);
            }
            return;
        }

        if (rawSlot == nextSlot) {
            List<Module> modules = getModulesForCategory(guiHolder.categoryKey);
            List<Integer> moduleSlots = layout.moduleSlots();
            int pageSize = Math.max(1, moduleSlots.size());
            int maxPage = Math.max(0, (int) Math.ceil((double) modules.size() / pageSize) - 1);
            if (guiHolder.page < maxPage) {
                openGui(player, guiHolder.categoryKey, guiHolder.page + 1, guiHolder.view);
            } else {
                syncInventory(player);
            }
            return;
        }

        if (rawSlot == closeSlot) {
            player.closeInventory();
            return;
        }

        if (rawSlot == layoutSwitchSlot && getAvailableLayouts().size() > 1) {
            cyclePlayerLayout(player);
            openGui(player, guiHolder.categoryKey, 0, guiHolder.view);
            return;
        }

        if (rawSlot == backSlot) {
            if (!isSinglePage) {
                openGui(player, null, 0, GuiView.CATEGORIES);
            } else {
                syncInventory(player);
            }
            return;
        }

        if (isSinglePage) {
            int catIdx = layout.categorySlots().indexOf(rawSlot);
            if (catIdx >= 0) {
                List<CategoryEntry> categories = getVisibleCategories();

                if (catIdx < categories.size()) {
                    openGui(player, categories.get(catIdx).key(), 0, GuiView.MODULES);
                } else {
                    syncInventory(player);
                }
                return;
            }
        }

        if (guiHolder.view == GuiView.CATEGORIES && !isSinglePage) {
            List<Integer> contentSlots = layout.moduleSlots();
            int idxInPage = contentSlots.indexOf(rawSlot);
            if (idxInPage < 0) {
                syncInventory(player);
                return;
            }
            List<CategoryEntry> categories = getVisibleCategories();
            int categoryIndex = guiHolder.page * contentSlots.size() + idxInPage;
            if (categoryIndex < 0 || categoryIndex >= categories.size()) {
                syncInventory(player);
                return;
            }
            CategoryEntry selected = categories.get(categoryIndex);
            openGui(player, selected.key(), 0, GuiView.MODULES);
            return;
        }

        if (guiHolder.view == GuiView.MODULES) {
            List<Module> modules = getModulesForCategory(guiHolder.categoryKey);
            Integer globalIdx = guiHolder.slotToGlobalModuleIndex.get(rawSlot);
            if (globalIdx == null || globalIdx < 0 || globalIdx >= modules.size()) {
                syncInventory(player);
                return;
            }

            Module module = modules.get(globalIdx);
            boolean enabled = plugin.getPlayerModulePreferences().toggleFor(player, module);

            String msg = color(getString("messages.toggle", "&e%module% &7-> %state%"))
                    .replace("%module%", toTitleCase(module.getName()))
                    .replace("%state%", enabled
                            ? color(getString("messages.state-enabled", "&aenabled"))
                            : color(getString("messages.state-disabled", "&cdisabled")));
            player.sendMessage(msg);

            openGui(player, guiHolder.categoryKey, guiHolder.page, guiHolder.view);
            return;
        }

        syncInventory(player);
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

    private void openGui(Player player, String categoryKey, int requestedPage, GuiView requestedView) {
        GuiView view = requestedView;
        LayoutConfig layout = resolveLayout(player, -1);
        boolean isSinglePage = !layout.categorySlots().isEmpty();

        if (isSinglePage) {
            view = GuiView.MODULES;
        } else if (!isCategoriesEnabled() && view == GuiView.CATEGORIES) {
            view = GuiView.MODULES;
        }

        int rows = layout.rows();
        int size = rows * 9;
        List<Integer> contentSlots = layout.moduleSlots();
        int pageSize = Math.max(1, contentSlots.size());

        String activeCategory = categoryKey;
        if (view == GuiView.MODULES && isCategoriesEnabled()) {
            if (activeCategory == null || activeCategory.isBlank() || "all".equalsIgnoreCase(activeCategory)) {
                activeCategory = resolveDefaultCategoryKey();
            }
        }

        int entryCount = (view == GuiView.CATEGORIES && !isSinglePage)
                ? getVisibleCategories().size()
                : getModulesForCategory(activeCategory).size();
        int totalPages = Math.max(1, (int) Math.ceil((double) entryCount / pageSize));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));

        String titleTemplate = (view == GuiView.CATEGORIES && !isSinglePage)
                ? color(getString("gui.category-title", "&8Mint Categories &7(%page%/%pages%)"))
                : color(getString("gui.module-title", "&8Mint Modules &7(%category%) &8(%page%/%pages%)"));
        String title = titleTemplate
                .replace("%category%", getCategoryDisplayName(activeCategory))
                .replace("%page%", String.valueOf(page + 1))
                .replace("%pages%", String.valueOf(totalPages));

        int start = page * pageSize;

        List<Module> modulesForView = view == GuiView.MODULES
                ? getModulesForCategory(activeCategory)
                : List.of();
        Map<Integer, Integer> moduleSlotIndexMap = view == GuiView.MODULES
                ? computeModulePlacements(modulesForView, start, pageSize, contentSlots, readModuleSlotOverrides())
                : Map.of();

        ModuleGuiHolder holder = new ModuleGuiHolder(page, view, activeCategory, layout.key(), moduleSlotIndexMap);
        Inventory inv = Bukkit.createInventory(holder, size, title);
        holder.setInventory(inv);

        if (layout.backgroundEnabled()) {
            fillBackground(inv, layout.backgroundMaterial(), layout.backgroundName());
        }

        if (isSinglePage) {
            List<CategoryEntry> categories = getVisibleCategories();

            for (int i = 0; i < layout.categorySlots().size(); i++) {
                if (i >= categories.size()) {
                    break;
                }
                int slot = layout.categorySlots().get(i);
                if (slot < 0 || slot >= inv.getSize()) {
                    continue;
                }
                CategoryEntry cat = categories.get(i);
                inv.setItem(slot, buildCategoryItem(cat, cat.key().equals(activeCategory)));
            }

            for (Map.Entry<Integer, Integer> e : moduleSlotIndexMap.entrySet()) {
                int invSlot = e.getKey();
                int modIdx = e.getValue();
                if (invSlot < 0 || invSlot >= inv.getSize()) {
                    continue;
                }
                if (modIdx < 0 || modIdx >= modulesForView.size()) {
                    continue;
                }
                inv.setItem(invSlot, buildModuleItem(player, modulesForView.get(modIdx)));
            }
        } else if (view == GuiView.CATEGORIES) {
            List<CategoryEntry> categories = getVisibleCategories();
            for (int i = 0; i < pageSize; i++) {
                int categoryIndex = start + i;
                if (categoryIndex >= categories.size()) {
                    break;
                }
                int slot = contentSlots.get(i);
                if (slot < 0 || slot >= inv.getSize()) {
                    continue;
                }
                inv.setItem(slot, buildCategoryItem(categories.get(categoryIndex), false));
            }
        } else {
            for (Map.Entry<Integer, Integer> e : moduleSlotIndexMap.entrySet()) {
                int invSlot = e.getKey();
                int modIdx = e.getValue();
                if (invSlot < 0 || invSlot >= inv.getSize()) {
                    continue;
                }
                if (modIdx < 0 || modIdx >= modulesForView.size()) {
                    continue;
                }
                inv.setItem(invSlot, buildModuleItem(player, modulesForView.get(modIdx)));
            }
        }

        placeNavigation(inv, holder, totalPages, layout);
        player.openInventory(inv);
    }

    private Map<String, Integer> readModuleSlotOverrides() {
        ConfigurationSection sec = gui().getConfigurationSection("gui.module-slot-overrides");
        Map<String, Integer> map = new HashMap<>();
        if (sec == null) {
            return map;
        }
        for (String key : sec.getKeys(false)) {
            map.put(key, sec.getInt(key));
        }
        return map;
    }

    


    private Map<Integer, Integer> computeModulePlacements(
            List<Module> modules,
            int start,
            int pageSize,
            List<Integer> contentSlots,
            Map<String, Integer> overrides
    ) {
        Map<Integer, Integer> slotToIndex = new LinkedHashMap<>();
        if (modules.isEmpty() || contentSlots.isEmpty()) {
            return slotToIndex;
        }
        int end = Math.min(start + pageSize, modules.size());
        if (start >= end) {
            return slotToIndex;
        }

        Set<Integer> allowed = Set.copyOf(contentSlots);
        Set<Integer> takenSlots = new HashSet<>();
        Set<Integer> assignedIndices = new HashSet<>();

        for (int i = start; i < end; i++) {
            Module m = modules.get(i);
            String key = plugin.getModuleManager().getModuleKey(m);
            if (!overrides.containsKey(key)) {
                continue;
            }
            int s = overrides.get(key);
            if (allowed.contains(s) && !takenSlots.contains(s)) {
                slotToIndex.put(s, i);
                takenSlots.add(s);
                assignedIndices.add(i);
            }
        }

        int si = 0;
        for (int i = start; i < end; i++) {
            if (assignedIndices.contains(i)) {
                continue;
            }
            while (si < contentSlots.size() && takenSlots.contains(contentSlots.get(si))) {
                si++;
            }
            if (si >= contentSlots.size()) {
                break;
            }
            int s = contentSlots.get(si++);
            slotToIndex.put(s, i);
            takenSlots.add(s);
        }

        return slotToIndex;
    }

    


    public List<Integer> getModuleSlotsForLayout(String layoutKey) {
        String key = layoutKey == null || layoutKey.isBlank()
                ? getString("gui.default-layout", "single-page")
                : layoutKey;
        LayoutConfig cfg = loadLayoutConfig(key);
        if (cfg == null) {
            return List.of();
        }
        return new ArrayList<>(cfg.moduleSlots());
    }

    private void placeNavigation(Inventory inv, ModuleGuiHolder holder, int totalPages, LayoutConfig layout) {
        int previousSlot = layout.previousSlot();
        int nextSlot = layout.nextSlot();
        int closeSlot = layout.closeSlot();
        int backSlot = layout.backSlot();
        int layoutSwitchSlot = layout.layoutSwitchSlot();
        boolean isSinglePage = !layout.categorySlots().isEmpty();
        String layoutKey = holder.layoutKey;

        if (paginationPartEnabled(layoutKey, "previous") && previousSlot >= 0 && previousSlot < inv.getSize()) {
            Map<String, String> prevPh = new HashMap<>();
            prevPh.put("%page%", String.valueOf(Math.max(1, holder.page)));
            prevPh.put("%pages%", String.valueOf(totalPages));
            inv.setItem(previousSlot, navigationItem(
                    parseMaterial(paginationPartString(layoutKey, "previous", "material", "ARROW"), Material.ARROW),
                    color(paginationPartString(layoutKey, "previous", "name", "&ePrevious Page")),
                    paginationPartLore(layoutKey, "previous", "&7Go to page &e%page%", prevPh),
                    paginationPartGlow(layoutKey, "previous")
            ));
        }

        if (paginationPartEnabled(layoutKey, "next") && nextSlot >= 0 && nextSlot < inv.getSize()) {
            Map<String, String> nextPh = new HashMap<>();
            nextPh.put("%page%", String.valueOf(Math.min(totalPages, holder.page + 2)));
            nextPh.put("%pages%", String.valueOf(totalPages));
            inv.setItem(nextSlot, navigationItem(
                    parseMaterial(paginationPartString(layoutKey, "next", "material", "ARROW"), Material.ARROW),
                    color(paginationPartString(layoutKey, "next", "name", "&eNext Page")),
                    paginationPartLore(layoutKey, "next", "&7Go to page &e%page%", nextPh),
                    paginationPartGlow(layoutKey, "next")
            ));
        }

        if (paginationPartEnabled(layoutKey, "close") && closeSlot >= 0 && closeSlot < inv.getSize()) {
            Map<String, String> closePh = new HashMap<>();
            closePh.put("%page%", String.valueOf(holder.page + 1));
            closePh.put("%pages%", String.valueOf(totalPages));
            inv.setItem(closeSlot, navigationItem(
                    parseMaterial(paginationPartString(layoutKey, "close", "material", "BARRIER"), Material.BARRIER),
                    color(paginationPartString(layoutKey, "close", "name", "&cClose")),
                    paginationCloseLore(layoutKey, closePh),
                    paginationPartGlow(layoutKey, "close")
            ));
        }

        if (!isSinglePage && holder.view == GuiView.MODULES && isCategoriesEnabled() && backSlot >= 0 && backSlot < inv.getSize()) {
            inv.setItem(backSlot, simpleItem(
                    parseMaterial(getString("gui.navigation.back.material", "BOOK"), Material.BOOK),
                    color(getString("gui.navigation.back.name", "&bBack to Categories")),
                    loreList("gui.navigation.back.lore")
            ));
        }

        if (layoutSwitchSlot >= 0 && layoutSwitchSlot < inv.getSize()
                && getAvailableLayouts().size() > 1) {
            inv.setItem(layoutSwitchSlot, simpleItem(
                    parseMaterial(getString("gui.navigation.layout-switch.material", "COMPASS"), Material.COMPASS),
                    color(getString("gui.navigation.layout-switch.name", "&dLayout: &f%layout%")
                            .replace("%layout%", toTitleCase(layout.key().replace('-', ' ')))),
                    loreList("gui.navigation.layout-switch.lore")
            ));
        }
    }

    private ItemStack buildModuleItem(Player player, Module module) {
        boolean enabled = plugin.getPlayerModulePreferences().isPersonalModuleEnabled(player, module);
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

    private ItemStack buildCategoryItem(CategoryEntry category, boolean active) {
        String base = "gui.categories.items." + category.key();
        Material material = parseMaterial(
                getString(base + ".material", getString("gui.categories.default-material", "CHEST")),
                Material.CHEST
        );

        String name = color(getString(base + ".display-name", "&f" + toTitleCase(category.key().replace('-', ' '))));
        List<String> lore = loreList("gui.categories.item-lore").stream()
                .map(line -> line
                        .replace("%category%", name)
                        .replace("%count%", String.valueOf(category.moduleCount())))
                .collect(Collectors.toList());
        
        ItemStack item = simpleItem(material, name, lore);
        if (active) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
                item.addUnsafeEnchantment(Enchantment.LUCK_OF_THE_SEA, 1);
                ItemMeta newMeta = item.getItemMeta();
                if (newMeta != null) {
                    newMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    markVirtual(newMeta);
                    item.setItemMeta(newMeta);
                }
            }
        }
        return item;
    }

    public String toTitleCase(String value) {
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
        return plugin.getModuleManager().getPlayerScopedModules().stream()
                .filter(module -> module.isEnabledByConfig(plugin.getConfig()))
                .sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    public List<Module> getModulesForCategory(String categoryKey) {
        if (!isCategoriesEnabled()) {
            if (categoryKey == null || categoryKey.isBlank() || "all".equalsIgnoreCase(categoryKey)) {
                return getModules();
            }
        } else {
            if (categoryKey == null || categoryKey.isBlank() || "all".equalsIgnoreCase(categoryKey)) {
                String def = resolveDefaultCategoryKey();
                if (def == null) {
                    return List.of();
                }
                categoryKey = def;
            }
        }
        final String resolvedKey = categoryKey;
        return getModules().stream()
                .filter(module -> resolveModuleCategoryKey(module).equalsIgnoreCase(resolvedKey))
                .collect(Collectors.toList());
    }

    private boolean isCategoriesEnabled() {
        return getBoolean("gui.categories.enabled", true);
    }

    public String getCategoryDisplayName(String categoryKey) {
        if (categoryKey == null || categoryKey.isBlank() || "all".equalsIgnoreCase(categoryKey)) {
            if (!isCategoriesEnabled()) {
                return color(getString("gui.modules-aggregate-display", "&fModules"));
            }
            String def = resolveDefaultCategoryKey();
            if (def != null) {
                return color(getString("gui.categories.items." + def + ".display-name", toTitleCase(def.replace('-', ' '))));
            }
            return color(getString("gui.modules-empty-display", "&fModules"));
        }
        return color(getString("gui.categories.items." + categoryKey + ".display-name", toTitleCase(categoryKey.replace('-', ' '))));
    }

    private String resolveDefaultCategoryKey() {
        List<CategoryEntry> visible = getVisibleCategories();
        return visible.isEmpty() ? null : visible.get(0).key();
    }

    private String resolveModuleCategoryKey(Module module) {
        String moduleKey = plugin.getModuleManager().getModuleKey(module);
        String configured = getString("gui.categories.module-overrides." + moduleKey, "");
        if (configured != null && !configured.isBlank()) {
            return normalizeCategoryKey(configured);
        }

        String packageName = module.getClass().getPackageName();
        String[] parts = packageName.split("\\.");
        String fallback = parts.length == 0 ? "other" : parts[parts.length - 1];
        return switch (fallback.toLowerCase(Locale.ROOT)) {
            case "building", "mobility", "inventory", "interaction", "farming", "transport", "entity" -> fallback.toLowerCase(Locale.ROOT);
            default -> "other";
        };
    }

    private String normalizeCategoryKey(String value) {
        if (value == null || value.isBlank()) {
            return "other";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
    }

    public List<CategoryEntry> getVisibleCategories() {
        Map<String, Long> counts = new HashMap<>();
        for (Module module : getModules()) {
            counts.merge(resolveModuleCategoryKey(module), 1L, Long::sum);
        }

        List<String> order = new ArrayList<>();
        for (String key : gui().getStringList("gui.categories.order")) {
            if (key != null && !key.isBlank()) {
                order.add(normalizeCategoryKey(key));
            }
        }
        if (order.isEmpty()) {
            order.addAll(List.of("building", "mobility", "inventory", "interaction", "farming", "transport", "entity", "other"));
        }

        Set<String> allKeys = new LinkedHashSet<>(order);
        allKeys.addAll(counts.keySet());

        List<CategoryEntry> categories = new ArrayList<>();
        for (String key : allKeys) {
            long count = counts.getOrDefault(key, 0L);
            if (count <= 0) {
                continue;
            }
            categories.add(new CategoryEntry(key, count));
        }
        return categories;
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

    private LayoutConfig resolveLayout(Player player, int currentInventorySize) {
        String defaultLayout = getString("gui.default-layout", "single-page");
        String selected = playerLayoutChoices.getOrDefault(player.getUniqueId(), defaultLayout);
        LayoutConfig active = loadLayoutConfig(selected);
        if (active == null) {
            active = loadLayoutConfig(defaultLayout);
        }
        if (active == null) {
            active = loadLayoutConfig("single-page");
        }
        if (active == null) {
            active = emergencyLayout();
        }

        if (currentInventorySize > 0 && active.rows() * 9 != currentInventorySize) {
            return new LayoutConfig(
                    active.key(),
                    clamp(currentInventorySize / 9, 1, 6),
                    active.moduleSlots(),
                    active.categorySlots(),
                    active.previousSlot(),
                    active.nextSlot(),
                    active.closeSlot(),
                    active.backSlot(),
                    active.layoutSwitchSlot(),
                    active.backgroundEnabled(),
                    active.backgroundMaterial(),
                    active.backgroundName()
            );
        }
        return active;
    }

    private void cyclePlayerLayout(Player player) {
        List<String> available = getAvailableLayouts();
        if (available.size() <= 1) {
            return;
        }
        String current = playerLayoutChoices.getOrDefault(player.getUniqueId(), getString("gui.default-layout", "single-page"));
        int idx = available.indexOf(current);
        int next = idx < 0 ? 0 : (idx + 1) % available.size();
        playerLayoutChoices.put(player.getUniqueId(), available.get(next));
    }

    private List<String> getAvailableLayouts() {
        List<String> order = gui().getStringList("gui.layouts.order").stream()
                .filter(v -> v != null && !v.isBlank())
                .collect(Collectors.toCollection(ArrayList::new));
        if (order.isEmpty()) {
            order.add("single-page");
        }
        return order;
    }

    private LayoutConfig loadLayoutConfig(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String base = "gui.layouts." + key;
        if (!gui().isConfigurationSection(base)) {
            return null;
        }

        int rows = clamp(getInt(base + ".rows", 6), 1, 6);
        int size = rows * 9;
        int previousSlot = getInt(base + ".pagination.previous-slot", getInt("gui.pagination.slots.previous", 45));
        int nextSlot = getInt(base + ".pagination.next-slot", getInt("gui.pagination.slots.next", 53));
        int closeSlot = getInt(base + ".pagination.close-slot", getInt("gui.pagination.slots.close", 49));
        int backSlot = getInt(base + ".navigation.back-slot", 48);
        int layoutSwitchSlot = getInt(base + ".navigation.layout-switch-slot", 50);

        List<Integer> categorySlots = new ArrayList<>();
        if (gui().contains(base + ".category-slots")) {
            for (Integer slot : gui().getIntegerList(base + ".category-slots")) {
                if (slot != null && slot >= 0 && slot < size && !categorySlots.contains(slot)) {
                    categorySlots.add(slot);
                }
            }
        }

        List<Integer> configured = new ArrayList<>();
        for (Integer slot : gui().getIntegerList(base + ".module-slots")) {
            if (slot == null || slot < 0 || slot >= size) {
                continue;
            }
            if (slot == previousSlot || slot == nextSlot || slot == closeSlot || slot == backSlot || slot == layoutSwitchSlot || categorySlots.contains(slot)) {
                continue;
            }
            if (!configured.contains(slot)) {
                configured.add(slot);
            }
        }

        if (configured.isEmpty()) {
            for (int i = 0; i < size; i++) {
                if (i == previousSlot || i == nextSlot || i == closeSlot || i == backSlot || i == layoutSwitchSlot || categorySlots.contains(i)) {
                    continue;
                }
                configured.add(i);
            }
        }

        boolean backgroundEnabled = getBoolean(base + ".background.enabled", getBoolean("gui.background.enabled", true));
        Material backgroundMaterial = parseMaterial(
                getString(base + ".background.material", getString("gui.background.material", "BLACK_STAINED_GLASS_PANE")),
                Material.BLACK_STAINED_GLASS_PANE
        );
        String backgroundName = color(getString(base + ".background.name", getString("gui.background.name", " ")));

        return new LayoutConfig(
                key,
                rows,
                configured,
                categorySlots,
                previousSlot,
                nextSlot,
                closeSlot,
                backSlot,
                layoutSwitchSlot,
                backgroundEnabled,
                backgroundMaterial,
                backgroundName
        );
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

    private boolean paginationPartEnabled(String layoutKey, String part) {
        String layoutPath = "gui.layouts." + layoutKey + ".pagination." + part + ".enabled";
        if (gui().isSet(layoutPath)) {
            return gui().getBoolean(layoutPath);
        }
        return gui().getBoolean("gui.pagination." + part + ".enabled", true);
    }

    private String paginationPartString(String layoutKey, String part, String field, String def) {
        String layoutPath = "gui.layouts." + layoutKey + ".pagination." + part + "." + field;
        if (gui().isSet(layoutPath)) {
            return gui().getString(layoutPath, def);
        }
        return getString("gui.pagination." + part + "." + field, def);
    }

    private boolean paginationPartGlow(String layoutKey, String part) {
        String layoutPath = "gui.layouts." + layoutKey + ".pagination." + part + ".glow";
        if (gui().isSet(layoutPath)) {
            return gui().getBoolean(layoutPath);
        }
        return gui().getBoolean("gui.pagination." + part + ".glow", false);
    }

    private List<String> yamlStringOrListLore(String path) {
        List<String> lines = new ArrayList<>(gui().getStringList(path));
        if (!lines.isEmpty()) {
            return lines;
        }
        String single = gui().getString(path);
        if (single != null && !single.isBlank()) {
            lines.add(single);
        }
        return lines;
    }

    private List<String> paginationPartLore(String layoutKey, String part, String defLine, Map<String, String> repl) {
        List<String> lines = new ArrayList<>(yamlStringOrListLore("gui.layouts." + layoutKey + ".pagination." + part + ".lore"));
        if (lines.isEmpty()) {
            lines = new ArrayList<>(yamlStringOrListLore("gui.pagination." + part + ".lore"));
        }
        if (lines.isEmpty()) {
            lines.add(defLine);
        }
        return lines.stream()
                .map(this::color)
                .map(line -> replacePaginationTokens(line, repl))
                .collect(Collectors.toList());
    }

    private List<String> paginationCloseLore(String layoutKey, Map<String, String> repl) {
        List<String> lines = new ArrayList<>(yamlStringOrListLore("gui.layouts." + layoutKey + ".pagination.close.lore"));
        if (lines.isEmpty()) {
            lines = new ArrayList<>(yamlStringOrListLore("gui.pagination.close.lore"));
        }
        if (lines.isEmpty()) {
            lines.add("&7Close");
        }
        return lines.stream()
                .map(this::color)
                .map(line -> replacePaginationTokens(line, repl))
                .collect(Collectors.toList());
    }

    private static String replacePaginationTokens(String line, Map<String, String> repl) {
        String out = line;
        for (Map.Entry<String, String> e : repl.entrySet()) {
            out = out.replace(e.getKey(), e.getValue());
        }
        return out;
    }

    private ItemStack navigationItem(Material material, String name, List<String> lore, boolean glow) {
        ItemStack item = simpleItem(material, name, lore);
        if (!glow) {
            return item;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        item.addUnsafeEnchantment(Enchantment.LUCK_OF_THE_SEA, 1);
        ItemMeta m2 = item.getItemMeta();
        if (m2 != null) {
            m2.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            markVirtual(m2);
            item.setItemMeta(m2);
        }
        return item;
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

    public enum GuiView {
        CATEGORIES,
        MODULES
    }

    public record CategoryEntry(String key, long moduleCount) {}

    private record LayoutConfig(
            String key,
            int rows,
            List<Integer> moduleSlots,
            List<Integer> categorySlots,
            int previousSlot,
            int nextSlot,
            int closeSlot,
            int backSlot,
            int layoutSwitchSlot,
            boolean backgroundEnabled,
            Material backgroundMaterial,
            String backgroundName
    ) {
    }

    


    private LayoutConfig emergencyLayout() {
        List<Integer> categorySlots = new ArrayList<>(List.of(0, 9, 18, 27, 36, 45, 1, 10, 19));
        List<Integer> moduleSlots = new ArrayList<>(List.of(
                3, 4, 5, 6, 7, 8,
                12, 13, 14, 15, 16, 17,
                21, 22, 23, 24, 25, 26,
                30, 31, 32, 33, 34, 35,
                39, 40, 41, 42, 43, 44
        ));
        return new LayoutConfig(
                "single-page",
                6,
                moduleSlots,
                categorySlots,
                48,
                50,
                49,
                -1,
                -1,
                true,
                Material.BLACK_STAINED_GLASS_PANE,
                color(getString("gui.background.name", " "))
        );
    }

    private static final class ModuleGuiHolder implements InventoryHolder {
        private final int page;
        private final GuiView view;
        private final String categoryKey;
        private final String layoutKey;
        
        private final Map<Integer, Integer> slotToGlobalModuleIndex;
        private Inventory inventory;

        private ModuleGuiHolder(
                int page,
                GuiView view,
                String categoryKey,
                String layoutKey,
                Map<Integer, Integer> slotToGlobalModuleIndex
        ) {
            this.page = page;
            this.view = view;
            this.categoryKey = categoryKey;
            this.layoutKey = layoutKey;
            this.slotToGlobalModuleIndex = Map.copyOf(slotToGlobalModuleIndex);
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
