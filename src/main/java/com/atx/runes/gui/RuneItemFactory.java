package com.atx.runes.gui;

import com.atx.runes.ATXRunesPlugin;
import com.atx.runes.rune.RuneInstance;
import com.atx.runes.rune.RunePlaceholders;
import com.atx.runes.rune.RuneType;
import com.atx.runes.util.Texts;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class RuneItemFactory {
    private final ATXRunesPlugin plugin;
    private final NamespacedKey runeIdKey;

    public RuneItemFactory(ATXRunesPlugin plugin) {
        this.plugin = plugin;
        this.runeIdKey = new NamespacedKey(plugin, "rune_id");
    }

    public ItemStack runeItem(RuneInstance rune) {
        RuneType type = plugin.getRuneRegistry().findType(rune.typeId()).orElse(null);
        if (type == null) {
            return button(Material.BARRIER, ChatColor.RED + "Unknown Rune", List.of(ChatColor.GRAY + rune.typeId()));
        }
        ItemStack item = new ItemStack(type.material());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Texts.color(type.displayName()));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "Tier: " + tierColor(rune.tier()) + ChatColor.BOLD + rune.tier() + ChatColor.DARK_GRAY + "/" + RuneInstance.MAX_TIER);
            lore.add("");
            type.description().forEach(line -> lore.add(Texts.color(RunePlaceholders.resolveText(line, type, rune))));
            meta.setLore(lore);
            if (type.customModelData() != null) {
                meta.setCustomModelData(type.customModelData());
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(runeIdKey, PersistentDataType.STRING, rune.id().toString());
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack button(Material material, String name, List<String> lore) {
        return button(material, name, lore, null);
    }

    public ItemStack button(Material material, String name, List<String> lore, Integer customModelData) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Texts.color(name));
            meta.setLore(Texts.color(lore));
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ChatColor tierColor(int tier) {
        return switch (tier) {
            case 1 -> ChatColor.WHITE;
            case 2 -> ChatColor.GREEN;
            case 3 -> ChatColor.AQUA;
            case 4 -> ChatColor.BLUE;
            case 5 -> ChatColor.LIGHT_PURPLE;
            case 6 -> ChatColor.GOLD;
            default -> ChatColor.RED;
        };
    }
}
