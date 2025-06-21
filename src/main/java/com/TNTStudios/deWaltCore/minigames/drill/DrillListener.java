// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/drill/DrillListener.java
package com.TNTStudios.deWaltCore.minigames.drill;

// Añado el import para el evento de Oraxen

import io.th0rgal.oraxen.api.events.furniture.OraxenFurnitureInteractEvent;
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
 * Ahora incluye un handler para la interacción con muebles de Oraxen.
 */
public class DrillListener implements Listener {

    private final DrillManager drillManager;

    public DrillListener(DrillManager drillManager) {
        this.drillManager = drillManager;
    }

    /**
     * Mi nuevo handler para cuando un jugador hace clic derecho en la mesa de trabajo.
     * Es la forma más limpia y optimizada de detectar esta interacción.
     */
    @EventHandler
    public void onFurnitureInteract(OraxenFurnitureInteractEvent event) {
        Player player = event.getPlayer();

        // Solo me interesa si el jugador está en el juego.
        if (!drillManager.isPlayerInGame(player)) {
            return;
        }

        // Compruebo si el mueble con el que se interactuó es mi mesa_de_trabajo.
        if (event.getFurniture().getItemID().equalsIgnoreCase("mesa_de_trabajo")) {
            event.setCancelled(true); // Cancelo el evento para evitar cualquier acción por defecto.
            drillManager.handlePaintingPickup(player);
        }
    }

    /**
     * Este handler ahora es solo para colocar la pintura.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!drillManager.isPlayerInGame(player)) {
            return;
        }

        // Me aseguro de que sea un clic derecho en un bloque para colocar la pintura.
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Si el jugador tiene el item "taladro" (que ahora representa una pintura), la coloca.
        if (drillManager.isDrillItem(itemInHand)) {
            event.setCancelled(true);
            drillManager.handlePaintingPlace(player, event.getBlockFace());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Si un jugador se desconecta, lo saco del lobby o del juego.
        drillManager.handlePlayerQuit(event.getPlayer());
    }

    // --- PROTECCIÓN DE PINTURAS (sin cambios) ---
    @EventHandler
    public void onHangingBreak(HangingBreakEvent event) {
        if (event.getCause() != HangingBreakEvent.RemoveCause.ENTITY) {
            if (drillManager.isPaintingManaged(event.getEntity())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onHangingBreakByEntity(HangingBreakByEntityEvent event) {
        if (drillManager.isPaintingManaged(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}