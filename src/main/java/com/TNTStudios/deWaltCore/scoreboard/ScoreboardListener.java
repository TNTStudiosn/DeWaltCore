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

        // TODO: reemplazar esta lógica por algo real
        boolean hasUnlockedAll = checkIfUnlockedAll(player);

        DeWaltScoreboardManager.showDefaultPage(player, hasUnlockedAll);
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
