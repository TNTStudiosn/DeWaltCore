package com.TNTStudios.deWaltCore.minigames.laberinto;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Map;

public class LaberintoBestTimeManager {
    private final JavaPlugin plugin;
    private final Map<String, Integer> bestTimesByName = new HashMap<>();

    public LaberintoBestTimeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("laberintoBestTimes");
        if (sec != null) {
            for (String name : sec.getKeys(false)) {
                bestTimesByName.put(name, sec.getInt(name));
            }
        }
    }

    public void save() {
        for (String name : bestTimesByName.keySet()) {
            plugin.getConfig().set("laberintoBestTimes." + name, bestTimesByName.get(name));
        }
        plugin.saveConfig();
    }

    public int getBestTime(String name) {
        return bestTimesByName.getOrDefault(name, -1);
    }

    public boolean updateBestTime(String name, int newTime) {
        int current = getBestTime(name);
        if (current == -1 || newTime < current) {
            bestTimesByName.put(name, newTime);
            return true;
        }
        return false;
    }

    public void showScoreboard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("laberinto_best", "dummy", ChatColor.DARK_AQUA + "Tu Mejor Tiempo");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int best = getBestTime(player.getName());
        String line = (best == -1) ? ChatColor.YELLOW + "A\u00fan no has hecho un intento" : ChatColor.GREEN + format(best);
        obj.getScore(line).setScore(1);
        player.setScoreboard(board);
    }

    private String format(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return String.format("%02dh:%02dm:%02ds", h, m, s);
    }
}
