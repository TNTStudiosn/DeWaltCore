package com.TNTStudios.deWaltCore.lobby;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/**
 * Mi listener para proteger a los jugadores en el lobby principal "DEWALT LOBBY".
 * Su única función es hacerlos completamente inmunes a cualquier tipo de daño
 * y a la pérdida de hambre, garantizando una zona segura.
 * Está optimizado para un alto rendimiento, actuando solo cuando es necesario.
 */
public class LobbyListener implements Listener {

    // El nombre del mundo del lobby principal que quiero proteger.
    private static final String LOBBY_WORLD_NAME = "DEWALT LOBBY";

    /**
     * Previene cualquier tipo de daño a los jugadores que se encuentren en el mundo del lobby.
     * La prioridad es HIGHEST para asegurarme de que mi decisión de cancelar el daño
     * sea la definitiva, sobrepasando a otros plugins que puedan modificarlo.
     * @param event El evento de daño.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        // Primero, verifico que la entidad afectada sea un jugador. Es el chequeo más rápido.
        if (event.getEntity() instanceof Player) {
            // Luego, compruebo si el jugador está en el mundo del lobby.
            // Esta comparación de strings es muy eficiente y se hace solo si la primera condición es cierta.
            if (event.getEntity().getWorld().getName().equalsIgnoreCase(LOBBY_WORLD_NAME)) {
                // Si está en el lobby, cancelo el evento. Inmune a caídas, lava, mobs, todo.
                event.setCancelled(true);
            }
        }
    }

    /**
     * Previene la pérdida de hambre (muslitos) para los jugadores en el mundo del lobby.
     * Al igual que con el daño, la prioridad es HIGHEST para garantizar que el
     * jugador nunca pierda hambre en esta zona segura.
     * @param event El evento de cambio de nivel de comida.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        // Al igual que antes, primero verifico que sea un jugador.
        if (event.getEntity() instanceof Player) {
            // Y luego, si está en el mundo del lobby.
            if (event.getEntity().getWorld().getName().equalsIgnoreCase(LOBBY_WORLD_NAME)) {
                // Cancelo el evento. El nivel de hambre del jugador no cambiará.
                event.setCancelled(true);
            }
        }
    }
}