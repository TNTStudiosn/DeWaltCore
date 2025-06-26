// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/woodcutter/WoodcutterListener.java
package com.TNTStudios.deWaltCore.minigames.woodcutter;

import io.th0rgal.oraxen.api.events.furniture.OraxenFurnitureInteractEvent;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureMechanic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Mi listener para eventos específicos del minijuego Cortadora de Madera.
 * Mantiene el código limpio delegando toda la lógica al WoodcutterManager.
 */
public class WoodcutterListener implements Listener {

    private final WoodcutterManager woodcutterManager;

    public WoodcutterListener(WoodcutterManager woodcutterManager) {
        this.woodcutterManager = woodcutterManager;
    }

    /**
     * Handler para el clic derecho del jugador, tanto en bloques como en el aire.
     * Es la acción principal para los minijuegos.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (woodcutterManager.isPlayerInGame(player)) {
            // Toda la lógica la maneja el manager
            woodcutterManager.handlePlayerInteract(event);
        }
    }

    /**
     * Handler para la interacción con los muebles de Oraxen.
     * Se usa para las mesas de corte y ensamblaje.
     */
    @EventHandler
    public void onFurnitureInteract(OraxenFurnitureInteractEvent event) {
        Player player = event.getPlayer();
        if (woodcutterManager.isPlayerInGame(player)) {
            if (event.getMechanic() instanceof FurnitureMechanic furnitureMechanic) {
                event.setCancelled(true);
                woodcutterManager.handleFurnitureInteract(player, furnitureMechanic);
            }
        }
    }

    /**
     * Handler para los clics en los inventarios de los minijuegos.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && woodcutterManager.isPlayerInGame(player)) {
            // La lógica de la GUI la maneja el manager
            woodcutterManager.handleInventoryClick(event);
        }
    }

    /**
     * Handler para cuando un jugador se desconecta.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Me aseguro de que el jugador salga del juego si estaba participando.
        woodcutterManager.handlePlayerQuit(event.getPlayer());
    }
}