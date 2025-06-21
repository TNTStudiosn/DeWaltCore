package com.TNTStudios.deWaltCore.points;

import com.TNTStudios.deWaltCore.DeWaltCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Mi gestor de datos y puntos para todos los minijuegos.
 * Guarda todo en archivos YAML por jugador, usando su nombre.
 */
public class PointsManager {

    private final DeWaltCore plugin;
    private final File playerDataFolder;
    private final ZoneId cdmxZoneId = ZoneId.of("America/Mexico_City");
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public PointsManager(DeWaltCore plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
    }

    /**
     * Registra el tiempo de un jugador en un minijuego, calcula y guarda los puntos.
     * @return Los puntos ganados en esta partida.
     */
    public int recordCompletion(Player player, String minigameId, int newTime) {
        File playerFile = new File(playerDataFolder, player.getName() + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

        int bestTime = config.getInt("minigames." + minigameId + ".best-time", -1);
        int improvementCount = config.getInt("minigames." + minigameId + ".improvement-count", 0);
        int pointsAwarded = 0;
        String reason;

        if (bestTime == -1) {
            // Primera vez que completa el minijuego
            pointsAwarded = 10;
            reason = "Primera finalización";
            config.set("minigames." + minigameId + ".best-time", newTime);
        } else if (newTime < bestTime) {
            // Mejoró su tiempo
            switch (improvementCount) {
                case 0: pointsAwarded = 5; break;
                case 1: pointsAwarded = 2; break;
                default: pointsAwarded = 1; break;
            }
            reason = "Nuevo mejor tiempo";
            config.set("minigames." + minigameId + ".best-time", newTime);
            config.set("minigames." + minigameId + ".improvement-count", improvementCount + 1);
        } else {
            // No mejoró el tiempo, no hay puntos
            return 0;
        }

        // Actualizo el total de puntos
        int totalPoints = config.getInt("total-points", 0);
        config.set("total-points", totalPoints + pointsAwarded);

        // Añado una entrada al historial
        String timestamp = LocalDateTime.now(cdmxZoneId).format(dateTimeFormatter);
        String logEntry = String.format("%s [CDMX] | Minijuego: %s | Tiempo: %ds | Puntos: +%d | Razón: %s",
                timestamp, minigameId, newTime, pointsAwarded, reason);

        List<String> history = config.getStringList("history");
        history.add(logEntry);
        config.set("history", history);

        try {
            config.save(playerFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return pointsAwarded;
    }

    /**
     * Obtiene el total de puntos acumulados por un jugador.
     */
    public int getTotalPoints(Player player) {
        File playerFile = new File(playerDataFolder, player.getName() + ".yml");
        if (!playerFile.exists()) return 0;
        FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
        return config.getInt("total-points", 0);
    }
}