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
        Objective objective = scoreboard.registerNewObjective("dewalt", "dummy", "§6§lDeWalt");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Línea 3
        objective.getScore("§r").setScore(3);

        // Línea 2 - Top
        objective.getScore("§7Tu posición en el top: §e0").setScore(2);

        // Línea 1 - Desbloqueo
        if (unlockedAll) {
            objective.getScore("§aTienes todos los juegos desbloqueados").setScore(1);
        } else {
            objective.getScore("§cVe a la zona de aprendizaje para desbloquear los juegos").setScore(1);
        }

        player.setScoreboard(scoreboard);
    }
}
