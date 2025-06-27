// RUTA: src/main/java/com/TNTStudios/deWaltCore/registration/RegistrationListener.java
package com.TNTStudios.deWaltCore.registration;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

import java.util.UUID;

/**
  * Mi listener para el proceso de registro.
  * Ahora usa metadatos para "congelar" a los jugadores, mucho más eficiente.
  */
public class RegistrationListener implements Listener {

    private final RegistrationManager registrationManager;

    // YA NO NECESITO EL SET 'pendingRegistration'. Los metadatos son mi nueva fuente de verdad.
    // private final Set<UUID> pendingRegistration = ConcurrentHashMap.newKeySet();

    public RegistrationListener(RegistrationManager registrationManager) {
        this.registrationManager = registrationManager;
    }

    // Un método de ayuda para mantener el código limpio y no repetirme.
    // OPTIMIZACIÓN: Este chequeo es mucho más rápido que buscar en un Set.
    private boolean isPlayerFrozen(Player player) {
        return player.hasMetadata("unregistered");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        registrationManager.clearPlayerInventory(player);

        // La lógica de verificación ahora está encapsulada en el manager, que usa el caché.
        if (registrationManager.isRegistered(player.getUniqueId())) {
            player.sendMessage(ChatColor.GREEN + "¡Bienvenido de vuelta!");
            registrationManager.teleportToRegisteredSpawn(player);
        } else {
            // El manager se encargará de teletransportarlo y ponerle los metadatos.
            registrationManager.teleportToUnregisteredSpawn(player);
            player.sendMessage(ChatColor.GOLD + "-------------------------------------------");
            player.sendMessage(ChatColor.AQUA + "¡Bienvenid@ a DeWALT! Para continuar, necesitamos que te registres.");
            player.sendMessage(ChatColor.YELLOW + "Por favor, escribe tu correo electrónico en el chat.");
            player.sendMessage(ChatColor.GRAY + "(Tu correo no será visible para los demás jugadores)");
            player.sendMessage(ChatColor.RED + "No podrás moverte ni interactuar hasta que te registres.");
            player.sendMessage(ChatColor.GOLD + "-------------------------------------------");
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Si el jugador no está congelado, no me interesa su chat.
        if (!isPlayerFrozen(player)) {
            return;
        }

        event.setCancelled(true);
        String email = event.getMessage();

        if (!EmailValidator.isValidFormat(email)) {
            player.sendMessage(ChatColor.RED + "El formato del correo no es válido. Asegúrate de que sea como 'usuario@dominio.com'. Inténtalo de nuevo.");
            return;
        }

        // El formato es correcto, procedo a registrarlo.
        // El manager se encarga de quitarle el metadato.
        registrationManager.registerPlayer(player, email);
        player.sendMessage(ChatColor.GREEN + "¡Registro completado con éxito! Gracias por unirte. Ya puedes moverte.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Le digo al manager que el jugador se fue, para que limpie metadatos y caché si es necesario.
        registrationManager.onPlayerQuit(event.getPlayer());
    }

    // --- SECCIÓN DE EVENTOS PARA CONGELAR AL JUGADOR (AHORA OPTIMIZADOS) ---

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isPlayerFrozen(event.getPlayer())) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        // La condición de movimiento de bloque es correcta, la mantengo.
        if (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ()) {
            event.setTo(from);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (isPlayerFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isPlayerFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && isPlayerFrozen((Player) event.getWhoClicked())) {
            event.setCancelled(true);
        }
    }
}