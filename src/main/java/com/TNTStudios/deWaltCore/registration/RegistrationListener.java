package com.TNTStudios.deWaltCore.registration;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;
import com.TNTStudios.deWaltCore.DeWaltCore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RegistrationListener implements Listener {

    private final Map<UUID, Integer> registrationStep = new HashMap<>();
    private final Map<UUID, BukkitRunnable> timeoutTasks = new HashMap<>();

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!RegistrationManager.isRegistered(player)) {
            player.setGameMode(GameMode.ADVENTURE); // no construcción
            player.sendTitle(ChatColor.RED + "\u168F", ChatColor.YELLOW + "Completa tu registro", 10, 99999, 10);

            player.sendMessage(ChatColor.GOLD + "Por favor, escribe tu nombre:");

            registrationStep.put(player.getUniqueId(), 1);

            // Start timeout
            BukkitRunnable task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (registrationStep.containsKey(player.getUniqueId())) {
                        player.kickPlayer(ChatColor.RED + "No completaste el registro a tiempo.");
                    }
                }
            };
            task.runTaskLater(DeWaltCore.getPlugin(DeWaltCore.class), 20 * 60); // 60 segundos
            timeoutTasks.put(player.getUniqueId(), task);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!registrationStep.containsKey(uuid)) return;

        event.setCancelled(true); // Bloquear chat público

        int step = registrationStep.get(uuid);
        String message = event.getMessage();

        switch (step) {
            case 1 -> {
                player.sendMessage(ChatColor.GREEN + "Correo electrónico:");
                registrationStep.put(uuid, 2);
                player.getPersistentDataContainer().set(RegistrationMetaKeys.NAME, RegistrationMetaKeys.STRING, message);
            }
            case 2 -> {
                player.sendMessage(ChatColor.GREEN + "Edad:");
                registrationStep.put(uuid, 3);
                player.getPersistentDataContainer().set(RegistrationMetaKeys.EMAIL, RegistrationMetaKeys.STRING, message);
            }
            case 3 -> {
                try {
                    int age = Integer.parseInt(message);
                    String name = player.getPersistentDataContainer().get(RegistrationMetaKeys.NAME, RegistrationMetaKeys.STRING);
                    String email = player.getPersistentDataContainer().get(RegistrationMetaKeys.EMAIL, RegistrationMetaKeys.STRING);

                    RegistrationManager.register(player, name, email, age);

                    registrationStep.remove(uuid);
                    if (timeoutTasks.containsKey(uuid)) {
                        timeoutTasks.get(uuid).cancel();
                        timeoutTasks.remove(uuid);
                    }

                    player.sendTitle(ChatColor.GREEN + "✔ Registro completo", "", 10, 70, 20);
                    player.setGameMode(GameMode.SURVIVAL);
                    player.sendMessage(ChatColor.GREEN + "¡Gracias por registrarte!");

                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Por favor, ingresa una edad válida.");
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        registrationStep.remove(uuid);
        if (timeoutTasks.containsKey(uuid)) {
            timeoutTasks.get(uuid).cancel();
            timeoutTasks.remove(uuid);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (registrationStep.containsKey(player.getUniqueId())) {
            event.setCancelled(true); // no puede moverse mientras no termine registro
        }
    }
}
