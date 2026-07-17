package com.atx.runes.gui;

import com.atx.runes.rune.RuneInstance;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class RuneGuiHolder implements InventoryHolder {
    private final RuneGuiType type;
    private final UUID viewerId;
    private final UUID targetId;
    private final int page;
    private final UUID selectedRuneId;
    private final int catalysts;
    private final boolean forgeGlowstone;
    private final boolean forgeTemplate;
    private final boolean forgeNetherite;
    private final RuneInstance forgedRune;

    public RuneGuiHolder(RuneGuiType type, UUID viewerId, UUID targetId) {
        this(type, viewerId, targetId, 0);
    }

    public RuneGuiHolder(RuneGuiType type, UUID viewerId, UUID targetId, int page) {
        this(type, viewerId, targetId, page, null, 0, false, false, false, null);
    }

    public RuneGuiHolder(RuneGuiType type, UUID viewerId, UUID targetId, int page, UUID selectedRuneId, int catalysts) {
        this(type, viewerId, targetId, page, selectedRuneId, catalysts, false, false, false, null);
    }

    public RuneGuiHolder(RuneGuiType type, UUID viewerId, UUID targetId, int page, boolean forgeGlowstone, boolean forgeTemplate, boolean forgeNetherite, RuneInstance forgedRune) {
        this(type, viewerId, targetId, page, null, 0, forgeGlowstone, forgeTemplate, forgeNetherite, forgedRune);
    }

    private RuneGuiHolder(RuneGuiType type, UUID viewerId, UUID targetId, int page, UUID selectedRuneId, int catalysts, boolean forgeGlowstone, boolean forgeTemplate, boolean forgeNetherite, RuneInstance forgedRune) {
        this.type = type;
        this.viewerId = viewerId;
        this.targetId = targetId;
        this.page = page;
        this.selectedRuneId = selectedRuneId;
        this.catalysts = catalysts;
        this.forgeGlowstone = forgeGlowstone;
        this.forgeTemplate = forgeTemplate;
        this.forgeNetherite = forgeNetherite;
        this.forgedRune = forgedRune;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public RuneGuiType type() {
        return type;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public UUID targetId() {
        return targetId;
    }

    public int page() {
        return page;
    }

    public UUID selectedRuneId() {
        return selectedRuneId;
    }

    public int catalysts() {
        return catalysts;
    }

    public boolean forgeGlowstone() {
        return forgeGlowstone;
    }

    public boolean forgeTemplate() {
        return forgeTemplate;
    }

    public boolean forgeNetherite() {
        return forgeNetherite;
    }

    public RuneInstance forgedRune() {
        return forgedRune;
    }
}
package com.atx.runes.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class RuneGuiHolder implements InventoryHolder {
    private final RuneGuiType type;
    private final UUID viewerId;
    private final UUID targetId;

    public RuneGuiHolder(RuneGuiType type, UUID viewerId, UUID targetId) {
        this.type = type;
        this.viewerId = viewerId;
        this.targetId = targetId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }

    public RuneGuiType type() {
        return type;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public UUID targetId() {
        return targetId;
    }
}
