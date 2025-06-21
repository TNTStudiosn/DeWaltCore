package com.TNTStudios.deWaltCore.scoreboard;

import java.util.ArrayList;
import java.util.List;

public class ScoreboardStyle {

    public static final String TITLE = "§6§lDeWalt";

    public static List<String> buildDefaultPageLines(int topPosition, int points, boolean unlockedAll) {
        List<String> lines = new ArrayList<>();

        lines.add("§8━━━━━━━━━━━━━━━━━━━━");

        lines.add("§7Tu posición en el top:");
        // Añado lógica para mostrar la posición correctamente.
        // Si la posición es 0 o menos, significa que no está clasificado.
        if (topPosition > 0) {
            lines.add("§e  #" + topPosition + " §7con §f" + points + " §7pts");
        } else {
            lines.add("§c  Sin clasificar");
        }


        lines.add("§8 "); // espacio

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