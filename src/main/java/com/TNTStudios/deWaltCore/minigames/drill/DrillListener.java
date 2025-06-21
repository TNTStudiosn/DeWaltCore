// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/drill/DrillListener.java
package com.TNTStudios.deWaltCore.minigames.drill;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Mi listener para todos los eventos relacionados con el minijuego del Taladro.
 */
public class DrillListener implements Listener {

    private final DrillManager drillManager;

    public DrillListener(DrillManager drillManager) {
        this.drillManager = drillManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Solo me interesan los jugadores que están en el minijuego.
        if (!drillManager.isPlayerInGame(player)) {
            return;
        }

        // Me aseguro de que la interacción sea un clic derecho en un bloque y con la mano principal.
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Compruebo que el jugador tenga el taladro en la mano.
        if (drillManager.isDrillItem(itemInHand)) {
            event.setCancelled(true); // Cancelo el evento para que no haga nada más (como abrir un cofre).
            drillManager.handlePaintingPlace(player, event.getClickedBlock(), event.getBlockFace());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Si un jugador se desconecta, lo saco del juego para limpiar sus datos.
        drillManager.handlePlayerQuit(event.getPlayer());
    }

    // --- PROTECCIÓN DE PINTURAS ---
    // Eventos para evitar que las pinturas del minijuego se rompan.

    @EventHandler
    public void onHangingBreak(HangingBreakEvent event) {
        // Si la causa de la rotura no es una entidad (ej. una explosión), la cancelo si es una pintura del juego.
        if (event.getCause() != HangingBreakEvent.RemoveCause.ENTITY) {
            if (drillManager.isPaintingManaged(event.getEntity())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        // Si cualquier entidad (incluyendo jugadores) intenta romperla, lo cancelo si es una pintura del juego.
        if (drillManager.isPaintingManaged(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}