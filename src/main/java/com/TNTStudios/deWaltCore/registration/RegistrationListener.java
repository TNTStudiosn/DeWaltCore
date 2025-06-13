package com.TNTStudios.deWaltCore.registration;

import com.TNTStudios.deWaltCore.DeWaltCore;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class RegistrationListener implements Listener {

    private final Map<UUID, Integer> registrationStep = new HashMap<>();
    private final Map<UUID, BukkitRunnable> timeoutTasks = new HashMap<>();
    private final Map<UUID, String> nameCache = new HashMap<>();
    private final Map<UUID, String> emailCache = new HashMap<>();

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!RegistrationManager.isRegistered(player)) {
            player.setGameMode(GameMode.ADVENTURE);
            player.sendTitle("§x§4§e§5§c§2§4\u168F", ChatColor.YELLOW + "Completa tu registro", 10, 99999, 10);

            sendStepMessage(player, "¡Bienvenido! Vamos a completar tu registro.", "Por favor, escribe tu " + ChatColor.AQUA + "NOMBRE");

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
                nameCache.put(uuid, message);
                registrationStep.put(uuid, 2);
                playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
                sendStepMessage(player, "¡Perfecto!", "Ahora escribe tu " + ChatColor.AQUA + "CORREO ELECTRÓNICO");
            }
            case 2 -> {
                emailCache.put(uuid, message);
                registrationStep.put(uuid, 3);
                playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING);
                sendStepMessage(player, "¡Casi terminamos!", "Por favor, escribe tu " + ChatColor.AQUA + "EDAD" + ChatColor.GRAY + " (solo número)");
            }
            case 3 -> {
                try {
                    int age = Integer.parseInt(message);
                    String name = nameCache.get(uuid);
                    String email = emailCache.get(uuid);

                    RegistrationManager.register(player, name, email, age);

                    // Limpieza
                    registrationStep.remove(uuid);
                    nameCache.remove(uuid);
                    emailCache.remove(uuid);

                    if (timeoutTasks.containsKey(uuid)) {
                        timeoutTasks.get(uuid).cancel();
                        timeoutTasks.remove(uuid);
                    }

                    player.sendTitle(ChatColor.GREEN + "✔ Registro completo", "", 10, 70, 20);
                    player.setGameMode(GameMode.SURVIVAL);
                    player.sendMessage("");
                    player.sendMessage(ChatColor.DARK_GREEN + "§m                           ");
                    player.sendMessage(ChatColor.GREEN + "✔ ¡Registro completado con éxito!");
                    player.sendMessage(ChatColor.GREEN + "¡Gracias por registrarte, disfruta el servidor!");
                    player.sendMessage(ChatColor.DARK_GREEN + "§m                           ");
                    player.sendMessage("");
                    playSound(player, Sound.ENTITY_PLAYER_LEVELUP);

                } catch (NumberFormatException e) {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.DARK_RED + "§m                           ");
                    player.sendMessage(ChatColor.RED + "Por favor, ingresa una " + ChatColor.YELLOW + "EDAD VÁLIDA" + ChatColor.RED + ".");
                    player.sendMessage(ChatColor.DARK_RED + "§m                           ");
                    player.sendMessage("");
                    playSound(player, Sound.ENTITY_VILLAGER_NO);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        registrationStep.remove(uuid);
        nameCache.remove(uuid);
        emailCache.remove(uuid);
        if (timeoutTasks.containsKey(uuid)) {
            timeoutTasks.get(uuid).cancel();
            timeoutTasks.remove(uuid);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (registrationStep.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Utils
    private void sendStepMessage(Player player, String header, String instruction) {
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_AQUA + "§m                           ");
        player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + header);
        player.sendMessage(ChatColor.GRAY + instruction);
        player.sendMessage(ChatColor.DARK_AQUA + "§m                           ");
        player.sendMessage("");
    }

    private void playSound(Player player, Sound sound) {
        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
    }
}
