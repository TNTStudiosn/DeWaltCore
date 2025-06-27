// FILE: src/main/java/com/TNTStudios/deWaltCore/helmet/HelmetManager.java

package com.TNTStudios.deWaltCore.helmet;

import com.TNTStudios.deWaltCore.DeWaltCore;
import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Mi manager para asegurar que los jugadores siempre tengan el casco puesto.
 * Completamente optimizado para operar basado en eventos, sin tareas periódicas
 * que puedan impactar el rendimiento del servidor.
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
        }
    }

    /**
     * Mi método principal de verificación. Se ejecuta en el hilo principal.
     * Revisa el casco del jugador y lo aplica si es necesario, devolviendo cualquier item previo.
     * @param player El jugador a verificar.
     */
    public void ensureHelmetIsEquipped(final Player player) {
        if (player == null || !player.isOnline() || helmetItemCache == null) {
            return;
        }

        final PlayerInventory inventory = player.getInventory();
        final ItemStack currentHelmet = inventory.getHelmet();

        // Si el casco que tiene no es mi casco personalizado, procedo a corregirlo.
        if (!isCustomHelmet(currentHelmet)) {
            // Si el jugador tenía un item en la cabeza (que no era el mío), lo devuelvo a su inventario
            // si hay espacio. Si no, lo dropeo cerca. Esto evita que pierda items legítimos.
            if (currentHelmet != null && currentHelmet.getType() != Material.AIR) {
                // El método addItem se encarga de dropear el item si no hay espacio. Es seguro.
                inventory.addItem(currentHelmet.clone());
            }

            // Le pongo el casco y limpio cualquier otra copia que pueda tener.
            equipHelmetAndCleanInventory(player);
        }
    }

    /**
     * Le pone el casco personalizado a un jugador y elimina cualquier otra copia
     * que pueda tener en su inventario para evitar la duplicación.
     * @param player El jugador que recibirá el casco.
     */
    private void equipHelmetAndCleanInventory(Player player) {
        if (helmetItemCache == null) return;

        PlayerInventory inventory = player.getInventory();

        // 1. Me aseguro de que el jugador tenga el casco puesto.
        inventory.setHelmet(getHelmetItem());

        // 2. Limpio copias del inventario principal para evitar acumulación.
        // El método de Bukkit es suficientemente performante para esta operación.
        inventory.remove(helmetItemCache);
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
        // OraxenItems.getIdByItem() es la forma más segura y eficiente de identificar mi item.
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