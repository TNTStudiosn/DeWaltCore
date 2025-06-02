package com.TNTStudios.deWaltCore.scoreboard;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeWaltScoreboardManager {

    private static final Map<UUID, ScoreboardPage> activePages = new HashMap<>();

    public static void setPage(Player player, ScoreboardPage page) {
        activePages.put(player.getUniqueId(), page);
        page.applyTo(player);
    }

    public static void showDefaultPage(Player player, boolean unlockedAll) {
        setPage(player, new DefaultMainPage(unlockedAll));
    }

    public static void refresh(Player player) {
        ScoreboardPage page = activePages.get(player.getUniqueId());
        if (page != null) page.applyTo(player);
    }

    public static void clear(Player player) {
        activePages.remove(player.getUniqueId());
        player.setScoreboard(player.getServer().getScoreboardManager().getNewScoreboard());
    }
}
