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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Mi controlador para la lógica del minijuego del laberinto.
 * SUPER-OPTIMIZADO: Usa un único temporizador global y estructuras de datos eficientes
 * para garantizar la estabilidad con 200+ jugadores.
 */
public class MazeManager {

    private final DeWaltCore plugin;
    private final PointsManager pointsManager;

    private static final Location PRE_LOBBY_LOCATION = new Location(Bukkit.getWorld("DeWALTLaberinto"), -2.45, 28.00, -294.39, 90, 0);
    private static final Location MAZE_START_LOCATION = new Location(Bukkit.getWorld("DeWALTLaberinto"), 0.56, 7.00, 0.67, 0, 0);
    private static final Location SAFE_EXIT_LOCATION = new Location(Bukkit.getWorld("DEWALT LOBBY"), -2.13, 78.00, 0.44, 90, 0);

    private final Map<UUID, PlayerData> playerStates = new ConcurrentHashMap<>();

    // ANÁLISIS: Uso un ConcurrentSkipListMap (una implementación de NavigableMap) para que los ticks estén ordenados.
    // Esto me permite acceder solo a las tareas de restauración que ya están vencidas, sin iterar todo el mapa.
    private final NavigableMap<Long, List<Block>> scheduledRestorations = new ConcurrentSkipListMap<>();
    private final Map<Block, BlockState> originalBlockStates = new ConcurrentHashMap<>();
    private long currentTick = 0; // Mi contador de ticks global.

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
        new BukkitRunnable() {
            @Override
            public void run() {
                // Primero, proceso la restauración de bloques de forma eficiente.
                processBlockRestoration();

                if (playerStates.isEmpty()) {
                    currentTick++;
                    return;
                }

                // MI CORRECCIÓN: Itero directamente sobre el mapa concurrente.
                // Es seguro contra ConcurrentModificationException y evita crear un nuevo mapa cada tick.
                for (Map.Entry<UUID, PlayerData> entry : playerStates.entrySet()) {
                    UUID uuid = entry.getKey();
                    PlayerData data = entry.getValue();
                    Player player = Bukkit.getPlayer(uuid);

                    if (player == null || !player.isOnline()) {
                        playerStates.remove(uuid);
                        continue;
                    }

                    switch (data.getState()) {
                        case IN_PRE_LOBBY:
                            tickPreLobby(player, data);
                            break;
                        case IN_MAZE:
                            tickMaze(player, data);
                            break;
                        case IN_CUTTER_MINIGAME:
                            if (data.getActiveMinigame() != null) {
                                data.getActiveMinigame().tick();
                            }
                            break;
                    }
                }
                currentTick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void tickPreLobby(Player player, PlayerData data) {
        if (currentTick % 20 == 0) {
            int remaining = data.getCountdown();
            if (remaining > 0) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent(ChatColor.AQUA + "El laberinto comienza en " + remaining + " segundos..."));
                data.setCountdown(remaining - 1);
            } else {
                startMaze(player);
            }
        }
    }

