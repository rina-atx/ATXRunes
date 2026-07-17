package com.atx.runes.core;

import com.ataraxia.atxcore.api.ATXCoreApi;
import com.ataraxia.atxcore.mechanic.ExecutionContext;
import com.ataraxia.atxcore.mechanic.condition.Condition;
import com.ataraxia.atxcore.mechanic.effect.Effect;
import com.ataraxia.atxcore.mechanic.filter.Filter;
import com.ataraxia.atxcore.mechanic.mutator.Mutator;
import com.atx.runes.ATXRunesPlugin;
import com.atx.runes.rune.RuneInstance;
import com.atx.runes.rune.RuneEffectConfig;
import com.atx.runes.rune.RuneMechanicStep;
import com.atx.runes.rune.RunePlaceholders;
import com.atx.runes.rune.RuneType;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ATXCoreBridge {
    private static final int MAX_REPEAT_TIMES = 20;
    private static final int MAX_REPEAT_INTERVAL_TICKS = 20 * 60;

    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("2b7415ce-b907-47dc-9a89-dbb2db29f001");
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("10a6f9af-7d2f-4ae8-9c3d-29b886a0d854");
    private static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString("c0730e11-09a4-4c10-a46a-f6c661de4c52");
    private static final UUID ARMOR_MODIFIER_ID = UUID.fromString("d192b26f-cba9-4a0e-8a83-d8b0ad8a7001");
    private static final UUID LUCK_MODIFIER_ID = UUID.fromString("f4f1f528-39a0-4f28-8017-1c084fca2e90");
    private static final String HEALTH_MODIFIER_NAME = "atxrunes_health";
    private static final String SPEED_MODIFIER_NAME = "atxrunes_speed";
    private static final String ATTACK_DAMAGE_MODIFIER_NAME = "atxrunes_attack_damage";
    private static final String ARMOR_MODIFIER_NAME = "atxrunes_armor";
    private static final String LUCK_MODIFIER_NAME = "atxrunes_luck";

    private final ATXRunesPlugin plugin;
    private final Plugin atxCore;
    private final ATXCoreApi api;
    private final Random random = new Random();
    private final Map<String, Long> cooldowns = new HashMap<>();

    public ATXCoreBridge(ATXRunesPlugin plugin) {
        this.plugin = plugin;
        this.atxCore = Bukkit.getPluginManager().getPlugin("ATXCore");
        this.api = Bukkit.getServicesManager().load(ATXCoreApi.class);
    }

    public boolean isAvailable() {
        return atxCore != null && atxCore.isEnabled() && api != null;
    }

    public void refreshPassiveEffects(Player player, Map<Integer, RuneInstance> equipped) {
        Map<String, Double> totals = totals(equipped);
        double health = totals.getOrDefault("MAX_HEALTH", 0.0);
        double speed = totals.getOrDefault("WALK_SPEED", 0.0);
        double haste = totals.getOrDefault("HASTE", 0.0);
        Map<Attribute, Double> passiveAttributes = passiveAttributeTotals(player, equipped);

        clearConfiguredPermanentPotions(player);
        runEquipped(player, "passive_refresh", null, player, player, Map.of());

        Map<Attribute, Double> attributesToApply = new HashMap<>();
        attributesToApply.putAll(configuredPassiveAttributes());
        attributesToApply.putAll(passiveAttributes);
        attributesToApply.put(Attribute.GENERIC_MAX_HEALTH, health + passiveAttributes.getOrDefault(Attribute.GENERIC_MAX_HEALTH, 0.0));
        attributesToApply.put(Attribute.GENERIC_MOVEMENT_SPEED, speed + passiveAttributes.getOrDefault(Attribute.GENERIC_MOVEMENT_SPEED, 0.0));
        attributesToApply.putIfAbsent(Attribute.GENERIC_ATTACK_DAMAGE, passiveAttributes.getOrDefault(Attribute.GENERIC_ATTACK_DAMAGE, 0.0));
        attributesToApply.putIfAbsent(Attribute.GENERIC_ARMOR, passiveAttributes.getOrDefault(Attribute.GENERIC_ARMOR, 0.0));
        attributesToApply.putIfAbsent(Attribute.GENERIC_LUCK, passiveAttributes.getOrDefault(Attribute.GENERIC_LUCK, 0.0));
        attributesToApply.forEach((attribute, amount) -> applyAttribute(player, attribute, modifierId(attribute), modifierName(attribute), amount));

        if (haste > 0) {
            int amplifier = Math.max(0, (int) Math.round(haste) - 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 20 * 45, amplifier, true, false, true));
        } else {
            player.removePotionEffect(PotionEffectType.FAST_DIGGING);
        }
    }

    public void clearPassiveEffects(Player player) {
        configuredPassiveAttributes().keySet().forEach(attribute -> applyAttribute(player, attribute, modifierId(attribute), modifierName(attribute), 0));
        applyAttribute(player, Attribute.GENERIC_MAX_HEALTH, HEALTH_MODIFIER_ID, HEALTH_MODIFIER_NAME, 0);
        applyAttribute(player, Attribute.GENERIC_MOVEMENT_SPEED, SPEED_MODIFIER_ID, SPEED_MODIFIER_NAME, 0);
        applyAttribute(player, Attribute.GENERIC_ATTACK_DAMAGE, ATTACK_DAMAGE_MODIFIER_ID, ATTACK_DAMAGE_MODIFIER_NAME, 0);
        applyAttribute(player, Attribute.GENERIC_ARMOR, ARMOR_MODIFIER_ID, ARMOR_MODIFIER_NAME, 0);
        applyAttribute(player, Attribute.GENERIC_LUCK, LUCK_MODIFIER_ID, LUCK_MODIFIER_NAME, 0);
        player.removePotionEffect(PotionEffectType.FAST_DIGGING);
        clearConfiguredPermanentPotions(player);
    }

    public double total(Map<Integer, RuneInstance> equipped, String effectId) {
        double value = 0;
        for (RuneInstance rune : equipped.values()) {
            Optional<RuneType> type = plugin.getRuneRegistry().findType(rune.typeId());
            if (type.isPresent() && type.get().effectId().equalsIgnoreCase(effectId)) {
                value += type.get().value(rune.tier());
            }
        }
        return value;
    }

    private Map<String, Double> totals(Map<Integer, RuneInstance> equipped) {
        Map<String, Double> totals = new HashMap<>();
        for (RuneInstance rune : equipped.values()) {
            Optional<RuneType> type = plugin.getRuneRegistry().findType(rune.typeId());
            type.filter(runeType -> !runeType.hasMechanicPipeline())
                .ifPresent(runeType -> totals.merge(runeType.effectId(), runeType.value(rune.tier()), Double::sum));
        }
        return totals;
    }

    private Map<Attribute, Double> passiveAttributeTotals(Player player, Map<Integer, RuneInstance> equipped) {
        Map<Attribute, Double> totals = new HashMap<>();
        for (RuneInstance rune : equipped.values()) {
            Optional<RuneType> maybeType = plugin.getRuneRegistry().findType(rune.typeId());
            if (maybeType.isEmpty()) {
                continue;
            }
            RuneType type = maybeType.get();
            ExecutionContext context = baseContext(player, rune, type, null, player, player, passiveRefreshData(player));
            if (!stepsAllow(context, type.conditions(), type, rune)) {
                continue;
            }
            for (RuneEffectConfig effect : type.effects()) {
                if (!effect.permanent()) {
                    continue;
                }
                if (!stepsAllow(context, effect.conditions(), type, rune) || !filtersAllow(context, effect.filters(), type, rune)) {
                    continue;
                }
                passiveAttribute(effect, type, rune).ifPresent(attribute ->
                    totals.merge(attribute, amount(effect, type, rune), Double::sum));
            }
        }
        return totals;
    }

    public void runEquipped(Player owner, String trigger, Event event, Entity actor, Entity target, Map<String, Object> eventData) {
        if (!isAvailable()) {
            return;
        }
        for (RuneInstance rune : plugin.getRuneStorage().getEquipped(owner.getUniqueId()).values()) {
            plugin.getRuneRegistry().findType(rune.typeId())
                .filter(RuneType::hasMechanicPipeline)
                .ifPresent(type -> runEffects(owner, rune, type, trigger, event, actor, target, eventData));
        }
    }

    private void runEffects(Player owner, RuneInstance rune, RuneType type, String trigger, Event event, Entity actor, Entity target, Map<String, Object> eventData) {
        for (int index = 0; index < type.effects().size(); index++) {
            RuneEffectConfig effectConfig = type.effects().get(index);
            if (!matchesTrigger(effectConfig, trigger)) {
                continue;
            }
            runEffect(owner, rune, type, effectConfig, index, event, actor, target, eventData);
        }
    }

    private void runEffect(Player owner, RuneInstance rune, RuneType type, RuneEffectConfig effectConfig, int effectIndex,
                           Event event, Entity actor, Entity target, Map<String, Object> eventData) {
        if (!passesChance(effectConfig, type, rune) || onCooldown(owner, rune, effectConfig, effectIndex)) {
            return;
        }
        ExecutionContext context = baseContext(owner, rune, type, event, actor, target, eventData);
        for (RuneMechanicStep step : type.conditions()) {
            Optional<Condition<ExecutionContext>> condition = condition(step.id());
            if (condition.isEmpty()) {
                warnMissing("condition", step.id(), type);
                return;
            }
            if (!condition.get().test(withStepData(context, step, type, rune)).passed()) {
                return;
            }
        }
        for (RuneMechanicStep step : effectConfig.conditions()) {
            Optional<Condition<ExecutionContext>> condition = condition(step.id());
            if (condition.isEmpty()) {
                warnMissing("condition", step.id(), type);
                return;
            }
            if (!condition.get().test(withStepData(context, step, type, rune)).passed()) {
                return;
            }
        }
        for (RuneMechanicStep step : effectConfig.mutators()) {
            Optional<Mutator<ExecutionContext>> mutator = mutator(step.id());
            if (mutator.isEmpty()) {
                warnMissing("mutator", step.id(), type);
                return;
            }
            context = mutator.get().mutate(withStepData(context, step, type, rune));
        }
        for (RuneMechanicStep step : effectConfig.filters()) {
            if (!filterAllows(context, step, type, rune)) {
                return;
            }
        }
        if (effectConfig.permanent() && passiveAttribute(effectConfig, type, rune).isPresent()) {
            return;
        }
        ExecutionContext finalContext = context;
        UUID ownerId = owner.getUniqueId();
        UUID runeId = rune.id();
        Runnable task = () -> {
            Player currentOwner = Bukkit.getPlayer(ownerId);
            if (currentOwner == null || !isEquipped(currentOwner, runeId)) {
                return;
            }
            Optional<Effect<ExecutionContext>> effect = effect(effectConfig.id());
            if (effect.isEmpty()) {
                warnMissing("effect", effectConfig.id(), type);
                return;
            }
            effect.get().execute(withEffectArgs(finalContext, effectConfig, type, rune));
        };
        int delay = intArg(effectConfig, "delay", type, rune, 0);
        int times = repeatTimes(effectConfig);
        int interval = repeatInterval(effectConfig);
        for (int repeat = 0; repeat < times; repeat++) {
            long runDelay = Math.max(0, delay) + (long) repeat * Math.max(1, interval);
            if (runDelay <= 0) {
                task.run();
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, task, runDelay);
            }
        }
    }

    private ExecutionContext baseContext(Player owner, RuneInstance rune, RuneType type, Event event, Entity actor, Entity target, Map<String, Object> eventData) {
        return ExecutionContext.builder()
            .player(owner)
            .actor(actor == null ? owner : actor)
            .target(target == null ? owner : target)
            .activeEntity(owner)
            .location(owner.getLocation())
            .event(event)
            .data(eventData)
            .data("rune_id", rune.id().toString())
            .data("rune_type", rune.typeId())
            .data("rune_tier", rune.tier())
            .data("tier", rune.tier())
            .data("level", rune.tier())
            .data("value", type.value(rune.tier()))
            .data("trigger_value", eventData.getOrDefault("value", eventData.getOrDefault("damage", type.value(rune.tier()))))
            .data("base_value", type.baseValue())
            .build();
    }

    private boolean matchesTrigger(RuneEffectConfig effectConfig, String trigger) {
        if (effectConfig.permanent()) {
            return trigger.equalsIgnoreCase("passive_refresh");
        }
        String normalized = normalizeTrigger(trigger);
        return effectConfig.triggers().stream().map(this::normalizeTrigger).anyMatch(normalized::equals);
    }

    private String normalizeTrigger(String trigger) {
        return switch (trigger.toLowerCase()) {
            case "mine_block" -> "block_break";
            case "place_block" -> "block_place";
            case "join" -> "player_join";
            case "leave" -> "player_quit";
            case "take_damage" -> "entity_damage";
            case "take_entity_damage" -> "entity_damage_by_entity";
            case "click_block", "alt_click" -> "player_interact";
            default -> trigger.toLowerCase();
        };
    }

    private Optional<Attribute> passiveAttribute(RuneEffectConfig effect, RuneType type, RuneInstance rune) {
        String id = effect.id();
        if (id.equals("add_max_health")) {
            return Optional.of(Attribute.GENERIC_MAX_HEALTH);
        }
        if (id.equals("add_movement_speed")) {
            return Optional.of(Attribute.GENERIC_MOVEMENT_SPEED);
        }
        if (id.equals("add_attack_damage")) {
            return Optional.of(Attribute.GENERIC_ATTACK_DAMAGE);
        }
        if (id.equals("add_armor")) {
            return Optional.of(Attribute.GENERIC_ARMOR);
        }
        if (id.equals("set_luck")) {
            return Optional.of(Attribute.GENERIC_LUCK);
        }
        if (id.equals("add_attribute")) {
            return resolveAttribute(String.valueOf(resolve(effect.args().getOrDefault("attribute", ""), type, rune)));
        }
        return Optional.empty();
    }

    private boolean stepsAllow(ExecutionContext context, List<RuneMechanicStep> steps, RuneType type, RuneInstance rune) {
        for (RuneMechanicStep step : steps) {
            Optional<Condition<ExecutionContext>> condition = condition(step.id());
            if (condition.isEmpty()) {
                warnMissing("condition", step.id(), type);
                return false;
            }
            if (!condition.get().test(withStepData(context, step, type, rune)).passed()) {
                return false;
            }
        }
        return true;
    }

    private boolean filtersAllow(ExecutionContext context, List<RuneMechanicStep> filters, RuneType type, RuneInstance rune) {
        for (RuneMechanicStep filter : filters) {
            if (!filterAllows(context, filter, type, rune)) {
                return false;
            }
        }
        return true;
    }

    private Optional<Attribute> resolveAttribute(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase()
            .replace("MINECRAFT:", "")
            .replace('-', '_')
            .replace(' ', '_');
        return exactAttribute(normalized)
            .or(() -> exactAttribute("GENERIC_" + normalized))
            .or(() -> exactAttribute(normalized.replace("GENERIC_", "")));
    }

    private Optional<Attribute> exactAttribute(String name) {
        try {
            return Optional.of(Attribute.valueOf(name));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private double amount(RuneEffectConfig effect, RuneType type, RuneInstance rune) {
        Object raw = resolve(effect.args().getOrDefault("amount", "{value}"), type, rune);
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return type.value(rune.tier());
        }
    }

    private ExecutionContext withStepData(ExecutionContext context, RuneMechanicStep step, RuneType type, RuneInstance rune) {
        Map<String, Object> data = new LinkedHashMap<>(context.data());
        step.data().forEach((key, value) -> data.put(key, resolve(value, type, rune)));
        return context.toBuilder().data(data).build();
    }

    private ExecutionContext withEffectArgs(ExecutionContext context, RuneEffectConfig effectConfig, RuneType type, RuneInstance rune) {
        Map<String, Object> data = new LinkedHashMap<>(context.data());
        effectConfig.args().forEach((key, value) -> data.put(key, resolve(value, type, rune)));
        return context.toBuilder().data(data).build();
    }

    private boolean filterAllows(ExecutionContext context, RuneMechanicStep step, RuneType type, RuneInstance rune) {
        String id = step.id();
        if (id.equals("blocks")) {
            return contains(step.data().get("values"), context.stringData("block_type").orElse(""));
        }
        if (id.equals("entities")) {
            String targetType = context.stringData("target_type").orElse(context.stringData("entity_type").orElse(""));
            return contains(step.data().get("values"), targetType);
        }
        if (id.equals("not_entities")) {
            String targetType = context.stringData("target_type").orElse(context.stringData("entity_type").orElse(""));
            return !contains(step.data().get("values"), targetType);
        }
        if (id.equals("held_item_groups")) {
            String material = context.stringData("held_material").orElse("");
            return heldItemGroupAllows(step.data().get("values"), material);
        }
        if (id.equals("night")) {
            return Boolean.TRUE.equals(context.data().get("is_night"));
        }
        if (id.equals("critical")) {
            return Boolean.TRUE.equals(context.data().get("critical"));
        }
        Optional<Filter<ExecutionContext>> filter = filter(id);
        if (filter.isEmpty()) {
            warnMissing("filter", id, type);
            return false;
        }
        return filter.get().allow(withStepData(context, step, type, rune));
    }

    private boolean contains(Object rawValues, String candidate) {
        if (rawValues instanceof List<?> list) {
            return list.stream().map(String::valueOf).anyMatch(value -> normalizeMinecraft(value).equals(normalizeMinecraft(candidate)));
        }
        return normalizeMinecraft(String.valueOf(rawValues)).equals(normalizeMinecraft(candidate));
    }

    private String normalizeMinecraft(String value) {
        return value == null ? "" : value.toUpperCase().replace("MINECRAFT:", "").replace('-', '_').replace(' ', '_');
    }

    private boolean heldItemGroupAllows(Object rawValues, String material) {
        if (!(rawValues instanceof List<?> list)) {
            return heldItemGroupAllows(List.of(rawValues), material);
        }
        String normalized = normalizeMinecraft(material);
        for (Object raw : list) {
            String group = String.valueOf(raw).toLowerCase();
            if (group.equals("swords") && normalized.endsWith("_SWORD")) {
                return true;
            }
            if (group.equals("axes") && normalized.endsWith("_AXE")) {
                return true;
            }
            if (group.equals("swords_or_axes") && (normalized.endsWith("_SWORD") || normalized.endsWith("_AXE"))) {
                return true;
            }
            if (normalizeMinecraft(group).equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> passiveRefreshData(Player player) {
        long time = player.getWorld().getTime();
        return Map.of(
            "world_time", time,
            "is_night", time >= 13000 && time <= 23000,
            "held_material", player.getInventory().getItemInMainHand().getType().name()
        );
    }

    private boolean passesChance(RuneEffectConfig effectConfig, RuneType type, RuneInstance rune) {
        double chance = doubleArg(effectConfig, "chance", type, rune, 100);
        return chance >= 100 || random.nextDouble() * 100 <= chance;
    }

    private boolean onCooldown(Player owner, RuneInstance rune, RuneEffectConfig effectConfig, int effectIndex) {
        double seconds = doubleArg(effectConfig, "cooldown", null, rune, 0);
        if (seconds <= 0) {
            return false;
        }
        String group = String.valueOf(effectConfig.args().getOrDefault("cooldown_group", rune.id() + ":" + effectIndex));
        String key = owner.getUniqueId() + ":" + group;
        long now = System.currentTimeMillis();
        long until = cooldowns.getOrDefault(key, 0L);
        if (until > now) {
            return true;
        }
        cooldowns.put(key, now + Math.round(seconds * 1000));
        return false;
    }

    private int repeatTimes(RuneEffectConfig effectConfig) {
        Object repeat = effectConfig.args().get("repeat");
        if (repeat instanceof Map<?, ?> map && map.get("times") != null) {
            return clamp(parseInt(map.get("times"), 1), 1, MAX_REPEAT_TIMES);
        }
        return 1;
    }

    private int repeatInterval(RuneEffectConfig effectConfig) {
        Object repeat = effectConfig.args().get("repeat");
        if (repeat instanceof Map<?, ?> map && map.get("interval") != null) {
            return clamp(parseInt(map.get("interval"), 1), 1, MAX_REPEAT_INTERVAL_TICKS);
        }
        return 1;
    }

    private boolean isEquipped(Player player, UUID runeId) {
        return plugin.getRuneStorage().getEquipped(player.getUniqueId()).values().stream()
            .anyMatch(equipped -> equipped.id().equals(runeId));
    }

    private void clearConfiguredPermanentPotions(Player player) {
        for (RuneType type : plugin.getRuneRegistry().allTypes()) {
            for (RuneEffectConfig effect : type.effects()) {
                if (!effect.permanent() || !effect.id().equals("add_potion")) {
                    continue;
                }
                resolvePotionType(effect.args().get("type")).ifPresent(player::removePotionEffect);
            }
        }
    }

    private Optional<PotionEffectType> resolvePotionType(Object raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = String.valueOf(raw).trim()
            .replace("minecraft:", "")
            .replace('-', '_');
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(PotionEffectType.getByName(normalized));
    }

    private Object resolve(Object value, RuneType type, RuneInstance rune) {
        return RunePlaceholders.resolve(value, type, rune);
    }

    private double doubleArg(RuneEffectConfig effectConfig, String key, RuneType type, RuneInstance rune, double fallback) {
        Object raw = effectConfig.args().get(key);
        if (raw == null) {
            return fallback;
        }
        Object resolved = type == null ? raw : resolve(raw, type, rune);
        try {
            return Double.parseDouble(String.valueOf(resolved));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private int intArg(RuneEffectConfig effectConfig, String key, RuneType type, RuneInstance rune, int fallback) {
        return (int) Math.round(doubleArg(effectConfig, key, type, rune, fallback));
    }

    private int parseInt(Object value, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @SuppressWarnings("unchecked")
    private Optional<Condition<ExecutionContext>> condition(String id) {
        return api.registries().conditions().get(id).map(condition -> (Condition<ExecutionContext>) condition);
    }

    @SuppressWarnings("unchecked")
    private Optional<Mutator<ExecutionContext>> mutator(String id) {
        return api.registries().mutators().get(id).map(mutator -> (Mutator<ExecutionContext>) mutator);
    }

    @SuppressWarnings("unchecked")
    private Optional<Filter<ExecutionContext>> filter(String id) {
        return api.registries().filters().get(id).map(filter -> (Filter<ExecutionContext>) filter);
    }

    @SuppressWarnings("unchecked")
    private Optional<Effect<ExecutionContext>> effect(String id) {
        return api.registries().effects().get(id).map(effect -> (Effect<ExecutionContext>) effect);
    }

    private void warnMissing(String type, String id, RuneType runeType) {
        plugin.getLogger().warning("Rune " + runeType.id() + " references unknown ATXCore " + type + ": " + id);
    }

    private void applyAttribute(Player player, Attribute attribute, UUID id, String name, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.getModifiers().stream()
            .filter(modifier -> modifier.getUniqueId().equals(id) || modifier.getName().equals(name))
            .toList()
            .forEach(instance::removeModifier);

        if (amount != 0) {
            instance.addModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADD_NUMBER));
        }

        if (attribute == Attribute.GENERIC_MAX_HEALTH && player.getHealth() > instance.getValue()) {
            player.setHealth(Math.max(1.0, instance.getValue()));
        }
    }

    private Map<Attribute, Double> configuredPassiveAttributes() {
        Map<Attribute, Double> attributes = new HashMap<>();
        for (RuneType type : plugin.getRuneRegistry().allTypes()) {
            for (RuneEffectConfig effect : type.effects()) {
                if (effect.permanent()) {
                    passiveAttribute(effect, type, RuneInstance.create(type.id(), RuneInstance.MIN_TIER))
                        .ifPresent(attribute -> attributes.put(attribute, 0.0));
                }
            }
        }
        return attributes;
    }

    private UUID modifierId(Attribute attribute) {
        if (attribute == Attribute.GENERIC_MAX_HEALTH) return HEALTH_MODIFIER_ID;
        if (attribute == Attribute.GENERIC_MOVEMENT_SPEED) return SPEED_MODIFIER_ID;
        if (attribute == Attribute.GENERIC_ATTACK_DAMAGE) return ATTACK_DAMAGE_MODIFIER_ID;
        if (attribute == Attribute.GENERIC_ARMOR) return ARMOR_MODIFIER_ID;
        if (attribute == Attribute.GENERIC_LUCK) return LUCK_MODIFIER_ID;
        return UUID.nameUUIDFromBytes(("atxrunes:" + attribute.name()).getBytes(StandardCharsets.UTF_8));
    }

    private String modifierName(Attribute attribute) {
        if (attribute == Attribute.GENERIC_MAX_HEALTH) return HEALTH_MODIFIER_NAME;
        if (attribute == Attribute.GENERIC_MOVEMENT_SPEED) return SPEED_MODIFIER_NAME;
        if (attribute == Attribute.GENERIC_ATTACK_DAMAGE) return ATTACK_DAMAGE_MODIFIER_NAME;
        if (attribute == Attribute.GENERIC_ARMOR) return ARMOR_MODIFIER_NAME;
        if (attribute == Attribute.GENERIC_LUCK) return LUCK_MODIFIER_NAME;
        return "atxrunes_" + attribute.name().toLowerCase();
    }

}
package com.atx.runes.core;

import com.ataraxia.atxcore.api.ATXCoreApi;
import com.ataraxia.atxcore.mechanic.ExecutionContext;
import com.ataraxia.atxcore.mechanic.condition.Condition;
import com.ataraxia.atxcore.mechanic.effect.Effect;
import com.ataraxia.atxcore.mechanic.filter.Filter;
import com.ataraxia.atxcore.mechanic.mutator.Mutator;
import com.atx.runes.ATXRunesPlugin;
import com.atx.runes.rune.RuneInstance;
import com.atx.runes.rune.RuneEffectConfig;
import com.atx.runes.rune.RuneMechanicStep;
import com.atx.runes.rune.RunePlaceholders;
import com.atx.runes.rune.RuneType;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public final class ATXCoreBridge {
    private static final int MAX_REPEAT_TIMES = 20;
    private static final int MAX_REPEAT_INTERVAL_TICKS = 20 * 60;

    private static final UUID HEALTH_MODIFIER_ID = UUID.fromString("2b7415ce-b907-47dc-9a89-dbb2db29f001");
    private static final UUID SPEED_MODIFIER_ID = UUID.fromString("10a6f9af-7d2f-4ae8-9c3d-29b886a0d854");
    private static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString("c0730e11-09a4-4c10-a46a-f6c661de4c52");
    private static final UUID ARMOR_MODIFIER_ID = UUID.fromString("d192b26f-cba9-4a0e-8a83-d8b0ad8a7001");
    private static final UUID LUCK_MODIFIER_ID = UUID.fromString("f4f1f528-39a0-4f28-8017-1c084fca2e90");
    private static final String HEALTH_MODIFIER_NAME = "atxrunes_health";
    private static final String SPEED_MODIFIER_NAME = "atxrunes_speed";
    private static final String ATTACK_DAMAGE_MODIFIER_NAME = "atxrunes_attack_damage";
    private static final String ARMOR_MODIFIER_NAME = "atxrunes_armor";
    private static final String LUCK_MODIFIER_NAME = "atxrunes_luck";

    private final ATXRunesPlugin plugin;
    private final Plugin atxCore;
    private final ATXCoreApi api;
    private final Random random = new Random();
    private final Map<String, Long> cooldowns = new HashMap<>();

    public ATXCoreBridge(ATXRunesPlugin plugin) {
        this.plugin = plugin;
        this.atxCore = Bukkit.getPluginManager().getPlugin("ATXCore");
        this.api = Bukkit.getServicesManager().load(ATXCoreApi.class);
    }

    public boolean isAvailable() {
        return atxCore != null && atxCore.isEnabled() && api != null;
    }

    public void refreshPassiveEffects(Player player, Map<Integer, RuneInstance> equipped) {
        Map<String, Double> totals = totals(equipped);
        double health = totals.getOrDefault("MAX_HEALTH", 0.0);
        double speed = totals.getOrDefault("WALK_SPEED", 0.0);
        double haste = totals.getOrDefault("HASTE", 0.0);
        Map<Attribute, Double> passiveAttributes = passiveAttributeTotals(equipped);

        clearConfiguredPermanentPotions(player);
        runEquipped(player, "passive_refresh", null, player, player, Map.of());

        applyAttribute(player, Attribute.GENERIC_MAX_HEALTH, HEALTH_MODIFIER_ID, HEALTH_MODIFIER_NAME,
            health + passiveAttributes.getOrDefault(Attribute.GENERIC_MAX_HEALTH, 0.0));
        applyAttribute(player, Attribute.GENERIC_MOVEMENT_SPEED, SPEED_MODIFIER_ID, SPEED_MODIFIER_NAME,
            speed + passiveAttributes.getOrDefault(Attribute.GENERIC_MOVEMENT_SPEED, 0.0));
        applyAttribute(player, Attribute.GENERIC_ATTACK_DAMAGE, ATTACK_DAMAGE_MODIFIER_ID, ATTACK_DAMAGE_MODIFIER_NAME,
            passiveAttributes.getOrDefault(Attribute.GENERIC_ATTACK_DAMAGE, 0.0));
        applyAttribute(player, Attribute.GENERIC_ARMOR, ARMOR_MODIFIER_ID, ARMOR_MODIFIER_NAME,
            passiveAttributes.getOrDefault(Attribute.GENERIC_ARMOR, 0.0));
        applyAttribute(player, Attribute.GENERIC_LUCK, LUCK_MODIFIER_ID, LUCK_MODIFIER_NAME,
            passiveAttributes.getOrDefault(Attribute.GENERIC_LUCK, 0.0));

        if (haste > 0) {
            int amplifier = Math.max(0, (int) Math.round(haste) - 1);
            player.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 20 * 45, amplifier, true, false, true));
        } else {
            player.removePotionEffect(PotionEffectType.FAST_DIGGING);
        }
    }

    public void clearPassiveEffects(Player player) {
        applyAttribute(player, Attribute.GENERIC_MAX_HEALTH, HEALTH_MODIFIER_ID, HEALTH_MODIFIER_NAME, 0);
        applyAttribute(player, Attribute.GENERIC_MOVEMENT_SPEED, SPEED_MODIFIER_ID, SPEED_MODIFIER_NAME, 0);
        applyAttribute(player, Attribute.GENERIC_ATTACK_DAMAGE, ATTACK_DAMAGE_MODIFIER_ID, ATTACK_DAMAGE_MODIFIER_NAME, 0);
        applyAttribute(player, Attribute.GENERIC_ARMOR, ARMOR_MODIFIER_ID, ARMOR_MODIFIER_NAME, 0);
        applyAttribute(player, Attribute.GENERIC_LUCK, LUCK_MODIFIER_ID, LUCK_MODIFIER_NAME, 0);
        player.removePotionEffect(PotionEffectType.FAST_DIGGING);
        clearConfiguredPermanentPotions(player);
    }

    public double total(Map<Integer, RuneInstance> equipped, String effectId) {
        double value = 0;
        for (RuneInstance rune : equipped.values()) {
            Optional<RuneType> type = plugin.getRuneRegistry().findType(rune.typeId());
            if (type.isPresent() && type.get().effectId().equalsIgnoreCase(effectId)) {
                value += type.get().value(rune.tier());
            }
        }
        return value;
    }

    private Map<String, Double> totals(Map<Integer, RuneInstance> equipped) {
        Map<String, Double> totals = new HashMap<>();
        for (RuneInstance rune : equipped.values()) {
            Optional<RuneType> type = plugin.getRuneRegistry().findType(rune.typeId());
            type.filter(runeType -> !runeType.hasMechanicPipeline())
                .ifPresent(runeType -> totals.merge(runeType.effectId(), runeType.value(rune.tier()), Double::sum));
        }
        return totals;
    }

    private Map<Attribute, Double> passiveAttributeTotals(Map<Integer, RuneInstance> equipped) {
        Map<Attribute, Double> totals = new HashMap<>();
        for (RuneInstance rune : equipped.values()) {
            Optional<RuneType> maybeType = plugin.getRuneRegistry().findType(rune.typeId());
            if (maybeType.isEmpty()) {
                continue;
            }
            RuneType type = maybeType.get();
            for (RuneEffectConfig effect : type.effects()) {
                if (!effect.permanent()) {
                    continue;
                }
                passiveAttribute(effect, type, rune).ifPresent(attribute ->
                    totals.merge(attribute, amount(effect, type, rune), Double::sum));
            }
        }
        return totals;
    }

    public void runEquipped(Player owner, String trigger, Event event, Entity actor, Entity target, Map<String, Object> eventData) {
        if (!isAvailable()) {
            return;
        }
        for (RuneInstance rune : plugin.getRuneStorage().getEquipped(owner.getUniqueId()).values()) {
            plugin.getRuneRegistry().findType(rune.typeId())
                .filter(RuneType::hasMechanicPipeline)
                .ifPresent(type -> runEffects(owner, rune, type, trigger, event, actor, target, eventData));
        }
    }

    private void runEffects(Player owner, RuneInstance rune, RuneType type, String trigger, Event event, Entity actor, Entity target, Map<String, Object> eventData) {
        for (int index = 0; index < type.effects().size(); index++) {
            RuneEffectConfig effectConfig = type.effects().get(index);
            if (!matchesTrigger(effectConfig, trigger)) {
                continue;
            }
            runEffect(owner, rune, type, effectConfig, index, event, actor, target, eventData);
        }
    }

    private void runEffect(Player owner, RuneInstance rune, RuneType type, RuneEffectConfig effectConfig, int effectIndex,
                           Event event, Entity actor, Entity target, Map<String, Object> eventData) {
        if (!passesChance(effectConfig, type, rune) || onCooldown(owner, rune, effectConfig, effectIndex)) {
            return;
        }
        ExecutionContext context = baseContext(owner, rune, type, event, actor, target, eventData);
        for (RuneMechanicStep step : type.conditions()) {
            Optional<Condition<ExecutionContext>> condition = condition(step.id());
            if (condition.isEmpty()) {
                warnMissing("condition", step.id(), type);
                return;
            }
            if (!condition.get().test(withStepData(context, step, type, rune)).passed()) {
                return;
            }
        }
        for (RuneMechanicStep step : effectConfig.conditions()) {
            Optional<Condition<ExecutionContext>> condition = condition(step.id());
            if (condition.isEmpty()) {
                warnMissing("condition", step.id(), type);
                return;
            }
            if (!condition.get().test(withStepData(context, step, type, rune)).passed()) {
                return;
            }
        }
        for (RuneMechanicStep step : effectConfig.mutators()) {
            Optional<Mutator<ExecutionContext>> mutator = mutator(step.id());
            if (mutator.isEmpty()) {
                warnMissing("mutator", step.id(), type);
                return;
            }
            context = mutator.get().mutate(withStepData(context, step, type, rune));
        }
        for (RuneMechanicStep step : effectConfig.filters()) {
            if (!filterAllows(context, step, type, rune)) {
                return;
            }
        }
        if (effectConfig.permanent() && passiveAttribute(effectConfig, type, rune).isPresent()) {
            return;
        }
        ExecutionContext finalContext = context;
        UUID ownerId = owner.getUniqueId();
        UUID runeId = rune.id();
        Runnable task = () -> {
            Player currentOwner = Bukkit.getPlayer(ownerId);
            if (currentOwner == null || !isEquipped(currentOwner, runeId)) {
                return;
            }
            Optional<Effect<ExecutionContext>> effect = effect(effectConfig.id());
            if (effect.isEmpty()) {
                warnMissing("effect", effectConfig.id(), type);
                return;
            }
            effect.get().execute(withEffectArgs(finalContext, effectConfig, type, rune));
        };
        int delay = intArg(effectConfig, "delay", type, rune, 0);
        int times = repeatTimes(effectConfig);
        int interval = repeatInterval(effectConfig);
        for (int repeat = 0; repeat < times; repeat++) {
            long runDelay = Math.max(0, delay) + (long) repeat * Math.max(1, interval);
            if (runDelay <= 0) {
                task.run();
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, task, runDelay);
            }
        }
    }

    private ExecutionContext baseContext(Player owner, RuneInstance rune, RuneType type, Event event, Entity actor, Entity target, Map<String, Object> eventData) {
        return ExecutionContext.builder()
            .player(owner)
            .actor(actor == null ? owner : actor)
            .target(target == null ? owner : target)
            .activeEntity(owner)
            .location(owner.getLocation())
            .event(event)
            .data(eventData)
            .data("rune_id", rune.id().toString())
            .data("rune_type", rune.typeId())
            .data("rune_tier", rune.tier())
            .data("tier", rune.tier())
            .data("level", rune.tier())
            .data("value", type.value(rune.tier()))
            .data("trigger_value", eventData.getOrDefault("value", eventData.getOrDefault("damage", type.value(rune.tier()))))
            .data("base_value", type.baseValue())
            .build();
    }

    private boolean matchesTrigger(RuneEffectConfig effectConfig, String trigger) {
        if (effectConfig.permanent()) {
            return trigger.equalsIgnoreCase("passive_refresh");
        }
        String normalized = normalizeTrigger(trigger);
        return effectConfig.triggers().stream().map(this::normalizeTrigger).anyMatch(normalized::equals);
    }

    private String normalizeTrigger(String trigger) {
        return switch (trigger.toLowerCase()) {
            case "mine_block" -> "block_break";
            case "place_block" -> "block_place";
            case "join" -> "player_join";
            case "leave" -> "player_quit";
            case "take_damage" -> "entity_damage";
            case "take_entity_damage" -> "entity_damage_by_entity";
            case "click_block", "alt_click" -> "player_interact";
            default -> trigger.toLowerCase();
        };
    }

    private Optional<Attribute> passiveAttribute(RuneEffectConfig effect, RuneType type, RuneInstance rune) {
        String id = effect.id();
        if (id.equals("add_max_health")) {
            return Optional.of(Attribute.GENERIC_MAX_HEALTH);
        }
        if (id.equals("add_movement_speed")) {
            return Optional.of(Attribute.GENERIC_MOVEMENT_SPEED);
        }
        if (id.equals("add_attack_damage")) {
            return Optional.of(Attribute.GENERIC_ATTACK_DAMAGE);
        }
        if (id.equals("add_armor")) {
            return Optional.of(Attribute.GENERIC_ARMOR);
        }
        if (id.equals("set_luck")) {
            return Optional.of(Attribute.GENERIC_LUCK);
        }
        if (id.equals("add_attribute")) {
            return resolveAttribute(String.valueOf(resolve(effect.args().getOrDefault("attribute", ""), type, rune)));
        }
        return Optional.empty();
    }

    private Optional<Attribute> resolveAttribute(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase()
            .replace("MINECRAFT:", "")
            .replace('-', '_')
            .replace(' ', '_');
        return exactAttribute(normalized)
            .or(() -> exactAttribute("GENERIC_" + normalized))
            .or(() -> exactAttribute(normalized.replace("GENERIC_", "")));
    }

    private Optional<Attribute> exactAttribute(String name) {
        try {
            return Optional.of(Attribute.valueOf(name));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private double amount(RuneEffectConfig effect, RuneType type, RuneInstance rune) {
        Object raw = resolve(effect.args().getOrDefault("amount", "{value}"), type, rune);
        try {
            return Double.parseDouble(String.valueOf(raw));
        } catch (NumberFormatException ex) {
            return type.value(rune.tier());
        }
    }

    private ExecutionContext withStepData(ExecutionContext context, RuneMechanicStep step, RuneType type, RuneInstance rune) {
        Map<String, Object> data = new LinkedHashMap<>(context.data());
        step.data().forEach((key, value) -> data.put(key, resolve(value, type, rune)));
        return context.toBuilder().data(data).build();
    }

    private ExecutionContext withEffectArgs(ExecutionContext context, RuneEffectConfig effectConfig, RuneType type, RuneInstance rune) {
        Map<String, Object> data = new LinkedHashMap<>(context.data());
        effectConfig.args().forEach((key, value) -> data.put(key, resolve(value, type, rune)));
        return context.toBuilder().data(data).build();
    }

    private boolean filterAllows(ExecutionContext context, RuneMechanicStep step, RuneType type, RuneInstance rune) {
        String id = step.id();
        if (id.equals("blocks")) {
            return contains(step.data().get("values"), context.stringData("block_type").orElse(""));
        }
        if (id.equals("entities")) {
            String targetType = context.stringData("target_type").orElse(context.stringData("entity_type").orElse(""));
            return contains(step.data().get("values"), targetType);
        }
        if (id.equals("not_entities")) {
            String targetType = context.stringData("target_type").orElse(context.stringData("entity_type").orElse(""));
            return !contains(step.data().get("values"), targetType);
        }
        Optional<Filter<ExecutionContext>> filter = filter(id);
        if (filter.isEmpty()) {
            warnMissing("filter", id, type);
            return false;
        }
        return filter.get().allow(withStepData(context, step, type, rune));
    }

    private boolean contains(Object rawValues, String candidate) {
        if (rawValues instanceof List<?> list) {
            return list.stream().map(String::valueOf).anyMatch(value -> normalizeMinecraft(value).equals(normalizeMinecraft(candidate)));
        }
        return normalizeMinecraft(String.valueOf(rawValues)).equals(normalizeMinecraft(candidate));
    }

    private String normalizeMinecraft(String value) {
        return value == null ? "" : value.toUpperCase().replace("MINECRAFT:", "").replace('-', '_').replace(' ', '_');
    }

    private boolean passesChance(RuneEffectConfig effectConfig, RuneType type, RuneInstance rune) {
        double chance = doubleArg(effectConfig, "chance", type, rune, 100);
        return chance >= 100 || random.nextDouble() * 100 <= chance;
    }

    private boolean onCooldown(Player owner, RuneInstance rune, RuneEffectConfig effectConfig, int effectIndex) {
        double seconds = doubleArg(effectConfig, "cooldown", null, rune, 0);
        if (seconds <= 0) {
            return false;
        }
        String group = String.valueOf(effectConfig.args().getOrDefault("cooldown_group", rune.id() + ":" + effectIndex));
        String key = owner.getUniqueId() + ":" + group;
        long now = System.currentTimeMillis();
        long until = cooldowns.getOrDefault(key, 0L);
        if (until > now) {
            return true;
        }
        cooldowns.put(key, now + Math.round(seconds * 1000));
        return false;
    }

    private int repeatTimes(RuneEffectConfig effectConfig) {
        Object repeat = effectConfig.args().get("repeat");
        if (repeat instanceof Map<?, ?> map && map.get("times") != null) {
            return clamp(parseInt(map.get("times"), 1), 1, MAX_REPEAT_TIMES);
        }
        return 1;
    }

    private int repeatInterval(RuneEffectConfig effectConfig) {
        Object repeat = effectConfig.args().get("repeat");
        if (repeat instanceof Map<?, ?> map && map.get("interval") != null) {
            return clamp(parseInt(map.get("interval"), 1), 1, MAX_REPEAT_INTERVAL_TICKS);
        }
        return 1;
    }

    private boolean isEquipped(Player player, UUID runeId) {
        return plugin.getRuneStorage().getEquipped(player.getUniqueId()).values().stream()
            .anyMatch(equipped -> equipped.id().equals(runeId));
    }

    private void clearConfiguredPermanentPotions(Player player) {
        for (RuneType type : plugin.getRuneRegistry().allTypes()) {
            for (RuneEffectConfig effect : type.effects()) {
                if (!effect.permanent() || !effect.id().equals("add_potion")) {
                    continue;
                }
                resolvePotionType(effect.args().get("type")).ifPresent(player::removePotionEffect);
            }
        }
    }

    private Optional<PotionEffectType> resolvePotionType(Object raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = String.valueOf(raw).trim()
            .replace("minecraft:", "")
            .replace('-', '_');
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(PotionEffectType.getByName(normalized));
    }

    private Object resolve(Object value, RuneType type, RuneInstance rune) {
        return RunePlaceholders.resolve(value, type, rune);
    }

    private double doubleArg(RuneEffectConfig effectConfig, String key, RuneType type, RuneInstance rune, double fallback) {
        Object raw = effectConfig.args().get(key);
        if (raw == null) {
            return fallback;
        }
        Object resolved = type == null ? raw : resolve(raw, type, rune);
        try {
            return Double.parseDouble(String.valueOf(resolved));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private int intArg(RuneEffectConfig effectConfig, String key, RuneType type, RuneInstance rune, int fallback) {
        return (int) Math.round(doubleArg(effectConfig, key, type, rune, fallback));
    }

    private int parseInt(Object value, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @SuppressWarnings("unchecked")
    private Optional<Condition<ExecutionContext>> condition(String id) {
        return api.registries().conditions().get(id).map(condition -> (Condition<ExecutionContext>) condition);
    }

    @SuppressWarnings("unchecked")
    private Optional<Mutator<ExecutionContext>> mutator(String id) {
        return api.registries().mutators().get(id).map(mutator -> (Mutator<ExecutionContext>) mutator);
    }

    @SuppressWarnings("unchecked")
    private Optional<Filter<ExecutionContext>> filter(String id) {
        return api.registries().filters().get(id).map(filter -> (Filter<ExecutionContext>) filter);
    }

    @SuppressWarnings("unchecked")
    private Optional<Effect<ExecutionContext>> effect(String id) {
        return api.registries().effects().get(id).map(effect -> (Effect<ExecutionContext>) effect);
    }

    private void warnMissing(String type, String id, RuneType runeType) {
        plugin.getLogger().warning("Rune " + runeType.id() + " references unknown ATXCore " + type + ": " + id);
    }

    private void applyAttribute(Player player, Attribute attribute, UUID id, String name, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.getModifiers().stream()
            .filter(modifier -> modifier.getUniqueId().equals(id) || modifier.getName().equals(name))
            .toList()
            .forEach(instance::removeModifier);

        if (amount != 0) {
            instance.addModifier(new AttributeModifier(id, name, amount, AttributeModifier.Operation.ADD_NUMBER));
        }

        if (attribute == Attribute.GENERIC_MAX_HEALTH && player.getHealth() > instance.getValue()) {
            player.setHealth(Math.max(1.0, instance.getValue()));
        }
    }

}
