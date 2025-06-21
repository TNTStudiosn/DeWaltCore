// FILE: src/main/java/com/TNTStudios/deWaltCore/scoreboard/ScoreboardListener.java
package com.TNTStudios.deWaltCore.scoreboard;

import com.TNTStudios.deWaltCore.DeWaltCore;
import com.TNTStudios.deWaltCore.points.PointsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ScoreboardListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PointsManager pointsManager = DeWaltCore.getPointsManager();

        // Le digo a mi PointsManager que cargue los datos de este jugador en el caché.
        pointsManager.loadPlayerData(player);

        // Ahora obtengo los datos cacheados. Esto es instantáneo.
        int totalPoints = pointsManager.getTotalPoints(player);
        int topPosition = pointsManager.getPlayerRank(player);
        boolean hasUnlockedAll = checkIfUnlockedAll(player);

        // Muestro el scoreboard usando el nuevo método optimizado.
        DeWaltScoreboardManager.showDefaultPage(player, topPosition, totalPoints, hasUnlockedAll);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        // Limpio el scoreboard del jugador que se va.
        DeWaltScoreboardManager.clear(player);

        // Le digo a mi PointsManager que puede liberar los datos de este jugador del caché.
        PointsManager pointsManager = DeWaltCore.getPointsManager();
        if (pointsManager != null) {
            pointsManager.unloadPlayerData(player);
        }
    }

    private boolean checkIfUnlockedAll(Player player) {
        // Tu lógica para comprobar si desbloqueó todo va aquí.
        return false;
    }
}