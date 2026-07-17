package com.atx.runes.gui;

import com.atx.runes.ATXRunesPlugin;
import com.atx.runes.util.Texts;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GuiConfig {
    private static final List<String> MENUS = List.of("main", "slots", "storage", "forge", "reforge", "salvage", "trade");

    private final ATXRunesPlugin plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();

    public GuiConfig(ATXRunesPlugin plugin) {
        this.plugin = plugin;
        saveDefaults();
        reload();
    }

    public void reload() {
        configs.clear();
        for (String menu : MENUS) {
            File file = new File(plugin.getDataFolder(), "GUIs/" + menu + ".yml");
            configs.put(menu, YamlConfiguration.loadConfiguration(file));
        }
    }

    public int size(String menu, int fallback) {
        int size = config(menu).getInt("size", plugin.getConfig().getInt("guis." + menu + ".size", fallback));
        size = Math.max(9, Math.min(54, size));
        return size - (size % 9);
    }

    public String title(String menu, String fallback) {
        return Texts.color(config(menu).getString("title", plugin.getConfig().getString("guis." + menu + ".title", fallback)));
    }

    public int slot(String path, int fallback) {
        String menu = menu(path);
        String localPath = localPath(path);
        return config(menu).getInt(localPath, plugin.getConfig().getInt("guis." + path, fallback));
    }

    public List<Integer> slots(String path, List<Integer> fallback) {
        String menu = menu(path);
        String localPath = localPath(path);
        List<Integer> configured = config(menu).getIntegerList(localPath);
        if (configured.isEmpty()) {
            configured = plugin.getConfig().getIntegerList("guis." + path);
        }
        return configured.isEmpty() ? fallback : configured;
    }

    public String string(String path, String fallback) {
        String menu = menu(path);
        String localPath = localPath(path);
        return config(menu).getString(localPath, plugin.getConfig().getString("guis." + path, fallback));
    }

    public List<String> stringList(String path) {
        String menu = menu(path);
        String localPath = localPath(path);
        List<String> configured = config(menu).getStringList(localPath);
        if (configured.isEmpty()) {
            configured = plugin.getConfig().getStringList("guis." + path);
        }
        return configured;
    }

    public boolean isInt(String path) {
        String menu = menu(path);
        String localPath = localPath(path);
        return config(menu).isInt(localPath) || plugin.getConfig().isInt("guis." + path);
    }

    public int integer(String path) {
        String menu = menu(path);
        String localPath = localPath(path);
        return config(menu).isInt(localPath) ? config(menu).getInt(localPath) : plugin.getConfig().getInt("guis." + path);
    }

    public List<Integer> range(int start, int amount) {
        List<Integer> slots = new ArrayList<>();
        for (int index = 0; index < amount; index++) {
            slots.add(start + index);
        }
        return slots;
    }

    private FileConfiguration config(String menu) {
        return configs.getOrDefault(menu, new YamlConfiguration());
    }

    private String menu(String path) {
        int split = path.indexOf('.');
        return split < 0 ? path : path.substring(0, split);
    }

    private String localPath(String path) {
        int split = path.indexOf('.');
        return split < 0 ? path : path.substring(split + 1);
    }

    private void saveDefaults() {
        for (String menu : MENUS) {
            File file = new File(plugin.getDataFolder(), "GUIs/" + menu + ".yml");
            if (!file.exists()) {
                plugin.saveResource("GUIs/" + menu + ".yml", false);
            }
        }
    }
}
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
