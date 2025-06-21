// FILE: src/main/java/com/TNTStudios/deWaltCore/scoreboard/DeWaltScoreboardManager.java
package com.TNTStudios.deWaltCore.scoreboard;

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

    // Uso ConcurrentHashMap por si múltiples hilos acceden a él (aunque es poco probable aquí). Es una buena práctica.
    private static final Map<UUID, Scoreboard> scoreboardCache = new ConcurrentHashMap<>();
    private static final String OBJECTIVE_NAME = "dewalt_sb";

    // Actualiza el scoreboard de un jugador con las líneas que le tocan.
    // Si no tiene uno, se lo crea.
    public static void updateScoreboard(Player player, List<String> lines) {
        // Obtengo el scoreboard del caché o creo uno nuevo si no existe.
        Scoreboard scoreboard = scoreboardCache.computeIfAbsent(player.getUniqueId(), uuid -> {
            Scoreboard newBoard = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective objective = newBoard.registerNewObjective(OBJECTIVE_NAME, "dummy", ScoreboardStyle.TITLE);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            return newBoard;
        });

        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);

        // Si por alguna razón el objetivo no existiera (ej. error de otro plugin), lo recreo.
        if (objective == null) {
            objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, "dummy", ScoreboardStyle.TITLE);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // --- LÓGICA DE ACTUALIZACIÓN EFICIENTE ---
        // Primero, borro todas las líneas viejas para evitar "fantasmas".
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }

        // Luego, añado las nuevas líneas.
        int score = lines.size();
        for (String line : lines) {
            // No necesito la lógica de "§r" porque cada scoreboard es único por jugador
            // y las líneas se resetean antes de poner las nuevas.
            objective.getScore(line).setScore(score--);
        }

        // Solo le asigno el scoreboard si no lo tiene ya, para evitar envíos de paquetes innecesarios.
        if (player.getScoreboard() != scoreboard) {
            player.setScoreboard(scoreboard);
        }
    }

    // Muestra la página por defecto usando el nuevo sistema de actualización.
    public static void showDefaultPage(Player player, int topPosition, int totalPoints, boolean unlockedAll) {
        List<String> lines = ScoreboardStyle.buildDefaultPageLines(topPosition, totalPoints, unlockedAll);
        updateScoreboard(player, lines);
    }

    // Elimina el scoreboard del jugador y lo limpia de nuestro caché.
    public static void clear(Player player) {
        scoreboardCache.remove(player.getUniqueId());
        if (player.isOnline()) {
            // Le devolvemos el scoreboard principal del servidor.
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }
}