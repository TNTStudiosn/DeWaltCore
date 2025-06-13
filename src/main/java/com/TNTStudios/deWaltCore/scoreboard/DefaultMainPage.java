package com.TNTStudios.deWaltCore.scoreboard;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.List;

public class DefaultMainPage implements ScoreboardPage {

    private final boolean unlockedAll;
    private final int topPosition;
    private final int totalPoints;

    public DefaultMainPage(int topPosition, int totalPoints, boolean unlockedAll) {
        this.topPosition = topPosition;
        this.totalPoints = totalPoints;
        this.unlockedAll = unlockedAll;
    }

    @Override
    public void applyTo(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        String objectiveId = "dewalt_" + player.getUniqueId().toString().substring(0, 8);
        Objective objective = scoreboard.registerNewObjective(objectiveId, "dummy", ScoreboardStyle.TITLE);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = ScoreboardStyle.buildDefaultPageLines(topPosition, totalPoints, unlockedAll);
        int score = lines.size();

        for (String line : lines) {
            while (objective.getScoreboard().getEntries().contains(line)) {
                line += "§r";
            }
            objective.getScore(line).setScore(score--);
        }

        player.setScoreboard(scoreboard);
    }
}

