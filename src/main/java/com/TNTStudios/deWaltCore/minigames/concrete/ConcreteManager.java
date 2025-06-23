// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/concrete/ConcreteManager.java
package com.TNTStudios.deWaltCore.minigames.concrete;

import com.TNTStudios.deWaltCore.DeWaltCore;
import com.TNTStudios.deWaltCore.points.PointsManager;
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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mi manager para el minijuego del Concreto.
 * Se encarga de toda la lógica: lobby, cuenta atrás, puntuación y restauración del mapa.
 * -- OPTIMIZACIÓN PARA ALTO RENDIMIENTO --
 * 1.  Uso de ConcurrentHashMap para gestionar jugadores y bloques de forma segura en entornos multihilo.
 * 2.  La restauración de bloques se hace de forma asíncrona para no impactar el hilo principal.
 * 3.  Las notificaciones (sonidos, actionbar) se envían de forma asíncrona.
 * 4.  Las comprobaciones de ítems y bloques son muy rápidas usando Sets.
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
                    broadcastToLobby(ChatColor.YELLOW + "El juego comenzará en " + ChatColor.WHITE + lobbyTimeLeft + " segundos...", null);
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
                p.sendTitle(ChatColor.GREEN + "¡A ROMPER!", ChatColor.WHITE + "¡Destruye Ladrillos, Granito y Terracota!", 10, 60, 20);
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

    public void handleBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (!gameScores.containsKey(playerUUID)) return;

        event.setCancelled(true); // Siempre cancelamos para controlar todo nosotros

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!HAMMER_ITEM_ID.equals(OraxenItems.getIdByItem(itemInHand))) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "¡Usa el Martillo Demoledor!"));
            return;
        }

        Block block = event.getBlock();
        if (TARGET_BLOCKS.contains(block.getType())) {
            // Guardamos el estado original del bloque antes de romperlo
            brokenBlocks.putIfAbsent(block.getLocation(), block.getBlockData());

            // Rompemos el bloque
            block.setType(Material.AIR);
            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);

            // Actualizamos la puntuación
            gameScores.computeIfPresent(playerUUID, (uuid, score) -> score + 1);
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

        // Damos un pequeño delay para que vean el mensaje de fin antes de los resultados
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
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void restoreBrokenBlocks() {
        if (brokenBlocks.isEmpty()) return;

        // Copiamos el mapa para trabajar de forma segura en un hilo asíncrono
        Map<Location, BlockData> blocksToRestore = new HashMap<>(brokenBlocks);
        brokenBlocks.clear();

        new BukkitRunnable() {
            @Override
            public void run() {
                // La restauración se hace en el hilo principal para evitar problemas con la API de Bukkit
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
        // brokenBlocks ya se limpió en restoreBrokenBlocks

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