// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/concrete/ConcreteListener.java
package com.TNTStudios.deWaltCore.minigames.concrete;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
// Importo el PlayerInteractEvent que necesito para la nueva mecánica.
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Mi listener para eventos específicos del minijuego del Concreto.
 * REFACTORIZADO: Ahora maneja el clic derecho para romper bloques y bloquea
 * el clic izquierdo para asegurar la compatibilidad con el modo Aventura.
 */
public class ConcreteListener implements Listener {

    private final ConcreteManager concreteManager;

    public ConcreteListener(ConcreteManager concreteManager) {
        this.concreteManager = concreteManager;
    }

    /**
     * Este handler previene que los jugadores rompan bloques con clic izquierdo.
     * Es importante para forzar la mecánica del clic derecho con el martillo.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // La lógica para cancelar el evento ahora está dentro del manager.
        concreteManager.handleBlockBreak(event);
    }

    /**
     * MI NUEVO HANDLER: Detecta el clic derecho del jugador sobre un bloque.
     * Esta es la nueva forma de romper bloques en el minijuego.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (concreteManager.isPlayerInGame(player)) {
            // Toda la lógica de validación (qué item usa, si es clic derecho, etc.)
            // se maneja dentro del manager para mantener este listener limpio.
            concreteManager.handleRightClickBreak(event);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Si un jugador se desconecta, me aseguro de que salga del juego
        concreteManager.handlePlayerQuit(event.getPlayer());
    }
}