package com.atx.runes;

import com.atx.runes.command.RunesCommand;
import com.atx.runes.core.ATXCoreBridge;
import com.atx.runes.gui.RuneGuiListener;
import com.atx.runes.listener.PassiveRuneListener;
import com.atx.runes.listener.PlayerLifecycleListener;
import com.atx.runes.listener.RuneTriggerListener;
import com.atx.runes.rune.RuneRegistry;
import com.atx.runes.storage.RuneStorage;
import com.atx.runes.trade.TradeManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class ATXRunesPlugin extends JavaPlugin {
    private static final int MAX_GUI_STORAGE_SIZE = 45;
    private static final int MAX_GUI_RUNE_SLOTS = 9;

    private RuneRegistry runeRegistry;
    private RuneStorage runeStorage;
    private ATXCoreBridge coreBridge;
    private RuneGuiListener guiListener;
    private TradeManager tradeManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("rune-types.yml", false);

        coreBridge = new ATXCoreBridge(this);
        if (!coreBridge.isAvailable()) {
            getLogger().severe("ATXCore is required. Disabling ATXRunes.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        runeRegistry = new RuneRegistry(this);
        runeStorage = new RuneStorage(this);
        tradeManager = new TradeManager(this);
        guiListener = new RuneGuiListener(this);

        PluginCommand command = getCommand("runes");
        if (command != null) {
            RunesCommand runesCommand = new RunesCommand(this);
            command.setExecutor(runesCommand);
            command.setTabCompleter(runesCommand);
        }

        Bukkit.getPluginManager().registerEvents(guiListener, this);
        Bukkit.getPluginManager().registerEvents(new PassiveRuneListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RuneTriggerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerLifecycleListener(this), this);

        long refreshTicks = Math.max(10L, getConfig().getLong("settings.passive-refresh-seconds", 30L) * 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> Bukkit.getOnlinePlayers().forEach(player ->
                getCoreBridge().refreshPassiveEffects(player, getRuneStorage().getEquipped(player.getUniqueId()))),
            40L, refreshTicks);
    }

    @Override
    public void onDisable() {
        if (coreBridge != null) {
            Bukkit.getOnlinePlayers().forEach(coreBridge::clearPassiveEffects);
        }
        if (runeStorage != null) {
            runeStorage.save();
        }
    }

    public void reloadRunePlugin() {
        reloadConfig();
        runeRegistry.reload();
        runeStorage.reload();
        Bukkit.getOnlinePlayers().forEach(player ->
            coreBridge.refreshPassiveEffects(player, runeStorage.getEquipped(player.getUniqueId())));
    }

    public int maxStorageSize() {
        return clamp(getConfig().getInt("settings.storage-size", MAX_GUI_STORAGE_SIZE), 0, MAX_GUI_STORAGE_SIZE);
    }

    public int maxRuneSlots() {
        int configuredSlots = getConfig().getIntegerList("guis.slots.equipped-slots").size();
        int guiCapacity = configuredSlots <= 0 ? MAX_GUI_RUNE_SLOTS : configuredSlots;
        return clamp(getConfig().getInt("settings.rune-slots", 4), 1, Math.min(MAX_GUI_RUNE_SLOTS, guiCapacity));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public RuneRegistry getRuneRegistry() {
        return runeRegistry;
    }

    public RuneStorage getRuneStorage() {
        return runeStorage;
    }

    public ATXCoreBridge getCoreBridge() {
        return coreBridge;
    }

    public RuneGuiListener getGuiListener() {
        return guiListener;
    }

    public TradeManager getTradeManager() {
        return tradeManager;
    }
}
