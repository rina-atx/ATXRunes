package com.atx.runes.listener;

import com.atx.runes.ATXRunesPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerLifecycleListener implements Listener {
    private final ATXRunesPlugin plugin;

    public PlayerLifecycleListener(ATXRunesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getCoreBridge().refreshPassiveEffects(event.getPlayer(), plugin.getRuneStorage().getEquipped(event.getPlayer().getUniqueId()));
        plugin.getCoreBridge().runEquipped(event.getPlayer(), "player_join", event, event.getPlayer(), event.getPlayer(), java.util.Map.of());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getCoreBridge().runEquipped(event.getPlayer(), "player_quit", event, event.getPlayer(), event.getPlayer(), java.util.Map.of());
        plugin.getCoreBridge().clearPassiveEffects(event.getPlayer());
        plugin.getRuneStorage().save();
    }
}
