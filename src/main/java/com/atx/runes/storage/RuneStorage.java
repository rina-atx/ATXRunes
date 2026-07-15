package com.atx.runes.storage;

import com.atx.runes.ATXRunesPlugin;
import com.atx.runes.rune.RuneInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RuneStorage {
    private final ATXRunesPlugin plugin;
    private final File dataFolder;
    private final File legacyFile;
    private final Map<UUID, FileConfiguration> playerData = new HashMap<>();

    public RuneStorage(ATXRunesPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        this.legacyFile = new File(plugin.getDataFolder(), "runes.yml");
        reload();
    }

    public void reload() {
        dataFolder.mkdirs();
        playerData.clear();
        migrateLegacyData();
    }

    public void save() {
        playerData.forEach((playerId, data) -> save(playerId, data));
    }

    public List<RuneInstance> getStorage(UUID playerId) {
        return data(playerId).getStringList("storage").stream()
            .map(RuneInstance::deserialize)
            .flatMap(Optional::stream)
            .toList();
    }

    public Map<Integer, RuneInstance> getEquipped(UUID playerId) {
        Map<Integer, RuneInstance> equipped = new HashMap<>();
        ConfigurationSection section = data(playerId).getConfigurationSection("slots");
        if (section == null) {
            return equipped;
        }
        for (String key : section.getKeys(false)) {
            try {
                RuneInstance.deserialize(section.getString(key, "")).ifPresent(rune -> equipped.put(Integer.parseInt(key), rune));
            } catch (NumberFormatException ignored) {
            }
        }
        return equipped;
    }

    public void setStorage(UUID playerId, List<RuneInstance> runes) {
        FileConfiguration data = data(playerId);
        data.set("storage", runes.stream().map(RuneInstance::serialize).toList());
        save(playerId, data);
    }

    public void setEquipped(UUID playerId, Map<Integer, RuneInstance> equipped) {
        FileConfiguration data = data(playerId);
        data.set("slots", null);
        equipped.forEach((slot, rune) -> data.set("slots." + slot, rune.serialize()));
        save(playerId, data);
    }

    public boolean addToStorage(UUID playerId, RuneInstance rune) {
        List<RuneInstance> runes = new ArrayList<>(getStorage(playerId));
        int max = plugin.maxStorageSize();
        if (runes.size() >= max) {
            return false;
        }
        runes.add(rune);
        setStorage(playerId, runes);
        return true;
    }

    public boolean removeFromStorage(UUID playerId, UUID runeId) {
        List<RuneInstance> runes = new ArrayList<>(getStorage(playerId));
        boolean removed = runes.removeIf(rune -> rune.id().equals(runeId));
        if (removed) {
            setStorage(playerId, runes);
        }
        return removed;
    }

    public Optional<RuneInstance> findStored(UUID playerId, UUID runeId) {
        return getStorage(playerId).stream().filter(rune -> rune.id().equals(runeId)).findFirst();
    }

    private FileConfiguration data(UUID playerId) {
        return playerData.computeIfAbsent(playerId, id -> YamlConfiguration.loadConfiguration(file(id)));
    }

    private File file(UUID playerId) {
        return new File(dataFolder, playerId + ".yml");
    }

    private void save(UUID playerId, FileConfiguration data) {
        try {
            dataFolder.mkdirs();
            data.save(file(playerId));
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save player rune data for " + playerId + ": " + ex.getMessage());
        }
    }

    private void migrateLegacyData() {
        if (!legacyFile.exists()) {
            return;
        }
        FileConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
        ConfigurationSection players = legacy.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        int migrated = 0;
        for (String rawId : players.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(rawId);
                File playerFile = file(playerId);
                if (playerFile.exists()) {
                    continue;
                }
                FileConfiguration data = new YamlConfiguration();
                data.set("storage", legacy.getStringList("players." + rawId + ".storage"));
                ConfigurationSection slots = legacy.getConfigurationSection("players." + rawId + ".slots");
                if (slots != null) {
                    for (String key : slots.getKeys(false)) {
                        data.set("slots." + key, slots.getString(key));
                    }
                }
                save(playerId, data);
                migrated++;
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (migrated > 0) {
            plugin.getLogger().info("Migrated rune data for " + migrated + " player(s) into the data folder.");
        }
    }
}
