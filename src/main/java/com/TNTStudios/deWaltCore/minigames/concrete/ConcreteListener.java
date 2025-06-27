// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/concrete/ConcreteListener.java
package com.TNTStudios.deWaltCore.minigames.concrete;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;
import java.util.List;

/**
 * Mi listener para eventos específicos del minijuego del Concreto.
 * REFACTORIZADO: Ahora maneja el clic derecho para romper bloques y bloquea
 * el clic izquierdo para asegurar la compatibilidad con el modo Aventura.
 * AÑADIDO: Ahora también gestiona la salida del jugador por comandos y previene que tire ítems.
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
        if (concreteManager.isPlayerInGameOrLobby(player)) {
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

    /**
     * MI NUEVA LÓGICA: Prevengo que los jugadores suelten ítems durante la partida o en el lobby.
     * Así evito que pierdan el martillo o cualquier otro ítem del minijuego.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (concreteManager.isPlayerInGameOrLobby(player)) {
            player.sendMessage(ChatColor.RED + "No puedes soltar ítems durante el minijuego.");
            event.setCancelled(true);
        }
    }

    /**
     * MI NUEVA LÓGICA: Saco al jugador del minijuego o de la cola si usa un comando para irse.
     * Esto asegura que nadie quede "atrapado" en el juego si intenta ir al spawn.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        // Me aseguro de que el jugador esté en el minijuego antes de procesar nada.
        if (concreteManager.isPlayerInGameOrLobby(player)) {
            String command = event.getMessage().split(" ")[0].toLowerCase();

            // Defino una lista de comandos que fuerzan la salida.
            final List<String> leaveCommands = Arrays.asList("/spawn", "/lobby", "/hub");

            if (leaveCommands.contains(command)) {
                // El manager se encargará de sacarlo, limpiarle el inventario y teletransportarlo.
                // El comando se ejecutará después, llevándolo a su destino.
                concreteManager.forceLeaveGame(player, "usaste un comando para salir");
            }
        }
    }
}