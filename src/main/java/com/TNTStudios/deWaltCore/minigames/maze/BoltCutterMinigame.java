package com.TNTStudios.deWaltCore.minigames.maze;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.function.Consumer;

/**
 * Mi minijuego para la cortadora de pernos. Se activa al hacer clic derecho en un barrote.
 * OPTIMIZADO: Ya no gestiona su propio temporizador. Es un objeto de estado
 * que es "tickeado" por el MazeManager para máxima eficiencia.
 */
public class BoltCutterMinigame {

    private final Player player;
    private final Consumer<Boolean> onComplete;
    private int progress = 0;
    private boolean resolved = false;

    // Los parámetros del minijuego no cambian.
    private static final int DURATION_TICKS = 40; // 2 segundos
    private static final int SUCCESS_START_TICK = 25;
    private static final int SUCCESS_END_TICK = 30;
    private static final int INPUT_DELAY_TICKS = 4;

    // MI NUEVO CAMPO para limitar el envío de paquetes de la action bar.
    private String lastProgressBar = "";

    public BoltCutterMinigame(Player player, Consumer<Boolean> onComplete) {
        this.player = player;
        this.onComplete = onComplete;
        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.5f);
    }

    /**
     * El MazeManager llamará a este método cada tick.
     * Aquí va toda la lógica que antes estaba en el BukkitRunnable.
     */
    public void tick() {
        if (resolved) return;

        // Si el progreso supera la duración, el jugador ha fallado.
        if (progress > DURATION_TICKS) {
            fail();
            return;
        }

        displayProgressBar();
        progress++;
    }

    public void onPlayerInteract(PlayerInteractEvent event) {
        // La lógica de interacción no cambia, pero ahora es más segura.
        if (progress < INPUT_DELAY_TICKS) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(true);
        resolveAttempt();
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
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + "¡Corte perfecto!"));
        onComplete.accept(true);
    }

    private void fail() {
        if (resolved) return;
        resolved = true;
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "¡Fallaste! Inténtalo de nuevo."));
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 1.5f);
        onComplete.accept(false);
    }

    // Este método es para cuando un jugador se desconecta o sale del juego.
    public void cancel() {
        resolved = true;
    }

    private void displayProgressBar() {
        if (!player.isOnline()) {
            fail();
            return;
        }

        StringBuilder bar = new StringBuilder();
        int totalChars = 30;
        int greenStart = (int) (totalChars * ((double) SUCCESS_START_TICK / DURATION_TICKS));
        int greenEnd = (int) (totalChars * ((double) SUCCESS_END_TICK / DURATION_TICKS));
        int markerPos = (int) (totalChars * ((double) progress / DURATION_TICKS));
        markerPos = Math.min(totalChars - 1, markerPos);

        for (int i = 0; i < totalChars; i++) {
            if (i == markerPos) {
                bar.append(ChatColor.WHITE).append(ChatColor.BOLD).append("X");
            } else if (i >= greenStart && i <= greenEnd) {
                bar.append(ChatColor.GREEN).append("|");
            } else {
                bar.append(ChatColor.RED).append("|");
            }
        }

        // OPTIMIZACIÓN CLAVE: Solo envío el paquete si la barra ha cambiado visualmente.
        String currentBar = bar.toString();
        if (!currentBar.equals(lastProgressBar)) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(currentBar));
            lastProgressBar = currentBar;
        }
    }
}