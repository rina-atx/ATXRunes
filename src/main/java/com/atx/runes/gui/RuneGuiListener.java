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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class RuneGuiListener implements Listener {
    private final ATXRunesPlugin plugin;
    private final RuneMenus menus;
    private final NamespacedKey runeIdKey;
    private final Set<UUID> claimedForgeOutputs = new HashSet<>();

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
            plugin.messages().send(player, "general.no-permission");
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
            case SLOTS -> handleSlots(player, holder, event.getRawSlot(), clicked);
            case STORAGE -> handleStorage(player, holder, event.getRawSlot());
            case FORGE -> handleForge(player, holder, event.getRawSlot());
            case REFORGE -> handleReforge(player, holder, event.getRawSlot(), clicked);
            case SALVAGE -> handleSalvage(player, holder, event.getRawSlot(), clicked);
            case TRADE -> handleTrade(player, holder, event.getRawSlot(), clicked);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !(event.getInventory().getHolder() instanceof RuneGuiHolder holder)) {
            return;
        }
        if (holder.type() != RuneGuiType.FORGE || holder.forgedRune() == null) {
            return;
        }
        UUID runeId = holder.forgedRune().id();
        if (claimedForgeOutputs.remove(runeId)) {
            return;
        }
        deliverForgedRune(player, holder.forgedRune());
    }

    private void handleMain(Player player, int slot) {
        if (slot == menus.gui().slot("main.buttons.slots.slot", 10)) menus.openSlots(player);
        if (slot == menus.gui().slot("main.buttons.forge.slot", 12)) menus.openForge(player);
        if (slot == menus.gui().slot("main.buttons.reforge.slot", 14)) menus.openReforge(player);
        if (slot == menus.gui().slot("main.buttons.storage.slot", 16)) menus.openStorage(player);
        if (slot == menus.gui().slot("main.buttons.salvage.slot", 20)) menus.openSalvage(player);
    }

    private void handleStorage(Player player, RuneGuiHolder holder, int slot) {
        if (slot == menus.gui().slot("storage.buttons.back.slot", 45)) {
            menus.openMain(player);
            return;
        }
        if (slot == menus.gui().slot("storage.buttons.previous.slot", 48)) {
            menus.openStorage(player, Math.max(0, holder.page() - 1));
            return;
        }
        if (slot == menus.gui().slot("storage.buttons.next.slot", 50)) {
            int pageSize = Math.max(1, menus.storageSlots("storage.storage-slots", 10, plugin.maxStorageSize()).size());
            int storageSize = plugin.getRuneStorage().getStorage(player.getUniqueId()).size();
            int maxPage = Math.max(0, (storageSize - 1) / pageSize);
            menus.openStorage(player, Math.min(maxPage, holder.page() + 1));
        }
    }

    private void handleSlots(Player player, RuneGuiHolder holder, int slot, ItemStack clicked) {
        if (slot == menus.gui().slot("slots.buttons.back.slot", 45)) {
            menus.openMain(player);
            return;
        }
        if (slot == menus.gui().slot("slots.buttons.previous.slot", 36)) {
            menus.openSlots(player, Math.max(0, holder.page() - 1));
            return;
        }
        if (slot == menus.gui().slot("slots.buttons.next.slot", 44)) {
            int pageSize = Math.max(1, menus.storageSlots("slots.storage-slots", 28, plugin.maxStorageSize()).size());
            int storageSize = plugin.getRuneStorage().getStorage(player.getUniqueId()).size();
            int maxPage = Math.max(0, (storageSize - 1) / pageSize);
            menus.openSlots(player, Math.min(maxPage, holder.page() + 1));
            return;
        }

        UUID playerId = player.getUniqueId();
        int maxSlots = plugin.maxRuneSlots();
        Map<Integer, RuneInstance> equipped = new HashMap<>(plugin.getRuneStorage().getEquipped(playerId));

        List<Integer> equippedSlots = menus.equippedSlots();
        int equippedIndex = equippedSlots.indexOf(slot);
        if (equippedIndex >= 0 && equippedIndex < maxSlots && equipped.containsKey(equippedIndex)) {
            RuneInstance removed = equipped.remove(equippedIndex);
            if (!plugin.getRuneStorage().setEquipped(playerId, equipped)) {
                equipped.put(equippedIndex, removed);
                plugin.messages().send(player, "slots.storage-full");
                plugin.getCoreBridge().refreshPassiveEffects(player, equipped);
                menus.openSlots(player);
                return;
            }
            if (!plugin.getRuneStorage().addToStorage(playerId, removed)) {
                plugin.messages().send(player, "slots.storage-full");
                equipped.put(equippedIndex, removed);
                plugin.getRuneStorage().setEquipped(playerId, equipped);
            }
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
                plugin.messages().send(player, "slots.full");
                return;
            }
            if (!plugin.getRuneStorage().removeFromStorage(playerId, rune.get().id())) {
                plugin.messages().send(player, "reforge.rune-missing");
                menus.openSlots(player);
                return;
            }
            equipped.put(emptySlot.get(), rune.get());
            if (!plugin.getRuneStorage().setEquipped(playerId, equipped)) {
                equipped.remove(emptySlot.get());
                plugin.getRuneStorage().addToStorage(playerId, rune.get());
                plugin.messages().send(player, "slots.storage-full");
                menus.openSlots(player);
                return;
            }
            plugin.getCoreBridge().refreshPassiveEffects(player, equipped);
            menus.openSlots(player);
        }
    }

    private void handleForge(Player player, RuneGuiHolder holder, int slot) {
        if (slot == menus.gui().slot("forge.buttons.back.slot", 45)) {
            menus.openMain(player);
            return;
        }
        if (holder.forgedRune() != null) {
            if (slot == menus.gui().slot("forge.output-slot", 25)) {
                claimedForgeOutputs.add(holder.forgedRune().id());
                deliverForgedRune(player, holder.forgedRune());
                menus.openForge(player);
            }
            return;
        }

        if (slot == menus.gui().slot("forge.ingredients.glowstone.slot", 19)) {
            menus.openForge(player, toggleForgeIngredient(player, "glowstone", holder.forgeGlowstone()), holder.forgeTemplate(), holder.forgeNetherite(), null);
            return;
        }
        if (slot == menus.gui().slot("forge.ingredients.template.slot", 20)) {
            menus.openForge(player, holder.forgeGlowstone(), toggleForgeIngredient(player, "template", holder.forgeTemplate()), holder.forgeNetherite(), null);
            return;
        }
        if (slot == menus.gui().slot("forge.ingredients.netherite.slot", 21)) {
            menus.openForge(player, holder.forgeGlowstone(), holder.forgeTemplate(), toggleForgeIngredient(player, "netherite", holder.forgeNetherite()), null);
            return;
        }
        if (slot != menus.gui().slot("forge.buttons.forge.slot", 41)) {
            return;
        }
        if (!holder.forgeGlowstone() || !holder.forgeTemplate() || !holder.forgeNetherite()) {
            plugin.messages().send(player, "forge.missing-selection");
            return;
        }
        if (!hasForgeIngredients(player)) {
            plugin.messages().send(player, "forge.missing-ingredients", Map.of("{requirements}", forgeRequirementsText()));
            return;
        }
        double forgeCost = plugin.getConfig().getDouble("forging.money-cost", 5000);
        if (!plugin.getEconomyBridge().charge(player, forgeCost, "forge a rune")) {
            return;
        }
        RuneInstance rune = plugin.getRuneRegistry().randomRune();
        if (!plugin.getRuneStorage().addToStorage(player.getUniqueId(), rune)) {
            plugin.getEconomyBridge().refund(player, forgeCost);
            plugin.messages().send(player, "forge.storage-full-delivered");
            return;
        }
        consumeForgeIngredients(player);
        plugin.messages().send(player, "forge.saved-to-storage");
        menus.openForge(player, false, false, false, null);
    }

    private boolean toggleForgeIngredient(Player player, String ingredient, boolean selected) {
        if (selected) {
            return false;
        }
        Material material = menus.forgeIngredientMaterial(ingredient);
        int amount = menus.forgeIngredientAmount(ingredient);
        String nexoId = menus.forgeIngredientNexoId(ingredient);
        if (!nexoId.isBlank() && !plugin.getNexoBridge().isAvailable()) {
            plugin.messages().send(player, "forge.nexo-unavailable", Map.of("{item}", nexoId));
            return false;
        }
        if (!hasIngredient(player, material, nexoId, amount)) {
            plugin.messages().send(player, "forge.missing-place-ingredient", Map.of(
                "{amount}", String.valueOf(amount),
                "{material}", menus.forgeIngredientDisplayName(ingredient)
            ));
            return false;
        }
        return true;
    }

    private boolean consumeForgeIngredients(Player player) {
        if (!hasForgeIngredients(player)) {
            return false;
        }
        forgeRequirements().forEach((key, amount) -> consumeIngredient(player, key.material(), key.nexoId(), amount));
        return true;
    }

    private boolean hasForgeIngredients(Player player) {
        for (Map.Entry<IngredientKey, Integer> entry : forgeRequirements().entrySet()) {
            IngredientKey key = entry.getKey();
            if (!key.nexoId().isBlank() && !plugin.getNexoBridge().isAvailable()) {
                return false;
            }
            if (!hasIngredient(player, key.material(), key.nexoId(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private Map<IngredientKey, Integer> forgeRequirements() {
        Map<IngredientKey, Integer> required = new LinkedHashMap<>();
        for (String ingredient : List.of("glowstone", "template", "netherite")) {
            required.merge(new IngredientKey(menus.forgeIngredientMaterial(ingredient), menus.forgeIngredientNexoId(ingredient)),
                menus.forgeIngredientAmount(ingredient), Integer::sum);
        }
        return required;
    }

    private String forgeRequirementsText() {
        List<String> parts = new ArrayList<>();
        forgeRequirements().forEach((key, amount) -> parts.add(amount + "x " + key.displayName()));
        if (parts.size() <= 1) {
            return parts.isEmpty() ? "" : parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + ", and " + parts.get(parts.size() - 1);
    }

    private void deliverForgedRune(Player player, RuneInstance rune) {
        if (!plugin.getRuneStorage().addToStorage(player.getUniqueId(), rune)) {
            menus.giveOrDropRune(player, rune);
            plugin.messages().send(player, "forge.storage-full-delivered");
            return;
        }
        plugin.messages().send(player, "forge.saved-to-storage");
    }

    private void handleReforge(Player player, RuneGuiHolder holder, int slot, ItemStack clicked) {
        if (slot == menus.gui().slot("reforge.buttons.back.slot", 45)) {
            menus.openMain(player);
            return;
        }
        if (slot == menus.gui().slot("reforge.buttons.previous.slot", 36)) {
            menus.openReforge(player, Math.max(0, holder.page() - 1), holder.selectedRuneId(), holder.catalysts());
            return;
        }
        if (slot == menus.gui().slot("reforge.buttons.next.slot", 44)) {
            int pageSize = Math.max(1, menus.storageSlots("reforge.storage-slots", 28, plugin.maxStorageSize()).size());
            int storageSize = plugin.getRuneStorage().getStorage(player.getUniqueId()).size();
            if (holder.selectedRuneId() != null) {
                storageSize = Math.max(0, storageSize - 1);
            }
            int maxPage = Math.max(0, (storageSize - 1) / pageSize);
            menus.openReforge(player, Math.min(maxPage, holder.page() + 1), holder.selectedRuneId(), holder.catalysts());
            return;
        }
        if (slot == menus.gui().slot("reforge.input-slot", 10)) {
            menus.openReforge(player, holder.page(), null, holder.catalysts());
            return;
        }
        if (menus.gui().slots("reforge.catalyst-slots", List.of(21, 22, 23)).contains(slot)) {
            handleReforgeCatalyst(player, holder, slot);
            return;
        }
        if (slot == menus.gui().slot("reforge.buttons.confirm.slot", 4)) {
            confirmReforge(player, holder);
            return;
        }

        Optional<UUID> runeId = readRuneId(clicked);
        if (runeId.isEmpty()) {
            return;
        }
        if (plugin.getRuneStorage().findStored(player.getUniqueId(), runeId.get()).isEmpty()) {
            plugin.messages().send(player, "reforge.rune-missing");
            menus.openReforge(player, holder.page(), null, holder.catalysts());
            return;
        }
        menus.openReforge(player, holder.page(), runeId.get(), holder.catalysts());
    }

    private void handleReforgeCatalyst(Player player, RuneGuiHolder holder, int slot) {
        List<Integer> catalystSlots = menus.gui().slots("reforge.catalyst-slots", List.of(21, 22, 23));
        int index = catalystSlots.indexOf(slot);
        if (index < 0) {
            return;
        }
        int catalysts = holder.catalysts();
        if (index < catalysts) {
            catalysts--;
        } else {
            int maxCatalysts = plugin.getConfig().getInt("reforging.catalyst.max", 3);
            Material material = menus.reforgeCatalystMaterial();
            String nexoId = menus.reforgeCatalystNexoId();
            if (catalysts >= maxCatalysts) {
                return;
            }
            if (!nexoId.isBlank() && !plugin.getNexoBridge().isAvailable()) {
                plugin.messages().send(player, "reforge.nexo-unavailable", Map.of("{item}", nexoId));
                return;
            }
            if (!hasIngredient(player, material, nexoId, catalysts + 1)) {
                plugin.messages().send(player, "reforge.missing-additional-catalyst", Map.of("{material}", menus.reforgeCatalystDisplayName()));
                return;
            }
            catalysts++;
        }
        menus.openReforge(player, holder.page(), holder.selectedRuneId(), catalysts);
    }

    private void confirmReforge(Player player, RuneGuiHolder holder) {
        if (holder.selectedRuneId() == null) {
            plugin.messages().send(player, "reforge.missing-rune");
            return;
        }
        UUID playerId = player.getUniqueId();
        List<RuneInstance> storage = new ArrayList<>(plugin.getRuneStorage().getStorage(playerId));
        int runeIndex = -1;
        for (int index = 0; index < storage.size(); index++) {
            if (storage.get(index).id().equals(holder.selectedRuneId())) {
                runeIndex = index;
                break;
            }
        }
        if (runeIndex < 0) {
            plugin.messages().send(player, "reforge.rune-missing");
            menus.openReforge(player, holder.page(), null, holder.catalysts());
            return;
        }

        RuneInstance current = storage.get(runeIndex);
        if (current.tier() >= RuneInstance.MAX_TIER) {
            plugin.messages().send(player, "reforge.max-tier", Map.of("{max_tier}", String.valueOf(RuneInstance.MAX_TIER)));
            menus.openReforge(player, holder.page(), current.id(), holder.catalysts());
            return;
        }

        Material material = menus.reforgeCatalystMaterial();
        String nexoId = menus.reforgeCatalystNexoId();
        int catalysts = Math.max(0, holder.catalysts());
        if (!nexoId.isBlank() && !plugin.getNexoBridge().isAvailable()) {
            plugin.messages().send(player, "reforge.nexo-unavailable", Map.of("{item}", nexoId));
            return;
        }
        if (!hasIngredient(player, material, nexoId, catalysts)) {
            plugin.messages().send(player, "reforge.missing-catalysts", Map.of(
                "{amount}", String.valueOf(catalysts),
                "{material}", menus.reforgeCatalystDisplayName()
            ));
            return;
        }
        if (!plugin.getEconomyBridge().charge(player, plugin.getConfig().getDouble("reforging.money-cost", 5000), "re-forge a rune")) {
            return;
        }

        String outcome = rollReforgeOutcome(catalysts, current.tier());
        boolean changed = false;
        switch (outcome) {
            case "upgrade" -> {
                if (current.tier() >= RuneInstance.MAX_TIER) {
                    plugin.messages().send(player, "reforge.max-tier", Map.of("{max_tier}", String.valueOf(RuneInstance.MAX_TIER)));
                } else {
                    RuneInstance upgraded = current.nextTier();
                    storage.set(runeIndex, upgraded);
                    if (!plugin.getRuneStorage().setStorage(playerId, storage)) {
                        plugin.getEconomyBridge().refund(player, plugin.getConfig().getDouble("reforging.money-cost", 5000));
                        plugin.messages().send(player, "reforge.rune-missing");
                        return;
                    }
                    changed = true;
                    plugin.messages().send(player, "reforge.upgraded", Map.of("{tier}", String.valueOf(upgraded.tier())));
                }
            }
            case "degrade" -> {
                if (current.tier() <= RuneInstance.MIN_TIER) {
                    plugin.messages().send(player, "reforge.downgrade-resisted", Map.of("{min_tier}", String.valueOf(RuneInstance.MIN_TIER)));
                } else {
                    RuneInstance degraded = current.withTier(current.tier() - 1);
                    storage.set(runeIndex, degraded);
                    if (!plugin.getRuneStorage().setStorage(playerId, storage)) {
                        plugin.getEconomyBridge().refund(player, plugin.getConfig().getDouble("reforging.money-cost", 5000));
                        plugin.messages().send(player, "reforge.rune-missing");
                        return;
                    }
                    changed = true;
                    plugin.messages().send(player, "reforge.degraded", Map.of("{tier}", String.valueOf(degraded.tier())));
                }
            }
            case "destroy" -> {
                storage.remove(runeIndex);
                if (!plugin.getRuneStorage().setStorage(playerId, storage)) {
                    plugin.getEconomyBridge().refund(player, plugin.getConfig().getDouble("reforging.money-cost", 5000));
                    plugin.messages().send(player, "reforge.rune-missing");
                    return;
                }
                consumeIngredient(player, material, nexoId, catalysts);
                plugin.messages().send(player, "reforge.destroyed");
                menus.openReforge(player, holder.page(), null, 0);
                return;
            }
            default -> plugin.messages().send(player, "reforge.nothing");
        }
        if (changed) {
            consumeIngredient(player, material, nexoId, catalysts);
        }
        menus.openReforge(player, holder.page(), current.id(), 0);
    }

    private String rollReforgeOutcome(int catalysts, int tier) {
        Map<String, Integer> chances = menus.reforgeChances(catalysts, tier);
        int upgrade = chances.get("upgrade");
        int degrade = chances.get("degrade");
        int destroy = chances.get("destroy");
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < upgrade) {
            return "upgrade";
        }
        if (roll < upgrade + degrade) {
            return "degrade";
        }
        if (roll < upgrade + degrade + destroy) {
            return "destroy";
        }
        return "nothing";
    }

    private void handleSalvage(Player player, RuneGuiHolder holder, int slot, ItemStack clicked) {
        if (slot == menus.gui().slot("salvage.input-slot", 13)) {
            menus.openSalvage(player, null);
            return;
        }
        if (slot == menus.gui().slot("salvage.buttons.confirm.slot", 15)) {
            confirmSalvage(player, holder);
            return;
        }

        Optional<UUID> runeId = readRuneId(clicked);
        if (runeId.isEmpty()) {
            return;
        }
        if (plugin.getRuneStorage().findStored(player.getUniqueId(), runeId.get()).isEmpty()) {
            plugin.messages().send(player, "salvage.rune-missing");
            menus.openSalvage(player, null);
            return;
        }
        menus.openSalvage(player, runeId.get());
    }

    private void confirmSalvage(Player player, RuneGuiHolder holder) {
        if (holder.selectedRuneId() == null) {
            plugin.messages().send(player, "salvage.missing-rune");
            return;
        }
        Optional<RuneInstance> rune = plugin.getRuneStorage().findStored(player.getUniqueId(), holder.selectedRuneId());
        if (rune.isEmpty()) {
            plugin.messages().send(player, "salvage.rune-missing");
            menus.openSalvage(player, null);
            return;
        }
        List<ItemStack> rewards = menus.salvageRewards(rune.get());
        if (rewards.isEmpty()) {
            plugin.messages().send(player, "salvage.no-rewards");
            return;
        }
        if (!plugin.getRuneStorage().removeFromStorage(player.getUniqueId(), rune.get().id())) {
            plugin.messages().send(player, "salvage.rune-missing");
            menus.openSalvage(player, null);
            return;
        }
        for (ItemStack reward : rewards) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
            leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        plugin.messages().send(player, "salvage.completed", Map.of("{rewards}", rewardText(rewards)));
        menus.openSalvage(player, null);
    }

    private String rewardText(List<ItemStack> rewards) {
        return String.join(", ", rewards.stream()
            .map(item -> item.getAmount() + "x " + item.getType().name())
            .toList());
    }

    private void handleTrade(Player player, RuneGuiHolder holder, int slot, ItemStack clicked) {
        if (holder.targetId() == null) {
            return;
        }
        Player target = Bukkit.getPlayer(holder.targetId());
        if (target == null) {
            plugin.messages().send(player, "trade.target-offline");
            return;
        }
        if (!target.hasPermission("atxrunes.use")) {
            plugin.getTradeManager().find(player.getUniqueId(), target.getUniqueId())
                .ifPresent(plugin.getTradeManager()::cancel);
            player.closeInventory();
            target.closeInventory();
            plugin.messages().send(player, "trade.target-lost-permission");
            return;
        }
        Optional<TradeSession> maybeSession = plugin.getTradeManager().find(player.getUniqueId(), target.getUniqueId());
        if (maybeSession.isEmpty()) {
            player.closeInventory();
            plugin.messages().send(player, "trade.expired");
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
        if (!has(player, material, amount)) {
            return false;
        }
        player.getInventory().removeItem(new ItemStack(material, amount));
        return true;
    }

    private boolean has(Player player, Material material, int amount) {
        return amount <= 0 || player.getInventory().containsAtLeast(new ItemStack(material), amount);
    }

    private boolean hasIngredient(Player player, Material material, String nexoId, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (nexoId == null || nexoId.isBlank()) {
            return has(player, material, amount);
        }
        return plugin.getNexoBridge().has(player.getInventory(), nexoId, amount);
    }

    private boolean consumeIngredient(Player player, Material material, String nexoId, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (nexoId == null || nexoId.isBlank()) {
            return consume(player, material, amount);
        }
        return plugin.getNexoBridge().consume(player.getInventory(), nexoId, amount);
    }

    private record IngredientKey(Material material, String nexoId) {
        private String displayName() {
            return nexoId == null || nexoId.isBlank() ? material.name() : nexoId;
        }
    }
}
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
