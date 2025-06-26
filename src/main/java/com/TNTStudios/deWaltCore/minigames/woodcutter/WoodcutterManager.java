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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mi manager para el minijuego Cortadora de Madera
 * Se encarga de toda la lógica: lobby, etapas, minijuegos interactivos, puntuación y reinicio.
 * Esta versión está completamente reparada y mejorada, con minijuegos reales y una lógica de
 * progresión clara, optimizada para un alto rendimiento en servidores con muchos jugadores.
 */
public class WoodcutterManager {

    private final DeWaltCore plugin;
    private final PointsManager pointsManager;

    // Defino los estados del juego y de cada jugador para un control claro.
    private enum GameState {INACTIVE, LOBBY, RUNNING}
    private enum PlayerStage {COLLECTING_LOGS, CUTTING_PLANKS, ASSEMBLING_TABLE}

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

    // Uso un mapa para gestionar cualquier minijuego activo de un jugador.
    private final Map<UUID, Minigame> activeMinigames = new ConcurrentHashMap<>();
    private final Map<UUID, Long> axeCooldowns = new ConcurrentHashMap<>();

    // --- CONSTANTES DE CONFIGURACIÓN DEL MINIJUEGO ---
    private static final int MIN_PLAYERS = 1;
    private static final int MAX_PLAYERS = 20;
    private static final int LOBBY_DURATION_SECONDS = 60;
    private static final int GAME_DURATION_SECONDS = 300; // 5 minutos
    private static final long AXE_MINIGAME_COOLDOWN_MS = 200L; // Cooldown de 0.2 segundos

    // --- IDs DE ITEMS Y BLOQUES (extraídos de tus instrucciones) ---
    private static final Material WOODCUTTER_AXE_MATERIAL = Material.IRON_AXE;
    private static final String CUTTER_ITEM_ID = "cortadora_de_madera";
    private static final String HAMMER_ITEM_ID = "martillo";
    private static final String CUTTER_TABLE_ID = "mesa_cortadora";
    private static final String ASSEMBLY_TABLE_ID = "mesa_vacia";

    // --- ZONAS DEL MINIJUEGO ---
    // Nota: Asegúrate de que estos mundos estén cargados.
    private static final Location LOBBY_SPAWN_LOCATION = new Location(Bukkit.getWorld("DeWALTCortaMadera"), 29.34, 1.00, 34, 90, 0);
    private static final Location SAFE_EXIT_LOCATION = new Location(Bukkit.getWorld("DEWALT LOBBY"), -2.13, 78.00, 0.44, 90, 0);

    // --- ITEMS DE GUI PARA MINIJUEGOS ---
    private static final ItemStack GUI_FILLER = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ");
    private static final ItemStack NAIL_ITEM = createGuiItem(Material.BROWN_STAINED_GLASS_PANE, "§e¡Clávalo!", "§7¡Haz clic rápido!");
    private static final ItemStack HIT_NAIL_ITEM = createGuiItem(Material.RED_STAINED_GLASS_PANE, "§c¡Clavado!");

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
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.sendTitle(ChatColor.GOLD + "Cortadora de Madera", ChatColor.YELLOW + "¡Prepárate para la acción!", 10, 70, 20);
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
                if (lobbyPlayers.isEmpty() || lobbyPlayers.size() < MIN_PLAYERS) {
                    broadcastToLobby(ChatColor.RED + "No hay suficientes jugadores. Se canceló el inicio.");
                    resetGame();
                    return;
                }
                if (lobbyTimeLeft <= 0) {
                    startGame();
                    this.cancel();
                    return;
                }

