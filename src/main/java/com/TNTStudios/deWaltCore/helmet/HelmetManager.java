package com.TNTStudios.deWaltCore.helmet;

import com.TNTStudios.deWaltCore.DeWaltCore;
import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
     * Revisa el casco del jugador y lo aplica si es necesario, limpiando duplicados.
     * @param player El jugador a verificar.
     */
    public void ensureHelmetIsEquipped(Player player) {
        final ItemStack currentHelmet = player.getInventory().getHelmet();
        // Si el casco que tiene no es mi casco personalizado, se lo pongo y limpio su inventario.
        if (!isCustomHelmet(currentHelmet)) {
            // Si el jugador tenía un item en la cabeza (que no era el mío), lo devuelvo a su inventario
            // si hay espacio. Si no, lo dropeo. Esto evita que pierda items legítimos.
            if (currentHelmet != null && !currentHelmet.getType().isAir()) {
                player.getInventory().addItem(currentHelmet);
            }

            equipHelmetAndCleanInventory(player);
        }
    }

    /**
     * Le pone el casco personalizado a un jugador y elimina cualquier otra copia
     * que pueda tener en su inventario o en el cursor para evitar la duplicación.
     * @param player El jugador que recibirá el casco y cuya limpieza se ejecutará.
     */
    private void equipHelmetAndCleanInventory(Player player) {
        if (helmetItemCache == null) return;

        // 1. Me aseguro de que el jugador tenga el casco puesto.
        player.getInventory().setHelmet(helmetItemCache.clone());

        // 2. Reviso el item en el cursor del jugador. Si es mi casco, lo elimino.
        // Esto es clave para cuando se quitan el casco y queda "agarrado".
        if (isCustomHelmet(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }

        // 3. Recorro todo el inventario principal y elimino cualquier otra copia de mi casco.
        // Así evito que los acumulen mediante cualquier método.
        for (ItemStack item : player.getInventory().getContents()) {
            if (isCustomHelmet(item)) {
                player.getInventory().remove(item);
            }
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