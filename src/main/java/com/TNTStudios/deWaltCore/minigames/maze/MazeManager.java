// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/maze/MazeManager.java
package com.TNTStudios.deWaltCore.minigames.maze;

import com.TNTStudios.deWaltCore.DeWaltCore;
import com.TNTStudios.deWaltCore.points.PointsManager;
import com.TNTStudios.deWaltCore.scoreboard.DeWaltScoreboardManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mi controlador para la lógica del minijuego del laberinto.
 * OPTIMIZADO: Ahora usa un único temporizador global para todos los jugadores,
 * lo que mejora drásticamente el rendimiento con muchos participantes.
 */
public class MazeManager {

    private final DeWaltCore plugin;
    private final PointsManager pointsManager;
    // Uso ConcurrentHashMap para poder añadir y quitar jugadores de forma segura
    // mientras el temporizador global está iterando.
    private final Map<UUID, Integer> playerTimers = new ConcurrentHashMap<>();
    private BukkitTask globalTimerTask;

    public MazeManager(DeWaltCore plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
    }

    /**
     * Inicia el temporizador global si no está activo.
     */
    private void startGlobalTimer() {
        // Si la tarea ya existe y está corriendo, no hago nada.
        if (globalTimerTask != null && !globalTimerTask.isCancelled()) {
            return;
        }

        globalTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Si no hay jugadores en el laberinto, cancelo la tarea para no gastar recursos.
                if (playerTimers.isEmpty()) {
                    this.cancel();
                    globalTimerTask = null;
                    return;
                }

                // Itero sobre todos los jugadores que están en el laberinto.
                for (UUID uuid : playerTimers.keySet()) {
                    Player player = Bukkit.getPlayer(uuid);
                    // Me aseguro que el jugador siga online.
                    if (player != null && player.isOnline()) {
                        int newTime = playerTimers.compute(uuid, (key, time) -> time + 1);
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                new TextComponent(ChatColor.GREEN + "Tiempo: " + formatTime(newTime)));
                    } else {
                        // Si el jugador se desconectó, lo elimino de la lista.
                        playerTimers.remove(uuid);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Se ejecuta cada segundo.
    }

    public void startMaze(Player player) {
        UUID uuid = player.getUniqueId();
        if (isPlayerInMaze(player)) {
            player.sendMessage(ChatColor.RED + "¡Ya estás en el laberinto! ¡Tú puedes!");
            return;
        }

        playerTimers.put(uuid, 0);
        player.sendTitle(ChatColor.GOLD + "¡Laberinto iniciado!", ChatColor.YELLOW + "¡Corre!", 10, 70, 20);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);

        // Me aseguro de que el temporizador global esté corriendo.
        startGlobalTimer();
    }

    public void finishMaze(Player player) {
        UUID uuid = player.getUniqueId();
        if (!isPlayerInMaze(player)) {
            player.sendMessage(ChatColor.RED + "No has iniciado el laberinto. Usa /empezar.");
            return;
        }

        // Ya no necesito cancelar una tarea específica, solo quitar al jugador del mapa.
        int finalTime = playerTimers.remove(uuid);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        int pointsWon = pointsManager.recordCompletion(player, "maze", finalTime);

        if (pointsWon > 0) {
            player.sendTitle(ChatColor.GREEN + "¡Laberinto completado!",
                    String.format(ChatColor.YELLOW + "Tu tiempo: %s (+%d pts)", formatTime(finalTime), pointsWon), 10, 80, 20);
        } else {
            player.sendTitle(ChatColor.GREEN + "¡Laberinto completado!",
                    String.format(ChatColor.YELLOW + "Tu tiempo fue de %s", formatTime(finalTime)), 10, 80, 20);
        }

        // Es importante actualizar el scoreboard DESPUÉS de registrar los puntos.
        // Lo hago con un pequeño retraso para asegurar que la info del ranking se haya actualizado.
        new BukkitRunnable() {
            @Override
            public void run() {
                int totalPoints = pointsManager.getTotalPoints(player);
                int topPosition = pointsManager.getPlayerRank(player);
                DeWaltScoreboardManager.updateDefaultPage(player, topPosition, totalPoints, false);
            }
        }.runTaskLater(plugin, 5L); // 5 ticks de retraso.
    }

    public void cancelMaze(Player player) {
        UUID uuid = player.getUniqueId();
        if (!isPlayerInMaze(player)) {
            return;
        }

        playerTimers.remove(uuid);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "Tu tiempo en el laberinto ha sido cancelado."));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
    }

    public boolean isPlayerInMaze(Player player) {
        return playerTimers.containsKey(player.getUniqueId());
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }
}