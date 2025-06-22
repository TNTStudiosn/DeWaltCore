// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/drill/DrillListener.java
package com.TNTStudios.deWaltCore.minigames.drill;

import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.api.events.furniture.OraxenFurnitureInteractEvent;
// Importamos la ruta correcta de la clase FurnitureMechanic que me proporcionaste
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureMechanic;
import io.th0rgal.oraxen.mechanics.Mechanic;
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

    @EventHandler
    public void onFurnitureInteract(OraxenFurnitureInteractEvent event) {
        Player player = event.getPlayer();

        if (!drillManager.isPlayerInGame(player)) {
            return;
        }

        Mechanic mechanic = event.getMechanic();

        if (mechanic instanceof FurnitureMechanic furnitureMechanic) {
            // ========== LA CORRECCIÓN FINAL ==========
            // El método correcto, según el código fuente que me diste, es getItemID()
            String furnitureId = furnitureMechanic.getItemID();

            if ("mesa_de_trabajo".equalsIgnoreCase(furnitureId)) {
                event.setCancelled(true);
                drillManager.handlePaintingPickup(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!drillManager.isPlayerInGame(player)) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (drillManager.isDrillItem(itemInHand)) {
            event.setCancelled(true);
            drillManager.handlePaintingPlace(player, event.getBlockFace());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        drillManager.handlePlayerQuit(event.getPlayer());
    }

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