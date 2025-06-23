// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/concrete/ConcreteListener.java
package com.TNTStudios.deWaltCore.minigames.concrete;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Mi listener para eventos específicos del minijuego del Concreto.
 * Mantiene la lógica centralizada en el ConcreteManager.
 */
public class ConcreteListener implements Listener {

    private final ConcreteManager concreteManager;

    public ConcreteListener(ConcreteManager concreteManager) {
        this.concreteManager = concreteManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (concreteManager.isPlayerInGame(player)) {
            // Toda la lógica compleja (qué item usa, qué bloque es, etc.)
            // se maneja dentro del manager para mantener este listener limpio.
            concreteManager.handleBlockBreak(event);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Si un jugador se desconecta, me aseguro de que salga del juego
        concreteManager.handlePlayerQuit(event.getPlayer());
    }
}