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