    private void tickMaze(Player player, PlayerData data) {
        if (currentTick % 20 == 0) {
            data.incrementTime();
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.GREEN + "Tiempo: " + formatTime(data.getTime())));
        }
    }

    private void processBlockRestoration() {
        if (scheduledRestorations.isEmpty()) return;

        // Gracias al NavigableMap, solo obtengo las entradas cuyo tick es menor o igual al actual.
        // Esto es increíblemente más eficiente que iterar un mapa completo.
        for (Map.Entry<Long, List<Block>> entry : scheduledRestorations.headMap(currentTick, true).entrySet()) {
            for (Block block : entry.getValue()) {
                BlockState originalState = originalBlockStates.remove(block);
                if (originalState != null) {
                    originalState.update(true, true);
                }
            }
        }
        // Limpio del mapa todas las entradas que acabo de procesar.
        scheduledRestorations.headMap(currentTick, true).clear();
    }

    private void removeBarsTemporarily(Block clickedBlock) {
        List<Block> barsToRemove = new ArrayList<>();
        barsToRemove.add(clickedBlock);
        Block blockAbove = clickedBlock.getRelative(0, 1, 0);
        if (blockAbove.getType() == Material.IRON_BARS) barsToRemove.add(blockAbove);
        Block blockBelow = clickedBlock.getRelative(0, -1, 0);
        if (blockBelow.getType() == Material.IRON_BARS) barsToRemove.add(blockBelow);

        long restoreTick = currentTick + 60L; // 3 segundos a partir de ahora.

        for (Block bar : barsToRemove) {
            if (!originalBlockStates.containsKey(bar)) {
                originalBlockStates.put(bar, bar.getState());
                // MI CORRECCIÓN: Añado el bloque a la lista del tick de restauración correspondiente.
                // computeIfAbsent crea la lista si no existe, de forma muy limpia.
                scheduledRestorations.computeIfAbsent(restoreTick, k -> new ArrayList<>()).add(bar);
                bar.setType(Material.AIR);
            }
        }
        clickedBlock.getWorld().playSound(clickedBlock.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1.0f, 0.8f);
    }

    // El resto de los métodos (joinPreLobby, startMaze, startBoltCutterMinigame, leaveGame, finishMaze, etc.)
    // no necesitan cambios estructurales y se integran perfectamente con la nueva lógica optimizada.

    // ... [Aquí irían el resto de tus métodos sin cambios: joinPreLobby, startMaze, etc.] ...

    public void joinPreLobby(Player player) {
        if (isPlayerInGame(player)) {
            player.sendMessage(ChatColor.RED + "¡Ya estás en una partida o en la cola!");
            return;
        }

        player.teleport(PRE_LOBBY_LOCATION);
        PlayerData data = new PlayerData(PlayerState.IN_PRE_LOBBY);
        data.setCountdown(30);
        playerStates.put(player.getUniqueId(), data);
    }

    public void startMaze(Player player) {
        PlayerData data = playerStates.get(player.getUniqueId());
        if (data == null || data.getState() != PlayerState.IN_PRE_LOBBY) return;

        data.setState(PlayerState.IN_MAZE);
        data.setCountdown(0);

        player.teleport(MAZE_START_LOCATION);
        player.sendTitle(ChatColor.GOLD + "¡Laberinto iniciado!", ChatColor.YELLOW + "¡Corre!", 10, 70, 20);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);

        ItemStack boltCutter = OraxenItems.getItemById("corta_pernos").build();
        player.getInventory().addItem(boltCutter);
    }

    public void startBoltCutterMinigame(Player player, Block clickedBlock) {
        PlayerData data = playerStates.get(player.getUniqueId());
        if (data == null || data.getState() != PlayerState.IN_MAZE) return;

        data.setState(PlayerState.IN_CUTTER_MINIGAME);

        BoltCutterMinigame minigame = new BoltCutterMinigame(player, success -> {
            if (success) {
                removeBarsTemporarily(clickedBlock);
            }
            data.setState(PlayerState.IN_MAZE);
            data.setActiveMinigame(null);
        });

        data.setActiveMinigame(minigame);
    }

    public void leaveGame(Player player, boolean teleportToExit) {
        PlayerData data = playerStates.remove(player.getUniqueId());
        if (data == null) return;

        if (data.getActiveMinigame() != null) {
            data.getActiveMinigame().cancel();
        }

        clearPlayerInventory(player);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "Has salido del laberinto."));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);

        if (teleportToExit) {
            player.teleport(SAFE_EXIT_LOCATION);
        }
    }

    public void finishMaze(Player player) {
        PlayerData data = playerStates.remove(player.getUniqueId());
        if (data == null || data.getState() == PlayerState.IN_PRE_LOBBY) {
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

    private void clearPlayerInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !isOraxenHelmet(item)) {
                inventory.setItem(i, null);
            }
        }
        player.setItemOnCursor(null);
    }

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

    public void handleMinigameInteract(Player player, PlayerInteractEvent event) {
        PlayerData data = playerStates.get(player.getUniqueId());
        if (data != null && data.getState() == PlayerState.IN_CUTTER_MINIGAME && data.getActiveMinigame() != null) {
            data.getActiveMinigame().onPlayerInteract(event);
        }
    }

    private static class PlayerData {
        private PlayerState state;
        private int time;
        private int countdown;
        private BoltCutterMinigame activeMinigame;

        public PlayerData(PlayerState initialState) {
            this.state = initialState;
            this.time = 0;
            this.countdown = 0;
        }

        public PlayerState getState() { return state; }
        public void setState(PlayerState state) { this.state = state; }
        public int getTime() { return time; }
        public void incrementTime() { this.time++; }
        public int getCountdown() { return countdown; }
        public void setCountdown(int countdown) { this.countdown = countdown; }
        public BoltCutterMinigame getActiveMinigame() { return activeMinigame; }
        public void setActiveMinigame(BoltCutterMinigame minigame) { this.activeMinigame = minigame; }
    }
}