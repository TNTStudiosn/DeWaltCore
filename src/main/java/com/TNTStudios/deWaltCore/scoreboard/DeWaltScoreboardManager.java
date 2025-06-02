package com.TNTStudios.deWaltCore.scoreboard;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeWaltScoreboardManager {

    // Mapeo de cada jugador a su página de scoreboard actual
    private static final Map<UUID, ScoreboardPage> activePages = new HashMap<>();

    // Establece una página de scoreboard para un jugador
    public static void setPage(Player player, ScoreboardPage page) {
        activePages.put(player.getUniqueId(), page);
        page.applyTo(player);
    }

    // Muestra la página por defecto si no está en un minijuego
    public static void showDefaultPage(Player player, boolean unlockedAll) {
        setPage(player, new DefaultMainPage(unlockedAll));
    }

    // Refresca la página actual (reaplica la lógica)
    public static void refresh(Player player) {
        ScoreboardPage page = activePages.get(player.getUniqueId());
        if (page != null) page.applyTo(player);
    }

    // Elimina el scoreboard y limpia su registro
    public static void clear(Player player) {
        activePages.remove(player.getUniqueId());
        if (player.isOnline()) {
            player.setScoreboard(player.getServer().getScoreboardManager().getNewScoreboard());
        }
    }

    // Solo actualiza si el jugador aún está viendo la página principal
    public static void updateDefaultPage(Player player, boolean unlockedAll) {
        UUID uuid = player.getUniqueId();
        ScoreboardPage current = activePages.get(uuid);

        if (current instanceof DefaultMainPage) {
            showDefaultPage(player, unlockedAll);
        }
    }
}
