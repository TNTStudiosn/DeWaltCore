package com.TNTStudios.deWaltCore.minigames.maze;

import com.TNTStudios.deWaltCore.DeWaltCore;
import com.TNTStudios.deWaltCore.points.PointsManager;
import com.TNTStudios.deWaltCore.scoreboard.DeWaltScoreboardManager;
import io.th0rgal.oraxen.api.OraxenItems;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mi controlador para la lógica del minijuego del laberinto.
 * OPTIMIZADO: Ahora usa un único temporizador global, estados de jugador
 * y maneja el pre-lobby y la mecánica de corte de barrotes de forma eficiente.
 */
public class MazeManager {

    private final DeWaltCore plugin;
    private final PointsManager pointsManager;

    // Defino las ubicaciones clave como constantes para fácil acceso.
    private static final Location PRE_LOBBY_LOCATION = new Location(Bukkit.getWorld("DeWALTLaberinto"), -2.45, 28.00, -294.39, 90, 0);
    private static final Location MAZE_START_LOCATION = new Location(Bukkit.getWorld("DeWALTLaberinto"), 0.56, 7.00, 0.67, 90, 0);
    private static final Location SAFE_EXIT_LOCATION = new Location(Bukkit.getWorld("DEWALTLOBBY"), -2.13, 78.00, 0.44, 90, 0);

    // Almaceno el estado completo de cada jugador para un mejor control.
    private final Map<UUID, PlayerData> playerStates = new ConcurrentHashMap<>();
    private final Map<Block, BlockState> restoringBlocks = new ConcurrentHashMap<>();
    private BukkitTask globalTimerTask;

    // Defino los estados en los que puede estar un jugador.
    public enum PlayerState {
        IN_PRE_LOBBY,
        IN_MAZE,
        IN_CUTTER_MINIGAME
    }

    public MazeManager(DeWaltCore plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
        startGlobalTimer();
    }

