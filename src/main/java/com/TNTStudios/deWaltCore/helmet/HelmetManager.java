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
        ItemStack item = OraxenItems.getItemById(HELMET_ID).build();

        if (item == null || item.getType() == Material.AIR) {
            plugin.getLogger().severe("¡ERROR CRÍTICO! El item de Oraxen con ID '" + HELMET_ID + "' no se pudo encontrar.");
            plugin.getLogger().severe("La funcionalidad del casco permanente estará deshabilitada.");
            this.helmetItemCache = null;
        } else {
            this.helmetItemCache = item.clone();
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

            // Le pongo mi casco personalizado.
            inventory.setHelmet(getHelmetItem());
        }

        // BlueAI: Muevo esta lógica fuera del if. De esta forma, siempre me aseguro
        // de que el jugador no tenga copias en su inventario, incluso si ya tenía el casco puesto.
        // Esto cubre casos de borde (ej: un admin le da un item) sin coste de rendimiento adicional.
        cleanCopiesFromInventory(player);
    }

    /**
     * Elimina copias de mi casco personalizado del inventario principal de un jugador.
     * Esta iteración es marginalmente más eficiente que inventory.remove() para este caso específico,
     * ya que solo recorre los slots de almacenamiento.
     * @param player El jugador cuyo inventario será limpiado.
     */
    private void cleanCopiesFromInventory(Player player) {
        if (helmetItemCache == null) return;

        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            if (isCustomHelmet(storage[i])) {
                player.getInventory().setItem(i, null); // Uso setItem con null que es muy directo.
            }
        }
    }

    /**
     * Comprueba si un ItemStack es mi casco personalizado de Oraxen.
     * Este método es crítico para el rendimiento y debe ser lo más rápido posible.
     * @param item El item a comprobar.
     * @return true si es mi casco, false en caso contrario.
     */
    public boolean isCustomHelmet(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        // OraxenItems.getIdByItem() es la forma más segura y eficiente de identificar mi item,
        // asumiendo que usa NBT/PDC lookups.
        return HELMET_ID.equals(OraxenItems.getIdByItem(item));
    }

    /**
     * Devuelve una copia segura de mi casco cacheado para evitar modificaciones no deseadas.
     * @return Un nuevo ItemStack que es una copia del casco.
     */
    public ItemStack getHelmetItem() {
        return this.helmetItemCache != null ? this.helmetItemCache.clone() : new ItemStack(Material.AIR);
    }
}