                // Mi sistema de instrucciones en el action bar durante el lobby.
                String instructions = getLobbyInstructions(lobbyTimeLeft);
                getLobbyPlayers().forEach(p -> p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(instructions)));

                if (lobbyTimeLeft % 10 == 0 || lobbyTimeLeft <= 5) {
                    String message = String.format("%sEl juego comienza en %s%d segundos...", ChatColor.YELLOW, ChatColor.WHITE, lobbyTimeLeft);
                    broadcastToLobby(message);
                    playSoundForLobby(Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f);
                }
                lobbyTimeLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private String getLobbyInstructions(int time) {
        int stage = (time / 5) % 4; // Añado una etapa más para el objetivo
        return switch (stage) {
            case 0 -> "§e§lOBJETIVO: §f¡Construye más mesas que nadie para ganar!";
            case 1 -> "§6§lETAPA 1: §fTala 5 troncos superando el minijuego del hacha.";
            case 2 -> "§6§lETAPA 2: §fUsa la mesa cortadora y supera el juego de secuencias.";
            case 3 -> "§6§lETAPA 3: §fUsa la mesa de armado y clava los clavos a tiempo.";
            default -> "§e§l¡Prepárate para la acción!";
        };
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
        player.teleport(LOBBY_SPAWN_LOCATION);
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);

        BossBar bossBar = Bukkit.createBossBar("", BarColor.YELLOW, BarStyle.SOLID);
        bossBar.addPlayer(player);
        playerBossBars.put(player.getUniqueId(), bossBar);

        // Empiezo dándole el hacha para la primera etapa
        player.getInventory().addItem(createWoodcutterAxe());
        updateBossBars();
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
        player.getInventory().clear();
        data.stageProgress = 0; // Reseteo el progreso para la nueva etapa

        switch (data.currentStage) {
            case COLLECTING_LOGS -> {
                data.currentStage = PlayerStage.CUTTING_PLANKS;
                player.getInventory().addItem(OraxenItems.getItemById(CUTTER_ITEM_ID).build());
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§a¡Troncos listos! Ahora ve a la §eMesa Cortadora."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 1f, 1.2f);
            }
            case CUTTING_PLANKS -> {
                data.currentStage = PlayerStage.ASSEMBLING_TABLE;
                player.getInventory().addItem(OraxenItems.getItemById(HAMMER_ITEM_ID).build());
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§b¡Tablones listos! Ahora ve a la §eMesa de Armado."));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_FLETCHER, 1f, 1.2f);
            }
            case ASSEMBLING_TABLE -> {
                data.score++;
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                player.sendTitle(" ", ChatColor.GOLD + "+1 Mesa Construida", 5, 40, 10);

                // Reinicia el ciclo completo para construir otra mesa
                data.currentStage = PlayerStage.COLLECTING_LOGS;
                player.getInventory().addItem(createWoodcutterAxe());
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.YELLOW + "¡Excelente! ¡A por la siguiente mesa!"));
            }
        }
        updateBossBars();
    }

    // --- 4. MANEJADORES DE EVENTOS (LLAMADOS DESDE EL LISTENER) ---

    public void handlePlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // Si el jugador no está en el juego, no hago nada.
        if (!gamePlayers.containsKey(playerUUID)) return;

        // **LA CLAVE DE LA REPARACIÓN ESTÁ AQUÍ**
        // Primero, verifico si el jugador ya está en un minijuego. Si es así, le cedo el control total del evento.
        if (activeMinigames.containsKey(playerUUID)) {
            activeMinigames.get(playerUUID).onPlayerInteract(event);
            return; // Detengo la ejecución aquí para no procesar el evento dos veces.
        }

        // Si no está en un minijuego, verifico si la interacción actual debe iniciar uno.
        // Solo me interesan los clics derechos.
        if (!event.getAction().name().contains("RIGHT_CLICK")) return;

        PlayerData data = gamePlayers.get(playerUUID);
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        long now = System.currentTimeMillis();
        if (now - axeCooldowns.getOrDefault(player.getUniqueId(), 0L) < AXE_MINIGAME_COOLDOWN_MS) {
            return; // Si el cooldown está activo, no hacemos nada.
        }

        if (data.currentStage == PlayerStage.COLLECTING_LOGS &&
                itemInHand.getType() == WOODCUTTER_AXE_MATERIAL &&
                event.getClickedBlock() != null &&
                event.getClickedBlock().getType() == Material.SPRUCE_LOG) {

            event.setCancelled(true); // Evito que el hacha interactúe normalmente con el bloque.
            startAxeMinigame(player);
        }
    }

    public void handleFurnitureInteract(Player player, FurnitureMechanic mechanic) {
        if (!isPlayerInGame(player) || activeMinigames.containsKey(player.getUniqueId())) return;

        PlayerData data = gamePlayers.get(player.getUniqueId());
        String furnitureId = mechanic.getItemID();

        if (data.currentStage == PlayerStage.CUTTING_PLANKS && CUTTER_TABLE_ID.equals(furnitureId)) {
            startCutterMinigame(player);
        } else if (data.currentStage == PlayerStage.ASSEMBLING_TABLE && ASSEMBLY_TABLE_ID.equals(furnitureId)) {
            startHammerMinigame(player);
        }
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID playerUUID = player.getUniqueId();

        // Cancelo toda interacción con el inventario si está en el juego pero no en un minijuego con GUI.
        if (isPlayerInGame(player) && !activeMinigames.containsKey(playerUUID)) {
            event.setCancelled(true);
            return;
        }

        // Si está en un minijuego activo, le delego el evento.
        if (activeMinigames.containsKey(playerUUID)) {
            event.setCancelled(true);
            activeMinigames.get(playerUUID).onInventoryClick(event);
        }
    }

    // --- 5. LÓGICA DE LOS MINIJUEGOS INTERNOS ---

    private void startAxeMinigame(Player player) {
        if (activeMinigames.containsKey(player.getUniqueId())) return;
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

    private void completeMinigame(Player player, boolean success, PlayerStage minigameStage) {
        axeCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        activeMinigames.remove(player.getUniqueId());
        if (!isPlayerInGame(player)) return;

        PlayerData data = gamePlayers.get(player.getUniqueId());
        if (data.currentStage != minigameStage) return; // Evito procesar resultados de etapas pasadas

        if (success) {
            boolean stageComplete = false;
            switch (data.currentStage) {
                case COLLECTING_LOGS:
                    data.stageProgress++;
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§a¡Tronco conseguido! (" + data.stageProgress + "/5)"));
                    player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 1.5f);
                    if (data.stageProgress >= 5) stageComplete = true;
                    break;
                case CUTTING_PLANKS:
                case ASSEMBLING_TABLE:
                    // Para estos minijuegos, ganar significa completar la etapa entera.
                    stageComplete = true;
                    break;
            }
            if (stageComplete) {
                advancePlayerStage(player);
            } else {
                updateBossBars();
            }
        } else {
            player.sendTitle(" ", ChatColor.RED + "¡Intento fallido!", 5, 40, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.0f);
        }
    }

    // --- 6. FINALIZACIÓN Y LIMPIEZA ---

    private void endGame(String reason) {
        if (currentState != GameState.RUNNING) return;

        broadcastToGame(ChatColor.GOLD + "¡El juego ha terminado! Razón: " + reason);

        // Cancelo todos los minijuegos activos y cierro inventarios.
        new BukkitRunnable() {
            @Override
            public void run() {
                activeMinigames.values().forEach(Minigame::cancel);
                activeMinigames.clear();
            }
        }.runTask(plugin);

        if (gameTimerTask != null) gameTimerTask.cancel();
        if (lobbyCountdownTask != null) lobbyCountdownTask.cancel();

        Map<UUID, PlayerData> finalScores = new HashMap<>(gamePlayers);

        teleportAllToSafety(finalScores.keySet());

        // Muestro resultados y reinicio el juego tras un delay.
        new BukkitRunnable() {
            @Override
            public void run() {
                awardPointsAndShowResults(finalScores);
                resetGame();
            }
        }.runTaskLater(plugin, 100L); // 5 segundos de espera para que lean los mensajes
    }

    private void awardPointsAndShowResults(Map<UUID, PlayerData> finalScores) {
        if (finalScores.isEmpty()) return;

        List<Map.Entry<UUID, PlayerData>> sortedPlayers = finalScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, PlayerData>comparingByValue(Comparator.comparingInt(d -> d.score)).reversed())
                .collect(Collectors.toList());

        StringBuilder resultsMessage = new StringBuilder();
        resultsMessage.append("\n§6--- Resultados de Cortadora de Madera ---\n");
        for (int i = 0; i < sortedPlayers.size() && i < 3; i++) {
            Map.Entry<UUID, PlayerData> entry = sortedPlayers.get(i);
            Player p = Bukkit.getPlayer(entry.getKey());
            String name = (p != null) ? p.getName() : "Jugador Desconectado";
            String color = i == 0 ? "§e" : (i == 1 ? "§7" : "§c");
            resultsMessage.append(String.format("%s#%d %s - %d mesas\n", color, i + 1, name, entry.getValue().score));
        }
        resultsMessage.append("§6--------------------------------------\n");
        String finalResults = resultsMessage.toString();

        // Me aseguro de enviar el resultado solo a los que jugaron.
        for (int i = 0; i < sortedPlayers.size(); i++) {
            Map.Entry<UUID, PlayerData> entry = sortedPlayers.get(i);
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) continue;

            int pointsWon = (i == 0) ? 15 : (i == 1) ? 7 : (i == 2) ? 3 : 1;
            pointsManager.addPoints(p, pointsWon, "woodcutter_minigame", "Ranking final");

            p.sendMessage(finalResults);
            p.sendMessage(String.format("§aTu posición: #%d. Has ganado §e%d puntos§a.", i + 1, pointsWon));
        }
    }

    private void teleportAllToSafety(Set<UUID> playerUuids) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : playerUuids) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.teleport(SAFE_EXIT_LOCATION);
                        p.getInventory().clear();
                        p.setGameMode(GameMode.SURVIVAL); // O el modo por defecto del lobby
                        BossBar bossBar = playerBossBars.remove(uuid);
                        if (bossBar != null) bossBar.removeAll();
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
        if (activeMinigames.containsKey(uuid)) {
            activeMinigames.get(uuid).cancel();
            activeMinigames.remove(uuid);
        }
        lobbyPlayers.remove(uuid);
        if (gamePlayers.containsKey(uuid)) {
            gamePlayers.remove(uuid);
            BossBar bossBar = playerBossBars.remove(uuid);
            if (bossBar != null) bossBar.removeAll();
            broadcastToGame(ChatColor.YELLOW + player.getName() + " ha abandonado la partida.");
            if(gamePlayers.isEmpty() && currentState == GameState.RUNNING){
                endGame("Todos los jugadores se han ido");
            }
        }
    }

    // --- 7. MÉTODOS DE UTILIDAD ---

    public boolean isPlayerInGame(Player player) {
        return gamePlayers.containsKey(player.getUniqueId());
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

            switch (data.currentStage) {
                case COLLECTING_LOGS -> { stageText = "§aTALA DE TRONCOS"; progressText = "Troncos"; maxProgress = 5; }
                case CUTTING_PLANKS -> { stageText = "§eCORTE DE TABLONES"; progressText = "Ronda"; maxProgress = 5; } // Ajustado para el minijuego
                case ASSEMBLING_TABLE -> { stageText = "§bENSAMBLAJE DE MESA"; progressText = "Ronda"; maxProgress = 3; } // Ajustado
            }

            // Añado el progreso actual del minijuego si está activo
            String minigameProgress = "";
            if (activeMinigames.containsKey(p.getUniqueId())) {
                minigameProgress = activeMinigames.get(p.getUniqueId()).getStatus();
            }

            String title = String.format("%s §f| §a%s: %d/%d §f| §cMesas: %d §f| §bTiempo: %02d:%02d %s",
                    stageText, progressText, data.stageProgress, maxProgress, data.score, gameTimeLeft / 60, gameTimeLeft % 60, minigameProgress);

            bb.setTitle(title);
            bb.setProgress(Math.max(0.0, Math.min(1.0, (double) gameTimeLeft / GAME_DURATION_SECONDS)));
        }
    }

    // --- MÉTODOS DE AYUDA (BROADCASTS, SONIDOS, ETC) ---
    private List<Player> getLobbyPlayers() { return lobbyPlayers.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).collect(Collectors.toList()); }
    private void broadcastToLobby(String message) { getLobbyPlayers().forEach(p -> p.sendMessage(message)); }
    private void playSoundForLobby(Sound sound, float pitch) { getLobbyPlayers().forEach(p -> p.playSound(p.getLocation(), sound, 1.0f, pitch)); }
    private void broadcastToGame(String message) { gamePlayers.keySet().stream().map(Bukkit::getPlayer).filter(Objects::nonNull).forEach(p -> p.sendMessage(message)); }
    private boolean validateOraxenItems() { return OraxenItems.getItemById(CUTTER_ITEM_ID) != null && OraxenItems.getItemById(HAMMER_ITEM_ID) != null; }
    private ItemStack createWoodcutterAxe() {
        ItemStack axe = new ItemStack(WOODCUTTER_AXE_MATERIAL);
        ItemMeta meta = axe.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "Hacha de Leñador");
            meta.setLore(Collections.singletonList(ChatColor.GRAY + "Úsala para talar troncos de abeto."));
            meta.setUnbreakable(true);
            axe.setItemMeta(meta);
        }
        return axe;
    }
    private static ItemStack createGuiItem(final Material material, final String name, final String... lore) {
        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    // --- CLASES INTERNAS PARA DATOS Y MINIJUEGOS ---

    private static class PlayerData {
        int score = 0;
        PlayerStage currentStage = PlayerStage.COLLECTING_LOGS;
        int stageProgress = 0; // Para los troncos
    }

    // --- ESTRUCTURA BASE PARA LOS MINIJUEGOS ---
    private interface Minigame {
        void start();
        void cancel();
        void onPlayerInteract(PlayerInteractEvent event);
        void onInventoryClick(InventoryClickEvent event);
        String getStatus(); // Para mostrar info en el BossBar
    }

    // --- MINIJUEGO 1: HACHA (TIMING) ---
    private class AxeMinigame implements Minigame {
        private final Player player;
        private BukkitTask task;
        private int progress = 0;
        private boolean resolved = false;

        private static final int DURATION_TICKS = 40; // 2 segundos
        private static final int SUCCESS_START_TICK = 25; // Zona de éxito (de 1.25s a 1.75s)
        private static final int SUCCESS_END_TICK = 35;

        AxeMinigame(Player player) { this.player = player; }

        @Override
        public void start() {
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
            if (resolved) return; // Evito múltiples resoluciones.
            if (progress >= SUCCESS_START_TICK && progress <= SUCCESS_END_TICK) succeed();
            else fail();
        }

        private void succeed() {
            if (resolved) return;
            resolved = true;
            task.cancel();
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + "¡Corte perfecto!"));
            completeMinigame(player, true, PlayerStage.COLLECTING_LOGS);
        }

        private void fail() {
            if (resolved) return;
            resolved = true;
            task.cancel();
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "¡Fallaste!"));
            completeMinigame(player, false, PlayerStage.COLLECTING_LOGS);
        }

        @Override
        public void cancel() {
            if (task != null && !task.isCancelled()) task.cancel();
            resolved = true;
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
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
                    bar.append("§f§lX");
                } else {
                    if (i >= greenStart && i <= greenEnd) {
                        bar.append("§a|"); // Zona de éxito
                    } else {
                        bar.append("§c|"); // Zona de fallo
                    }
                }
            }
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(bar.toString()));
        }

        @Override
        public void onPlayerInteract(PlayerInteractEvent event) {
            // SOLUCIÓN: Se comprueba que el evento provenga de la mano principal para evitar dobles ejecuciones.
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }

            // Este minijuego se resuelve con un clic derecho en el aire o en un bloque.
            if (event.getAction().name().contains("RIGHT_CLICK")) {
                event.setCancelled(true);
                resolveAttempt();
            }
        }

        @Override
        public void onInventoryClick(InventoryClickEvent event) { /* No aplica a este minijuego */ }

        @Override
        public String getStatus() { return "§f(§e¡CLIC!§f)"; }
    }

    // --- MINIJUEGO 2: CORTADORA (SIMON SAYS) ---
    private class CutterMinigame implements Minigame {
        private final Player player;
        private final Inventory inventory;
        private BukkitTask sequenceTask;
        private final List<Integer> sequence = new ArrayList<>();
        private int currentRound = 0;
        private int playerSequencePosition = 0;
        private boolean playerTurn = false;

        private final Material[] colors = {Material.LIME_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE};
        private final int[] slots = {11, 12, 13, 14, 15};
        private static final int TOTAL_ROUNDS = 5;

        CutterMinigame(Player player) {
            this.player = player;
            this.inventory = Bukkit.createInventory(null, 27, "§8CORTADORA: Sigue la secuencia");
        }

        @Override
        public void start() {
            for (int i = 0; i < 27; i++) inventory.setItem(i, GUI_FILLER);
            player.openInventory(inventory);
            nextRound();
        }

        private void nextRound() {
            playerTurn = false;
            currentRound++;
            playerSequencePosition = 0;

            // Actualizo la barra de progreso del jugador principal
            if(gamePlayers.containsKey(player.getUniqueId())) {
                gamePlayers.get(player.getUniqueId()).stageProgress = currentRound -1;
                updateBossBars();
            }

            inventory.setItem(4, createGuiItem(Material.BOOK, "§eMostrando Ronda " + currentRound + "/" + TOTAL_ROUNDS));

            sequence.add(new Random().nextInt(colors.length));
            showSequence();
        }

        private void showSequence() {
            final Iterator<Integer> iterator = sequence.iterator();
            sequenceTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || !inventory.getViewers().contains(player)) {
                        this.cancel();
                        return;
                    }
                    if (!iterator.hasNext()) {
                        playerTurn = true;
                        inventory.setItem(4, createGuiItem(Material.GREEN_WOOL, "§a¡Tu turno! Repite la secuencia"));
                        this.cancel();
                        return;
                    }
                    int colorIndex = iterator.next();
                    int slot = slots[colorIndex];
                    ItemStack original = createGuiItem(colors[colorIndex], "§r");
                    inventory.setItem(slot, createGuiItem(Material.GLOWSTONE, "§e..."));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f + (colorIndex * 0.2f));
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (inventory.getViewers().contains(player)) inventory.setItem(slot, original);
                        }
                    }.runTaskLater(plugin, 10L);
                }
            }.runTaskTimer(plugin, 20L, 15L);
        }

        @Override
        public void onInventoryClick(InventoryClickEvent event) {
            if (!playerTurn || event.getClickedInventory() != inventory) return;

            int clickedSlot = event.getSlot();
            int colorIndexClicked = -1;
            for(int i = 0; i < slots.length; i++) {
                if(slots[i] == clickedSlot) {
                    colorIndexClicked = i;
                    break;
                }
            }
            if (colorIndexClicked == -1) return; // Clic en un slot no válido

            int expectedColorIndex = sequence.get(playerSequencePosition);

            if (colorIndexClicked == expectedColorIndex) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f + (expectedColorIndex * 0.2f));
                playerSequencePosition++;
                if (playerSequencePosition >= sequence.size()) {
                    if (currentRound >= TOTAL_ROUNDS) {
                        succeed();
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1.5f);
                        nextRound();
                    }
                }
            } else {
                fail();
            }
        }

        private void succeed() {
            if (gamePlayers.containsKey(player.getUniqueId())) {
                gamePlayers.get(player.getUniqueId()).stageProgress = TOTAL_ROUNDS;
            }
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            completeMinigame(player, true, PlayerStage.CUTTING_PLANKS);
        }

        private void fail() {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            completeMinigame(player, false, PlayerStage.CUTTING_PLANKS);
        }

        @Override
        public void cancel() {
            if (sequenceTask != null && !sequenceTask.isCancelled()) sequenceTask.cancel();
            player.closeInventory();
        }

        @Override
        public void onPlayerInteract(PlayerInteractEvent event) { /* No aplica */ }

        @Override
        public String getStatus() { return String.format("§f(§eRonda %d/%d§f)", currentRound, TOTAL_ROUNDS); }
    }

    // --- MINIJUEGO 3: MARTILLO (WHAC-A-MOLE) ---
    private class HammerMinigame implements Minigame {
        private final Player player;
        private final Inventory inventory;
        private BukkitTask spawnerTask, roundTimerTask;
        private final Set<Integer> activeNails = new HashSet<>();
        private int currentRound = 0;
        private int hits = 0;

        private final int[] slots = {10, 11, 12, 13, 14, 15, 16};
        private static final int TOTAL_ROUNDS = 3;
        private static final int HITS_PER_ROUND = 5;
        private static final int ROUND_DURATION_TICKS = 100; // 5 segundos

        HammerMinigame(Player player) {
            this.player = player;
            this.inventory = Bukkit.createInventory(null, 27, "§8ENSAMBLAJE: ¡Clava rápido!");
        }

        @Override
        public void start() {
            for (int i = 0; i < 27; i++) inventory.setItem(i, GUI_FILLER);
            player.openInventory(inventory);
            nextRound();
        }

        private void nextRound() {
            currentRound++;
            hits = 0;
            activeNails.clear();
            if (spawnerTask != null) spawnerTask.cancel();
            if (roundTimerTask != null) roundTimerTask.cancel();

            if(gamePlayers.containsKey(player.getUniqueId())) {
                gamePlayers.get(player.getUniqueId()).stageProgress = currentRound - 1;
                updateBossBars();
            }

            inventory.setItem(4, createGuiItem(Material.CLOCK, "§eRonda " + currentRound + "/" + TOTAL_ROUNDS, "§aClavos: 0/" + HITS_PER_ROUND));

            spawnerTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || !inventory.getViewers().contains(player)) {
                        this.cancel();
                        return;
                    }
                    if (activeNails.size() < 3) spawnNail();
                }
            }.runTaskTimer(plugin, 0, 10L); // Spawnea clavos rápido

            roundTimerTask = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || !inventory.getViewers().contains(player)) return;
                    // Si el tiempo se acaba y no ha completado los hits, falla la ronda.
                    if (hits < HITS_PER_ROUND) fail();
                }
            }.runTaskLater(plugin, ROUND_DURATION_TICKS);
        }

        private void spawnNail() {
            int slot = slots[new Random().nextInt(slots.length)];
            if (!activeNails.contains(slot)) {
                activeNails.add(slot);
                inventory.setItem(slot, NAIL_ITEM);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 0.8f);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (activeNails.remove(slot) && inventory.getViewers().contains(player)) {
                            inventory.setItem(slot, GUI_FILLER);
                        }
                    }
                }.runTaskLater(plugin, 30L); // El clavo desaparece después de 1.5s
            }
        }

        @Override
        public void onInventoryClick(InventoryClickEvent event) {
            if (event.getClickedInventory() != inventory) return;
            int slot = event.getSlot();
            if(activeNails.remove(slot)) {
                hits++;
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 1.5f);
                inventory.setItem(slot, HIT_NAIL_ITEM);
                new BukkitRunnable() { @Override public void run() { if (inventory.getViewers().contains(player)) inventory.setItem(slot, GUI_FILLER); } }.runTaskLater(plugin, 5L);
                inventory.setItem(4, createGuiItem(Material.CLOCK, "§eRonda " + currentRound + "/" + TOTAL_ROUNDS, "§aClavos: " + hits + "/" + HITS_PER_ROUND));

                if (hits >= HITS_PER_ROUND) {
                    roundTimerTask.cancel(); // Detengo el timer de la ronda, la pasó.
                    if (currentRound >= TOTAL_ROUNDS) {
                        succeed();
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1.5f);
                        new BukkitRunnable() { @Override public void run() { nextRound(); } }.runTaskLater(plugin, 20L); // Pequeña pausa
                    }
                }
            }
        }

        private void succeed() {
            if (gamePlayers.containsKey(player.getUniqueId())) {
                gamePlayers.get(player.getUniqueId()).stageProgress = TOTAL_ROUNDS;
            }
            cancelTasks();
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.2f);
            completeMinigame(player, true, PlayerStage.ASSEMBLING_TABLE);
        }

        private void fail() {
            cancelTasks();
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.0f);
            completeMinigame(player, false, PlayerStage.ASSEMBLING_TABLE);
        }

        private void cancelTasks() {
            if (spawnerTask != null) spawnerTask.cancel();
            if (roundTimerTask != null) roundTimerTask.cancel();
        }

        @Override
        public void cancel() {
            cancelTasks();
            player.closeInventory();
        }

        @Override
        public void onPlayerInteract(PlayerInteractEvent event) { /* No aplica */ }

        @Override
        public String getStatus() { return String.format("§f(§e%d/%d Clavos§f)", hits, HITS_PER_ROUND); }
    }
}