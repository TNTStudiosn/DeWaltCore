package com.TNTStudios.deWaltCore.stats;

import com.TNTStudios.deWaltCore.DeWaltCore;
import com.TNTStudios.deWaltCore.scoreboard.DeWaltScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Me encargo de manejar toda la lógica de puntos y rankings de los jugadores.
 * Guardo y cargo los datos desde un archivo para que sean persistentes.
 */
public class PointsManager {

    private final File pointsFile;
    private FileConfiguration pointsConfig;
    private final Map<UUID, Integer> playerPoints = new HashMap<>();
    private final DeWaltCore plugin;

    public PointsManager(DeWaltCore plugin) {
        this.plugin = plugin;
        // Me aseguro de que la carpeta del plugin exista
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.pointsFile = new File(plugin.getDataFolder(), "player_points.yml");
        loadPoints(); // Cargo los puntos guardados en cuanto me inician
    }

    /**
     * Cargo los puntos desde el archivo player_points.yml.
     * Si el archivo no existe, simplemente me preparo para guardar nuevos datos.
     */
    public void loadPoints() {
        if (!pointsFile.exists()) {
            // Si no hay archivo, no hay nada que cargar. Se creará al guardar.
            return;
        }

        pointsConfig = YamlConfiguration.loadConfiguration(pointsFile);
        playerPoints.clear(); // Limpio el mapa por si es una recarga

        // Busco la sección "points" que contiene los UUIDs y sus puntos
        if (pointsConfig.isConfigurationSection("points")) {
            for (String uuidString : pointsConfig.getConfigurationSection("points").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    int points = pointsConfig.getInt("points." + uuidString);
                    playerPoints.put(uuid, points);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("He encontrado un UUID inválido en player_points.yml: " + uuidString);
                }
            }
        }
    }

    /**
     * Guardo todos los puntos del mapa de jugadores en el archivo yml.
     * Esto asegura que los datos no se pierdan si se reinicia el servidor.
     */
    public void savePoints() {
        // Creo una nueva configuración para evitar guardar datos de jugadores que ya no existen
        pointsConfig = new YamlConfiguration();

        for (Map.Entry<UUID, Integer> entry : playerPoints.entrySet()) {
            pointsConfig.set("points." + entry.getKey().toString(), entry.getValue());
        }

        try {
            pointsConfig.save(pointsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("¡Error! No pude guardar los puntos en player_points.yml");
            e.printStackTrace();
        }
    }

    /**
     * Obtengo los puntos de un jugador. Si no tiene, devuelvo 0.
     */
    public int getPoints(UUID uuid) {
        return playerPoints.getOrDefault(uuid, 0);
    }

    /**
     * Añado puntos a un jugador y actualizo su scoreboard si está online.
     */
    public void addPoints(UUID uuid, int amount) {
        int newPoints = getPoints(uuid) + amount;
        playerPoints.put(uuid, newPoints);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            updatePlayerScoreboard(player); // Actualizo su vista del scoreboard al instante
        }
    }

    /**
     * Calculo la posición de un jugador en el ranking basándome en sus puntos.
     * Ordeno a todos los jugadores de mayor a menor y busco su lugar.
     */
    public int getPlayerRank(UUID playerUuid) {
        // Ordeno una lista de todos los jugadores por sus puntos, de más a menos
        List<Map.Entry<UUID, Integer>> sortedPlayers = playerPoints.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toList());

        // Busco al jugador en la lista ordenada para saber su posición
        for (int i = 0; i < sortedPlayers.size(); i++) {
            if (sortedPlayers.get(i).getKey().equals(playerUuid)) {
                return i + 1; // La posición del top es el índice + 1
            }
        }

        // Si el jugador no está en la lista (porque tiene 0 puntos), lo pongo al final
        return sortedPlayers.size() + 1;
    }

    /**
     * Un método práctico para refrescar el scoreboard de un jugador con sus datos actuales.
     */
    public void updatePlayerScoreboard(Player player) {
        if (player == null || !player.isOnline()) return;

        int points = getPoints(player.getUniqueId());
        int rank = getPlayerRank(player.getUniqueId());
        // La lógica para saber si desbloqueó todo se mantiene donde estaba
        boolean hasUnlockedAll = false; // Aquí iría tu lógica para esta condición

        DeWaltScoreboardManager.updateDefaultPage(player, rank, points, hasUnlockedAll);
    }
}