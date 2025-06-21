// FILE: src/main/java/com/TNTStudios/deWaltCore/scoreboard/DeWaltScoreboardManager.java
package com.TNTStudios.deWaltCore.scoreboard;

import com.TNTStudios.deWaltCore.points.PointsManager; // Importo mi clase PlayerScore
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mi gestor de Scoreboards.
 * OPTIMIZADO: Ahora cacheo el scoreboard de cada jugador para evitar recrearlo
 * constantemente, lo que elimina el parpadeo (flicker) y mejora el rendimiento.
 */
public class DeWaltScoreboardManager {

    private static final Map<UUID, Scoreboard> scoreboardCache = new ConcurrentHashMap<>();
    private static final String OBJECTIVE_NAME = "dewalt_sb";

    public static void updateScoreboard(Player player, List<String> lines) {
        Scoreboard scoreboard = scoreboardCache.computeIfAbsent(player.getUniqueId(), uuid -> {
            Scoreboard newBoard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = newBoard.registerNewObjective(OBJECTIVE_NAME, "dummy", ScoreboardStyle.TITLE);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            return newBoard;
        });

        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);

        if (objective == null) {
            objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, "dummy", ScoreboardStyle.TITLE);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // --- LÓGICA DE ACTUALIZACIÓN EFICIENTE ---
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        int score = lines.size();
        for (String line : lines) {
            objective.getScore(line).setScore(score--);
        }

        if (player.getScoreboard() != scoreboard) {
            player.setScoreboard(scoreboard);
        }
    }

    // --- MI CAMBIO ---
    // Actualizo la firma del método para aceptar la lista del top.
    public static void showDefaultPage(Player player, int topPosition, int totalPoints, boolean unlockedAll, List<PointsManager.PlayerScore> topPlayers) {
        List<String> lines = ScoreboardStyle.buildDefaultPageLines(topPosition, totalPoints, unlockedAll, topPlayers);
        updateScoreboard(player, lines);
    }

    public static void clear(Player player) {
        scoreboardCache.remove(player.getUniqueId());
        if (player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }
}