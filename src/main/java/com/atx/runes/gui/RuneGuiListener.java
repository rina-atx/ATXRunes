package com.atx.runes.gui;

import com.atx.runes.ATXRunesPlugin;
import com.atx.runes.rune.RuneInstance;
import com.atx.runes.trade.TradeSession;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RuneGuiListener implements Listener {
    private final ATXRunesPlugin plugin;
    private final RuneMenus menus;
    private final NamespacedKey runeIdKey;

    public RuneGuiListener(ATXRunesPlugin plugin) {
        this.plugin = plugin;
        this.menus = new RuneMenus(plugin);
        this.runeIdKey = new NamespacedKey(plugin, "rune_id");
    }

    public RuneMenus menus() {
        return menus;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !(event.getInventory().getHolder() instanceof RuneGuiHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!player.hasPermission("atxrunes.use")) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "You do not have permission to use runes.");
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        switch (holder.type()) {
            case MAIN -> handleMain(player, event.getRawSlot());
            case SLOTS -> handleSlots(player, event.getRawSlot(), clicked);
            case STORAGE -> handleStorage(player, event.getRawSlot());
            case FORGE -> handleForge(player, event.getRawSlot());
            case REFORGE -> handleReforge(player, clicked);
            case TRADE -> handleTrade(player, holder, event.getRawSlot(), clicked);
        }
    }

    private void handleMain(Player player, int slot) {
        if (slot == menus.gui().slot("main.buttons.slots.slot", 10)) menus.openSlots(player);
        if (slot == menus.gui().slot("main.buttons.forge.slot", 12)) menus.openForge(player);
        if (slot == menus.gui().slot("main.buttons.reforge.slot", 14)) menus.openReforge(player);
        if (slot == menus.gui().slot("main.buttons.storage.slot", 16)) menus.openStorage(player);
    }

    private void handleStorage(Player player, int slot) {
        if (slot == menus.gui().slot("storage.buttons.back.slot", 49)) {
            menus.openMain(player);
        }
    }

    private void handleSlots(Player player, int slot, ItemStack clicked) {
        UUID playerId = player.getUniqueId();
        int maxSlots = plugin.maxRuneSlots();
        Map<Integer, RuneInstance> equipped = new HashMap<>(plugin.getRuneStorage().getEquipped(playerId));

        List<Integer> equippedSlots = menus.equippedSlots();
        int equippedIndex = equippedSlots.indexOf(slot);
        if (equippedIndex >= 0 && equippedIndex < maxSlots && equipped.containsKey(equippedIndex)) {
            RuneInstance removed = equipped.remove(equippedIndex);
            if (!plugin.getRuneStorage().addToStorage(playerId, removed)) {
                player.sendMessage(ChatColor.RED + "Your rune storage is full.");
                equipped.put(equippedIndex, removed);
            }
            plugin.getRuneStorage().setEquipped(playerId, equipped);
            plugin.getCoreBridge().refreshPassiveEffects(player, equipped);
            menus.openSlots(player);
            return;
        }

        if (!equippedSlots.contains(slot)) {
            Optional<UUID> runeId = readRuneId(clicked);
            if (runeId.isEmpty()) {
                return;
            }
            Optional<RuneInstance> rune = plugin.getRuneStorage().findStored(playerId, runeId.get());
            if (rune.isEmpty()) {
                return;
            }
            Optional<Integer> emptySlot = findEmptySlot(equipped, maxSlots);
            if (emptySlot.isEmpty()) {
                player.sendMessage(ChatColor.RED + "All rune slots are full.");
                return;
            }
            plugin.getRuneStorage().removeFromStorage(playerId, rune.get().id());
            equipped.put(emptySlot.get(), rune.get());
            plugin.getRuneStorage().setEquipped(playerId, equipped);
            plugin.getCoreBridge().refreshPassiveEffects(player, equipped);
            menus.openSlots(player);
        }
    }

    private void handleForge(Player player, int slot) {
        if (slot == menus.gui().slot("forge.buttons.back.slot", 22)) {
            menus.openMain(player);
            return;
        }
        if (slot != menus.gui().slot("forge.buttons.forge.slot", 11)) {
            return;
        }
        Material material = Material.matchMaterial(plugin.getConfig().getString("forging.cost.material", "AMETHYST_SHARD"));
        int amount = plugin.getConfig().getInt("forging.cost.amount", 8);
        if (material == null || !consume(player, material, amount)) {
            player.sendMessage(ChatColor.RED + "You need " + amount + "x " + (material == null ? "configured material" : material.name()) + " to forge a rune.");
            return;
        }
        RuneInstance rune = plugin.getRuneRegistry().randomRune();
        if (!plugin.getRuneStorage().addToStorage(player.getUniqueId(), rune)) {
            menus.giveOrDropRune(player, rune);
            player.sendMessage(ChatColor.YELLOW + "Storage was full, so the forged rune was delivered to you.");
        }
        player.sendMessage(ChatColor.GREEN + "Forged a tier " + rune.tier() + " " + plugin.getRuneRegistry().findType(rune.typeId()).map(type -> type.displayName()).orElse(rune.typeId()) + ChatColor.GREEN + ".");
        menus.openForge(player);
    }

    private void handleReforge(Player player, ItemStack clicked) {
        Optional<UUID> runeId = readRuneId(clicked);
        if (runeId.isEmpty()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        List<RuneInstance> storage = new ArrayList<>(plugin.getRuneStorage().getStorage(playerId));
        int runeIndex = -1;
        for (int index = 0; index < storage.size(); index++) {
            if (storage.get(index).id().equals(runeId.get())) {
                runeIndex = index;
                break;
            }
        }
        if (runeIndex < 0) {
            player.sendMessage(ChatColor.RED + "That rune is no longer in storage.");
            menus.openReforge(player);
            return;
        }

        Material material = Material.matchMaterial(plugin.getConfig().getString("reforging.cost.material", "NETHER_STAR"));
        int amount = plugin.getConfig().getInt("reforging.cost.amount", 1);
        if (material == null || !consume(player, material, amount)) {
            player.sendMessage(ChatColor.RED + "You need " + amount + "x " + (material == null ? "configured material" : material.name()) + " to re-forge a rune.");
            return;
        }

        RuneInstance current = storage.get(runeIndex);
        if (current.tier() >= RuneInstance.MAX_TIER) {
            player.sendMessage(ChatColor.RED + "That rune is already tier " + RuneInstance.MAX_TIER + ".");
            menus.openReforge(player);
            return;
        }
        storage.set(runeIndex, reforge(current));
        plugin.getRuneStorage().setStorage(playerId, storage);
        player.sendMessage(ChatColor.GOLD + "Rune upgraded to tier " + (current.tier() + 1) + ".");
        menus.openReforge(player);
    }

    private RuneInstance reforge(RuneInstance current) {
        if (plugin.getConfig().getBoolean("reforging.keep-type", true)) {
            return current.nextTier();
        }
        return RuneInstance.create(plugin.getRuneRegistry().randomRune().typeId(), current.tier() + 1);
    }

    private void handleTrade(Player player, RuneGuiHolder holder, int slot, ItemStack clicked) {
        if (holder.targetId() == null) {
            return;
        }
        Player target = Bukkit.getPlayer(holder.targetId());
        if (target == null) {
            player.sendMessage(ChatColor.RED + "That player is no longer online.");
            return;
        }
        if (!target.hasPermission("atxrunes.use")) {
            plugin.getTradeManager().find(player.getUniqueId(), target.getUniqueId())
                .ifPresent(plugin.getTradeManager()::cancel);
            player.closeInventory();
            target.closeInventory();
            player.sendMessage(ChatColor.RED + "That player no longer has permission to trade runes.");
            return;
        }
        Optional<TradeSession> maybeSession = plugin.getTradeManager().find(player.getUniqueId(), target.getUniqueId());
        if (maybeSession.isEmpty()) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "That trade has expired.");
            return;
        }
        TradeSession session = maybeSession.get();
        if (slot == menus.gui().slot("trade.accept-slot", 22)) {
            session.accept(player.getUniqueId());
            if (plugin.getTradeManager().tryComplete(session)) {
                return;
            }
            menus.openTrade(player, target, session);
            menus.openTrade(target, player, session);
            return;
        }
        Optional<UUID> runeId = readRuneId(clicked);
        if (runeId.isEmpty() || plugin.getRuneStorage().findStored(player.getUniqueId(), runeId.get()).isEmpty()) {
            return;
        }
        session.setOffer(player.getUniqueId(), runeId.get());
        menus.openTrade(player, target, session);
        menus.openTrade(target, player, session);
    }

    private Optional<Integer> findEmptySlot(Map<Integer, RuneInstance> equipped, int maxSlots) {
        for (int slot = 0; slot < maxSlots; slot++) {
            if (!equipped.containsKey(slot)) {
                return Optional.of(slot);
            }
        }
        return Optional.empty();
    }

    private Optional<UUID> readRuneId(ItemStack item) {
        if (!item.hasItemMeta()) {
            return Optional.empty();
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(runeIdKey, PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private boolean consume(Player player, Material material, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (!player.getInventory().containsAtLeast(new ItemStack(material), amount)) {
            return false;
        }
        player.getInventory().removeItem(new ItemStack(material, amount));
        return true;
    }
}
