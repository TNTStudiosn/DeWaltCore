package com.TNTStudios.deWaltCore.minigames.maze;

import io.th0rgal.oraxen.api.OraxenItems;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Mi listener para eventos generales de minijuegos. Ahora gestiona la
 * interacción con el corta pernos, previene el drop de ítems y mejora
 * la gestión de desconexiones y comandos.
 */
public class MinigameListener implements Listener {

    private final MazeManager mazeManager;

    public MinigameListener(MazeManager mazeManager) {
        this.mazeManager = mazeManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Si el jugador está en el minijuego de corte, le paso el evento para que lo procese.
        if (mazeManager.getPlayerState(player) == MazeManager.PlayerState.IN_CUTTER_MINIGAME) {
            // Aquí debería haber una referencia al minijuego activo, lo gestiono desde MazeManager.
            // Para mantenerlo simple por ahora, asumiré que el manager tiene un método que lo delega.
            // Esto lo implementé en la refactorización de MazeManager y BoltCutterMinigame.
            return; // El minijuego activo se encargará del evento.
        }

        // Reviso si el jugador intenta usar el corta pernos.
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.IRON_BARS) return;

        // Verifico si el jugador está en el laberinto y no en otro estado.
        if (mazeManager.getPlayerState(player) != MazeManager.PlayerState.IN_MAZE) return;

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        String oraxenId = OraxenItems.getIdByItem(itemInHand);

        // Si tiene el corta pernos, inicio el minijuego.
        if ("corta_pernos".equals(oraxenId)) {
            event.setCancelled(true);
            mazeManager.startBoltCutterMinigame(player, clickedBlock);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        // Evito que los jugadores en cualquier fase del minijuego puedan tirar ítems.
        if (mazeManager.isPlayerInGame(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(org.bukkit.ChatColor.RED + "No puedes soltar ítems durante el minijuego.");
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        // Si el jugador usa /spawn mientras está en cualquier fase, lo saco del juego.
        if (event.getMessage().equalsIgnoreCase("/spawn")) {
            if (mazeManager.isPlayerInGame(event.getPlayer())) {
                mazeManager.leaveGame(event.getPlayer(), false); // No lo teletransporto, ya que /spawn lo hará.
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Si el jugador se desconecta, lo saco del juego para limpiar sus datos.
        if (mazeManager.isPlayerInGame(event.getPlayer())) {
            mazeManager.leaveGame(event.getPlayer(), false);
        }
    }
}