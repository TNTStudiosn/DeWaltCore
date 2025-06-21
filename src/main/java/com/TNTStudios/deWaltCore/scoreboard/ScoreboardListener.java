package com.TNTStudios.deWaltCore.scoreboard;

import com.TNTStudios.deWaltCore.points.PointsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ScoreboardListener implements Listener {
    private final PointsManager pointsManager;

    public ScoreboardListener(PointsManager pointsManager) {
        this.pointsManager = pointsManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        boolean hasUnlockedAll = checkIfUnlockedAll(player);
        int totalPoints = pointsManager.getPoints(player.getName());
        int topPosition = pointsManager.getTopPosition(player.getName());

        DeWaltScoreboardManager.showDefaultPage(player, topPosition, totalPoints, hasUnlockedAll);
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        DeWaltScoreboardManager.clear(event.getPlayer());
    }

    private boolean checkIfUnlockedAll(Player player) {
        // Aquí va tu lógica real para saber si el jugador desbloqueó todo
        return false;
    }
}