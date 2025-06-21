package com.TNTStudios.deWaltCore.scoreboard;

import com.TNTStudios.deWaltCore.DeWaltCore;
import com.TNTStudios.deWaltCore.points.PointsManager; // Corrijo el paquete, era "points" no "stats"
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ScoreboardListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Accedo a mi manager de puntos para obtener la información.
        // Uso el método estático que creé en mi clase principal.
        PointsManager pointsManager = DeWaltCore.getPointsManager();

        // Ahora obtengo los datos reales del jugador
        int totalPoints = pointsManager.getTotalPoints(player);
        int topPosition = pointsManager.getPlayerRank(player);
        boolean hasUnlockedAll = checkIfUnlockedAll(player); // Esto sigue pendiente, pero está bien por ahora.

        // Muestro el scoreboard con la información correcta y actualizada
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