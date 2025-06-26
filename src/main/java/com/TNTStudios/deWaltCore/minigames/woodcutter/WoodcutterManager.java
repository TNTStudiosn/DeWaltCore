// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/woodcutter/WoodcutterManager.java
package com.TNTStudios.deWaltCore.minigames.woodcutter;

import com.TNTStudios.deWaltCore.DeWaltCore;
import com.TNTStudios.deWaltCore.points.PointsManager;
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.mechanics.provided.gameplay.furniture.FurnitureMechanic;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mi manager para el minijuego Cortadora de Madera.
 * Se encarga de toda la lógica: lobby, etapas, minijuegos, puntuación y reinicio.
 * Está optimizado para alto rendimiento, minimizando la creación de tareas y usando
 * estructuras de datos concurrentes para manejar a más de 200 jugadores sin problemas.
 */
public class WoodcutterManager {

    private final DeWaltCore plugin;
    private final PointsManager pointsManager;

    // Defino los estados del juego y de cada jugador para un control claro.
    private enum GameState { INACTIVE, LOBBY, RUNNING }
    private enum PlayerStage { COLLECTING_LOGS, CUTTING_PLANKS, ASSEMBLING_TABLE }

    private volatile GameState currentState = GameState.INACTIVE;

    // Estructuras de datos seguras para un entorno multijugador masivo.
    private final Set<UUID> lobbyPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerData> gamePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> playerBossBars = new ConcurrentHashMap<>();

    // Tareas principales del juego
    private BukkitTask lobbyCountdownTask;
    private BukkitTask gameTimerTask;

    private int lobbyTimeLeft;
    private int gameTimeLeft;

    // --- CONSTANTES DE CONFIGURACIÓN DEL MINIJUEGO ---
    private static final int MIN_PLAYERS = 1;
    private static final int MAX_PLAYERS = 20;
    private static final int LOBBY_DURATION_SECONDS = 60;
    private static final int GAME_DURATION_SECONDS = 300; // 5 minutos de partida

    // --- IDs DE ITEMS Y BLOQUES ---
    // BLUEAI: He eliminado la constante AXE_ITEM_ID y la he reemplazado por un Material de Minecraft.
    private static final Material WOODCUTTER_AXE_MATERIAL = Material.IRON_AXE;
    private static final String CUTTER_ITEM_ID = "cortadora_de_madera";
    private static final String HAMMER_ITEM_ID = "martillo";
    private static final String CUTTER_TABLE_ID = "mesa_cortadora";
    private static final String ASSEMBLY_TABLE_ID = "mesa_vacia";

    // --- ZONAS DEL MINIJUEGO ---
    private static final Location LOBBY_SPAWN_LOCATION = new Location(Bukkit.getWorld("DeWALTCortaMadera"), 29.34, 1.00, 34, 90, 0);
    private static final Location SAFE_EXIT_LOCATION = new Location(Bukkit.getWorld("DEWALT LOBBY"), -2.13, 78.00, 0.44, 90, 0);

    // --- LÓGICA DE MINIJUEGOS INTERNOS ---
    // Uso un mapa para gestionar cualquier minijuego activo de un jugador.
    private final Map<UUID, Object> activeMinigames = new ConcurrentHashMap<>();

    public WoodcutterManager(DeWaltCore plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
    }

    // --- 1. LÓGICA DE LOBBY Y ACCESO ---

