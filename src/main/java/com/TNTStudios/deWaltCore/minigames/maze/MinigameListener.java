package com.TNTStudios.deWaltCore.minigames;

import com.TNTStudios.deWaltCore.minigames.maze.MazeManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Mi listener para eventos generales de minijuegos, como comandos prohibidos o desconexiones.
 */
public class MinigameListener implements Listener {

    private final MazeManager mazeManager;

    public MinigameListener(MazeManager mazeManager) {
        this.mazeManager = mazeManager;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        // Si el jugador usa /spawn mientras está en el laberinto, cancelo su tiempo.
        if (event.getMessage().equalsIgnoreCase("/spawn")) {
            if (mazeManager.isPlayerInMaze(event.getPlayer())) {
                mazeManager.cancelMaze(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Si el jugador se desconecta, también cancelo su tiempo.
        if (mazeManager.isPlayerInMaze(event.getPlayer())) {
            mazeManager.cancelMaze(event.getPlayer());
        }
    }
}