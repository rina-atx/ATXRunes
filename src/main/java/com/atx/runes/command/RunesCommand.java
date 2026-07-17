package com.atx.runes.command;

import com.atx.runes.ATXRunesPlugin;
import com.atx.runes.rune.RuneInstance;
import com.atx.runes.trade.TradeSession;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RunesCommand implements CommandExecutor, TabCompleter {
    private final ATXRunesPlugin plugin;

    public RunesCommand(ATXRunesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("open")) {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "general.players-only");
                return true;
            }
            if (!player.hasPermission("atxrunes.use")) {
                plugin.messages().send(player, "general.no-permission");
                return true;
            }
            plugin.getGuiListener().menus().openMain(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload") && sender.hasPermission("atxrunes.admin")) {
            plugin.reloadRunePlugin();
            plugin.messages().send(sender, "general.reloaded");
            return true;
        }

        if (sub.equals("give") && sender.hasPermission("atxrunes.admin")) {
            return give(sender, args);
        }

        if (sender instanceof Player player) {
            if (!player.hasPermission("atxrunes.use")) {
                plugin.messages().send(player, "general.no-permission");
                return true;
            }
            switch (sub) {
                case "slots", "equip" -> {
                    plugin.getGuiListener().menus().openSlots(player);
                    return true;
                }
                case "forge" -> {
                    plugin.getGuiListener().menus().openForge(player);
                    return true;
                }
                case "reforge" -> {
                    plugin.getGuiListener().menus().openReforge(player);
                    return true;
                }
                case "storage" -> {
                    plugin.getGuiListener().menus().openStorage(player);
                    return true;
                }
                case "salvage" -> {
                    plugin.getGuiListener().menus().openSalvage(player);
                    return true;
                }
                case "trade" -> {
                    return trade(player, args);
                }
            }
        }

        plugin.messages().send(sender, "general.usage");
        return true;
    }

    private boolean trade(Player player, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(player, "trade.usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || target.equals(player)) {
            plugin.messages().send(player, "trade.choose-online-player");
            return true;
        }
        if (!target.hasPermission("atxrunes.use")) {
            plugin.messages().send(player, "trade.target-no-permission", Map.of("{player}", target.getName()));
            return true;
        }
        TradeSession session = plugin.getTradeManager().start(player, target);
        plugin.getGuiListener().menus().openTrade(player, target, session);
        plugin.getGuiListener().menus().openTrade(target, player, session);
        plugin.messages().send(target, "trade.opened", Map.of("{player}", player.getName()));
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.messages().send(sender, "admin.give-usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "admin.player-offline");
            return true;
        }
        String typeId = args[2].toUpperCase(Locale.ROOT);
        if (plugin.getRuneRegistry().findType(typeId).isEmpty()) {
            plugin.messages().send(sender, "admin.unknown-rune");
            return true;
        }
        int tier;
        try {
            tier = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            plugin.messages().send(sender, "admin.tier-number", Map.of("{max_tier}", String.valueOf(RuneInstance.MAX_TIER)));
            return true;
        }
        if (tier < RuneInstance.MIN_TIER || tier > RuneInstance.MAX_TIER) {
            plugin.messages().send(sender, "admin.tier-range", Map.of("{max_tier}", String.valueOf(RuneInstance.MAX_TIER)));
            return true;
        }
        RuneInstance rune = RuneInstance.create(typeId, tier);
        if (!plugin.getRuneStorage().addToStorage(target.getUniqueId(), rune)) {
            plugin.getGuiListener().menus().giveOrDropRune(target, rune);
        }
        plugin.messages().send(sender, "admin.gave-rune", Map.of("{player}", target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(Arrays.asList("open", "slots", "forge", "reforge", "salvage", "storage", "trade"));
            if (sender.hasPermission("atxrunes.admin")) {
                base.add("give");
                base.add("reload");
            }
            return filter(base, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trade") || args[0].equalsIgnoreCase("give"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(plugin.getRuneRegistry().allTypes().stream().map(type -> type.id().toLowerCase(Locale.ROOT)).toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("1", "2", "3", "4", "5", "6", "7"), args[3]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String token) {
        String lowered = token.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowered)).toList();
    }
}
package com.atx.runes.command;

