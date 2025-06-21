package com.TNTStudios.deWaltCore.minigames.laberinto;

import com.TNTStudios.deWaltCore.points.PointsManager;
import com.TNTStudios.deWaltCore.scoreboard.DeWaltScoreboardManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class LaberintoTimerCommand implements CommandExecutor, Listener {
    private final JavaPlugin plugin;
    private final LaberintoBestTimeManager bestTimeManager;
    private final PointsManager pointsManager;
    private final Map<String, Integer> timers = new HashMap<>();
    private final Map<String, BukkitRunnable> tasks = new HashMap<>();

    public LaberintoTimerCommand(JavaPlugin plugin, LaberintoBestTimeManager bestTimeManager, PointsManager pointsManager) {
        this.plugin = plugin;
        this.bestTimeManager = bestTimeManager;
        this.pointsManager = pointsManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores pueden usar este comando.");
            return true;
        }

        if (!player.isOp()) {
            player.sendMessage(ChatColor.RED + "No tienes permiso para usar este comando.");
            return true;
        }

        String name = player.getName();
        String cmd = command.getName();

        if (cmd.equalsIgnoreCase("empezar")) {
            if (tasks.containsKey(name)) {
                player.sendMessage(ChatColor.RED + "\u00a1Tu Puedes!");
                return true;
            }
            timers.put(name, 0);
            player.sendTitle(ChatColor.GOLD + "Contador iniciado", "", 10, 70, 20);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
            BukkitRunnable task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!timers.containsKey(name)) {
                        cancel();
                        tasks.remove(name);
                        return;
                    }
                    int t = timers.get(name) + 1;
                    timers.put(name, t);
                    String formatted = format(t);
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + "Tiempo transcurrido: " + formatted));
                }
            };
            task.runTaskTimer(plugin, 20, 20);
            tasks.put(name, task);
        } else if (cmd.equalsIgnoreCase("detener")) {
            if (!tasks.containsKey(name)) {
                player.sendMessage(ChatColor.RED + "\u00a1Tu Puedes!");
                return true;
            }
            tasks.get(name).cancel();
            tasks.remove(name);

            if (!timers.containsKey(name)) {
                player.sendMessage(ChatColor.RED + "No hay tiempo registrado para detener.");
                return true;
            }
            int total = timers.remove(name);
            boolean improved = bestTimeManager.updateBestTime(name, total);
            String formatted = format(total);
            player.sendTitle(ChatColor.RED + "Tiempo detenido", ChatColor.YELLOW + "Tardaste " + formatted, 10, 70, 20);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
            bestTimeManager.showScoreboard(player);
            bestTimeManager.save();

            int pointsToAdd = calculatePoints(name, total, improved);
            if (pointsToAdd > 0) {
                pointsManager.addPoints(name, pointsToAdd, "laberinto");
                int pos = pointsManager.getTopPosition(name);
                int totalPts = pointsManager.getPoints(name);
                DeWaltScoreboardManager.updateDefaultPage(player, pos, totalPts, false);
            }

            World world = player.getWorld();
            Location spawn = new Location(world, -4.53, -38, 26.53, -90, 0);
            player.teleport(spawn);
        }
        return true;
    }

    private int calculatePoints(String name, int newTime, boolean improvedNow) {
        int best = bestTimeManager.getBestTime(name);
        if (best == -1 || best == newTime && improvedNow) {
            return 10; // first completion
        }
        // improvement scoring
        int diffCount = 0;
        File file = new File(plugin.getDataFolder(), "players/" + name + ".yml");
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            diffCount = config.getInt("improvements.lab", 0);
        }
        int pts;
        if (diffCount == 0) {
            pts = 5;
        } else if (diffCount == 1) {
            pts = 2;
        } else {
            pts = 1;
        }
        if (improvedNow) {
            diffCount++;
        }
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        config.set("improvements.lab", diffCount);
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return pts;
    }

    private String format(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return String.format("%02dh:%02dm:%02ds", h, m, s);
    }

    @EventHandler
    public void onSpawnCommand(PlayerCommandPreprocessEvent event) {
        if (event.getMessage().equalsIgnoreCase("/spawn")) {
            String name = event.getPlayer().getName();
            if (tasks.containsKey(name)) {
                tasks.get(name).cancel();
                tasks.remove(name);
                timers.remove(name);
                event.getPlayer().sendMessage(ChatColor.RED + "Tiempo cancelado.");
            }
        }
    }
}
