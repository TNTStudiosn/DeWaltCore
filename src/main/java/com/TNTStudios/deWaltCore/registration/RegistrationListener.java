// RUTA: src/main/java/com/TNTStudios/deWaltCore/registration/RegistrationListener.java
package com.TNTStudios.deWaltCore.registration;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mi listener para el proceso de registro.
 * Se activa cuando un jugador entra al servidor y maneja la captura de su email por chat.
 */
public class RegistrationListener implements Listener {

    private final RegistrationManager registrationManager;
    private final EmailValidator emailValidator;

    // Uso un Set concurrente para guardar los jugadores que están en proceso de registro.
    // Es seguro para añadir/quitar desde diferentes hilos.
    private final Set<UUID> pendingRegistration = ConcurrentHashMap.newKeySet();

    public RegistrationListener(RegistrationManager registrationManager, EmailValidator emailValidator) {
        this.registrationManager = registrationManager;
        this.emailValidator = emailValidator;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // 1. Limpio el inventario del jugador, como se solicitó.
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
            player.sendMessage(ChatColor.GOLD + "-------------------------------------------");

            // Aquí podríamos añadir lógica para congelar al jugador y que no pueda moverse.
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Si el jugador no está en la lista de "pendiente de registro", no hago nada.
        if (!pendingRegistration.contains(playerUUID)) {
            return;
        }

        // Cancelo el evento para que su correo no aparezca en el chat público.
        event.setCancelled(true);
        String email = event.getMessage();

        // Verifico el formato del correo.
        if (!emailValidator.isValidFormat(email)) {
            player.sendMessage(ChatColor.RED + "El formato del correo no es válido. Asegúrate de que incluya un '@' y un dominio (ej: usuario@dominio.com). Inténtalo de nuevo.");
            return;
        }

        player.sendMessage(ChatColor.YELLOW + "Verificando tu correo en nuestro sistema, por favor espera...");

        // Verifico el correo con el servicio web. El resultado llega en el callback.
        emailValidator.verifyEmailOnline(email, isValid -> {
            // Este código se ejecuta cuando la API responde.
            if (isValid) {
                pendingRegistration.remove(playerUUID);
                registrationManager.registerPlayer(playerUUID, email);
                player.sendMessage(ChatColor.GREEN + "¡Registro completado con éxito! Gracias por unirte.");
                // Una vez registrado, lo envío a la zona de jugadores registrados.
                registrationManager.teleportToRegisteredSpawn(player);
            } else {
                player.sendMessage(ChatColor.RED + "Este correo no parece ser válido o no pudo ser verificado. Por favor, intenta con otro.");
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Si un jugador se desconecta durante el proceso de registro, lo elimino de la lista.
        pendingRegistration.remove(event.getPlayer().getUniqueId());
    }
}