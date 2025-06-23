// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/concrete/ConcreteManager.java
package com.TNTStudios.deWaltCore.minigames.concrete;

import com.TNTStudios.deWaltCore.DeWaltCore;
import com.TNTStudios.deWaltCore.points.PointsManager;
import com.TNTStudios.deWaltCore.scoreboard.DeWaltScoreboardManager;
import io.th0rgal.oraxen.api.OraxenItems;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
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
 * 2.  El scoreboard ahora se actualiza inmediatamente al finalizar el juego.
 * 3.  El contador de jugadores ahora es visible en los mensajes de la cuenta atrás del lobby.
 * 4.  Añadidos comentarios explicativos sobre la lógica de control de jugadores.
 * -- ¡NUEVAS MEJORAS! --
 * 5.  Implementado un minijuego de "timing" para romper bloques, añadiendo dificultad.
 * 6.  Solucionado el bug de doble ruptura con un sistema de cooldown.
 * 7.  Al final del juego, los jugadores son teletransportados a una zona segura para evitar asfixia.
 * 8.  El inventario se limpia y el martillo se retira inmediatamente al acabar la partida.
 */
public class ConcreteManager {

    private final DeWaltCore plugin;
    private final PointsManager pointsManager;

    private enum GameState {INACTIVE, LOBBY, RUNNING}

    private volatile GameState currentState = GameState.INACTIVE;

    private final Set<UUID> lobbyPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> gameScores = new ConcurrentHashMap<>();
    private final Map<Location, BlockData> brokenBlocks = new ConcurrentHashMap<>();

    private BukkitTask lobbyCountdownTask;
    private BukkitTask gameTimerTask;

    private int lobbyTimeLeft;
    private int gameTimeLeft;

    // --- MI NUEVA LÓGICA PARA EL MINIJUEGO DE ROMPER BLOQUES ---
    private final Map<UUID, BlockBreakingAttempt> activeBreakingAttempts = new ConcurrentHashMap<>();
    private final Set<Location> targettedBlocks = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> playerBreakCooldowns = new ConcurrentHashMap<>();

    // --- CONSTANTES DE CONFIGURACIÓN DEL JUEGO ---
    private static final int MIN_PLAYERS = 1;
    private static final int MAX_PLAYERS = 20;
    private static final int LOBBY_DURATION_SECONDS = 60;
    private static final int GAME_DURATION_SECONDS = 120; // 2 minutos
    private static final String HAMMER_ITEM_ID = "martillo_demoledor";

    // --- ZONAS DEL MINIJUEGO ---
    // NOTA: Asumo que el mundo principal se llama 'world'. Esto es un ejemplo.
    private static final Location LOBBY_SPAWN_LOCATION = new Location(Bukkit.getWorld("DeWALTConcreto"), 9.48, 117.00, -3.23, 90, 0);
    private static final Location GAME_SPAWN_LOCATION = new Location(Bukkit.getWorld("DeWALTConcreto"), 9.48, 117.00, -3.23, 90, 0);
    private static final Location SAFE_EXIT_LOCATION = new Location(Bukkit.getWorld("DEWALT LOBBY"), -2.13, 78.00, 0.44, 90, 0);


    // --- CONSTANTES DEL MINIJUEGO DE RUPTURA ---
    private static final int BREAK_MINIGAME_DURATION_TICKS = 30; // 1.5 segundos para reaccionar
    private static final int BREAK_SUCCESS_START_TICK = 12; // Ventana de éxito empieza al 60%
    private static final int BREAK_SUCCESS_END_TICK = 24;   // Ventana de éxito termina al 80%
    private static final long BREAK_COOLDOWN_MS = 200L;      // 200ms de cooldown para evitar doble ruptura

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

