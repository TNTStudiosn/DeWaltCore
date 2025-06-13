package com.TNTStudios.deWaltCore.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ScoreboardListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        boolean hasUnlockedAll = checkIfUnlockedAll(player);
        int topPosition = 0; // ← reemplazar con ranking real más adelante
        int totalPoints = 0; // ← reemplazar con puntos reales

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