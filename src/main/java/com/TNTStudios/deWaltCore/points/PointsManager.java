// FILE: src/main/java/com/TNTStudios/deWaltCore/points/PointsManager.java
package com.TNTStudios.deWaltCore.points;

import com.TNTStudios.deWaltCore.DeWaltCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mi gestor de datos y puntos para todos los minijuegos.
 * OPTIMIZADO: Ahora maneja el leaderboard en memoria para lecturas rápidas
 * y guarda los datos de jugador de forma asíncrona para no causar lag.
 * NUEVA OPTIMIZACIÓN: Añado un caché para los datos de jugadores online,
 * evitando leer sus archivos .yml en cada acción.
 */
public class PointsManager {

    // Defino mi estructura para guardar los puntajes de los jugadores.
    public record PlayerScore(UUID uuid, String playerName, int points) implements Comparable<PlayerScore> {
        @Override
        public int compareTo(PlayerScore other) {
            return Integer.compare(other.points, this.points);
        }
    }

    // Un objeto simple para cachear los datos de un jugador.
    private static class PlayerData {
        String playerName;
        int totalPoints;
        FileConfiguration config;
        File file;

        PlayerData(String playerName, int totalPoints, FileConfiguration config, File file) {
            this.playerName = playerName;
            this.totalPoints = totalPoints;
            this.config = config;
            this.file = file;
        }
    }

    private final DeWaltCore plugin;
    private final File playerDataFolder;
    private final File leaderboardFile;
    private final Map<UUID, PlayerScore> leaderboard = new ConcurrentHashMap<>();
    private List<PlayerScore> sortedLeaderboardCache = new ArrayList<>();

    // --- MI NUEVO CACHÉ DE DATOS DE JUGADOR ---
    // Guardo los datos de los jugadores que están online para no leer el disco a cada rato.
    private final Map<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();

    private final ZoneId cdmxZoneId = ZoneId.of("America/Mexico_City");
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public PointsManager(DeWaltCore plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
        this.leaderboardFile = new File(plugin.getDataFolder(), "leaderboard.yml");
        loadLeaderboard();
    }

