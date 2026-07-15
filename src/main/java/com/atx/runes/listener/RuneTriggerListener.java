package com.atx.runes.listener;

import com.atx.runes.ATXRunesPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RuneTriggerListener implements Listener {
    private final ATXRunesPlugin plugin;

    public RuneTriggerListener(ATXRunesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMeleeAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            plugin.getCoreBridge().runEquipped(player, "melee_attack", event, event.getDamager(), event.getEntity(), damageData(event));
            plugin.getCoreBridge().runEquipped(player, "entity_damage_by_entity", event, event.getDamager(), event.getEntity(), damageData(event));
        }
        if (event.getEntity() instanceof Player player && !player.equals(event.getDamager())) {
            plugin.getCoreBridge().runEquipped(player, "entity_damage_by_entity", event, event.getDamager(), event.getEntity(), damageData(event));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("value", event.getDamage());
            data.put("target_type", event.getEntityType().name());
            data.put("entity_type", event.getEntityType().name());
            data.put("damage", event.getDamage());
            data.put("final_damage", event.getFinalDamage());
            data.put("cause", event.getCause().name());
            plugin.getCoreBridge().runEquipped(player, "entity_damage", event, event.getEntity(), event.getEntity(), data);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("value", 1);
        data.put("block_type", event.getBlock().getType().name());
        data.put("exp", event.getExpToDrop());
        plugin.getCoreBridge().runEquipped(event.getPlayer(), "block_break", event, event.getPlayer(), event.getPlayer(), data);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("value", 1);
        data.put("block_type", event.getBlock().getType().name());
        plugin.getCoreBridge().runEquipped(event.getPlayer(), "block_place", event, event.getPlayer(), event.getPlayer(), data);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("value", 1);
        data.put("action", event.getAction().name());
        if (event.getItem() != null) {
            data.put("material", event.getItem().getType().name());
        }
        if (event.getClickedBlock() != null) {
            data.put("block_type", event.getClickedBlock().getType().name());
        }
        plugin.getCoreBridge().runEquipped(event.getPlayer(), "player_interact", event, event.getPlayer(), event.getPlayer(), data);
    }

    private Map<String, Object> damageData(EntityDamageByEntityEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("value", event.getDamage());
        data.put("actor_type", event.getDamager().getType().name());
        data.put("target_type", event.getEntity().getType().name());
        data.put("damager_type", event.getDamager().getType().name());
        data.put("damage", event.getDamage());
        data.put("final_damage", event.getFinalDamage());
        data.put("cause", event.getCause().name());
        return data;
    }
}
