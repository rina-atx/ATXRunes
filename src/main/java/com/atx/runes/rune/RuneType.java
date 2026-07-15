package com.atx.runes.rune;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public final class RuneType {
    private final String id;
    private final String displayName;
    private final Material material;
    private final Integer customModelData;
    private final List<String> description;
    private final String effectId;
    private final List<RuneMechanicStep> conditions;
    private final List<RuneEffectConfig> effects;
    private final double baseValue;
    private final Map<String, Object> placeholders;

    public RuneType(String id, String displayName, Material material, Integer customModelData, List<String> description, String effectId,
                    List<RuneMechanicStep> conditions, List<RuneEffectConfig> effects, double baseValue, Map<String, Object> placeholders) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.customModelData = customModelData;
        this.description = List.copyOf(description);
        this.effectId = effectId;
        this.conditions = List.copyOf(conditions);
        this.effects = List.copyOf(effects);
        this.baseValue = baseValue;
        this.placeholders = Map.copyOf(placeholders);
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public Material material() {
        return material;
    }

    public Integer customModelData() {
        return customModelData;
    }

    public List<String> description() {
        return description;
    }

    public String effectId() {
        return effectId;
    }

    public List<RuneMechanicStep> conditions() {
        return conditions;
    }

    public List<RuneEffectConfig> effects() {
        return effects;
    }

    public boolean hasMechanicPipeline() {
        return !effects.isEmpty();
    }

    public double baseValue() {
        return baseValue;
    }

    public Map<String, Object> placeholders() {
        return placeholders;
    }

    public double value(int tier) {
        return baseValue * tier;
    }
}
