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
 * Ahora está optimizado para depender de eventos y no de una tarea agresiva.
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
        // Lo clono para asegurarme de que la caché nunca sea modificada por accidente.
        this.helmetItemCache = OraxenItems.getItemById(HELMET_ID).build().clone();

        if (this.helmetItemCache == null || this.helmetItemCache.getType() == Material.AIR) {
            plugin.getLogger().severe("¡ERROR CRÍTICO! El item de Oraxen con ID '" + HELMET_ID + "' no se pudo encontrar.");
            plugin.getLogger().severe("La funcionalidad del casco permanente estará deshabilitada.");
            return; // No inicio la tarea si el item no existe.
        }

        // Esta es mi tarea de SEGURIDAD, no la lógica principal.
        // Se ejecuta con mucha menos frecuencia solo para corregir estados inesperados
        // (ej: otro plugin que limpia el inventario sin llamar eventos).
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Verifico si el jugador tiene el casco correcto.
                    ensureHelmetIsEquipped(player);
                }
            }
        }.runTaskTimerAsynchronously(plugin, 600L, 600L); // Empieza tras 30s, se repite cada 30s (600L = 30s * 20tps) y de forma asíncrona.
    }

    /**
     * Mi método principal de verificación.
     * Revisa el casco del jugador y lo aplica si es necesario.
     * @param player El jugador a verificar.
     */
    public void ensureHelmetIsEquipped(final Player player) {
        if (player == null || !player.isOnline()) return;

        final ItemStack currentHelmet = player.getInventory().getHelmet();

        // Si el casco que tiene no es mi casco personalizado, se lo pongo.
        if (!isCustomHelmet(currentHelmet)) {
            // Toda la manipulación del inventario debe hacerse en el hilo principal.
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Si el jugador tenía un item en la cabeza (que no era el mío), lo devuelvo a su inventario
                    // si hay espacio. Si no, lo dropeo. Esto evita que pierda items legítimos.
                    if (currentHelmet != null && currentHelmet.getType() != Material.AIR) {
                        player.getInventory().addItem(currentHelmet.clone());
                    }

                    // Le pongo el casco y limpio cualquier otra copia que pueda tener.
                    equipHelmetAndCleanInventory(player);
                }
            }.runTask(plugin);
        }
    }

    /**
     * Le pone el casco personalizado a un jugador y elimina cualquier otra copia
     * que pueda tener en su inventario para evitar la duplicación.
     * @param player El jugador que recibirá el casco.
     */
    private void equipHelmetAndCleanInventory(Player player) {
        if (helmetItemCache == null) return;

        // 1. Me aseguro de que el jugador tenga el casco puesto.
        player.getInventory().setHelmet(getHelmetItem());

        // 2. Limpio copias del inventario principal para evitar acumulación.
        // La limpieza del cursor la maneja ahora el listener de clics, que es más eficiente.
        player.getInventory().remove(helmetItemCache);
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

    /**
     * Devuelve una copia segura de mi casco cacheado.
     * @return Un nuevo ItemStack que es una copia del casco.
     */
    public ItemStack getHelmetItem() {
        return this.helmetItemCache != null ? this.helmetItemCache.clone() : new ItemStack(Material.AIR);
    }
}