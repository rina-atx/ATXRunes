package com.atx.runes.listener;

import com.atx.runes.ATXRunesPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public final class PassiveRuneListener implements Listener {
    private final ATXRunesPlugin plugin;

    public PassiveRuneListener(ATXRunesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSwordHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || !isSword(player.getInventory().getItemInMainHand())) {
            return;
        }
        double bonus = plugin.getCoreBridge().total(plugin.getRuneStorage().getEquipped(player.getUniqueId()), "BONUS_SWORD_DAMAGE");
        if (bonus > 0) {
            event.setDamage(event.getDamage() + bonus);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        double reduction = plugin.getCoreBridge().total(plugin.getRuneStorage().getEquipped(player.getUniqueId()), "DAMAGE_REDUCTION");
        if (reduction > 0) {
            event.setDamage(event.getDamage() * Math.max(0.05, 1.0 - reduction));
        }
    }

    private boolean isSword(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        return type == Material.WOODEN_SWORD
            || type == Material.STONE_SWORD
            || type == Material.IRON_SWORD
            || type == Material.GOLDEN_SWORD
            || type == Material.DIAMOND_SWORD
            || type == Material.NETHERITE_SWORD;
    }
}