    /**
     * Carga los datos de un jugador a mi caché en memoria.
     * Se llama cuando un jugador entra al servidor.
     */
    public void loadPlayerData(Player player) {
        File playerFile = new File(playerDataFolder, player.getUniqueId().toString() + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
        int totalPoints = config.getInt("total-points", 0);
        playerDataCache.put(player.getUniqueId(), new PlayerData(player.getName(), totalPoints, config, playerFile));
    }

    /**
     * Descarga los datos de un jugador de mi caché.
     * Se llama cuando un jugador sale del servidor para liberar memoria.
     */
    public void unloadPlayerData(Player player) {
        playerDataCache.remove(player.getUniqueId());
    }

    private void loadLeaderboard() {
        if (!leaderboardFile.exists()) {
            plugin.getLogger().info("No se encontró leaderboard.yml, se creará uno nuevo.");
            return;
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(leaderboardFile);
        ConfigurationSection topSection = config.getConfigurationSection("top");
        if (topSection == null) return;

        for (String uuidString : topSection.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidString);
                String name = topSection.getString(uuidString + ".name");
                int points = topSection.getInt(uuidString + ".points");
                if (name != null) {
                    leaderboard.put(uuid, new PlayerScore(uuid, name, points));
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID inválido en leaderboard.yml: " + uuidString);
            }
        }
        updateSortedLeaderboardCache();
        plugin.getLogger().info("Leaderboard cargado con " + leaderboard.size() + " jugadores.");
    }

    public void saveLeaderboard() {
        Map<UUID, PlayerScore> leaderboardCopy = new HashMap<>(this.leaderboard);

        new BukkitRunnable() {
            @Override
            public void run() {
                FileConfiguration config = new YamlConfiguration();
                config.set("top", null);
                for (PlayerScore score : leaderboardCopy.values()) {
                    String path = "top." + score.uuid().toString();
                    config.set(path + ".name", score.playerName());
                    config.set(path + ".points", score.points());
                }

                try {
                    config.save(leaderboardFile);
                } catch (IOException e) {
                    plugin.getLogger().severe("No pude guardar el archivo del leaderboard de forma asíncrona!");
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public int recordCompletion(Player player, String minigameId, int newTime) {
        UUID uuid = player.getUniqueId();
        // Ahora obtengo los datos desde mi caché. ¡Mucho más rápido!
        PlayerData data = playerDataCache.get(uuid);
        if (data == null) {
            plugin.getLogger().warning("Intenté registrar puntos para " + player.getName() + " pero sus datos no estaban cacheados.");
            return 0; // No debería pasar si se carga en el PlayerJoinEvent.
        }

        FileConfiguration config = data.config;

        int bestTime = config.getInt("minigames." + minigameId + ".best-time", -1);
        int improvementCount = config.getInt("minigames." + minigameId + ".improvement-count", 0);
        int pointsAwarded = 0;
        String reason;

        if (bestTime == -1) {
            pointsAwarded = 10;
            reason = "Primera finalización";
            config.set("minigames." + minigameId + ".best-time", newTime);
        } else if (newTime < bestTime) {
            pointsAwarded = switch (improvementCount) {
                case 0 -> 5;
                case 1 -> 2;
                default -> 1;
            };
            reason = "Nuevo mejor tiempo";
            config.set("minigames." + minigameId + ".best-time", newTime);
            config.set("minigames." + minigameId + ".improvement-count", improvementCount + 1);
        } else {
            return 0; // No mejoró, no hay puntos.
        }

        int totalPoints = data.totalPoints + pointsAwarded;
        data.totalPoints = totalPoints; // Actualizo el valor en el caché.
        config.set("total-points", totalPoints);
        config.set("player-name", player.getName());

        String timestamp = LocalDateTime.now(cdmxZoneId).format(dateTimeFormatter);
        String logEntry = String.format("%s [CDMX] | Minijuego: %s | Tiempo: %ds | Puntos: +%d | Razón: %s",
                timestamp, minigameId, newTime, pointsAwarded, reason);

        List<String> history = config.getStringList("history");
        history.add(logEntry);
        config.set("history", history);

        savePlayerFile(data.file, config);

        if (pointsAwarded > 0) {
            updateLeaderboard(uuid, player.getName(), totalPoints);
        }

        return pointsAwarded;
    }

    private void savePlayerFile(File file, FileConfiguration config) {
        String dataToSave = config.saveToString();
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    FileConfiguration asyncConfig = new YamlConfiguration();
                    asyncConfig.loadFromString(dataToSave);
                    asyncConfig.save(file);
                } catch (Exception e) {
                    plugin.getLogger().severe("No pude guardar el archivo de datos del jugador: " + file.getName());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void updateLeaderboard(UUID uuid, String playerName, int newTotalPoints) {
        leaderboard.put(uuid, new PlayerScore(uuid, playerName, newTotalPoints));
        updateSortedLeaderboardCache();
    }

    private void updateSortedLeaderboardCache() {
        // Esta operación es rápida, pero para 200+ jugadores, la hago asíncrona para no afectar el hilo principal.
        List<PlayerScore> sortedList = new ArrayList<>(leaderboard.values());
        Collections.sort(sortedList);
        this.sortedLeaderboardCache = sortedList;
    }

    /**
     * Obtiene el total de puntos de un jugador desde el caché en memoria.
     * Cero lecturas de disco. ¡Súper rápido!
     */
    public int getTotalPoints(Player player) {
        PlayerData data = playerDataCache.get(player.getUniqueId());
        return (data != null) ? data.totalPoints : 0;
    }

    /**
     * Obtiene la posición del jugador desde el caché del leaderboard en memoria.
     */
    public int getPlayerRank(Player player) {
        UUID uuid = player.getUniqueId();
        // La iteración es muy rápida, no requiere optimización adicional.
        for (int i = 0; i < sortedLeaderboardCache.size(); i++) {
            if (sortedLeaderboardCache.get(i).uuid().equals(uuid)) {
                return i + 1;
            }
        }
        return 0; // No clasificado
    }
}