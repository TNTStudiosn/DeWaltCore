// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/concrete/ConcreteManager.java
package com.TNTStudios.deWaltCore.minigames.concrete;

import com.TNTStudios.deWaltCore.DeWaltCore;
import com.TNTStudios.deWaltCore.points.PointsManager;
// Importo el ScoreboardManager para poder actualizarlo al final del juego.
import com.TNTStudios.deWaltCore.scoreboard.DeWaltScoreboardManager;
import io.th0rgal.oraxen.api.OraxenItems;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
// Importo los eventos que necesito para la nueva funcionalidad.
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mi manager para el minijuego del Concreto.
 * Se encarga de toda la lógica: lobby, cuenta atrás, puntuación y restauración del mapa.
 * -- MEJORAS REALIZADAS --
 * 1.  Añadida la capacidad de romper bloques con clic derecho para jugadores en modo Aventura.
 * 2.  El scoreboard ahora se actualiza inmediatamente al finalizar el juego, sin necesidad de reconectar.
 * 3.  El contador de jugadores ahora es visible en los mensajes de la cuenta atrás del lobby.
 * 4.  Añadidos comentarios explicativos sobre la lógica de control de jugadores.
 */
public class ConcreteManager {

    private final DeWaltCore plugin;
    private final PointsManager pointsManager;

    private enum GameState { INACTIVE, LOBBY, RUNNING }
    private volatile GameState currentState = GameState.INACTIVE;

    private final Set<UUID> lobbyPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> gameScores = new ConcurrentHashMap<>();
    private final Map<Location, BlockData> brokenBlocks = new ConcurrentHashMap<>();

    private BukkitTask lobbyCountdownTask;
    private BukkitTask gameTimerTask;

    private int lobbyTimeLeft;
    private int gameTimeLeft;

    // --- CONSTANTES DE CONFIGURACIÓN DEL JUEGO ---
    private static final int MIN_PLAYERS = 1;
    private static final int MAX_PLAYERS = 20;
    private static final int LOBBY_DURATION_SECONDS = 30;
    private static final int GAME_DURATION_SECONDS = 120; // 2 minutos
    private static final String HAMMER_ITEM_ID = "martillo_demoledor";

    // --- OPTIMIZACIÓN: Uso un Set para la comprobación de bloques, es O(1) ---
    private static final Set<Material> TARGET_BLOCKS = EnumSet.of(
            Material.BRICKS,
            Material.GRANITE,
            Material.TERRACOTTA
    );

    public ConcreteManager(DeWaltCore plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
    }

    // --- 1. LÓGICA DEL LOBBY ---

