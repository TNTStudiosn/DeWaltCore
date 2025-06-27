package com.TNTStudios.deWaltCore.minigames.maze;

import com.TNTStudios.deWaltCore.DeWaltCore;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

/**
 * Mi minijuego para la cortadora de pernos. Se activa al hacer clic derecho en un barrote.
 * Está optimizado para correr con su propia tarea y notificar al MazeManager cuando termina.
 */
public class BoltCutterMinigame {

    private final Player player;
    private final Consumer<Boolean> onComplete; // Callback para notificar el resultado (true=éxito, false=fallo).
    private BukkitTask task;
    private int progress = 0;
    private boolean resolved = false;

    // Defino los parámetros del minijuego.
    private static final int DURATION_TICKS = 40; // 2 segundos de duración total.
    private static final int SUCCESS_START_TICK = 25; // Inicio de la zona verde.
    private static final int SUCCESS_END_TICK = 30; // Fin de la zona verde.

    public BoltCutterMinigame(Player player, Consumer<Boolean> onComplete) {
        this.player = player;
        this.onComplete = onComplete;
    }

    public void start() {
        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.5f);
        this.task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(DeWaltCore.getInstance(), 0L, 1L); // Se ejecuta cada tick para mayor fluidez.
    }

    private void tick() {
        if (resolved) return;

        if (progress > DURATION_TICKS) {
            fail();
            return;
        }
        displayProgressBar();
        progress++;
    }

    public void onPlayerInteract(PlayerInteractEvent event) {
        // El minijuego se resuelve con cualquier click derecho.
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            resolveAttempt();
        }
    }

    private void resolveAttempt() {
        if (resolved) return;
        if (progress >= SUCCESS_START_TICK && progress <= SUCCESS_END_TICK) {
            succeed();
        } else {
            fail();
        }
    }

    private void succeed() {
        if (resolved) return;
        resolved = true;
        task.cancel();
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + "¡Corte perfecto!"));
        onComplete.accept(true);
    }

    private void fail() {
        if (resolved) return;
        resolved = true;
        task.cancel();
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "¡Fallaste! Inténtalo de nuevo."));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.5f);
        onComplete.accept(false);
    }

    public void cancel() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        resolved = true;
    }

    private void displayProgressBar() {
        if (!player.isOnline()) {
            fail();
            return;
        }

        StringBuilder bar = new StringBuilder();
        int totalChars = 30;
        // Calculo las posiciones para la barra de progreso.
        int greenStart = (int) (totalChars * ((double) SUCCESS_START_TICK / DURATION_TICKS));
        int greenEnd = (int) (totalChars * ((double) SUCCESS_END_TICK / DURATION_TICKS));
        int markerPos = (int) (totalChars * ((double) progress / DURATION_TICKS));
        markerPos = Math.min(totalChars - 1, markerPos); // Me aseguro que no se salga de los límites.

        for (int i = 0; i < totalChars; i++) {
            if (i == markerPos) {
                bar.append(ChatColor.WHITE).append(ChatColor.BOLD).append("X");
            } else if (i >= greenStart && i <= greenEnd) {
                bar.append(ChatColor.GREEN).append("|"); // Zona de éxito.
            } else {
                bar.append(ChatColor.RED).append("|"); // Zona de fallo.
            }
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(bar.toString()));
    }
}