import com.atx.runes.ATXRunesPlugin;
import com.atx.runes.rune.RuneInstance;
import com.atx.runes.trade.TradeSession;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class RunesCommand implements CommandExecutor, TabCompleter {
    private final ATXRunesPlugin plugin;

    public RunesCommand(ATXRunesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("open")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            if (!player.hasPermission("atxrunes.use")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use runes.");
                return true;
            }
            plugin.getGuiListener().menus().openMain(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload") && sender.hasPermission("atxrunes.admin")) {
            plugin.reloadRunePlugin();
            sender.sendMessage(ChatColor.GREEN + "ATXRunes reloaded.");
            return true;
        }

        if (sub.equals("give") && sender.hasPermission("atxrunes.admin")) {
            return give(sender, args);
        }

        if (sender instanceof Player player) {
            if (!player.hasPermission("atxrunes.use")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use runes.");
                return true;
            }
            switch (sub) {
                case "slots", "equip" -> {
                    plugin.getGuiListener().menus().openSlots(player);
                    return true;
                }
                case "forge" -> {
                    plugin.getGuiListener().menus().openForge(player);
                    return true;
                }
                case "reforge" -> {
                    plugin.getGuiListener().menus().openReforge(player);
                    return true;
                }
                case "storage" -> {
                    plugin.getGuiListener().menus().openStorage(player);
                    return true;
                }
                case "trade" -> {
                    return trade(player, args);
                }
            }
        }

        sender.sendMessage(ChatColor.RED + "Usage: /runes [slots|forge|reforge|storage|trade <player>]");
        return true;
    }

    private boolean trade(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /runes trade <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || target.equals(player)) {
            player.sendMessage(ChatColor.RED + "Choose an online player.");
            return true;
        }
        if (!target.hasPermission("atxrunes.use")) {
            player.sendMessage(ChatColor.RED + target.getName() + " does not have permission to trade runes.");
            return true;
        }
        TradeSession session = plugin.getTradeManager().start(player, target);
        plugin.getGuiListener().menus().openTrade(player, target, session);
        plugin.getGuiListener().menus().openTrade(target, player, session);
        target.sendMessage(ChatColor.YELLOW + player.getName() + " opened a rune trade with you.");
        return true;
    }

    private boolean give(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Usage: /runes give <player> <type> <tier>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player is not online.");
            return true;
        }
        String typeId = args[2].toUpperCase(Locale.ROOT);
        if (plugin.getRuneRegistry().findType(typeId).isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Unknown rune type.");
            return true;
        }
        int tier;
        try {
            tier = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(ChatColor.RED + "Tier must be a number from 1 to " + RuneInstance.MAX_TIER + ".");
            return true;
        }
        if (tier < RuneInstance.MIN_TIER || tier > RuneInstance.MAX_TIER) {
            sender.sendMessage(ChatColor.RED + "Tier must be from 1 to " + RuneInstance.MAX_TIER + ".");
            return true;
        }
        RuneInstance rune = RuneInstance.create(typeId, tier);
        if (!plugin.getRuneStorage().addToStorage(target.getUniqueId(), rune)) {
            plugin.getGuiListener().menus().giveOrDropRune(target, rune);
        }
        sender.sendMessage(ChatColor.GREEN + "Gave rune to " + target.getName() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(Arrays.asList("open", "slots", "forge", "reforge", "storage", "trade"));
            if (sender.hasPermission("atxrunes.admin")) {
                base.add("give");
                base.add("reload");
            }
            return filter(base, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("trade") || args[0].equalsIgnoreCase("give"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(plugin.getRuneRegistry().allTypes().stream().map(type -> type.id().toLowerCase(Locale.ROOT)).toList(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            return filter(List.of("1", "2", "3", "4", "5", "6", "7"), args[3]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String token) {
        String lowered = token.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowered)).toList();
    }
}