    public void addPlayerToLobby(Player player) {
        if (currentState == GameState.RUNNING) {
            player.sendMessage(ChatColor.RED + "¡El minijuego del Concreto ya ha comenzado! Espera a que termine.");
            return;
        }

        // --- MI LÓGICA DE CONTROL DE JUGADORES ---
        // Me aseguro de que el lobby no supere el máximo de jugadores permitidos.
        // Si el número de jugadores en el lobby (lobbyPlayers.size()) es mayor o igual
        // al máximo (MAX_PLAYERS), rechazo al nuevo jugador.
        // Esto evita que el jugador 21 (o superior) pueda entrar.
        if (lobbyPlayers.size() >= MAX_PLAYERS) {
            player.sendMessage(ChatColor.RED + "¡El lobby está lleno! (" + MAX_PLAYERS + " jugadores).");
            return;
        }
        if (!lobbyPlayers.add(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Ya estás en la sala de espera.");
            return;
        }

        player.sendTitle(ChatColor.AQUA + "¡Bienvenido!", ChatColor.YELLOW + "El juego del Concreto empezará pronto.", 10, 70, 20);
        broadcastToLobby(ChatColor.AQUA + player.getName() + " ha entrado al lobby. (" + lobbyPlayers.size() + "/" + MAX_PLAYERS + ")", player.getUniqueId());

        if (currentState == GameState.INACTIVE && lobbyPlayers.size() >= MIN_PLAYERS) {
            startLobbyCountdown();
        }
    }

    private void startLobbyCountdown() {
        currentState = GameState.LOBBY;
        lobbyTimeLeft = LOBBY_DURATION_SECONDS;

        lobbyCountdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (lobbyPlayers.size() < MIN_PLAYERS) {
                    broadcastToLobby(ChatColor.RED + "No hay suficientes jugadores. El inicio se ha cancelado.", null);
                    resetGame();
                    return;
                }

                if (lobbyTimeLeft <= 0) {
                    this.cancel();
                    startGame();
                    return;
                }

                if (lobbyTimeLeft % 10 == 0 || lobbyTimeLeft <= 5) {
                    // --- MI MEJORA: Añado el contador de jugadores al mensaje de la cuenta atrás ---
                    String message = String.format("%sEl juego comenzará en %s%d segundos... %s(%d/%d jugadores)",
                            ChatColor.YELLOW, ChatColor.WHITE, lobbyTimeLeft, ChatColor.AQUA, lobbyPlayers.size(), MAX_PLAYERS);
                    broadcastToLobby(message, null);
                    playSoundForLobby(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f);
                }
                lobbyTimeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // --- 2. LÓGICA DEL MINIJUEGO ---

    private void startGame() {
        currentState = GameState.RUNNING;
        gameTimeLeft = GAME_DURATION_SECONDS;

        ItemStack hammer = OraxenItems.getItemById(HAMMER_ITEM_ID).build();
        if (hammer == null || hammer.getType() == Material.AIR) {
            plugin.getLogger().severe("¡El item 'martillo_demoledor' no existe en Oraxen! El minijuego no puede empezar.");
            broadcastToLobby(ChatColor.RED + "Error del servidor: No se pudo encontrar el ítem del juego. Avisa a un admin.", null);
            resetGame();
            return;
        }

        for (UUID uuid : lobbyPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                gameScores.put(uuid, 0);
                p.getInventory().clear();
                p.getInventory().addItem(hammer.clone());
                p.sendTitle(ChatColor.GREEN + "¡A ROMPER!", ChatColor.WHITE + "¡Usa clic derecho en Ladrillos, Granito y Terracota!", 10, 60, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            }
        }
        lobbyPlayers.clear();

        broadcastToGame(ChatColor.GOLD + "¡El juego ha comenzado! ¡El que más bloques rompa, gana!");
        startGameTimer();
    }

    private void startGameTimer() {
        gameTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (gameScores.isEmpty() || gameTimeLeft <= 0) {
                    endGame(gameTimeLeft <= 0 ? "Se acabó el tiempo" : "Todos los jugadores salieron");
                    return;
                }
                updateActionBarForAll();
                gameTimeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Este método prohíbe el 'BlockBreakEvent' (clic izquierdo) normal,
     * para forzar al jugador a usar la nueva mecánica de clic derecho.
     */
    public void handleBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isPlayerInGame(player)) {
            event.setCancelled(true);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "¡Debes usar el clic derecho para romper bloques!"));
        }
    }

    /**
     * MI NUEVO MÉTODO: Maneja el intento de romper un bloque con clic derecho.
     * Centraliza toda la lógica de validación y puntuación.
     */
    public void handleRightClickBreak(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Me aseguro de que el jugador está en el juego y ha hecho clic derecho en un bloque.
        if (!isPlayerInGame(player) || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        event.setCancelled(true); // Cancelo la interacción para evitar acciones no deseadas (abrir cofres, etc.).

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!HAMMER_ITEM_ID.equals(OraxenItems.getIdByItem(itemInHand))) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "¡Usa el Martillo Demoledor!"));
            return;
        }

        Block block = event.getClickedBlock();
        if (block != null && TARGET_BLOCKS.contains(block.getType())) {
            // Guardamos el estado original del bloque antes de romperlo
            brokenBlocks.putIfAbsent(block.getLocation(), block.getBlockData());

            // Rompemos el bloque
            block.setType(Material.AIR);
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);

            // Actualizamos la puntuación
            gameScores.computeIfPresent(player.getUniqueId(), (uuid, score) -> score + 1);
        }
    }


    // --- 3. FINALIZACIÓN Y LIMPIEZA ---

    private void endGame(String reason) {
        if (currentState != GameState.RUNNING) return;
        currentState = GameState.INACTIVE;
        if (gameTimerTask != null) gameTimerTask.cancel();

        broadcastToGame(ChatColor.GOLD + "¡El juego ha terminado! Razón: " + reason + ". Calculando resultados...");

        List<Map.Entry<UUID, Integer>> sortedPlayers = gameScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());

        new BukkitRunnable() {
            @Override
            public void run() {
                awardPoints(sortedPlayers);
                restoreBrokenBlocks();
                clearPlayerInventories();
                resetGame();
            }
        }.runTaskLater(plugin, 60L); // 3 segundos de delay
    }

    private void awardPoints(List<Map.Entry<UUID, Integer>> sortedPlayers) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < sortedPlayers.size(); i++) {
                    Map.Entry<UUID, Integer> entry = sortedPlayers.get(i);
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p == null || !p.isOnline()) continue;

                    int pointsWon;
                    String positionMessage;

                    if (i == 0) {
                        pointsWon = 15;
                        positionMessage = ChatColor.GOLD + "¡Ganaste! (1er Lugar)";
                    } else if (i == 1) {
                        pointsWon = 7;
                        positionMessage = ChatColor.GRAY + "¡Quedaste 2do!";
                    } else if (i == 2) {
                        pointsWon = 2;
                        positionMessage = ChatColor.DARK_RED + "¡Quedaste 3ro!";
                    } else {
                        pointsWon = 1;
                        positionMessage = ChatColor.AQUA + "¡Buena partida!";
                    }

                    pointsManager.addPoints(p, pointsWon, "concrete_minigame", "Ranking final del minijuego");
                    String finalMessage = String.format(ChatColor.YELLOW + "Rompiste %d bloques. " + ChatColor.GREEN + "(+%d pts)", entry.getValue(), pointsWon);
                    p.sendTitle(positionMessage, finalMessage, 10, 80, 20);

                    // --- MI SOLUCIÓN: Actualizo el scoreboard en el hilo principal ---
                    // Después de dar los puntos, necesito refrescar el scoreboard para que el jugador vea el cambio al instante.
                    final Player finalPlayer = p;
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (finalPlayer.isOnline()) {
                                int totalPoints = pointsManager.getTotalPoints(finalPlayer);
                                int topPosition = pointsManager.getPlayerRank(finalPlayer);
                                List<PointsManager.PlayerScore> topPlayers = pointsManager.getTopPlayers(3);
                                DeWaltScoreboardManager.showDefaultPage(finalPlayer, topPosition, totalPoints, false, topPlayers);
                            }
                        }
                    }.runTask(plugin);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void restoreBrokenBlocks() {
        if (brokenBlocks.isEmpty()) return;

        Map<Location, BlockData> blocksToRestore = new HashMap<>(brokenBlocks);
        brokenBlocks.clear();

        new BukkitRunnable() {
            @Override
            public void run() {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        blocksToRestore.forEach((loc, data) -> loc.getBlock().setBlockData(data, false));
                        plugin.getLogger().info("Se han restaurado " + blocksToRestore.size() + " bloques del minijuego del concreto.");
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    private void resetGame() {
        if (lobbyCountdownTask != null) lobbyCountdownTask.cancel();
        if (gameTimerTask != null) gameTimerTask.cancel();

        lobbyPlayers.clear();
        gameScores.clear();
        currentState = GameState.INACTIVE;
    }

    public void handlePlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        if (lobbyPlayers.remove(uuid)) {
            broadcastToLobby(ChatColor.YELLOW + player.getName() + " ha salido del lobby.", null);
        }
        gameScores.remove(uuid);
    }

    private void clearPlayerInventories() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : gameScores.keySet()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.getInventory().clear();
                    }
                }
            }
        }.runTask(plugin);
    }

    // --- 4. MÉTODOS DE UTILIDAD Y NOTIFICACIÓN ---

    public boolean isPlayerInGame(Player player) {
        return gameScores.containsKey(player.getUniqueId());
    }

    private void updateActionBarForAll() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : gameScores.keySet()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        int score = gameScores.getOrDefault(uuid, 0);
                        String message = String.format("§eTiempo restante: §f%ds §8| §eBloques rotos: §f%d", gameTimeLeft, score);
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                    }
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void broadcastToLobby(String message, UUID excludedPlayer) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : lobbyPlayers) {
                    if (!uuid.equals(excludedPlayer)) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) p.sendMessage(message);
                    }
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void broadcastToGame(String message) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : gameScores.keySet()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) p.sendMessage(message);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void playSoundForLobby(Sound sound, float pitch) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : lobbyPlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) p.playSound(p.getLocation(), sound, 1.0f, pitch);
                }
            }
        }.runTaskAsynchronously(plugin);
    }
}