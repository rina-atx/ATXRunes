package com.atx.runes.gui;

import com.atx.runes.ATXRunesPlugin;
import com.atx.runes.util.Texts;

import java.util.ArrayList;
import java.util.List;

public final class GuiConfig {
    private final ATXRunesPlugin plugin;

    public GuiConfig(ATXRunesPlugin plugin) {
        this.plugin = plugin;
    }

    public int size(String menu, int fallback) {
        int size = plugin.getConfig().getInt("guis." + menu + ".size", fallback);
        size = Math.max(9, Math.min(54, size));
        return size - (size % 9);
    }

    public String title(String menu, String fallback) {
        return Texts.color(plugin.getConfig().getString("guis." + menu + ".title", fallback));
    }

    public int slot(String path, int fallback) {
        return plugin.getConfig().getInt("guis." + path, fallback);
    }

    public List<Integer> slots(String path, List<Integer> fallback) {
        List<Integer> configured = plugin.getConfig().getIntegerList("guis." + path);
        return configured.isEmpty() ? fallback : configured;
    }

    public List<Integer> range(int start, int amount) {
        List<Integer> slots = new ArrayList<>();
        for (int index = 0; index < amount; index++) {
            slots.add(start + index);
        }
        return slots;
    }
}
