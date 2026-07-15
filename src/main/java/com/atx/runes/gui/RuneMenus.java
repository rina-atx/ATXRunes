package com.atx.runes.gui;

import com.atx.runes.ATXRunesPlugin;
import com.atx.runes.rune.RuneInstance;
import com.atx.runes.trade.TradeSession;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public final class RuneMenus {
    private final ATXRunesPlugin plugin;
    private final RuneItemFactory items;
    private final GuiConfig gui;

    public RuneMenus(ATXRunesPlugin plugin) {
        this.plugin = plugin;
        this.items = new RuneItemFactory(plugin);
        this.gui = new GuiConfig(plugin);
    }

    public void openMain(Player player) {
        Inventory inventory = Bukkit.createInventory(new RuneGuiHolder(RuneGuiType.MAIN, player.getUniqueId(), null), gui.size("main", 27), gui.title("main", "&5Runes"));
        set(inventory, gui.slot("main.buttons.slots.slot", 10), button("main.buttons.slots", Material.NETHER_STAR, "&bRune Slots", List.of("&7Equip passive runes.")));
        set(inventory, gui.slot("main.buttons.forge.slot", 12), button("main.buttons.forge", Material.ANVIL, "&aForge Runes", List.of("&7Create a new rune.")));
        set(inventory, gui.slot("main.buttons.reforge.slot", 14), button("main.buttons.reforge", Material.SMITHING_TABLE, "&6Re-forge Runes", List.of("&7Improve or reroll a rune.")));
        set(inventory, gui.slot("main.buttons.storage.slot", 16), button("main.buttons.storage", Material.ENDER_CHEST, "&dRune Storage", List.of("&7View stored runes.")));
        set(inventory, gui.slot("main.buttons.trade.slot", 22), button("main.buttons.trade", Material.EMERALD, "&eRune Trading", List.of("&7Use /runes trade <player>.")));
        player.openInventory(inventory);
    }

    public void openSlots(Player player) {
        Inventory inventory = Bukkit.createInventory(new RuneGuiHolder(RuneGuiType.SLOTS, player.getUniqueId(), null), gui.size("slots", 54), gui.title("slots", "&5Rune Slots"));
        Map<Integer, RuneInstance> equipped = plugin.getRuneStorage().getEquipped(player.getUniqueId());
        int maxSlots = plugin.maxRuneSlots();
        List<Integer> equippedSlots = equippedSlots();
        for (int index = 0; index < maxSlots; index++) {
            if (index >= equippedSlots.size()) {
                break;
            }
            RuneInstance rune = equipped.get(index);
            set(inventory, equippedSlots.get(index), rune == null
                ? button("slots.empty-slot-item", Material.GRAY_STAINED_GLASS_PANE, "&7Empty Rune Slot", List.of("&8Click a stored rune below to equip."))
                : items.runeItem(rune));
        }
        placeRunes(inventory, storageSlots("slots.storage-slots", 9, plugin.maxStorageSize()), plugin.getRuneStorage().getStorage(player.getUniqueId()), plugin.maxStorageSize());
        player.openInventory(inventory);
    }

    public void openStorage(Player player) {
        Inventory inventory = Bukkit.createInventory(new RuneGuiHolder(RuneGuiType.STORAGE, player.getUniqueId(), null), gui.size("storage", 54), gui.title("storage", "&5Rune Storage"));
        placeRunes(inventory, storageSlots("storage.storage-slots", 0, plugin.maxStorageSize()), plugin.getRuneStorage().getStorage(player.getUniqueId()), plugin.maxStorageSize());
        set(inventory, gui.slot("storage.buttons.back.slot", 49), button("storage.buttons.back", Material.ARROW, "&eBack", List.of()));
        player.openInventory(inventory);
    }

    public void openForge(Player player) {
        String material = plugin.getConfig().getString("forging.cost.material", "AMETHYST_SHARD");
        int amount = plugin.getConfig().getInt("forging.cost.amount", 8);
        Inventory inventory = Bukkit.createInventory(new RuneGuiHolder(RuneGuiType.FORGE, player.getUniqueId(), null), gui.size("forge", 27), gui.title("forge", "&5Forge Runes"));
        set(inventory, gui.slot("forge.buttons.forge.slot", 11), button("forge.buttons.forge", Material.ANVIL, "&aForge Random Rune", List.of(
            "&7Cost: " + amount + "x " + material,
            "&8Creates a tier 1 rune."
        )));
        set(inventory, gui.slot("forge.buttons.info.slot", 15), button("forge.buttons.info", Material.BOOK, "&bTier Progression", List.of("&7Tier 1 -> Tier 7")));
        set(inventory, gui.slot("forge.buttons.back.slot", 22), button("forge.buttons.back", Material.ARROW, "&eBack", List.of()));
        player.openInventory(inventory);
    }

    public void openReforge(Player player) {
        Inventory inventory = Bukkit.createInventory(new RuneGuiHolder(RuneGuiType.REFORGE, player.getUniqueId(), null), gui.size("reforge", 54), gui.title("reforge", "&5Re-forge Runes"));
        placeRunes(inventory, storageSlots("reforge.storage-slots", 0, plugin.maxStorageSize()), plugin.getRuneStorage().getStorage(player.getUniqueId()), plugin.maxStorageSize());
        set(inventory, gui.slot("reforge.buttons.info.slot", 49), button("reforge.buttons.info", Material.SMITHING_TABLE, "&6Pick a Rune", List.of("&7Click a stored rune to upgrade it.")));
        player.openInventory(inventory);
    }

    public void openTrade(Player player, Player target, TradeSession session) {
        Inventory inventory = Bukkit.createInventory(new RuneGuiHolder(RuneGuiType.TRADE, player.getUniqueId(), target.getUniqueId()), gui.size("trade", 54),
            gui.title("trade", "&5Trade with " + target.getName()).replace("{target}", target.getName()));
        session.offerFor(player.getUniqueId())
            .flatMap(id -> plugin.getRuneStorage().findStored(player.getUniqueId(), id))
            .ifPresent(rune -> set(inventory, gui.slot("trade.offer-slot", 20), items.runeItem(rune)));
        session.offerAgainst(player.getUniqueId())
            .flatMap(id -> plugin.getRuneStorage().findStored(target.getUniqueId(), id))
            .ifPresent(rune -> set(inventory, gui.slot("trade.target-offer-slot", 24), items.runeItem(rune)));
        set(inventory, gui.slot("trade.accept-slot", 22), items.button(session.isAccepted(player.getUniqueId()) ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
            session.isAccepted(player.getUniqueId()) ? "&aAccepted" : "&eAccept Trade",
            List.of("&7Both players must offer and accept.")));
        placeRunes(inventory, storageSlots("trade.storage-slots", 27, 27), plugin.getRuneStorage().getStorage(player.getUniqueId()), 27);
        set(inventory, gui.slot("trade.labels.your-offer.slot", 4), button("trade.labels.your-offer", Material.EMERALD, "&aYour Offer", List.of("&7Select from storage below.")));
        set(inventory, gui.slot("trade.labels.target-offer.slot", 8), button("trade.labels.target-offer", Material.EMERALD, "&b" + target.getName() + "'s Offer", List.of("&7Updates as they choose."), target.getName()));
        player.openInventory(inventory);
    }

    public ItemStack renderRune(RuneInstance rune) {
        return items.runeItem(rune);
    }

    public void giveOrDropRune(Player player, RuneInstance rune) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(renderRune(rune));
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        if (!leftovers.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Your inventory was full, so the rune was dropped nearby.");
        }
    }

    public GuiConfig gui() {
        return gui;
    }

    public List<Integer> equippedSlots() {
        return gui.slots("slots.equipped-slots", gui.range(0, plugin.maxRuneSlots()));
    }

    public List<Integer> storageSlots(String path, int fallbackStart, int fallbackAmount) {
        return gui.slots(path, gui.range(fallbackStart, fallbackAmount));
    }

    private void placeRunes(Inventory inventory, List<Integer> slots, List<RuneInstance> runes, int maxRunes) {
        int limit = Math.min(Math.min(runes.size(), maxRunes), slots.size());
        for (int index = 0; index < limit; index++) {
            set(inventory, slots.get(index), items.runeItem(runes.get(index)));
        }
    }

    private ItemStack button(String path, Material fallbackMaterial, String fallbackName, List<String> fallbackLore) {
        return button(path, fallbackMaterial, fallbackName, fallbackLore, null);
    }

    private ItemStack button(String path, Material fallbackMaterial, String fallbackName, List<String> fallbackLore, String targetName) {
        String base = "guis." + path + ".";
        Material material = Material.matchMaterial(plugin.getConfig().getString(base + "material", fallbackMaterial.name()));
        if (material == null) {
            material = fallbackMaterial;
        }
        Integer customModelData = plugin.getConfig().isInt(base + "custom-model-data") ? plugin.getConfig().getInt(base + "custom-model-data") : null;
        String name = plugin.getConfig().getString(base + "name", fallbackName);
        List<String> lore = plugin.getConfig().getStringList(base + "lore").isEmpty() ? fallbackLore : plugin.getConfig().getStringList(base + "lore");
        if (targetName != null) {
            name = name.replace("{target}", targetName);
            lore = lore.stream().map(line -> line.replace("{target}", targetName)).toList();
        }
        return items.button(
            material,
            name,
            lore,
            customModelData
        );
    }

    private void set(Inventory inventory, int slot, ItemStack item) {
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, item);
        }
    }
}
