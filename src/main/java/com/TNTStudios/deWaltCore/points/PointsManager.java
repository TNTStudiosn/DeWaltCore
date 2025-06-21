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
import java.util.stream.Collectors;

/**
 * Mi gestor de datos y puntos para todos los minijuegos.
 * OPTIMIZADO: Ahora maneja el leaderboard en memoria para lecturas rápidas
 * y guarda los datos de jugador de forma asíncrona para no causar lag.
 */
public class PointsManager {

    // Defino mi estructura para guardar los puntajes de los jugadores.
    // Es un "record", una clase inmutable simple para guardar datos.
    // Implementa Comparable para que pueda ordenar mi lista fácilmente.
    public record PlayerScore(UUID uuid, String playerName, int points) implements Comparable<PlayerScore> {
        @Override
        public int compareTo(PlayerScore other) {
            // Lo ordeno de mayor a menor puntaje.
            return Integer.compare(other.points, this.points);
        }
    }

    private final DeWaltCore plugin;
    private final File playerDataFolder;
    private final File leaderboardFile;
    // Hago el leaderboard concurrente para poder modificarlo de forma segura desde tareas asíncronas.
    private final Map<UUID, PlayerScore> leaderboard = new ConcurrentHashMap<>();
    private List<PlayerScore> sortedLeaderboardCache = new ArrayList<>(); // Un caché ordenado para lecturas rápidas.

    private final ZoneId cdmxZoneId = ZoneId.of("America/Mexico_City");
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public PointsManager(DeWaltCore plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
        this.leaderboardFile = new File(plugin.getDataFolder(), "leaderboard.yml");
        // La carga inicial la hago de forma síncrona, ya que es al encender el servidor.
        loadLeaderboard();
    }

    /**
     * Carga el leaderboard desde el archivo a la memoria.
     * Se ejecuta una vez al iniciar el plugin.
     */
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

    /**
     * Guarda el leaderboard de la memoria al archivo de forma asíncrona.
     * Ya no bloquea el hilo principal.
     */
    public void saveLeaderboard() {
        // Hago una copia para evitar problemas de concurrencia mientras guardo.
        Map<UUID, PlayerScore> leaderboardCopy = new HashMap<>(this.leaderboard);

        new BukkitRunnable() {
            @Override
            public void run() {
                FileConfiguration config = new YamlConfiguration();
                // Limpio la config antes de guardar.
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

    /**
     * Registra la finalización de un minijuego, calcula y guarda los puntos.
     * Ahora, el guardado del archivo de jugador es asíncrono.
     * @return Los puntos ganados en esta partida.
     */
    public int recordCompletion(Player player, String minigameId, int newTime) {
        // Uso el UUID para el nombre del archivo. Es mucho más seguro.
        File playerFile = new File(playerDataFolder, player.getUniqueId().toString() + ".yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);

        int bestTime = config.getInt("minigames." + minigameId + ".best-time", -1);
        int improvementCount = config.getInt("minigames." + minigameId + ".improvement-count", 0);
        int pointsAwarded = 0;
        String reason;

        if (bestTime == -1) {
            pointsAwarded = 10;
            reason = "Primera finalización";
            config.set("minigames." + minigameId + ".best-time", newTime);
        } else if (newTime < bestTime) {
            switch (improvementCount) {
                case 0 -> pointsAwarded = 5;
                case 1 -> pointsAwarded = 2;
                default -> pointsAwarded = 1;
            }
            reason = "Nuevo mejor tiempo";
            config.set("minigames." + minigameId + ".best-time", newTime);
            config.set("minigames." + minigameId + ".improvement-count", improvementCount + 1);
        } else {
            return 0; // No mejoró, no hay puntos.
        }

        int totalPoints = config.getInt("total-points", 0) + pointsAwarded;
        config.set("total-points", totalPoints);
        config.set("player-name", player.getName()); // Guardo el nombre actual por si acaso.

        String timestamp = LocalDateTime.now(cdmxZoneId).format(dateTimeFormatter);
        String logEntry = String.format("%s [CDMX] | Minijuego: %s | Tiempo: %ds | Puntos: +%d | Razón: %s",
                timestamp, minigameId, newTime, pointsAwarded, reason);

        List<String> history = config.getStringList("history");
        history.add(logEntry);
        config.set("history", history);

        // Guardo el archivo del jugador de forma asíncrona.
        savePlayerFile(playerFile, config);

        // Actualizo el leaderboard en memoria si hubo cambios.
        if (pointsAwarded > 0) {
            updateLeaderboard(player.getUniqueId(), player.getName(), totalPoints);
        }

        return pointsAwarded;
    }

    /**
     * Método auxiliar para guardar el archivo de configuración de un jugador asíncronamente.
     */
    private void savePlayerFile(File file, FileConfiguration config) {
        // Copio el contenido a guardar para que no haya problemas si se modifica mientras se guarda.
        String dataToSave = config.saveToString();
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // La clase FileConfiguration no es thread-safe, por eso guardo el string directamente.
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

    /**
     * Actualiza la puntuación de un jugador en el leaderboard en memoria y reordena el caché.
     */
    private void updateLeaderboard(UUID uuid, String playerName, int newTotalPoints) {
        leaderboard.put(uuid, new PlayerScore(uuid, playerName, newTotalPoints));
        updateSortedLeaderboardCache();
    }

    /**
     * Reordena la lista cacheada del leaderboard. Se llama después de una actualización.
     */
    private void updateSortedLeaderboardCache() {
        List<PlayerScore> sortedList = new ArrayList<>(leaderboard.values());
        Collections.sort(sortedList);
        this.sortedLeaderboardCache = sortedList;
    }


    /**
     * Obtiene el total de puntos de un jugador. Lee desde el archivo, ya que es específico del jugador.
     */
    public int getTotalPoints(Player player) {
        File playerFile = new File(playerDataFolder, player.getUniqueId().toString() + ".yml");
        if (!playerFile.exists()) return 0;
        FileConfiguration config = YamlConfiguration.loadConfiguration(playerFile);
        return config.getInt("total-points", 0);
    }

    /**
     * Obtiene la posición del jugador desde el caché en memoria. Es una operación súper rápida.
     */
    public int getPlayerRank(Player player) {
        UUID uuid = player.getUniqueId();
        // Itero sobre la lista ya ordenada.
        for (int i = 0; i < sortedLeaderboardCache.size(); i++) {
            if (sortedLeaderboardCache.get(i).uuid().equals(uuid)) {
                return i + 1; // El rango es el índice + 1
            }
        }
        return 0; // No clasificado
    }
}