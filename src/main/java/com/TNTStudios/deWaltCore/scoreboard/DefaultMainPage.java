package com.TNTStudios.deWaltCore.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

public class DefaultMainPage implements ScoreboardPage {

    private final boolean unlockedAll;

    public DefaultMainPage(boolean unlockedAll) {
        this.unlockedAll = unlockedAll;
    }

    @Override
    public void applyTo(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        String objectiveId = "dewalt_" + player.getUniqueId().toString().substring(0, 8);
        Objective objective = scoreboard.registerNewObjective(objectiveId, "dummy", "§6§lDeWalt");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int line = 7;

        // Línea vacía
        objective.getScore("§f").setScore(line--);

        // Línea: Tu posición
        objective.getScore("§7Tu posición en el top:").setScore(line--);
        objective.getScore("§e       0").setScore(line--);

        // Línea vacía
        objective.getScore("§f§f").setScore(line--);

        // Desbloqueo en múltiples líneas
        if (unlockedAll) {
            objective.getScore("§aTienes todos los juegos").setScore(line--);
            objective.getScore("§adesbloqueados").setScore(line--);
        } else {
            objective.getScore("§cVe a la zona de").setScore(line--);
            objective.getScore("§caprendizaje para").setScore(line--);
            objective.getScore("§cdesbloquear los juegos").setScore(line--);
        }

        player.setScoreboard(scoreboard);
    }

}
