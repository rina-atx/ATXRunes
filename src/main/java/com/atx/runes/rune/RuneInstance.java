package com.atx.runes.rune;

import java.util.UUID;

public final class RuneInstance {
    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 7;

    private final UUID id;
    private final String typeId;
    private final int tier;

    public RuneInstance(UUID id, String typeId, int tier) {
        this.id = id;
        this.typeId = typeId;
        this.tier = clampTier(tier);
    }

    public static RuneInstance create(String typeId, int tier) {
        return new RuneInstance(UUID.randomUUID(), typeId, tier);
    }

    public UUID id() {
        return id;
    }

    public String typeId() {
        return typeId;
    }

    public int tier() {
        return tier;
    }

    public RuneInstance withTier(int nextTier) {
        return new RuneInstance(id, typeId, nextTier);
    }

    public RuneInstance nextTier() {
        return withTier(tier + 1);
    }

    public RuneInstance withType(String nextTypeId) {
        return new RuneInstance(id, nextTypeId, tier);
    }

    public String serialize() {
        return id + ";" + typeId + ";" + tier;
    }

    public static java.util.Optional<RuneInstance> deserialize(String raw) {
        String[] pieces = raw.split(";");
        if (pieces.length != 3) {
            return java.util.Optional.empty();
        }
        try {
            UUID id = UUID.fromString(pieces[0]);
            return java.util.Optional.of(new RuneInstance(id, pieces[1], parseTier(pieces[2])));
        } catch (IllegalArgumentException ex) {
            return java.util.Optional.empty();
        }
    }

    private static int parseTier(String raw) {
        try {
            return clampTier(Integer.parseInt(raw));
        } catch (NumberFormatException ex) {
            return switch (raw.toUpperCase(java.util.Locale.ROOT)) {
                case "COMMON" -> 1;
                case "UNCOMMON" -> 2;
                case "RARE" -> 3;
                case "EPIC" -> 4;
                case "LEGENDARY" -> 5;
                default -> MIN_TIER;
            };
        }
    }

    private static int clampTier(int tier) {
        return Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
    }
}
