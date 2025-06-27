package com.TNTStudios.deWaltCore.helmet;

import com.TNTStudios.deWaltCore.DeWaltCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Mi listener para eventos que podrían quitarle el casco a los jugadores.
 * Mi objetivo es bloquear CUALQUIER intento de quitárselo de forma eficiente.
 * Optimizado para alto rendimiento.
 */
public class HelmetListener implements Listener {

    private final HelmetManager helmetManager;
    private final DeWaltCore plugin;

    public HelmetListener(HelmetManager helmetManager, DeWaltCore plugin) {
        this.helmetManager = helmetManager;
        this.plugin = plugin;
    }

    // Cuando un jugador entra, me aseguro de que tenga el casco.
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // BlueAI: Uso el scheduler directamente con una lambda. Es más limpio y evita crear una
        // instancia de una clase anónima (new BukkitRunnable) para cada jugador que entra.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> helmetManager.ensureHelmetIsEquipped(player), 5L);
    }

    // Cuando el jugador reaparece, le devuelvo su casco.
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        // BlueAI: Aplico la misma optimización que en onPlayerJoin.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> helmetManager.ensureHelmetIsEquipped(player), 1L);
    }

    // El evento más importante: bloqueo los clics en el slot del casco.
    // La prioridad HIGHEST asegura que soy el último en revisar este evento.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Raw slot 5 es universalmente el slot del casco en el inventario del jugador.
        // Esta única comprobación cubre clics, shift-clicks, hotbar swaps y más. Es muy eficiente.
        if (event.getRawSlot() == 5) {
            event.setCancelled(true);

            // BlueAI: ¡Optimización CRÍTICA!
            // En lugar de limpiar el cursor y clonar un nuevo casco desde la caché (lo que crea un objeto
            // nuevo en un evento de alta frecuencia), reutilizo el item que ya está en el cursor.
            // Si el jugador logró poner el casco en el cursor, simplemente lo tomo de ahí y lo vuelvo a equipar.
            // Esto evita por completo la creación de un nuevo ItemStack (clone()) y reduce la presión sobre el GC.
            ItemStack cursorItem = event.getCursor();
            if (helmetManager.isCustomHelmet(cursorItem)) {
                ((Player) event.getWhoClicked()).getInventory().setHelmet(cursorItem);
                event.setCursor(null);
            }
        }
    }

    // Si un jugador muere, evito que el casco caiga al suelo.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        // BlueAI: Esta implementación con removeIf es moderna, limpia y la más performante. No necesita cambios.
        event.getDrops().removeIf(helmetManager::isCustomHelmet);
    }

    // Evito que el jugador dropee el casco con la tecla 'Q' (o la que tenga asignada).
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (helmetManager.isCustomHelmet(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    // Caso extra: evito que un dispensador le pueda quitar o poner un casco a un jugador.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispenseArmor(BlockDispenseArmorEvent event) {
        // Si el objetivo es un jugador, cancelo el evento.
        // Esto evita que le pongan otro casco (quitándole el nuestro) o le quiten el nuestro si está dañado.
        if (event.getTargetEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }
}