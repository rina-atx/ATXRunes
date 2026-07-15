package com.atx.runes.trade;

import com.atx.runes.ATXRunesPlugin;
import com.atx.runes.rune.RuneInstance;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class TradeManager {
    private final ATXRunesPlugin plugin;
    private final Map<String, TradeSession> sessions = new HashMap<>();

    public TradeManager(ATXRunesPlugin plugin) {
        this.plugin = plugin;
    }

    public TradeSession start(Player first, Player second) {
        TradeSession session = new TradeSession(first.getUniqueId(), second.getUniqueId());
        sessions.put(key(first.getUniqueId(), second.getUniqueId()), session);
        return session;
    }

    public Optional<TradeSession> find(UUID first, UUID second) {
        TradeSession session = sessions.get(key(first, second));
        if (session == null || expired(session)) {
            sessions.remove(key(first, second));
            return Optional.empty();
        }
        return Optional.of(session);
    }

    public void cancel(TradeSession session) {
        sessions.remove(key(session.firstPlayer(), session.secondPlayer()));
    }

    public boolean tryComplete(TradeSession session) {
        if (!session.ready()) {
            return false;
        }
        Player first = Bukkit.getPlayer(session.firstPlayer());
        Player second = Bukkit.getPlayer(session.secondPlayer());
        if (first == null || second == null) {
            cancel(session);
            return false;
        }
        Optional<UUID> firstOfferId = session.offerFor(session.firstPlayer());
        Optional<UUID> secondOfferId = session.offerFor(session.secondPlayer());
        if (firstOfferId.isEmpty() || secondOfferId.isEmpty()) {
            return false;
        }
        Optional<RuneInstance> firstRune = plugin.getRuneStorage().findStored(first.getUniqueId(), firstOfferId.get());
        Optional<RuneInstance> secondRune = plugin.getRuneStorage().findStored(second.getUniqueId(), secondOfferId.get());
        if (firstRune.isEmpty() || secondRune.isEmpty()) {
            first.sendMessage(ChatColor.RED + "Trade failed because one offered rune is no longer in storage.");
            second.sendMessage(ChatColor.RED + "Trade failed because one offered rune is no longer in storage.");
            cancel(session);
            return false;
        }

        List<RuneInstance> firstStorage = new ArrayList<>(plugin.getRuneStorage().getStorage(first.getUniqueId()));
        List<RuneInstance> secondStorage = new ArrayList<>(plugin.getRuneStorage().getStorage(second.getUniqueId()));
        int firstIndex = indexOf(firstStorage, firstRune.get().id());
        int secondIndex = indexOf(secondStorage, secondRune.get().id());
        if (firstIndex < 0 || secondIndex < 0) {
            first.sendMessage(ChatColor.RED + "Trade failed because one offered rune is no longer in storage.");
            second.sendMessage(ChatColor.RED + "Trade failed because one offered rune is no longer in storage.");
            cancel(session);
            return false;
        }

        firstStorage.set(firstIndex, secondRune.get());
        secondStorage.set(secondIndex, firstRune.get());
        plugin.getRuneStorage().setStorage(first.getUniqueId(), firstStorage);
        plugin.getRuneStorage().setStorage(second.getUniqueId(), secondStorage);

        first.closeInventory();
        second.closeInventory();
        first.sendMessage(ChatColor.GREEN + "Trade completed.");
        second.sendMessage(ChatColor.GREEN + "Trade completed.");
        cancel(session);
        return true;
    }

    private boolean expired(TradeSession session) {
        long timeout = plugin.getConfig().getLong("settings.trade-timeout-seconds", 90);
        return Duration.between(session.createdAt(), Instant.now()).getSeconds() > timeout;
    }

    private int indexOf(List<RuneInstance> storage, UUID runeId) {
        for (int index = 0; index < storage.size(); index++) {
            if (storage.get(index).id().equals(runeId)) {
                return index;
            }
        }
        return -1;
    }

    private String key(UUID first, UUID second) {
        return first.compareTo(second) < 0 ? first + ":" + second : second + ":" + first;
    }
}
