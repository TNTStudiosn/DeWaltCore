package com.TNTStudios.deWaltCore.helmet;

import com.TNTStudios.deWaltCore.DeWaltCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Mi listener para eventos que podrían quitarle el casco a los jugadores.
 * Mi objetivo es bloquear cualquier intento de quitárselo.
 */
public class HelmetListener implements Listener {

    private final HelmetManager helmetManager;
    private final DeWaltCore plugin; // Necesito el plugin para el delayed task de respawn

    public HelmetListener(HelmetManager helmetManager, DeWaltCore plugin) {
        this.helmetManager = helmetManager;
        this.plugin = plugin;
    }

    // Cuando un jugador entra, me aseguro de que tenga el casco.
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        helmetManager.ensureHelmetIsEquipped(event.getPlayer());
    }

    // Cuando el jugador reaparece (después de morir), le devuelvo su casco.
    // Lo hago en un task con un tick de retraso para asegurar que el jugador esté listo.
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        new BukkitRunnable() {
            @Override
            public void run() {
                helmetManager.ensureHelmetIsEquipped(player);
            }
        }.runTaskLater(plugin, 1L);
    }

    // El evento más importante: bloqueo los clics en el slot del casco.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Si el clic fue en el slot de armadura que corresponde al casco...
        if (event.getSlotType() == InventoryType.SlotType.ARMOR && event.getRawSlot() == 5) { // Raw slot 5 es el casco
            event.setCancelled(true);
        }

        // Bloqueo también que puedan reemplazarlo con shift-click desde el inventario principal.
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            // Si el jugador está intentando mover un item desde su inventario principal...
            if (event.getClickedInventory().getType() == InventoryType.PLAYER) {
                // Y ese item es un casco...
                if (event.getCurrentItem() != null && event.getCurrentItem().getType().name().endsWith("_HELMET")) {
                    event.setCancelled(true);
                }
            }
        }
    }

    // Si un jugador muere, evito que el casco caiga al suelo.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Uso un iterador para remover de forma segura el casco de los drops.
        event.getDrops().removeIf(helmetManager::isCustomHelmet);
    }

    // Evito que el jugador dropee el casco con la tecla 'Q' (o la que tenga asignada).
    // Esto es una doble seguridad por si logran seleccionarlo de alguna manera.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event){
        if(helmetManager.isCustomHelmet(event.getItemDrop().getItemStack())){
            event.setCancelled(true);
        }
    }
}