    public void addPlayerToLobby(Player player) {
        if (currentState == GameState.RUNNING) {
            player.sendMessage(ChatColor.RED + "¡La Cortadora de Madera ya está en marcha! Espera a que termine la ronda.");
            return;
        }
        if (lobbyPlayers.size() >= MAX_PLAYERS) {
            player.sendMessage(ChatColor.RED + "¡El lobby está lleno! (" + MAX_PLAYERS + "/" + MAX_PLAYERS + ")");
            return;
        }
        if (!lobbyPlayers.add(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Ya estás en la sala de espera.");
            return;
        }

        player.teleport(LOBBY_SPAWN_LOCATION);
        player.sendTitle(ChatColor.GOLD + "Cortadora de Madera", ChatColor.YELLOW + "¡Prepárate para la acción!", 10, 70, 20);
        player.getInventory().clear();
        broadcastToLobby(ChatColor.AQUA + player.getName() + " se ha unido. (" + lobbyPlayers.size() + "/" + MAX_PLAYERS + ")");

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
                if (lobbyPlayers.isEmpty()) {
                    broadcastToLobby(ChatColor.RED + "No hay suficientes jugadores. Se canceló el inicio.");
                    resetGame();
                    return;
                }
                if (lobbyTimeLeft <= 0) {
                    this.cancel();
                    startGame();
                    return;
                }
                if (lobbyTimeLeft % 10 == 0 || lobbyTimeLeft <= 5) {
                    String message = String.format("%sEl juego comienza en %s%d segundos...", ChatColor.YELLOW, ChatColor.WHITE, lobbyTimeLeft);
                    broadcastToLobby(message);
                    playSoundForLobby(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f);
                }
                // Muestro las instrucciones en la action bar durante la espera
                getLobbyPlayers().forEach(p -> p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GOLD + "¡Construye más mesas que nadie para ganar!")));
                lobbyTimeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // --- 2. LÓGICA PRINCIPAL DEL JUEGO ---

    private void startGame() {
        currentState = GameState.RUNNING;
        gameTimeLeft = GAME_DURATION_SECONDS;

        if (!validateOraxenItems()) {
            broadcastToLobby(ChatColor.RED + "Error crítico: Faltan items de Oraxen. El juego no puede empezar. Avisa a un administrador.");
            resetGame();
            return;
        }

        for (UUID uuid : lobbyPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                gamePlayers.put(uuid, new PlayerData());
                setupPlayerForGame(p);
            }
        }
        lobbyPlayers.clear();

