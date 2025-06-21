package com.TNTStudios.deWaltCore.points;

import com.TNTStudios.deWaltCore.DeWaltCore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Mi gestor de datos y puntos para todos los minijuegos.
 * Guarda todo en archivos YAML por jugador y mantiene un ranking global.
 */
public class PointsManager {

    // Defino mi estructura para guardar los puntajes de los jugadores.
    // Es un "record", una clase inmutable simple para guardar datos.
    // Implementa Comparable para que pueda ordenar mi lista fácilmente.
    public record PlayerScore(String playerName, int points) implements Comparable<PlayerScore> {
        @Override
        public int compareTo(PlayerScore other) {
            // Lo ordeno de mayor a menor puntaje.
            return Integer.compare(other.points, this.points);
        }
    }

    private final DeWaltCore plugin;
    private final File playerDataFolder;
    private final File leaderboardFile; // Mi nuevo archivo para el top
    private List<PlayerScore> leaderboard = new ArrayList<>(); // Mi top en memoria

    private final ZoneId cdmxZoneId = ZoneId.of("America/Mexico_City");
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public PointsManager(DeWaltCore plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
        // Inicializo mi nuevo archivo del leaderboard
        this.leaderboardFile = new File(plugin.getDataFolder(), "leaderboard.yml");
        // Cargo el leaderboard existente o lo calculo si no existe.
        if (leaderboardFile.exists()) {
            loadLeaderboard();
        } else {
            recalculateLeaderboard();
        }
    }

    /**
     * Carga el leaderboard desde el archivo leaderboard.yml a la memoria.
     */
    private void loadLeaderboard() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(leaderboardFile);
        ConfigurationSection topSection = config.getConfigurationSection("top");
        if (topSection == null) return;

        List<PlayerScore> loadedScores = new ArrayList<>();
        for (String rank : topSection.getKeys(false)) {
            String name = topSection.getString(rank + ".name");
            int points = topSection.getInt(rank + ".points");
            if (name != null) {
                loadedScores.add(new PlayerScore(name, points));
            }
        }
        // Me aseguro de que la lista esté ordenada por si acaso.
        Collections.sort(loadedScores);
        this.leaderboard = loadedScores;
    }

    /**
     * Guarda el leaderboard de la memoria al archivo leaderboard.yml.
     */
    public void saveLeaderboard() {
        FileConfiguration config = new YamlConfiguration();
        // Borro la sección "top" anterior para escribir la nueva.
        config.set("top", null);
        for (int i = 0; i < leaderboard.size(); i++) {
            PlayerScore score = leaderboard.get(i);
            String path = "top." + (i + 1);
            config.set(path + ".name", score.playerName());
            config.set(path + ".points", score.points());
        }

        try {
            config.save(leaderboardFile);
        } catch (IOException e) {
            plugin.getLogger().severe("No pude guardar el archivo del leaderboard!");
            e.printStackTrace();
        }
    }

    /**
     * Recalcula la tabla de clasificación completa leyendo todos los archivos de jugador.
     * Es una operación costosa, así que la uso con cuidado.
     */
    public void recalculateLeaderboard() {
        List<PlayerScore> newLeaderboard = new ArrayList<>();
        File[] playerFiles = playerDataFolder.listFiles((dir, name) -> name.endsWith(".yml"));

        if (playerFiles == null) return;

        for (File playerFile : playerFiles) {
            FileConfiguration pConfig = YamlConfiguration.loadConfiguration(playerFile);
            int totalPoints = pConfig.getInt("total-points", 0);
            // El nombre del jugador lo saco del nombre del archivo.
            String playerName = playerFile.getName().replace(".yml", "");
            newLeaderboard.add(new PlayerScore(playerName, totalPoints));
        }

        // Ordeno la lista de mayor a menor.
        Collections.sort(newLeaderboard);
        this.leaderboard = newLeaderboard;

        // Guardo el nuevo leaderboard en el archivo.
        saveLeaderboard();
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

        // Si se otorgaron puntos, recalculo el leaderboard.
        if (pointsAwarded > 0) {
            // Para un servidor grande, esto debería ser asíncrono o agrupado,
            // pero por ahora lo hago directo para que funcione.
            recalculateLeaderboard();
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

    /**
     * Obtiene la posición (rango) de un jugador en el leaderboard.
     * @return El rango del jugador (1 para el top), o 0 si no está clasificado.
     */
    public int getPlayerRank(Player player) {
        String playerName = player.getName();
        for (int i = 0; i < leaderboard.size(); i++) {
            if (leaderboard.get(i).playerName().equalsIgnoreCase(playerName)) {
                return i + 1; // El rango es el índice + 1
            }
        }
        return 0; // No clasificado
    }
}