    private void startGlobalTimer() {
        if (globalTimerTask != null && !globalTimerTask.isCancelled()) return;

        globalTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (playerStates.isEmpty()) {
                    this.cancel();
                    globalTimerTask = null;
                    return;
                }

                playerStates.forEach((uuid, data) -> {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        playerStates.remove(uuid); // Limpio jugadores desconectados.
                        return;
                    }

                    // El temporizador principal solo avanza si el jugador está en el laberinto.
                    if (data.getState() == PlayerState.IN_MAZE) {
                        data.incrementTime();
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                new TextComponent(ChatColor.GREEN + "Tiempo: " + formatTime(data.getTime())));
                    }
                    // Si está en el minijuego de corte, su propio controlador se encarga de la action bar.
                });
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * El jugador entra a la sala de espera (pre-lobby).
     * @param player El jugador que ejecuta el comando /empezar.
     */
    public void joinPreLobby(Player player) {
        if (isPlayerInGame(player)) {
            player.sendMessage(ChatColor.RED + "¡Ya estás en una partida o en la cola!");
            return;
        }

        player.teleport(PRE_LOBBY_LOCATION);
        PlayerData data = new PlayerData(PlayerState.IN_PRE_LOBBY);
        playerStates.put(player.getUniqueId(), data);

        // Inicio la cuenta regresiva del pre-lobby.
        data.setTask(new BukkitRunnable() {
            private int countdown = 30;

            @Override
            public void run() {
                if (!player.isOnline() || !playerStates.containsKey(player.getUniqueId())) {
                    this.cancel();
                    return;
                }

                if (countdown > 0) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            new TextComponent(ChatColor.AQUA + "El laberinto comienza en " + countdown + " segundos... ¡Prepárate!"));
                    countdown--;
                } else {
                    startMaze(player);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L));
    }

    /**
     * Inicia el laberinto para un jugador después del pre-lobby.
     * @param player El jugador que va a comenzar.
     */
    public void startMaze(Player player) {
        PlayerData data = playerStates.get(player.getUniqueId());
        if (data == null) return; // Si el jugador se fue justo antes de empezar.

        data.setState(PlayerState.IN_MAZE);
        data.getTask().cancel(); // Cancelo la tarea del pre-lobby.

        player.teleport(MAZE_START_LOCATION);
        player.sendTitle(ChatColor.GOLD + "¡Laberinto iniciado!", ChatColor.YELLOW + "¡Corre!", 10, 70, 20);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);

        // Le doy el corta pernos de Oraxen.
        ItemStack boltCutter = OraxenItems.getItemById("corta_pernos").build();
        player.getInventory().addItem(boltCutter);

        startGlobalTimer(); // Me aseguro que el temporizador global esté activo.
    }

    /**
     * Inicia el minijuego de la cortadora de pernos.
     * @param player El jugador que lo usa.
     * @param clickedBlock El barrote de hierro al que le dio click.
     */
    public void startBoltCutterMinigame(Player player, Block clickedBlock) {
        PlayerData data = playerStates.get(player.getUniqueId());
        if (data == null || data.getState() != PlayerState.IN_MAZE) return;

        data.setState(PlayerState.IN_CUTTER_MINIGAME);

        BoltCutterMinigame minigame = new BoltCutterMinigame(player, success -> {
            // Este código se ejecuta cuando el minijuego termina (éxito o fallo).
            if (success) {
                // Quito los barrotes temporalmente.
                removeBarsTemporarily(clickedBlock);
            }
            // Restauro el estado del jugador para que pueda continuar en el laberinto.
            data.setState(PlayerState.IN_MAZE);
        });

        minigame.start();
        data.setActiveMinigame(minigame); // Guardo la referencia para poder cancelarlo si es necesario.
    }

    private void removeBarsTemporarily(Block clickedBlock) {
        List<Block> barsToRemove = new ArrayList<>();
        barsToRemove.add(clickedBlock);

        Block blockAbove = clickedBlock.getRelative(0, 1, 0);
        if (blockAbove.getType() == Material.IRON_BARS) {
            barsToRemove.add(blockAbove);
        }
        Block blockBelow = clickedBlock.getRelative(0, -1, 0);
        if (blockBelow.getType() == Material.IRON_BARS) {
            barsToRemove.add(blockBelow);
        }

        // Guardo el estado original de los bloques y los convierto en aire.
        for (Block bar : barsToRemove) {
            if (!restoringBlocks.containsKey(bar)) {
                restoringBlocks.put(bar, bar.getState());
                bar.setType(Material.AIR);
            }
        }

        clickedBlock.getWorld().playSound(clickedBlock.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1.0f, 0.8f);

        // Programo su restauración después de 3 segundos.
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Block bar : barsToRemove) {
                    BlockState originalState = restoringBlocks.remove(bar);
                    if (originalState != null) {
                        originalState.update(true, false);
                    }
                }
                clickedBlock.getWorld().playSound(clickedBlock.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.0f, 1.2f);
            }
        }.runTaskLater(plugin, 60L); // 3 segundos (3 * 20 ticks).
    }

    public void finishMaze(Player player) {
        PlayerData data = playerStates.remove(player.getUniqueId());
        if (data == null || data.getState() != PlayerState.IN_MAZE) {
            player.sendMessage(ChatColor.RED + "No has iniciado el laberinto. Usa /empezar.");
            return;
        }

        clearPlayerInventory(player);
        player.teleport(SAFE_EXIT_LOCATION);

        int finalTime = data.getTime();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        int pointsWon = pointsManager.recordCompletion(player, "maze", finalTime);

        if (pointsWon > 0) {
            player.sendTitle(ChatColor.GREEN + "¡Laberinto completado!",
                    String.format(ChatColor.YELLOW + "Tu tiempo: %s (+%d pts)", formatTime(finalTime), pointsWon), 10, 80, 20);
        } else {
            player.sendTitle(ChatColor.GREEN + "¡Laberinto completado!",
                    String.format(ChatColor.YELLOW + "Tu tiempo fue de %s", formatTime(finalTime)), 10, 80, 20);
        }

        int totalPoints = pointsManager.getTotalPoints(player);
        int topPosition = pointsManager.getPlayerRank(player);
        List<PointsManager.PlayerScore> topPlayers = pointsManager.getTopPlayers(3);
        DeWaltScoreboardManager.showDefaultPage(player, topPosition, totalPoints, false, topPlayers);
    }

    /**
     * Expulsa a un jugador del minijuego o de la cola.
     * @param player El jugador a expulsar.
     * @param teleportToExit Si debe ser teletransportado al lobby de salida.
     */
    public void leaveGame(Player player, boolean teleportToExit) {
        PlayerData data = playerStates.remove(player.getUniqueId());
        if (data == null) return;

        // Si estaba en alguna tarea (pre-lobby o minijuego de corte), la cancelo.
        if (data.getTask() != null) data.getTask().cancel();
        if (data.getActiveMinigame() != null) data.getActiveMinigame().cancel();

        clearPlayerInventory(player);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "Has salido del laberinto."));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);

        if (teleportToExit) {
            player.teleport(SAFE_EXIT_LOCATION);
        }
    }

    private void clearPlayerInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !isOraxenHelmet(item)) {
                inventory.setItem(i, null);
            }
        }
        // Limpio también el cursor por si tenía un ítem agarrado.
        player.setItemOnCursor(null);
    }

    // Método de ayuda para verificar el casco de Oraxen.
    private boolean isOraxenHelmet(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        String oraxenId = OraxenItems.getIdByItem(item);
        return "casco".equals(oraxenId);
    }

    public boolean isPlayerInGame(Player player) {
        return playerStates.containsKey(player.getUniqueId());
    }

    public PlayerState getPlayerState(Player player) {
        PlayerData data = playerStates.get(player.getUniqueId());
        return (data != null) ? data.getState() : null;
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }

    // Una clase interna para almacenar los datos de cada jugador de forma ordenada.
    private static class PlayerData {
        private PlayerState state;
        private int time;
        private BukkitTask task;
        private BoltCutterMinigame activeMinigame;

        public PlayerData(PlayerState initialState) {
            this.state = initialState;
            this.time = 0;
        }

        public PlayerState getState() { return state; }
        public void setState(PlayerState state) { this.state = state; }
        public int getTime() { return time; }
        public void incrementTime() { this.time++; }
        public BukkitTask getTask() { return task; }
        public void setTask(BukkitTask task) { this.task = task; }
        public BoltCutterMinigame getActiveMinigame() { return activeMinigame; }
        public void setActiveMinigame(BoltCutterMinigame minigame) { this.activeMinigame = minigame; }
    }

    /**
     * Pasa el evento de interacción al minijuego de corte activo del jugador.
     * Así me aseguro de que la instancia correcta procesa el clic.
     * @param player El jugador que interactúa.
     * @param event El evento de interacción.
     */
    public void handleMinigameInteract(Player player, PlayerInteractEvent event) {
        PlayerData data = playerStates.get(player.getUniqueId());

        // Me aseguro de que el jugador tiene datos y un minijuego activo para evitar errores.
        if (data != null && data.getState() == PlayerState.IN_CUTTER_MINIGAME && data.getActiveMinigame() != null) {
            data.getActiveMinigame().onPlayerInteract(event);
        }
    }
}