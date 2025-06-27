package com.TNTStudios.deWaltCore.helmet;

import com.TNTStudios.deWaltCore.DeWaltCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Mi listener para eventos que podrían quitarle el casco a los jugadores.
 * Mi objetivo es bloquear CUALQUIER intento de quitárselo de forma eficiente.
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
        // Lo hago con un pequeño retraso para asegurar que el jugador esté completamente cargado.
        new BukkitRunnable() {
            @Override
            public void run() {
                helmetManager.ensureHelmetIsEquipped(event.getPlayer());
            }
        }.runTaskLater(plugin, 5L);
    }

    // Cuando el jugador reaparece, le devuelvo su casco.
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        // El retraso de 1 tick es clave para asegurar que el inventario está listo para ser modificado.
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
        // Si el clic fue en el slot de armadura que corresponde al casco (raw slot 5), lo cancelo.
        // Esto cubre clics directos, shift-clicks, y cualquier otra interacción que empiece en ese slot.
        if (event.getRawSlot() == 5) {
            event.setCancelled(true);

            // Adicionalmente, si el jugador logró de alguna forma "agarrar" el casco en su cursor,
            // lo limpio y se lo re-equipo para corregir el estado visual.
            ItemStack cursorItem = event.getCursor();
            if (helmetManager.isCustomHelmet(cursorItem)) {
                event.setCursor(null); // Limpio el cursor
                ((Player) event.getWhoClicked()).getInventory().setHelmet(helmetManager.getHelmetItem()); // Re-equipo
            }
            return; // No necesito más validaciones si el clic fue en el slot 5.
        }

        // Bloqueo el intercambio con teclas numéricas (1-9) si el destino es el slot del casco.
        if (event.getAction() == InventoryAction.HOTBAR_SWAP) {
            // getSlot() en este caso es el slot de la hotbar. getHotbarButton() es el índice del botón presionado.
            if (event.getRawSlot() == (5)) { // hotbar_button es el slot destino
                event.setCancelled(true);
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