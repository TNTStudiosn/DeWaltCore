// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/drill/DrillListener.java
package com.TNTStudios.deWaltCore.minigames.drill;

import io.th0rgal.oraxen.api.events.furniture.OraxenFurnitureInteractEvent;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureMechanic;
import io.th0rgal.oraxen.mechanics.Mechanic;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
  * Mi listener para todos los eventos relacionados con el minijuego del Taladro.
  * Ahora toda la lógica compleja está en el DrillManager para mantener el código limpio.
 * --- ACTUALIZADO ---
 * Añadí una validación para que el jugador no pueda dropear el taladro durante la partida.
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

        if (drillManager.isOraxenItem(itemInHand, "taladro")) {
            event.setCancelled(true);
            drillManager.handlePaintingPlace(player, event.getBlockFace());
        }
    }

    /**
     * --- MI NUEVA IMPLEMENTACIÓN ---
     * Evito que el jugador pueda soltar el taladro mientras está en el minijuego.
     * Esto previene que se deshaga del ítem clave y quede atascado sin poder colocar pinturas.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        // Solo me interesa si el jugador está participando activamente en el juego.
        if (!drillManager.isPlayerInGame(player)) {
            return;
        }

        // Obtengo el ItemStack que se está intentando dropear.
        ItemStack droppedItem = event.getItemDrop().getItemStack();

        // Verifico si el ítem es el taladro de Oraxen.
        if (drillManager.isOraxenItem(droppedItem, "taladro")) {
            // Si es el taladro, cancelo el evento para que no se pueda soltar.
            event.setCancelled(true);
            // Le envío un mensaje claro al jugador para que sepa por qué no puede hacerlo.
            player.sendMessage(ChatColor.RED + "¡No puedes soltar el taladro durante el minijuego!");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // El manager se encarga de si estaba en el lobby o en el juego.
        drillManager.handlePlayerQuit(event.getPlayer());
    }

    // MI NUEVO EVENTO PARA CAPTURAR COMANDOS Y SACAR AL JUGADOR
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        // Me aseguro de que el jugador esté en el lobby o en el juego.
        if (drillManager.isPlayerInLobby(player) || drillManager.isPlayerInGame(player)) {
            String command = event.getMessage().split(" ")[0].toLowerCase();
            if (command.equals("/spawn")) {
                // Cancelo el comando para evitar que se ejecute.
                event.setCancelled(true);
                // El manager se encargará de sacarlo, limpiarlo y teletransportarlo.
                drillManager.handlePlayerCommand(player);
            }
        }
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