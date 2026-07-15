package com.atx.runes.util;

import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Texts {
    private static final Pattern AMP_HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern MINI_HEX = Pattern.compile("<#([A-Fa-f0-9]{6})>");
    private static final Pattern MINI_TAG = Pattern.compile("</?([a-zA-Z_]+)>");
    private static final Map<String, String> TAGS = new HashMap<>();

    static {
        TAGS.put("black", ChatColor.BLACK.toString());
        TAGS.put("dark_blue", ChatColor.DARK_BLUE.toString());
        TAGS.put("dark_green", ChatColor.DARK_GREEN.toString());
        TAGS.put("dark_aqua", ChatColor.DARK_AQUA.toString());
        TAGS.put("dark_red", ChatColor.DARK_RED.toString());
        TAGS.put("dark_purple", ChatColor.DARK_PURPLE.toString());
        TAGS.put("gold", ChatColor.GOLD.toString());
        TAGS.put("gray", ChatColor.GRAY.toString());
        TAGS.put("dark_gray", ChatColor.DARK_GRAY.toString());
        TAGS.put("blue", ChatColor.BLUE.toString());
        TAGS.put("green", ChatColor.GREEN.toString());
        TAGS.put("aqua", ChatColor.AQUA.toString());
        TAGS.put("red", ChatColor.RED.toString());
        TAGS.put("light_purple", ChatColor.LIGHT_PURPLE.toString());
        TAGS.put("yellow", ChatColor.YELLOW.toString());
        TAGS.put("white", ChatColor.WHITE.toString());
        TAGS.put("bold", ChatColor.BOLD.toString());
        TAGS.put("italic", ChatColor.ITALIC.toString());
        TAGS.put("underlined", ChatColor.UNDERLINE.toString());
        TAGS.put("strikethrough", ChatColor.STRIKETHROUGH.toString());
        TAGS.put("obfuscated", ChatColor.MAGIC.toString());
        TAGS.put("reset", ChatColor.RESET.toString());
    }

    private Texts() {
    }

    public static String color(String text) {
        if (text == null) {
            return "";
        }
        String colored = replaceHex(text, AMP_HEX);
        colored = replaceHex(colored, MINI_HEX);
        colored = replaceMiniTags(colored);
        return ChatColor.translateAlternateColorCodes('&', colored);
    }

    public static List<String> color(List<String> lines) {
        return lines.stream().map(Texts::color).toList();
    }

    private static String replaceMiniTags(String text) {
        Matcher matcher = MINI_TAG.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String tag = matcher.group(1).toLowerCase(Locale.ROOT);
            String replacement = matcher.group().startsWith("</") ? ChatColor.RESET.toString() : TAGS.getOrDefault(tag, matcher.group());
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String replaceHex(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(net.md_5.bungee.api.ChatColor.of("#" + matcher.group(1)).toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