        // Lo muevo a una zona segura del lobby
        player.teleport(LOBBY_SPAWN_LOCATION);

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
                    this.cancel(); // Me aseguro de cancelar la tarea aquí
                    return;
                }

                if (lobbyTimeLeft <= 0) {
                    this.cancel();
                    startGame();
                    return;
                }

                if (lobbyTimeLeft % 10 == 0 || lobbyTimeLeft <= 5) {
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
                p.teleport(GAME_SPAWN_LOCATION); // Llevo al jugador a la arena
                p.getInventory().clear();
                p.getInventory().addItem(hammer.clone());
                p.sendTitle(ChatColor.GREEN + "¡A ROMPER!", ChatColor.WHITE + "¡Clic derecho para iniciar, y otra vez en el momento justo!", 10, 60, 20);
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
                    this.cancel();
                    return;
                }
                updateActionBarForAll();
                gameTimeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void handleBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isPlayerInGame(player)) {
            event.setCancelled(true);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "¡Debes usar el clic derecho para romper bloques!"));
        }
    }

    /**
     * MI MÉTODO REFACTORIZADO: Maneja el minijuego de romper bloques.
     * El primer clic inicia el intento, el segundo debe ser en el momento justo.
     */
    public void handleRightClickBreak(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        if (!isPlayerInGame(player) || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        event.setCancelled(true);

        // Si el jugador ya está en un intento de ruptura (segundo clic)
        if (activeBreakingAttempts.containsKey(playerUUID)) {
            activeBreakingAttempts.get(playerUUID).tryResolve();
            return;
        }

        // Si es el primer clic, se inicia un nuevo intento
        long currentTime = System.currentTimeMillis();
        long lastBreak = playerBreakCooldowns.getOrDefault(playerUUID, 0L);
        if (currentTime - lastBreak < BREAK_COOLDOWN_MS) {
            return; // Cooldown para evitar el bug de doble-evento
        }

        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (!HAMMER_ITEM_ID.equals(OraxenItems.getIdByItem(itemInHand))) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "¡Usa el Martillo Demoledor!"));
            return;
        }

        Block block = event.getClickedBlock();
        if (block != null && TARGET_BLOCKS.contains(block.getType())) {
            if (targettedBlocks.contains(block.getLocation())) {
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.YELLOW + "Alguien ya está rompiendo ese bloque."));
                return;
            }
            // Inicio el minijuego de ruptura
            BlockBreakingAttempt attempt = new BlockBreakingAttempt(player, block);
            activeBreakingAttempts.put(playerUUID, attempt);
            targettedBlocks.add(block.getLocation());
            attempt.start();
        }
    }


    // --- 3. FINALIZACIÓN Y LIMPIEZA ---

    private void endGame(String reason) {
        if (currentState != GameState.RUNNING) return;
        currentState = GameState.INACTIVE;
        if (gameTimerTask != null) gameTimerTask.cancel();

        broadcastToGame(ChatColor.GOLD + "¡El juego ha terminado! Razón: " + reason + ". Calculando resultados...");

        // Cancelo todos los minijuegos de ruptura activos
        activeBreakingAttempts.values().forEach(BlockBreakingAttempt::cancel);
        activeBreakingAttempts.clear();
        targettedBlocks.clear();

        List<Map.Entry<UUID, Integer>> sortedPlayers = gameScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .collect(Collectors.toList());

        // MI LÓGICA MEJORADA: Muevo a los jugadores a un lugar seguro INMEDIATAMENTE.
        // Esto evita que mueran asfixiados cuando se restauren los bloques.
        teleportPlayersToSafetyAndClearInventory();

        new BukkitRunnable() {
            @Override
            public void run() {
                awardPoints(sortedPlayers);
                restoreBrokenBlocks();
                resetGame(); // Limpia los mapas y resetea el estado
            }
        }.runTaskLater(plugin, 60L); // 3 segundos de delay para que vean los títulos
    }

    private void teleportPlayersToSafetyAndClearInventory() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : gameScores.keySet()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.teleport(SAFE_EXIT_LOCATION);
                        p.getInventory().clear();
                        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.0f);
                    }
                }
            }
        }.runTask(plugin);
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

                    // Actualizo el scoreboard en el hilo principal
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
                blocksToRestore.forEach((loc, data) -> loc.getBlock().setBlockData(data, false));
                plugin.getLogger().info("Se han restaurado " + blocksToRestore.size() + " bloques del minijuego del concreto.");
            }
        }.runTask(plugin);
    }

    private void resetGame() {
        if (lobbyCountdownTask != null) lobbyCountdownTask.cancel();
        if (gameTimerTask != null) gameTimerTask.cancel();

        lobbyPlayers.clear();
        gameScores.clear();
        // Me aseguro de que no quede ningún intento de ruptura activo
        activeBreakingAttempts.values().forEach(BlockBreakingAttempt::cancel);
        activeBreakingAttempts.clear();
        targettedBlocks.clear();
        playerBreakCooldowns.clear();

        currentState = GameState.INACTIVE;
    }

    public void handlePlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        if (lobbyPlayers.remove(uuid)) {
            broadcastToLobby(ChatColor.YELLOW + player.getName() + " ha salido del lobby.", null);
        }
        // Si el jugador estaba en un intento de ruptura, lo cancelo
        if (activeBreakingAttempts.containsKey(uuid)) {
            activeBreakingAttempts.get(uuid).cancel();
        }
        gameScores.remove(uuid);
    }

    // Ya no necesito 'clearPlayerInventories' porque lo integré en 'teleportPlayersToSafetyAndClearInventory'
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
                    // No muestro la action bar si el jugador está en el minijuego de ruptura
                    if (p != null && p.isOnline() && !activeBreakingAttempts.containsKey(uuid)) {
                        int score = gameScores.getOrDefault(uuid, 0);
                        String message = String.format("§eTiempo restante: §f%ds §8| §eBloques rotos: §f%d", gameTimeLeft, score);
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
                    }
                }
            }
        }.runTask(plugin); // Lo ejecuto síncrono para evitar problemas con la API de Spigot
    }

    private void broadcastToLobby(String message, UUID excludedPlayer) {
        lobbyPlayers.stream()
                .filter(uuid -> !uuid.equals(excludedPlayer))
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(p -> p.sendMessage(message));
    }

    private void broadcastToGame(String message) {
        gameScores.keySet().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(p -> p.sendMessage(message));
    }

    private void playSoundForLobby(Sound sound, float pitch) {
        lobbyPlayers.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(p -> p.playSound(p.getLocation(), sound, 1.0f, pitch));
    }

    // --- 5. MI NUEVA CLASE INTERNA PARA EL MINIJUEGO DE RUPTURA ---
    private class BlockBreakingAttempt {
        private final Player player;
        private final Block block;
        private final UUID playerUUID;
        private BukkitTask task;
        private int progress = 0;

        BlockBreakingAttempt(Player player, Block block) {
            this.player = player;
            this.block = block;
            this.playerUUID = player.getUniqueId();
        }

        void start() {
            this.task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (progress > BREAK_MINIGAME_DURATION_TICKS) {
                        fail("¡Demasiado lento!");
                        return;
                    }
                    displayProgressBar();
                    progress++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        void tryResolve() {
            if (progress >= BREAK_SUCCESS_START_TICK && progress <= BREAK_SUCCESS_END_TICK) {
                succeed();
            } else {
                fail("¡Mal timing!");
            }
        }

        private void succeed() {
            this.cancel(); // Detiene la tarea de progreso

            playerBreakCooldowns.put(playerUUID, System.currentTimeMillis());

            // Guardo el estado original del bloque antes de romperlo
            brokenBlocks.putIfAbsent(block.getLocation(), block.getBlockData());
            block.setType(Material.AIR, true); // Rompo el bloque

            player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.2f);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + "¡Perfecto!"));

            // Actualizo la puntuación
            gameScores.computeIfPresent(playerUUID, (uuid, score) -> score + 1);
        }

        private void fail(String reason) {
            this.cancel();
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + reason));
        }

        void cancel() {
            if (task != null) task.cancel();
            activeBreakingAttempts.remove(playerUUID);
            targettedBlocks.remove(block.getLocation());
        }

        private void displayProgressBar() {
            StringBuilder bar = new StringBuilder("§7[");
            int total_chars = 20;
            double percentage = (double) progress / BREAK_MINIGAME_DURATION_TICKS;
            int filled_chars = (int) (total_chars * percentage);

            // Defino la ventana de éxito para el color
            double success_start_percent = (double) BREAK_SUCCESS_START_TICK / BREAK_MINIGAME_DURATION_TICKS;
            double success_end_percent = (double) BREAK_SUCCESS_END_TICK / BREAK_MINIGAME_DURATION_TICKS;

            for (int i = 0; i < total_chars; i++) {
                double current_pos_percent = (double) i / total_chars;
                if (i <= filled_chars) {
                    if (current_pos_percent >= success_start_percent && current_pos_percent <= success_end_percent) {
                        bar.append("§a|"); // Verde para la zona de éxito
                    } else {
                        bar.append("§c|"); // Rojo fuera de la zona de éxito
                    }
                } else {
                    bar.append("§8-"); // Gris para lo que falta
                }
            }
            bar.append("§7]");
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(bar.toString()));
        }
    }
}