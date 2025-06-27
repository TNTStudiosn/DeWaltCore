package com.TNTStudios.deWaltCore.helmet;

import com.TNTStudios.deWaltCore.DeWaltCore;
import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Mi manager para asegurar que los jugadores siempre tengan el casco puesto.
 * Está diseñado para ser altamente eficiente y a prueba de fallos.
 */
public class HelmetManager {

    private final DeWaltCore plugin;
    private static final String HELMET_ID = "casco"; // El ID de mi item de Oraxen

    // Caché para el item, para no tener que pedirlo a Oraxen una y otra vez.
    private ItemStack helmetItemCache;

    public HelmetManager(DeWaltCore plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        // Intento cargar el item del casco una vez al inicio para máxima eficiencia.
        this.helmetItemCache = OraxenItems.getItemById(HELMET_ID).build();
        if (this.helmetItemCache == null) {
            plugin.getLogger().severe("¡ERROR CRÍTICO! El item de Oraxen con ID '" + HELMET_ID + "' no se pudo encontrar.");
            plugin.getLogger().severe("La funcionalidad del casco permanente estará deshabilitada.");
            return; // No inicio la tarea si el item no existe.
        }

        // Esta es mi tarea de seguridad. Se asegura de que nadie se quite el casco.
        // Se ejecuta cada segundo para corregir cualquier problema (inventarios limpiados por minijuegos, etc.).
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Verifico si el jugador tiene el casco correcto.
                    ensureHelmetIsEquipped(player);
                }
            }
        }.runTaskTimer(plugin, 40L, 20L); // Empieza tras 2 segundos, se repite cada segundo.
    }

    /**
     * Mi método principal de verificación.
     * Revisa el casco del jugador y lo aplica si es necesario.
     * @param player El jugador a verificar.
     */
    public void ensureHelmetIsEquipped(Player player) {
        final ItemStack currentHelmet = player.getInventory().getHelmet();
        // Si el casco que tiene no es mi casco personalizado, se lo pongo.
        if (!isCustomHelmet(currentHelmet)) {
            applyHelmet(player);
        }
    }

    /**
     * Le pone el casco personalizado a un jugador.
     * @param player El jugador que recibirá el casco.
     */
    private void applyHelmet(Player player) {
        if (helmetItemCache != null) {
            // Clono el item para evitar cualquier problema con el stack original.
            player.getInventory().setHelmet(helmetItemCache.clone());
        }
    }

    /**
     * Comprueba si un ItemStack es mi casco personalizado de Oraxen.
     * @param item El item a comprobar.
     * @return true si es mi casco, false en caso contrario.
     */
    public boolean isCustomHelmet(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        // OraxenItems.getIdByItem() es la forma más segura de identificar mi item.
        return HELMET_ID.equals(OraxenItems.getIdByItem(item));
    }
}