package com.TNTStudios.deWaltCore.minigames.maze;

import com.TNTStudios.deWaltCore.DeWaltCore;
import com.TNTStudios.deWaltCore.points.PointsManager;
import com.TNTStudios.deWaltCore.scoreboard.DeWaltScoreboardManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mi controlador para la lógica del minijuego del laberinto.
 * Aquí manejo los timers de cada jugador.
 */
public class MazeManager {

    private final DeWaltCore plugin;
    private final PointsManager pointsManager;
    private final Map<UUID, Integer> playerTimers = new HashMap<>();
    private final Map<UUID, BukkitTask> timerTasks = new HashMap<>();

    public MazeManager(DeWaltCore plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
    }

    /**
     * Inicia el temporizador para un jugador en el laberinto.
     */
    public void startMaze(Player player) {
        UUID uuid = player.getUniqueId();
        if (isPlayerInMaze(player)) {
            player.sendMessage(ChatColor.RED + "¡Ya estás en el laberinto! ¡Tú puedes!");
            return;
        }

        playerTimers.put(uuid, 0);
        player.sendTitle(ChatColor.GOLD + "¡Laberinto iniciado!", ChatColor.YELLOW + "¡Corre!", 10, 70, 20);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                int time = playerTimers.getOrDefault(uuid, 0) + 1;
                playerTimers.put(uuid, time);
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(ChatColor.GREEN + "Tiempo: " + formatTime(time)));
            }
        }.runTaskTimer(plugin, 20L, 20L);

        timerTasks.put(uuid, task);
    }

    /**
     * Finaliza el intento del jugador en el laberinto y registra su puntuación.
     */
    public void finishMaze(Player player) {
        UUID uuid = player.getUniqueId();
        if (!isPlayerInMaze(player)) {
            player.sendMessage(ChatColor.RED + "No has iniciado el laberinto. Usa /empezar.");
            return;
        }

        // Detengo y elimino la tarea
        timerTasks.get(uuid).cancel();
        timerTasks.remove(uuid);

        int finalTime = playerTimers.remove(uuid);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // Registro la puntuación
        int pointsWon = pointsManager.recordCompletion(player, "maze", finalTime);

        // Muestro el mensaje de finalización
        if (pointsWon > 0) {
            player.sendTitle(ChatColor.GREEN + "¡Laberinto completado!",
                    String.format(ChatColor.YELLOW + "Tu tiempo: %s (+%d pts)", formatTime(finalTime), pointsWon), 10, 80, 20);
        } else {
            player.sendTitle(ChatColor.GREEN + "¡Laberinto completado!",
                    String.format(ChatColor.YELLOW + "Tu tiempo fue de %s", formatTime(finalTime)), 10, 80, 20);
        }

        // Actualizo el scoreboard principal con los datos reales.
        int totalPoints = pointsManager.getTotalPoints(player);
        int topPosition = pointsManager.getPlayerRank(player); // Obtengo la posición real del jugador.
        // Aquí uso el valor de "unlockedAll" en false, porque aún no lo implemento.
        DeWaltScoreboardManager.updateDefaultPage(player, topPosition, totalPoints, false);
    }

    /**
     * Cancela el tiempo del jugador si hace /spawn o se desconecta.
     */
    public void cancelMaze(Player player) {
        UUID uuid = player.getUniqueId();
        if (!isPlayerInMaze(player)) {
            return;
        }

        timerTasks.get(uuid).cancel();
        timerTasks.remove(uuid);
        playerTimers.remove(uuid);

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "Tu tiempo en el laberinto ha sido cancelado."));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
    }

    public boolean isPlayerInMaze(Player player) {
        return timerTasks.containsKey(player.getUniqueId());
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }
}