// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/woodcutter/WoodcutterListener.java
package com.TNTStudios.deWaltCore.minigames.woodcutter;

import io.th0rgal.oraxen.api.events.furniture.OraxenFurnitureInteractEvent;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureMechanic;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        woodcutterManager.handleInventoryClose(event);
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

    /**
     * Mi handler para evitar que los jugadores tiren ítems durante el minijuego.
     * Así me aseguro de que no pierdan objetos clave ni ensucien el mapa.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(org.bukkit.event.player.PlayerDropItemEvent event) {
        // Si el jugador está en el lobby o en el juego, cancelo el evento.
        if (woodcutterManager.isPlayerParticipating(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "No puedes soltar ítems durante el minijuego.");
        }
    }

    /**
     * Mi handler para detectar cuando un jugador usa un comando de teletransporte.
     * Si está en el minijuego, lo saco para evitar conflictos.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        // Defino una lista de comandos que fuerzan la salida. Uso un Set para que la comprobación sea instantánea.
        final java.util.Set<String> teleportCommands = java.util.Set.of("/spawn", "/lobby", "/hub");
        String command = event.getMessage().toLowerCase().split(" ")[0];

        if (teleportCommands.contains(command) && woodcutterManager.isPlayerParticipating(player)) {
            // El comando lo teletransportará, así que solo necesito que el manager lo elimine de las listas del juego.
            woodcutterManager.removeOnlinePlayer(player, "Uso de comando de teletransporte", false);
        }
    }
}