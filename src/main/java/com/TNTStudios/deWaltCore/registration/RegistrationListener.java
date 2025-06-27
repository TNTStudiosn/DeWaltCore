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

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mi listener para el proceso de registro.
 * Ahora también se encarga de "congelar" a los jugadores no registrados.
 */
public class RegistrationListener implements Listener {

    private final RegistrationManager registrationManager;

    // Uso un Set concurrente para guardar los jugadores que están en proceso de registro.
    // Es seguro para añadir/quitar desde diferentes hilos.
    private final Set<UUID> pendingRegistration = ConcurrentHashMap.newKeySet();

    public RegistrationListener(RegistrationManager registrationManager) {
        this.registrationManager = registrationManager;
        // Ya no necesito pasar el EmailValidator, usaré su método estático directamente.
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // 1. Limpio el inventario del jugador.
        registrationManager.clearPlayerInventory(player);

        // 2. Verifico si el jugador ya está registrado.
        if (registrationManager.isRegistered(playerUUID)) {
            player.sendMessage(ChatColor.GREEN + "¡Bienvenido de vuelta!");
            registrationManager.teleportToRegisteredSpawn(player);
        } else {
            // 3. Si no está registrado, inicio el proceso.
            pendingRegistration.add(playerUUID);
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
        UUID playerUUID = player.getUniqueId();

        if (!pendingRegistration.contains(playerUUID)) {
            return;
        }

        // Cancelo el evento para que su correo no aparezca en el chat público.
        event.setCancelled(true);
        String email = event.getMessage();

        // Verifico el formato del correo de forma síncrona y directa.
        if (!EmailValidator.isValidFormat(email)) {
            player.sendMessage(ChatColor.RED + "El formato del correo no es válido. Asegúrate de que sea como 'usuario@dominio.com'. Inténtalo de nuevo.");
            return;
        }

        // El formato es correcto, procedo a registrarlo.
        pendingRegistration.remove(playerUUID);
        registrationManager.registerPlayer(playerUUID, email);
        player.sendMessage(ChatColor.GREEN + "¡Registro completado con éxito! Gracias por unirte.");

    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Si un jugador se desconecta durante el proceso de registro, lo elimino de la lista.
        pendingRegistration.remove(event.getPlayer().getUniqueId());
    }

    // --- SECCIÓN DE EVENTOS PARA CONGELAR AL JUGADOR ---

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Solo cancelo el evento si realmente se está moviendo de bloque, para permitirle mirar a su alrededor.
        Location from = event.getFrom();
        Location to = event.getTo();
        if (pendingRegistration.contains(event.getPlayer().getUniqueId()) && (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ())) {
            event.setTo(from); // Lo devuelvo a su posición anterior para evitar que se mueva.
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (pendingRegistration.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (pendingRegistration.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player && pendingRegistration.contains(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}