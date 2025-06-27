// RUTA: src/main/java/com/TNTStudios/deWaltCore/registration/RegistrationManager.java
package com.TNTStudios.deWaltCore.registration;

import com.TNTStudios.deWaltCore.DeWaltCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
  * Mi gestor para el sistema de registro.
  * Ahora utiliza un caché para las verificaciones y carga las ubicaciones desde config.yml.
  */
public class RegistrationManager {

    public final DeWaltCore plugin;
    private final File registrationFolder;

    // OPTIMIZACIÓN: Caché para evitar leer el disco en cada join.
    // Guardo el estado de registro de los jugadores para una consulta casi instantánea.
    private final ConcurrentHashMap<UUID, Boolean> registeredCache = new ConcurrentHashMap<>();

    // MEJORA: Ahora las ubicaciones se cargarán desde la config para mayor flexibilidad.
    private Location unregisteredSpawn;
    private Location registeredSpawn;

    public RegistrationManager(DeWaltCore plugin) {
        this.plugin = plugin;
        this.registrationFolder = new File(plugin.getDataFolder(), "registrations");
        if (!registrationFolder.exists()) {
            registrationFolder.mkdirs();
        }
        // Cargo las configuraciones al iniciar el manager.
        loadLocations();
    }

    /**
     * Cargo las ubicaciones de spawn desde el archivo config.yml del plugin.
     * Esto me permite cambiarlas sin tener que recompilar todo.
     */
    public void loadLocations() {
        plugin.saveDefaultConfig(); // Me aseguro de que config.yml exista.
        FileConfiguration config = plugin.getConfig();

        unregisteredSpawn = getLocationFromConfig("spawns.unregistered");
        registeredSpawn = getLocationFromConfig("spawns.registered");

        plugin.getLogger().info("Ubicaciones de spawn cargadas correctamente.");
    }

    private Location getLocationFromConfig(String path) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) {
            plugin.getLogger().severe("¡ERROR! La sección '" + path + "' no existe en config.yml. Usando ubicación por defecto.");
            return new Location(Bukkit.getWorlds().get(0), 0, 100, 0); // Un fallback seguro.
        }
        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) {
            plugin.getLogger().severe("¡ERROR! El mundo '" + section.getString("world") + "' no existe. Usando mundo principal.");
            world = Bukkit.getWorlds().get(0);
        }
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    public void clearPlayerInventory(Player player) {
        player.getInventory().clear();
    }

    // OPTIMIZACIÓN: Usaré un BukkitRunnable para asegurar que el teletransporte ocurra en el hilo principal
    // y en un momento seguro, evitando conflictos con otros plugins durante el join.
    public void teleportToUnregisteredSpawn(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.teleport(unregisteredSpawn);
                // Marco al jugador como no registrado usando metadatos para un chequeo eficiente.
                player.setMetadata("unregistered", new FixedMetadataValue(plugin, true));
            }
        }.runTask(plugin);
    }

    public void teleportToRegisteredSpawn(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.teleport(registeredSpawn);
            }
        }.runTask(plugin);
    }

    /**
     * Verifico si un jugador está registrado usando el caché.
     * Si no está en el caché, consulto el disco y actualizo el caché.
     * @param playerUUID La UUID del jugador.
     * @return true si el jugador está registrado.
     */
    public boolean isRegistered(UUID playerUUID) {
        // Primero, consulto el caché, que es la operación más rápida.
        if (registeredCache.containsKey(playerUUID)) {
            return registeredCache.get(playerUUID);
        }

        // Si no está en caché, reviso el archivo (operación más lenta).
        File playerFile = new File(registrationFolder, playerUUID.toString() + ".yml");
        boolean exists = playerFile.exists();

        // Guardo el resultado en el caché para futuras consultas.
        registeredCache.put(playerUUID, exists);
        return exists;
    }

    /**
     * Registro a un jugador de forma asíncrona y actualizo el caché.
     * @param player El jugador a registrar.
     * @param email El correo electrónico a guardar.
     */
    public void registerPlayer(Player player, String email) {
        UUID playerUUID = player.getUniqueId();

        // Lo primero es actualizar el caché para que futuras llamadas a isRegistered() sean instantáneas.
        registeredCache.put(playerUUID, true);

        // Ahora sí, elimino el metadato de "congelado".
        player.removeMetadata("unregistered", plugin);

        new BukkitRunnable() {
            @Override
            public void run() {
                String playerName = player.getName();
                File playerFile = new File(registrationFolder, playerUUID.toString() + ".yml");
                FileConfiguration playerData = YamlConfiguration.loadConfiguration(playerFile);

                try {
                    playerData.set("uuid", playerUUID.toString());
                    playerData.set("username", playerName);
                    playerData.set("email", email);
                    long timestamp = System.currentTimeMillis();
                    playerData.set("registration_timestamp", timestamp);
                    String readableDate = Instant.ofEpochMilli(timestamp)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
                    playerData.set("registration_date", readableDate);

                    playerData.save(playerFile);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "¡ERROR CRÍTICO! No pude guardar el archivo de registro para " + playerName + " (" + playerUUID + ")", e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    // Método para limpiar el caché y metadatos cuando el jugador sale.
    public void onPlayerQuit(Player player) {
        UUID playerUUID = player.getUniqueId();
        // Si el jugador no estaba registrado, lo elimino del caché para que la próxima vez
        // se vuelva a verificar desde el archivo. Si ya estaba registrado, lo dejo en caché.
        if (player.hasMetadata("unregistered")) {
            player.removeMetadata("unregistered", plugin);
            registeredCache.remove(playerUUID);
        }
    }
}