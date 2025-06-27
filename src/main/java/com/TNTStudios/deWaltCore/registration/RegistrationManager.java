// RUTA: src/main/java/com/TNTStudios/deWaltCore/registration/RegistrationManager.java
package com.TNTStudios.deWaltCore.registration;

import com.TNTStudios.deWaltCore.DeWaltCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Mi gestor para el sistema de registro.
 * Se encarga de verificar si un jugador ya se registró y de guardar
 * sus datos de forma asíncrona para un rendimiento máximo.
 */
public class RegistrationManager {

    public final DeWaltCore plugin; // Hago el plugin accesible para la tarea de teleport.
    private final File registrationFolder;

    // Defino las ubicaciones exactas que me pediste.
    private static final Location UNREGISTERED_SPAWN = new Location(Bukkit.getWorld("DEWALT LOBBY"), -277.58, -29.00, 0.63, 90, 0);
    private static final Location REGISTERED_SPAWN = new Location(Bukkit.getWorld("DEWALT LOBBY"), -2.13, 78.00, 0.44, 90, 0);

    public RegistrationManager(DeWaltCore plugin) {
        this.plugin = plugin;
        // Creo una carpeta específica para los registros, manteniendo todo ordenado.
        this.registrationFolder = new File(plugin.getDataFolder(), "registrations");
        if (!registrationFolder.exists()) {
            registrationFolder.mkdirs();
        }
    }

    /**
     * Limpio el inventario del jugador. Esto se ejecuta siempre que entra.
     * @param player El jugador cuyo inventario voy a limpiar.
     */
    public void clearPlayerInventory(Player player) {
        player.getInventory().clear();
    }

    /**
     * Teletransporto al jugador a la zona de "no registrados".
     * @param player El jugador a teletransportar.
     */
    public void teleportToUnregisteredSpawn(Player player) {
        player.teleport(UNREGISTERED_SPAWN);
    }

    /**
     * Teletransporto al jugador a la zona de "registrados".
     * @param player El jugador a teletransportar.
     */
    public void teleportToRegisteredSpawn(Player player) {
        player.teleport(REGISTERED_SPAWN);
    }

    /**
     * Verifico si un jugador ya tiene un archivo de registro.
     * Esta operación es rápida y no necesita ser asíncrona.
     * @param playerUUID La UUID del jugador.
     * @return true si el archivo de registro existe.
     */
    public boolean isRegistered(UUID playerUUID) {
        File playerFile = new File(registrationFolder, playerUUID.toString() + ".yml");
        return playerFile.exists();
    }

    /**
     * Guardo los datos de registro de un jugador en su archivo .yml.
     * OPTIMIZADO: La escritura del archivo se hace en un hilo secundario
     * para no afectar el rendimiento del servidor.
     * Ahora acepto el objeto Player completo para acceder a su nombre.
     * @param player El jugador a registrar.
     * @param email El correo electrónico a guardar.
     */
    public void registerPlayer(Player player, String email) {
        new BukkitRunnable() {
            @Override
            public void run() {
                UUID playerUUID = player.getUniqueId();
                String playerName = player.getName();

                File playerFile = new File(registrationFolder, playerUUID.toString() + ".yml");
                FileConfiguration playerData = YamlConfiguration.loadConfiguration(playerFile);

                try {
                    // Ahora guardo todos los datos importantes para una mejor gestión.
                    playerData.set("uuid", playerUUID.toString());
                    playerData.set("username", playerName);
                    playerData.set("email", email);

                    long timestamp = System.currentTimeMillis();
                    playerData.set("registration_timestamp", timestamp);

                    // Añado una versión legible de la fecha para facilitar la revisión manual de los archivos.
                    String readableDate = Instant.ofEpochMilli(timestamp)
                            .atZone(ZoneId.systemDefault()) // O una zona horaria específica como "America/Mexico_City"
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
                    playerData.set("registration_date", readableDate);

                    playerData.save(playerFile);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "¡ERROR CRÍTICO! No pude guardar el archivo de registro para " + playerName + " (" + playerUUID + ")", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}