        broadcastToGame(ChatColor.GOLD + "¡El juego ha comenzado! ¡Ve a por troncos de abeto!");
        startGameTimer();
    }

    private void setupPlayerForGame(Player player) {
        player.teleport(LOBBY_SPAWN_LOCATION); // Se quedan en el mismo mundo
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);

        // Crear y mostrar la BossBar
        BossBar bossBar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID);
        bossBar.addPlayer(player);
        playerBossBars.put(player.getUniqueId(), bossBar);

        // Empezar la primera etapa
        advancePlayerStage(player);
    }

    private void startGameTimer() {
        gameTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (gamePlayers.isEmpty() || gameTimeLeft <= 0) {
                    endGame("Se acabó el tiempo");
                    return;
                }
                updateBossBars();
                gameTimeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // --- 3. GESTIÓN DE ETAPAS Y PROGRESO ---

    private void advancePlayerStage(Player player) {
        if (!isPlayerInGame(player)) return;

        PlayerData data = gamePlayers.get(player.getUniqueId());
        player.getInventory().clear(); // Limpio el inventario en cada transición de etapa.

        switch(data.currentStage) {
            case COLLECTING_LOGS:
                data.currentStage = PlayerStage.CUTTING_PLANKS;
                player.getInventory().addItem(OraxenItems.getItemById(CUTTER_ITEM_ID).build());
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + "¡Troncos conseguidos! Ahora ve a la Mesa Cortadora."));
                break;
            case CUTTING_PLANKS:
                data.currentStage = PlayerStage.ASSEMBLING_TABLE;
                player.getInventory().addItem(OraxenItems.getItemById(HAMMER_ITEM_ID).build());
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.AQUA + "¡Tablones listos! Ve a la Mesa de Armado."));
                break;
            case ASSEMBLING_TABLE:
                // El jugador completó una mesa, se le da un punto y vuelve a empezar
                data.score++;
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                player.sendTitle(" ", ChatColor.GOLD + "+1 Mesa Construida", 5, 40, 10);

                // Reinicia el ciclo
                data.currentStage = PlayerStage.COLLECTING_LOGS;
                data.stageProgress = 0;
                // BLUEAI: Doy al jugador el hacha normal en lugar de la de Oraxen.
                player.getInventory().addItem(createWoodcutterAxe());
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.YELLOW + "¡Excelente! ¡A por la siguiente mesa!"));
                break;
        }
        updateBossBars();
    }

    // --- 4. MANEJADORES DE EVENTOS (LLAMADOS DESDE EL LISTENER) ---

    public void handlePlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isPlayerInGame(player) || !event.getAction().name().contains("RIGHT_CLICK")) return;

        PlayerData data = gamePlayers.get(player.getUniqueId());
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // BLUEAI: Modifiqué esta sección. Ahora comprueba el material del hacha en lugar de su ID de Oraxen.
        // La comprobación de Oraxen se mantiene para los otros items.
        if (data.currentStage == PlayerStage.COLLECTING_LOGS && itemInHand.getType() == WOODCUTTER_AXE_MATERIAL) {
            if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.SPRUCE_LOG) {
                event.setCancelled(true);
                startAxeMinigame(player);
            }
            return; // Salgo para evitar la comprobación de Oraxen innecesaria.
        }

        if (OraxenItems.getIdByItem(itemInHand) == null) return;
    }

    public void handleFurnitureInteract(Player player, FurnitureMechanic mechanic) {
        if (!isPlayerInGame(player)) return;

        PlayerData data = gamePlayers.get(player.getUniqueId());
        String furnitureId = mechanic.getItemID();

        // Etapa 2: Cortar tablones
        if (data.currentStage == PlayerStage.CUTTING_PLANKS && CUTTER_TABLE_ID.equals(furnitureId)) {
            startCutterMinigame(player);
        }
        // Etapa 3: Ensamblar mesa
        else if (data.currentStage == PlayerStage.ASSEMBLING_TABLE && ASSEMBLY_TABLE_ID.equals(furnitureId)) {
            startHammerMinigame(player);
        }
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!isPlayerInGame(player) || !activeMinigames.containsKey(player.getUniqueId())) return;

        event.setCancelled(true);
        Object minigame = activeMinigames.get(player.getUniqueId());

        if (minigame instanceof CutterMinigame cutter) {
            cutter.handleClick(event.getSlot());
        } else if (minigame instanceof HammerMinigame hammer) {
            hammer.handleClick(event.getSlot());
        }
    }

    // --- 5. LÓGICA DE LOS MINIJUEGOS INTERNOS ---

    private void startAxeMinigame(Player player) {
        if (activeMinigames.containsKey(player.getUniqueId())) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "¡Ya estás en un minijuego!"));
            return;
        }
        AxeMinigame minigame = new AxeMinigame(player);
        activeMinigames.put(player.getUniqueId(), minigame);
        minigame.start();
    }

    private void startCutterMinigame(Player player) {
        if (activeMinigames.containsKey(player.getUniqueId())) return;
        CutterMinigame minigame = new CutterMinigame(player);
        activeMinigames.put(player.getUniqueId(), minigame);
        minigame.start();
    }

    private void startHammerMinigame(Player player) {
        if (activeMinigames.containsKey(player.getUniqueId())) return;
        HammerMinigame minigame = new HammerMinigame(player);
        activeMinigames.put(player.getUniqueId(), minigame);
        minigame.start();
    }

    private void completeMinigame(Player player, boolean success) {
        activeMinigames.remove(player.getUniqueId());
        if (!isPlayerInGame(player)) return;

        PlayerData data = gamePlayers.get(player.getUniqueId());

        if (success) {
            data.stageProgress++;
            // Compruebo si se completó la etapa
            boolean stageComplete = false;
            switch(data.currentStage) {
                case COLLECTING_LOGS:
                    if (data.stageProgress >= 5) stageComplete = true;
                    break;
                case CUTTING_PLANKS:
                    if (data.stageProgress >= 5) stageComplete = true; // 5 rondas completadas
                    break;
                case ASSEMBLING_TABLE:
                    if (data.stageProgress >= 3) stageComplete = true;
                    break;
            }

            if(stageComplete) {
                advancePlayerStage(player);
            } else {
                updateBossBars();
            }
        } else {
            // Si falla, no avanza, puede intentarlo de nuevo.
            player.sendTitle(" ", ChatColor.RED + "¡Intento fallido!", 5, 40, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.0f);
        }
    }


    // --- 6. FINALIZACIÓN Y LIMPIEZA ---

    private void endGame(String reason) {
        if (currentState != GameState.RUNNING) return;
        currentState = GameState.INACTIVE;

        if (gameTimerTask != null) gameTimerTask.cancel();

        // Cancelo cualquier minijuego activo
        activeMinigames.values().forEach(minigame -> {
            if (minigame instanceof AxeMinigame axe) axe.cancel();
            if (minigame instanceof CutterMinigame cutter) cutter.cancel();
            if (minigame instanceof HammerMinigame hammer) hammer.cancel();
        });
        activeMinigames.clear();

        broadcastToGame(ChatColor.GOLD + "¡El juego ha terminado! Razón: " + reason);

        // Uso una copia para evitar problemas de concurrencia al iterar
        Map<UUID, PlayerData> finalScores = new HashMap<>(gamePlayers);

        // Teletransporto a todos a una zona segura primero
        teleportAllToSafety(finalScores.keySet());

        // Después de un delay, muestro los resultados y doy los puntos
        new BukkitRunnable() {
            @Override
            public void run() {
                awardPointsAndShowResults(finalScores);
                resetGame();
            }
        }.runTaskLater(plugin, 100L); // 5 segundos de espera
    }

    private void awardPointsAndShowResults(Map<UUID, PlayerData> finalScores) {
        List<Map.Entry<UUID, PlayerData>> sortedPlayers = finalScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, PlayerData>comparingByValue(Comparator.comparingInt(d -> d.score)).reversed())
                .collect(Collectors.toList());

        // Construyo el mensaje del top 3
        StringBuilder resultsMessage = new StringBuilder();
        resultsMessage.append(ChatColor.GOLD).append("--- Resultados de Cortadora de Madera ---\n");
        for (int i = 0; i < sortedPlayers.size() && i < 3; i++) {
            Map.Entry<UUID, PlayerData> entry = sortedPlayers.get(i);
            Player p = Bukkit.getPlayer(entry.getKey());
            String name = (p != null) ? p.getName() : "Jugador Desconectado";
            resultsMessage.append(String.format("%s#%d %s - %d mesas\n",
                    (i == 0 ? ChatColor.GOLD : i == 1 ? ChatColor.GRAY : ChatColor.DARK_RED),
                    i + 1, name, entry.getValue().score));
        }

        String finalResults = resultsMessage.toString();

        // Doy puntos y envío el mensaje a cada jugador que participó
        for (int i = 0; i < sortedPlayers.size(); i++) {
            Map.Entry<UUID, PlayerData> entry = sortedPlayers.get(i);
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) continue;

            int pointsWon;
            if (i == 0) pointsWon = 15;
            else if (i == 1) pointsWon = 7;
            else if (i == 2) pointsWon = 3;
            else pointsWon = 1;

            pointsManager.addPoints(p, pointsWon, "woodcutter_minigame", "Ranking final");

            p.sendMessage(finalResults);
            p.sendMessage(String.format(ChatColor.GREEN + "Tu posición: #%d. Has ganado %d puntos.", i + 1, pointsWon));
        }
    }

    private void teleportAllToSafety(Set<UUID> playerUuids) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for(UUID uuid : playerUuids) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.teleport(SAFE_EXIT_LOCATION);
                        p.getInventory().clear();
                        p.setGameMode(GameMode.SURVIVAL); // O el modo de juego por defecto
                        // Limpio la boss bar
                        BossBar bossBar = playerBossBars.remove(uuid);
                        if (bossBar != null) {
                            bossBar.removeAll();
                        }
                    }
                }
            }
        }.runTask(plugin);
    }

    private void resetGame() {
        if (lobbyCountdownTask != null) lobbyCountdownTask.cancel();
        if (gameTimerTask != null) gameTimerTask.cancel();

        getLobbyPlayers().forEach(p -> p.teleport(SAFE_EXIT_LOCATION));

        lobbyPlayers.clear();
        gamePlayers.clear();

        playerBossBars.values().forEach(BossBar::removeAll);
        playerBossBars.clear();

        currentState = GameState.INACTIVE;
    }

    public void handlePlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        lobbyPlayers.remove(uuid);

        if (gamePlayers.containsKey(uuid)) {
            gamePlayers.remove(uuid);
            BossBar bossBar = playerBossBars.remove(uuid);
            if (bossBar != null) {
                bossBar.removeAll();
            }
            activeMinigames.remove(uuid);
            broadcastToGame(ChatColor.YELLOW + player.getName() + " ha abandonado la partida.");
        }
    }


    // --- 7. MÉTODOS DE UTILIDAD ---

    /**
     * BLUEAI: He creado este método para generar el hacha del leñador.
     * Esto centraliza su creación y permite añadirle propiedades especiales,
     * como un nombre custom o hacerla irrompible, para que funcione bien en el minijuego.
     * @return Un ItemStack que representa el hacha del minijuego.
     */
    private ItemStack createWoodcutterAxe() {
        ItemStack axe = new ItemStack(WOODCUTTER_AXE_MATERIAL);
        ItemMeta meta = axe.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Hacha de Leñador");
            meta.setLore(Collections.singletonList(ChatColor.GRAY + "Úsala para talar troncos de abeto."));
            meta.setUnbreakable(true); // El hacha no se debe romper durante el minijuego.
            axe.setItemMeta(meta);
        }
        return axe;
    }

    public boolean isPlayerInGame(Player player) {
        return gamePlayers.containsKey(player.getUniqueId());
    }

    public boolean isPlayerInLobby(Player player) {
        return lobbyPlayers.contains(player.getUniqueId());
    }

    private void updateBossBars() {
        for (Map.Entry<UUID, PlayerData> entry : gamePlayers.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;

            BossBar bb = playerBossBars.get(entry.getKey());
            PlayerData data = entry.getValue();
            if (bb == null) continue;

            String stageText = "";
            String progressText = "";
            int maxProgress = 0;

            switch(data.currentStage) {
                case COLLECTING_LOGS:
                    stageText = "TALA DE TRONCOS";
                    maxProgress = 5;
                    progressText = "Troncos";
                    break;
                case CUTTING_PLANKS:
                    stageText = "CORTE DE TABLONES";
                    maxProgress = 5;
                    progressText = "Rondas";
                    break;
                case ASSEMBLING_TABLE:
                    stageText = "ENSAMBLAJE";
                    maxProgress = 3;
                    progressText = "Rondas";
                    break;
            }

            String title = String.format("§e§l%s §f| §a%s: %d/%d §f| §cMesas: %d §f| §bTiempo: %02d:%02d",
                    stageText, progressText, data.stageProgress, maxProgress, data.score, gameTimeLeft / 60, gameTimeLeft % 60);

            bb.setTitle(title);
            bb.setProgress(Math.max(0.0, Math.min(1.0, (double) gameTimeLeft / GAME_DURATION_SECONDS)));
        }
    }

    private boolean validateOraxenItems() {
        // BLUEAI: He eliminado la validación del hacha de Oraxen de este método.
        return OraxenItems.getItemById(CUTTER_ITEM_ID) != null &&
                OraxenItems.getItemById(HAMMER_ITEM_ID) != null;
    }

    private void broadcastToLobby(String message) {
        getLobbyPlayers().forEach(p -> p.sendMessage(message));
    }

    private List<Player> getLobbyPlayers() {
        return lobbyPlayers.stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void playSoundForLobby(Sound sound, float pitch) {
        getLobbyPlayers().forEach(p -> p.playSound(p.getLocation(), sound, 1.0f, pitch));
    }

    private void broadcastToGame(String message) {
        gamePlayers.keySet().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(p -> p.sendMessage(message));
    }

    // --- CLASES INTERNAS PARA DATOS Y MINIJUEGOS ---

    /**
     * Almacena los datos de un jugador durante la partida.
     */
    private static class PlayerData {
        int score = 0;
        PlayerStage currentStage = PlayerStage.COLLECTING_LOGS;
        int stageProgress = 0;
    }

    /**
     * Minijuego de timing para el hacha, basado en la implementación de ConcreteManager.
     */
    private class AxeMinigame {
        private final Player player;
        private final UUID playerUUID;
        private BukkitTask task;
        private int progress = 0;
        private boolean resolved = false;

        private static final int DURATION_TICKS = 40;
        private static final int SUCCESS_START_TICK = 25;
        private static final int SUCCESS_END_TICK = 35;

        AxeMinigame(Player player) {
            this.player = player;
            this.playerUUID = player.getUniqueId();
        }

        void start() {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.5f);
            this.task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (progress > DURATION_TICKS) {
                        fail();
                        return;
                    }
                    displayProgressBar();
                    progress++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        public void resolveAttempt() {
            if (resolved) return;
            if (progress >= SUCCESS_START_TICK && progress <= SUCCESS_END_TICK) {
                succeed();
            } else {
                fail();
            }
        }

        private void succeed() {
            if(resolved) return;
            resolved = true;
            task.cancel();
            player.playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 1.0f, 1.2f);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + "¡Corte perfecto!"));
            completeMinigame(player, true);
        }

        private void fail() {
            if(resolved) return;
            resolved = true;
            task.cancel();
            player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.0f);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "¡Fallaste!"));
            completeMinigame(player, false);
        }

        public void cancel() {
            if(task != null) task.cancel();
        }

        private void displayProgressBar() {
            if (!player.isOnline()) { fail(); return; }

            StringBuilder bar = new StringBuilder("§c[");
            int total_chars = 30;
            double percentage = (double) progress / DURATION_TICKS;
            int green_start = (int) (total_chars * ((double) SUCCESS_START_TICK / DURATION_TICKS));
            int green_end = (int) (total_chars * ((double) SUCCESS_END_TICK / DURATION_TICKS));

            for (int i = 0; i < total_chars; i++) {
                if(i > green_start && i < green_end) bar.append("§a=");
                else bar.append("§c=");
            }
            bar.append("§c]");

            int markerPos = (int) (total_chars * percentage);
            if (markerPos < bar.length() - 2) {
                bar.setCharAt(markerPos + 3, '█'); // +3 para compensar el color inicial y el corchete
            }

            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(bar.toString()));
        }
    }

    /**
     * Minijuego de secuencia de clics para la cortadora.
     */
    private class CutterMinigame {
        private final Player player;
        private BukkitTask mainTask;
        CutterMinigame(Player player) { this.player = player; }

        void start() {
            player.openInventory(Bukkit.createInventory(null, 9, "Procesando..."));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f);
            mainTask = new BukkitRunnable() {
                int currentRound = 0;
                @Override
                public void run() {
                    if(!player.isOnline() || !isPlayerInGame(player)) {
                        this.cancel();
                        activeMinigames.remove(player.getUniqueId());
                        return;
                    }

                    PlayerData data = gamePlayers.get(player.getUniqueId());
                    if (data.stageProgress >= 5) { // Objetivo de 5 rondas completadas
                        player.closeInventory();
                        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.5f);
                        completeMinigame(player, true);
                        this.cancel();
                    } else {
                        // Simulo el éxito de una ronda
                        data.stageProgress++;
                        updateBossBars();
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f + (data.stageProgress * 0.1f));
                    }
                }
            }.runTaskTimer(plugin, 20L, 20L); // Simula una ronda completada por segundo
        }

        void cancel() {
            if(mainTask != null) mainTask.cancel();
            player.closeInventory();
        }
        void handleClick(int slot) { /* Lógica de click */ }
    }

    /**
     * Minijuego de crafteo/ensamblaje para el martillo.
     */
    private class HammerMinigame {
        private final Player player;
        private BukkitTask mainTask;
        HammerMinigame(Player player) { this.player = player; }

        void start() {
            player.openInventory(Bukkit.createInventory(null, 9, "Ensamblando..."));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
            mainTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if(!player.isOnline() || !isPlayerInGame(player)) {
                        this.cancel();
                        activeMinigames.remove(player.getUniqueId());
                        return;
                    }
                    PlayerData data = gamePlayers.get(player.getUniqueId());
                    if (data.stageProgress >= 3) { // Objetivo de 3 rondas
                        player.closeInventory();
                        completeMinigame(player, true);
                        this.cancel();
                    } else {
                        data.stageProgress++;
                        updateBossBars();
                        player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.2f + (data.stageProgress * 0.1f));
                    }
                }
            }.runTaskTimer(plugin, 20L, 20L);
        }
        void cancel() {
            if (mainTask != null) mainTask.cancel();
            player.closeInventory();
        }
        void handleClick(int slot) { /* Lógica de click */ }
    }
}