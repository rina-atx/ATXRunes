package com.atx.runes.rune;

import com.atx.runes.ATXRunesPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

public final class RuneRegistry {
    private final ATXRunesPlugin plugin;
    private final Random random = new Random();
    private final Map<String, RuneType> runeTypes = new HashMap<>();

    public RuneRegistry(ATXRunesPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        runeTypes.clear();
        loadTypes();
    }

    private void loadTypes() {
        File file = new File(plugin.getDataFolder(), "rune-types.yml");
        if (!file.exists()) {
            plugin.saveResource("rune-types.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("runes");
        if (section == null) {
            plugin.getLogger().warning("No runes are configured in rune-types.yml.");
            return;
        }
        for (String id : section.getKeys(false)) {
            loadType(id.toUpperCase(Locale.ROOT), section.getConfigurationSection(id));
        }
    }

    private void loadType(String id, ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }
        Material material = Material.matchMaterial(section.getString("item.material", section.getString("material", "AMETHYST_SHARD")));
        if (material == null) {
            plugin.getLogger().warning("Rune " + id + " has an invalid material. Falling back to AMETHYST_SHARD.");
            material = Material.AMETHYST_SHARD;
        }
        String effectId = section.getString("effect", section.getString("effect-id", id)).toUpperCase(Locale.ROOT);
        Integer customModelData = customModelData(section);
        runeTypes.put(id, new RuneType(
            id,
            section.getString("display-name", id),
            material,
            customModelData,
            section.getStringList("description"),
            effectId,
            steps(section, "conditions"),
            effects(section),
            section.getDouble("base-value", 1.0),
            placeholders(section)
        ));
    }

    private Integer customModelData(ConfigurationSection section) {
        if (section.isInt("item.custom-model-data")) {
            return section.getInt("item.custom-model-data");
        }
        if (section.isInt("custom-model-data")) {
            return section.getInt("custom-model-data");
        }
        return null;
    }

    private Map<String, Object> placeholders(ConfigurationSection section) {
        ConfigurationSection placeholders = section.getConfigurationSection("placeholders");
        if (placeholders == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : placeholders.getKeys(false)) {
            values.put(key, placeholders.get(key));
        }
        return values;
    }

    private List<RuneEffectConfig> effects(ConfigurationSection section) {
        List<RuneEffectConfig> effects = new ArrayList<>();
        for (Map<?, ?> rawEffect : section.getMapList("effects")) {
            Object rawId = rawEffect.get("id");
            if (rawId == null || String.valueOf(rawId).isBlank()) {
                continue;
            }
            Map<String, Object> args = map(rawEffect.get("args"));
            if (args.isEmpty()) {
                args = map(rawEffect.get("data"));
            }
            effects.add(new RuneEffectConfig(
                String.valueOf(rawId).toLowerCase(Locale.ROOT),
                args,
                triggers(rawEffect.get("triggers")),
                steps(rawEffect.get("conditions")),
                steps(rawEffect.get("mutators")),
                filters(rawEffect.get("filters"))
            ));
        }
        return effects;
    }

    private List<String> triggers(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                .map(String::valueOf)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
        }
        if (raw instanceof String value && !value.isBlank()) {
            return List.of(value.toLowerCase(Locale.ROOT));
        }
        return List.of();
    }

    private List<RuneMechanicStep> steps(ConfigurationSection section, String key) {
        return steps(section.get(key));
    }

    private List<RuneMechanicStep> steps(Object raw) {
        List<RuneMechanicStep> steps = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return steps;
        }
        for (Object rawObject : list) {
            if (!(rawObject instanceof Map<?, ?> rawStep)) {
                continue;
            }
            Object rawId = rawStep.get("id");
            if (rawId == null || String.valueOf(rawId).isBlank()) {
                continue;
            }
            Map<String, Object> data = map(rawStep.get("args"));
            if (data.isEmpty()) {
                data = map(rawStep.get("data"));
            }
            steps.add(new RuneMechanicStep(String.valueOf(rawId).toLowerCase(Locale.ROOT), data));
        }
        return steps;
    }

    private List<RuneMechanicStep> filters(Object raw) {
        if (raw instanceof List<?>) {
            return steps(raw);
        }
        List<RuneMechanicStep> filters = new ArrayList<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return filters;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                filters.add(new RuneMechanicStep(String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT), Map.of("values", entry.getValue())));
            }
        }
        return filters;
    }

    private Map<String, Object> map(Object raw) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return data;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                data.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return data;
    }

    public Optional<RuneType> findType(String typeId) {
        return Optional.ofNullable(runeTypes.get(typeId.toUpperCase(Locale.ROOT)));
    }

    public List<RuneType> allTypes() {
        return new ArrayList<>(runeTypes.values());
    }

    public RuneInstance randomRune() {
        List<RuneType> types = allTypes();
        if (types.isEmpty()) {
            throw new IllegalStateException("No rune types are enabled.");
        }
        Collections.shuffle(types);
        return RuneInstance.create(types.get(0).id(), RuneInstance.MIN_TIER);
    }
}
