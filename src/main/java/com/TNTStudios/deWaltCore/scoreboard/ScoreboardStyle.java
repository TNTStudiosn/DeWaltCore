package com.TNTStudios.deWaltCore.scoreboard;

import com.TNTStudios.deWaltCore.points.PointsManager.PlayerScore;

import java.util.ArrayList;
import java.util.List;

public class ScoreboardStyle {

    public static final String TITLE = "\tᚏ";

    // --- MI CAMBIO ---
    // Añado la lista del top como parámetro.
    public static List<String> buildDefaultPageLines(int topPosition, int points, boolean unlockedAll, List<PlayerScore> topPlayers) {
        List<String> lines = new ArrayList<>();
        String[] topColors = {"§e", "§7", "§c"}; // Colores para #1, #2, #3

        lines.add("§8━━━━━━━━━━━━━━━━━━━━§f "); // Añadí un espacio con color blanco al final para evitar problemas en algunas versiones de cliente

        lines.add("§6§lTOP 3 GENERAL");
        if (topPlayers.isEmpty()) {
            lines.add("§c  Aún no hay nadie");
            lines.add("§c  en el top.");
        } else {
            for (int i = 0; i < topPlayers.size(); i++) {
                PlayerScore score = topPlayers.get(i);
                // Limito el nombre para que no se salga del scoreboard
                String playerName = score.playerName().length() > 12 ? score.playerName().substring(0, 12) : score.playerName();
                String color = (i < topColors.length) ? topColors[i] : "§f";
                lines.add(String.format("%s #%d §f%s §7- §f%d pts", color, i + 1, playerName, score.points()));
            }
        }

        lines.add("§8 "); // espacio

        lines.add("§7Tu posición en el top:");
        if (topPosition > 0) {
            lines.add("§e  #" + topPosition + " §7con §f" + points + " §7pts");
        } else {
            lines.add("§c  Sin clasificar");
        }

        lines.add("§1 "); // Otro espacio

        if (unlockedAll) {
            lines.add("§aTienes todos los juegos");
            lines.add("§adesbloqueados");
        } else {
            lines.add("§cVe a la zona de");
            lines.add("§caprendizaje para");
            lines.add("§cdesbloquear los juegos");
        }

        lines.add("§8━━━━━━━━━━━━━━━━━━━━");

        return lines;
    }
}