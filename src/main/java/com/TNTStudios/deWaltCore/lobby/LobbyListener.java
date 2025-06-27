package com.TNTStudios.deWaltCore.lobby;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;

/**
 * Listener global que protege a todos los jugadores en todos los mundos
 * contra cualquier tipo de daño y pérdida de hambre.
 *
 * Altamente optimizado para servidores con alta concurrencia (~200 jugadores).
 * Solo actúa cuando la entidad afectada es un jugador.
 */
public class LobbyListener implements Listener {

    /**
     * Cancela todo tipo de daño a jugadores, sin importar el mundo.
     * Se ignoran entidades no jugador para evitar procesamiento innecesario.
     *
     * @param event Evento de daño a entidad.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
    }

    /**
     * Cancela la pérdida de hambre a jugadores, sin importar el mundo.
     *
     * @param event Evento de cambio de nivel de comida.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        event.setCancelled(true);
    }
}
