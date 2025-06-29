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
import java.util.concurrent.ThreadLocalRandom;
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
    private BukkitTask minigameTickTask;
    private int lobbyTimeLeft;
    private int gameTimeLeft;

    // Uso un mapa para gestionar cualquier minijuego activo de un jugador.
    private final Map<UUID, Minigame> activeMinigames = new ConcurrentHashMap<>();
    private final Map<UUID, Long> axeCooldowns = new ConcurrentHashMap<>();

    // --- CONSTANTES DE CONFIGURACIÓN DEL MINIJUEGO ---
    private static final int MIN_PLAYERS = 1;
    private static final int MAX_PLAYERS = 20;
    private static final int LOBBY_DURATION_SECONDS = 60;
    private static final int GAME_DURATION_SECONDS = 400;
    private static final long AXE_MINIGAME_COOLDOWN_MS = 200L;

    // --- IDs DE ITEMS Y BLOQUES (extraídos de tus instrucciones) ---
    private static final Material WOODCUTTER_AXE_MATERIAL = Material.IRON_AXE;
    private static final String CUTTER_ITEM_ID = "cortadora_de_madera";
    private static final String HAMMER_ITEM_ID = "martillo";
    private static final String CUTTER_TABLE_ID = "mesa_cortadora";
    private static final String ASSEMBLY_TABLE_ID = "mesa_vacia";
    private static final String ORAXEN_HELMET_ID = "casco";
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

        // Inicio mi nuevo task maestro que gestionará todos los minijuegos.
        minigameTickTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (activeMinigames.isEmpty()) return;
                // Itera de forma segura sobre los valores del mapa concurrente.
                activeMinigames.values().forEach(Minigame::tick);
            }
        }.runTaskTimer(plugin, 0L, 1L);
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
        updatePlayerBossBar(player);
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
    }.runTaskTimer(plugin, 0L, 20L); // Se mantiene en 20L (1 segundo)
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
        updatePlayerBossBar(player);
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

    // Esto es CRUCIAL para evitar que el jugador se quede "atascado".

    /**
     * Maneja el cierre de un inventario. Si el jugador estaba en un minijuego con GUI,
     * lo detecta y cancela el minijuego para limpiar su estado de forma segura.
     * @param event El evento de cierre de inventario proporcionado por Bukkit.
     */
    public void handleInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        UUID playerUUID = player.getUniqueId();
        Minigame minigame = activeMinigames.get(playerUUID);

        // Si el jugador está en un minijuego y el inventario cerrado es el del minijuego...
        if (minigame != null && event.getInventory().equals(minigame.getInventory())) {
            // ...lo considero un fallo. Uso un BukkitRunnable para asegurar que se procese
            // en el siguiente tick, evitando cualquier conflicto con el evento de cierre.
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Verifico de nuevo que el minijuego siga activo antes de fallar.
                    // Esto evita problemas si el jugador ganó justo en el mismo tick en que cerró el inventario.
                    if (activeMinigames.get(playerUUID) == minigame) {
                        // Aquí no necesito saber el tipo de minijuego, simplemente lo fallo.
                        // La lógica de `completeMinigame` se encarga del resto.
                        if (minigame instanceof CutterMinigame cutterGame) {
                            cutterGame.fail();
                        } else if (minigame instanceof HammerMinigame hammerGame) {
                            hammerGame.fail();
                        }
                    }
                }
            }.runTask(plugin);
        }
    }

    /**
     * Mi método específico para actualizar la boss bar de un jugador.
     * Lo llamo únicamente cuando sus datos de progreso cambian.
     * @param player El jugador cuya barra se debe actualizar.
     */
    public void updatePlayerBossBar(Player player) {
        if (player == null || !isPlayerInGame(player)) return;

        UUID uuid = player.getUniqueId();
        BossBar bb = playerBossBars.get(uuid);
        PlayerData data = gamePlayers.get(uuid);
        if (bb == null || data == null) return;

        String stageText = "";
        String progressText = "";
        int maxProgress = 0;

        switch (data.currentStage) {
            case COLLECTING_LOGS -> { stageText = "§aTALA DE TRONCOS"; progressText = "Troncos"; maxProgress = 5; }
            case CUTTING_PLANKS -> { stageText = "§eCORTE DE TABLONES"; progressText = "Ronda"; maxProgress = 5; }
            case ASSEMBLING_TABLE -> { stageText = "§bENSAMBLAJE DE MESA"; progressText = "Ronda"; maxProgress = 3; }
        }

        String minigameProgress = "";
        if (activeMinigames.containsKey(uuid)) {
            minigameProgress = activeMinigames.get(uuid).getStatus();
        }

        String title = String.format("%s §f| §a%s: %d/%d §f| §cMesas: %d §f| §bTiempo: %02d:%02d %s",
                stageText, progressText, data.stageProgress, maxProgress, data.score, gameTimeLeft / 60, gameTimeLeft % 60, minigameProgress);

        bb.setTitle(title);
    }

    /**
     * Mi método optimizado que actualiza todas las barras.
     * Solo se llama una vez por segundo desde el gameTimer para el tiempo,
     * o se puede llamar a 'updatePlayerBossBar' para un jugador específico.
     */
    private void updateBossBars() {
        double progress = Math.max(0.0, Math.min(1.0, (double) gameTimeLeft / GAME_DURATION_SECONDS));
        for (UUID uuid : gamePlayers.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                // Actualizo el progreso (la barra de tiempo) para todos.
                BossBar bb = playerBossBars.get(uuid);
                if (bb != null) {
                    bb.setProgress(progress);
                }
                // Y actualizo el texto completo para reflejar el contador de tiempo.
                updatePlayerBossBar(p);
            }
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
                updatePlayerBossBar(player);
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

        // Me aseguro de detener también mi nuevo task maestro.
        if (minigameTickTask != null) minigameTickTask.cancel();

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

        // Ordeno a los jugadores de mayor a menor puntuación. La forma en que lo hacías ya era correcta.
        final List<Map.Entry<UUID, PlayerData>> sortedPlayers = finalScores.entrySet().stream()
                .sorted(Map.Entry.<UUID, PlayerData>comparingByValue(Comparator.comparingInt(d -> d.score)).reversed())
                .collect(Collectors.toList());

        final List<PointsManager.PlayerScore> topBefore = pointsManager.getTopPlayers(3);

        // 1. Construyo el mensaje del Top 3 (o más si hay empates) para enviarlo a todos.
        StringBuilder topMessage = new StringBuilder("\n§6--- Resultados de Cortadora de Madera ---\n");
        int lastScore = -1;
        int rank = 0;
        for (int i = 0; i < sortedPlayers.size(); i++) {
            Map.Entry<UUID, PlayerData> entry = sortedPlayers.get(i);
            int currentScore = entry.getValue().score;

            // Si la puntuación actual es diferente a la del jugador anterior, actualizo el puesto.
            // Si es la misma, mantienen el mismo puesto (manejo de empate).
            if (currentScore != lastScore) {
                rank = i + 1;
            }

            // Solo me interesa mostrar los 3 primeros puestos en el podio.
            if (rank <= 3) {
                Player p = Bukkit.getPlayer(entry.getKey());
                String name = (p != null) ? p.getName() : "Jugador Desc.";
                String color = rank == 1 ? "§e" : (rank == 2 ? "§7" : "§c");
                // Aquí uso "mesas", como en tu lógica de puntuación. Si prefieres "pinturas", solo cambia la palabra.
                topMessage.append(String.format("%s#%d %s - %d mesas\n", color, rank, name, currentScore));
            }
            lastScore = currentScore;
        }
        topMessage.append("§6--------------------------------------\n");
        String finalTopMessage = topMessage.toString();

        // 2. Ahora itero de nuevo para asignar puntos y enviar mensajes personalizados.
        lastScore = -1;
        rank = 0;
        for (int i = 0; i < sortedPlayers.size(); i++) {
            Map.Entry<UUID, PlayerData> entry = sortedPlayers.get(i);
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) continue;

            int currentScore = entry.getValue().score;
            if (currentScore != lastScore) {
                rank = i + 1; // Actualizo el rango basado en la posición en la lista ordenada.
            }

            int pointsWon = switch (rank) {
                case 1 -> 15;
                case 2 -> 7;
                case 3 -> 3;
                default -> 1; // El resto gana 1 punto por participar.
            };

            pointsManager.addPoints(p, pointsWon, "woodcutter_minigame", "Ranking final");

            // Le envío el podio general...
            p.sendMessage(finalTopMessage);
            // ...y su resultado personal detallado.
            p.sendMessage(String.format("§aQuedaste en la posición #%d con %d mesas. ¡Ganaste §e%d puntos§a!", rank, currentScore, pointsWon));

            lastScore = currentScore;
        }

        // La lógica para actualizar el scoreboard global ya estaba bien, así que la mantengo intacta.
        final List<PointsManager.PlayerScore> topAfter = pointsManager.getTopPlayers(3);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!topBefore.equals(topAfter)) {
                    plugin.getLogger().info("El Top 3 ha cambiado. Actualizando scoreboard para todos los jugadores online...");
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        if (onlinePlayer.isOnline()) {
                            updatePlayerScoreboard(onlinePlayer, topAfter);
                        }
                    }
                } else {
                    for (Map.Entry<UUID, PlayerData> entry : sortedPlayers) {
                        Player p = Bukkit.getPlayer(entry.getKey());
                        if (p != null && p.isOnline()) {
                            updatePlayerScoreboard(p, topAfter);
                        }
                    }
                }
            }
        }.runTask(plugin);
    }

// --- MI NUEVO MÉTODO DE AYUDA (Añádelo al final de la clase WoodcutterManager) ---
    /**
     * Un método de ayuda para centralizar la lógica de actualización del scoreboard.
     * Es privado porque solo lo necesita esta clase.
     * @param player El jugador cuyo scoreboard se actualizará.
     * @param topPlayers La lista actualizada del Top 3 para mostrar.
     */
    private void updatePlayerScoreboard(Player player, List<PointsManager.PlayerScore> topPlayers) {
        int totalPoints = pointsManager.getTotalPoints(player);
        int topPosition = pointsManager.getPlayerRank(player);
        // Tu lógica para 'unlockedAll' debería estar aquí, por ahora será false.
        boolean unlockedAll = false;
        com.TNTStudios.deWaltCore.scoreboard.DeWaltScoreboardManager.showDefaultPage(player, topPosition, totalPoints, unlockedAll, topPlayers);
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
    if (minigameTickTask != null) minigameTickTask.cancel();

    getLobbyPlayers().forEach(p -> p.teleport(SAFE_EXIT_LOCATION));

        lobbyPlayers.clear();
        gamePlayers.clear();
        playerBossBars.values().forEach(BossBar::removeAll);
        playerBossBars.clear();

        currentState = GameState.INACTIVE;
    }

    public void handlePlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        boolean wasInLobby = lobbyPlayers.remove(uuid);

        if (wasInLobby) {
            broadcastToLobby(ChatColor.YELLOW + player.getName() + " ha salido de la cola.");
            // Si el lobby se queda sin suficientes jugadores, cancelo la cuenta atrás.
            if (currentState == GameState.LOBBY && lobbyPlayers.size() < MIN_PLAYERS) {
                broadcastToLobby(ChatColor.RED + "No hay suficientes jugadores. Se canceló el inicio.");
                resetGame(); // resetGame ya cancela el task.
            }
        }

        if (gamePlayers.containsKey(uuid)) {
            // Cancelo cualquier minijuego activo que tuviera
            if (activeMinigames.containsKey(uuid)) {
                activeMinigames.get(uuid).cancel();
                activeMinigames.remove(uuid);
            }
            gamePlayers.remove(uuid);
            BossBar bossBar = playerBossBars.remove(uuid);
            if (bossBar != null) {
                bossBar.removeAll();
            }
            broadcastToGame(ChatColor.YELLOW + player.getName() + " ha abandonado la partida.");
            if (currentState == GameState.RUNNING && gamePlayers.isEmpty()) {
                endGame("Todos los jugadores se han ido");
            }
        }
    }

    /**
     * Mi nuevo método para sacar a un jugador que está online.
     * Realiza todas las acciones necesarias como enviarle mensajes, limpiar su inventario y teletransportarlo.
     * @param player El jugador a eliminar.
     * @param reason La razón por la que se le elimina.
     * @param teleportToSafeZone Si debe ser teletransportado a la zona segura.
     */
    public void removeOnlinePlayer(Player player, String reason, boolean teleportToSafeZone) {
        if (!isPlayerParticipating(player)) return;

        // 1. Ejecuto la lógica de limpieza de datos primero.
        handlePlayerQuit(player);

        // 2. Ahora, realizo acciones en el jugador que sigue online.
        player.sendMessage(ChatColor.RED + "Has salido del minijuego. Razón: " + reason);
        clearInventorySafely(player);
        player.setGameMode(GameMode.SURVIVAL); // O el modo de juego por defecto del lobby

        if (teleportToSafeZone) {
            player.teleport(SAFE_EXIT_LOCATION);
        }
    }

    /**
     * Mi nuevo método seguro para limpiar el inventario de un jugador.
     * Borra todo excepto el casco de Oraxen con el ID 'casco'.
     * @param player El jugador cuyo inventario será limpiado.
     */
    private void clearInventorySafely(Player player) {
        // Guardo el casco si es el correcto
        ItemStack helmet = player.getInventory().getHelmet();
        boolean keepHelmet = false;
        if (helmet != null) {
            String oraxenId = OraxenItems.getIdByItem(helmet);
            if (ORAXEN_HELMET_ID.equals(oraxenId)) {
                keepHelmet = true;
            }
        }

        // Limpio el inventario principal y la armadura. player.getInventory().clear() hace todo esto.
        player.getInventory().clear();

        // Si tenía el casco correcto, se lo devuelvo.
        if (keepHelmet) {
            player.getInventory().setHelmet(helmet);
        }
    }


    // --- 7. MÉTODOS DE UTILIDAD ---

    /**
     * Mi comprobador para saber si un jugador está en el lobby o en el juego.
     * @param player El jugador a comprobar.
     * @return true si está participando.
     */
    public boolean isPlayerParticipating(Player player) {
        UUID uuid = player.getUniqueId();
        return lobbyPlayers.contains(uuid) || gamePlayers.containsKey(uuid);
    }

    public boolean isPlayerInGame(Player player) {
        return gamePlayers.containsKey(player.getUniqueId());
    }

    // --- MÉTODOS DE AYUDA (BROADCASTS, SONIDOS, ETC) ---
    private List<Player> getLobbyPlayers() { return lobbyPlayers.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).collect(Collectors.toList()); }
    private void broadcastToLobby(String message) {
        for (UUID uuid : lobbyPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }

  private void playSoundForLobby(Sound sound, float pitch) {
        for (UUID uuid : lobbyPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.playSound(p.getLocation(), sound, 1.0f, pitch);
            }
        }
    }

  private void broadcastToGame(String message) {
        for (UUID uuid : gamePlayers.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }
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
        void tick();
        void onPlayerInteract(PlayerInteractEvent event);
        void onInventoryClick(InventoryClickEvent event);

        /**
         * Me devuelve el inventario de la GUI del minijuego, si es que tiene uno.
         * @return El inventario, o null si el minijuego no usa una GUI.
         */
        Inventory getInventory();

        String getStatus(); // Para mostrar info en el BossBar
    }

    // --- MINIJUEGO 1: HACHA (TIMING) ---
    private class AxeMinigame implements Minigame {
    private final Player player;
    // private BukkitTask task; <-- YA NO NECESITO ESTO
            private int progress = 0;
    private boolean resolved = false;

    private static final int DURATION_TICKS = 40; // 2 segundos
    private static final int SUCCESS_START_TICK = 25;
    private static final int SUCCESS_END_TICK = 30;

    AxeMinigame(Player player) { this.player = player; }

    @Override
    public void start() {
      player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.5f);
    }

        // Este es mi nuevo método que será llamado por el task maestro 20 veces por segundo.
        @Override
        public void tick() {
            if (resolved) return;

            if (progress > DURATION_TICKS) {
                fail();
                return;
            }
            displayProgressBar();
            progress++;
        }

    public void resolveAttempt() {
      if (resolved) return; // Evito múltiples resoluciones.
      if (progress >= SUCCESS_START_TICK && progress <= SUCCESS_END_TICK) succeed();
      else fail();
    }

    private void succeed() {
      if (resolved) return;
      resolved = true;
      // task.cancel(); <-- YA NO ES NECESARIO
      player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + "¡Corte perfecto!"));
      completeMinigame(player, true, PlayerStage.COLLECTING_LOGS);
    }

    private void fail() {
      if (resolved) return;
      resolved = true;
      // task.cancel(); <-- YA NO ES NECESARIO
      player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "¡Fallaste!"));
      completeMinigame(player, false, PlayerStage.COLLECTING_LOGS);
    }

    @Override
    public void cancel() {
      // if (task != null && !task.isCancelled()) task.cancel(); <-- YA NO ES NECESARIO
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

        /**
         * Este minijuego no tiene una GUI, así que devuelvo null
         * como lo requiere el contrato de la interfaz Minigame.
         * @return null siempre.
         */
        @Override
        public Inventory getInventory() {
            return null;
        }

        @Override
        public String getStatus() { return "§f(§e¡CLIC!§f)"; }
    }

    // --- MINIJUEGO 2: CORTADORA (SIMON SAYS) ---
    private class CutterMinigame implements Minigame {
        private final Player player;
        private final Inventory inventory;

        // --- ESTADO Y LÓGICA DE TICKS (SIN BUKKIT_TASK INTERNO) ---
        private final List<Integer> sequence = new ArrayList<>();
        private int currentRound = 0;
        private int playerSequencePosition = 0;
        private boolean playerTurn = false;
        private boolean resolved = false;

        // Variables para controlar la animación de la secuencia con el tick maestro.
        private boolean showingSequence = false;
        private int sequenceIndexToShow = 0;
        private long nextFlashTick = 0;
        private int slotToFlash = -1;

        private final Material[] colors = {Material.LIME_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE, Material.YELLOW_STAINED_GLASS_PANE, Material.ORANGE_STAINED_GLASS_PANE};
        private final int[] slots = {11, 12, 13, 14, 15};
        private static final int TOTAL_ROUNDS = 5;
        private static final long SEQUENCE_FLASH_DELAY_TICKS = 15L; // Tiempo entre cada flash
        private static final long SEQUENCE_FLASH_DURATION_TICKS = 10L; // Cuánto tiempo se muestra el flash

        CutterMinigame(Player player) {
            this.player = player;
            this.inventory = Bukkit.createInventory(null, 27, "§fCORTADORA: Sigue la secuencia");
        }

        @Override
        public void start() {
            for (int i = 0; i < 27; i++) inventory.setItem(i, GUI_FILLER);
            player.openInventory(inventory);
            nextRound();
        }

        private void nextRound() {
            // Reseteo el estado para la nueva ronda
            playerTurn = false;
            showingSequence = true;
            sequenceIndexToShow = 0;
            playerSequencePosition = 0;
            currentRound++;

            if(gamePlayers.containsKey(player.getUniqueId())) {
                gamePlayers.get(player.getUniqueId()).stageProgress = currentRound - 1;
                updatePlayerBossBar(player);
            }

            inventory.setItem(4, createGuiItem(Material.BOOK, "§eMostrando Ronda " + currentRound + "/" + TOTAL_ROUNDS));
            sequence.add(new Random().nextInt(colors.length));

            // Empiezo a mostrar la secuencia después de 1 segundo (20 ticks).
            nextFlashTick = plugin.getServer().getCurrentTick() + 20L;
        }

        /**
         * Este método es el corazón de la optimización y la corrección.
         * Es llamado 20 veces por segundo y gestiona la animación de la secuencia,
         * eliminando el delay que causaba el problema de desincronización.
         */
        @Override
        public void tick() {
            if (resolved || !showingSequence) return;
            if (!player.isOnline() || !inventory.getViewers().contains(player)) {
                fail();
                return;
            }

            long currentTick = plugin.getServer().getCurrentTick();

            // Lógica para APAGAR un panel que ya se mostró.
            if (slotToFlash != -1 && currentTick >= nextFlashTick) {
                int colorIndex = sequence.get(sequenceIndexToShow - 1);
                inventory.setItem(slotToFlash, createGuiItem(colors[colorIndex], "§r")); // Lo apago.
                slotToFlash = -1; // Marco que ya no hay un panel encendido.

                // MI CORRECCIÓN: Verifico si ese era el ÚLTIMO panel de la secuencia.
                if (sequenceIndexToShow >= sequence.size()) {
                    // Si es así, la animación terminó. Habilito el turno del jugador INMEDIATAMENTE.
                    showingSequence = false;
                    playerTurn = true;
                    inventory.setItem(4, createGuiItem(Material.GREEN_WOOL, "§a¡Tu turno! Repite la secuencia"));
                } else {
                    // Si aún faltan paneles, establezco el delay para mostrar el siguiente.
                    nextFlashTick = currentTick + SEQUENCE_FLASH_DELAY_TICKS;
                }
            }
            // Lógica para ENCENDER el siguiente panel de la secuencia.
            else if (slotToFlash == -1 && currentTick >= nextFlashTick) {
                // Me aseguro de que todavía queden paneles por mostrar.
                if (sequenceIndexToShow < sequence.size()) {
                    int colorIndex = sequence.get(sequenceIndexToShow);
                    slotToFlash = slots[colorIndex];
                    inventory.setItem(slotToFlash, createGuiItem(Material.GLOWSTONE, "§e..."));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f + (colorIndex * 0.2f));

                    sequenceIndexToShow++;
                    // Establezco cuánto tiempo durará encendido el panel.
                    nextFlashTick = currentTick + SEQUENCE_FLASH_DURATION_TICKS;
                }
                // Ya no hay un 'else' aquí, porque el final de la secuencia se maneja arriba.
            }
        }

        @Override
        public void onInventoryClick(InventoryClickEvent event) {
            if (resolved || !playerTurn || event.getClickedInventory() != inventory) return;

            int clickedSlot = event.getSlot();
            int colorIndexClicked = -1;
            for(int i = 0; i < slots.length; i++) {
                if(slots[i] == clickedSlot) {
                    colorIndexClicked = i;
                    break;
                }
            }
            if (colorIndexClicked == -1) return;

            if (colorIndexClicked == sequence.get(playerSequencePosition)) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f + (colorIndexClicked * 0.2f));
                playerSequencePosition++;

                if (playerSequencePosition >= sequence.size()) {
                    if (currentRound >= TOTAL_ROUNDS) {
                        succeed();
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1.5f);
                        // El jugador acertó la secuencia, preparo la siguiente ronda.
                        // 'playerTurn' se vuelve 'false' inmediatamente para evitar clics extra.
                        nextRound();
                    }
                }
            } else {
                fail();
            }
        }

        private void succeed() {
            if (resolved) return;
            resolved = true;
            if (gamePlayers.containsKey(player.getUniqueId())) {
                gamePlayers.get(player.getUniqueId()).stageProgress = TOTAL_ROUNDS;
            }
            completeMinigame(player, true, PlayerStage.CUTTING_PLANKS);
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        }

        private void fail() {
            if (resolved) return;
            resolved = true;
            completeMinigame(player, false, PlayerStage.CUTTING_PLANKS);
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        }

        @Override
        public void cancel() {
            resolved = true;
            player.closeInventory();
        }

        @Override
        public Inventory getInventory() { return this.inventory; }

        @Override
        public void onPlayerInteract(PlayerInteractEvent event) { /* No aplica */ }

        @Override
        public String getStatus() { return String.format("§f(§eRonda %d/%d§f)", currentRound, TOTAL_ROUNDS); }
    }

    // --- MINIJUEGO 3: MARTILLO (WHAC-A-MOLE) ---
    private class HammerMinigame implements Minigame {
        private final Player player;
        private final Inventory inventory;

        // Almaceno el 'tick' del servidor en que cada clavo apareció.
        // La clave es el slot, el valor es el tick. Es concurrente por seguridad.
        private final Map<Integer, Long> activeNails = new ConcurrentHashMap<>();

        private int currentRound = 0;
        private int hits = 0;
        private boolean resolved = false;

        // Contadores de tiempo basados en ticks, para no usar tasks internos.
        private long roundStartTick;
        private long nextNailSpawnTick;

        private final int[] slots = {10, 11, 12, 13, 14, 15, 16};
        private static final int TOTAL_ROUNDS = 3;
        private static final int HITS_PER_ROUND = 5;

        // Duraciones en ticks (20 ticks = 1 segundo), fáciles de ajustar.
        private static final long ROUND_DURATION_TICKS = 100L; // 5 segundos
        private static final long NAIL_LIFETIME_TICKS = 30L;   // 1.5 segundos
        private static final long NAIL_SPAWN_INTERVAL_TICKS = 10L; // 0.5 segundos

        HammerMinigame(Player player) {
            this.player = player;
            this.inventory = Bukkit.createInventory(null, 27, "§fENSAMBLAJE: ¡Clava rápido!");
        }

        /**
         * Devuelvo la instancia del inventario de este minijuego,
         * cumpliendo con el contrato de la interfaz.
         * @return El inventario del minijuego.
         */
        @Override
        public Inventory getInventory() {
            return this.inventory;
        }

        @Override
        public void start() {
            for (int i = 0; i < 27; i++) inventory.setItem(i, GUI_FILLER);
            player.openInventory(inventory);
            nextRound();
        }

        private void nextRound() {
            this.currentRound++;
            this.hits = 0;
            this.activeNails.clear();

            // Limpio el inventario visualmente para evitar clavos de la ronda anterior.
            for (int slot : slots) {
                inventory.setItem(slot, GUI_FILLER);
            }

            // Reseteo los contadores de tiempo para la nueva ronda
            long currentTick = plugin.getServer().getCurrentTick();
            this.roundStartTick = currentTick;
            this.nextNailSpawnTick = currentTick;

            if (gamePlayers.containsKey(player.getUniqueId())) {
                gamePlayers.get(player.getUniqueId()).stageProgress = currentRound - 1;
                updatePlayerBossBar(player);
            }
            inventory.setItem(4, createGuiItem(Material.CLOCK, "§eRonda " + currentRound + "/" + TOTAL_ROUNDS, "§aClavos: 0/" + HITS_PER_ROUND));
        }

        /**
         * Este es el corazón de la optimización. Se llama cada tick desde el task maestro
         * y gestiona el tiempo, la aparición y la expiración de los clavos sin crear nuevas tareas.
         */
        @Override
        public void tick() {
            if (resolved) return;

            // Si el jugador cierra el inventario o se desconecta, el minijuego falla.
            if (!player.isOnline() || !inventory.getViewers().contains(player)) {
                fail();
                return;
            }

            long currentTick = plugin.getServer().getCurrentTick();

            // 1. Verifico si la ronda se acabó por tiempo.
            if (currentTick > roundStartTick + ROUND_DURATION_TICKS) {
                fail(); // Si ya ganó, 'resolved' sería true y esta línea no se alcanzaría.
                return;
            }

            // 2. Verifico si un clavo existente debe expirar usando removeIf, que es eficiente.
            activeNails.entrySet().removeIf(entry -> {
                if (currentTick > entry.getValue() + NAIL_LIFETIME_TICKS) {
                    inventory.setItem(entry.getKey(), GUI_FILLER);
                    return true; // Elimina el clavo del mapa.
                }
                return false;
            });

            // 3. Verifico si debo generar un nuevo clavo.
            if (currentTick >= nextNailSpawnTick && activeNails.size() < 3) {
                spawnNail(currentTick);
                this.nextNailSpawnTick = currentTick + NAIL_SPAWN_INTERVAL_TICKS;
            }
        }

        private void spawnNail(long currentTick) {
            // Lógica para encontrar un slot vacío de forma segura.
            int slot;
            int attempts = 0;
            do {
                slot = slots[ThreadLocalRandom.current().nextInt(slots.length)];
                attempts++;
            } while (activeNails.containsKey(slot) && attempts < 20);

            if (!activeNails.containsKey(slot)) {
                activeNails.put(slot, currentTick);
                inventory.setItem(slot, NAIL_ITEM);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 0.8f);
            }
        }

        @Override
        public void onInventoryClick(InventoryClickEvent event) {
            if (resolved || event.getClickedInventory() != inventory) return;

            // Verifico que el ítem sea un clavo para evitar procesar clics en otros ítems.
            if (event.getCurrentItem() == null || !event.getCurrentItem().isSimilar(NAIL_ITEM)) {
                return;
            }

            int slot = event.getSlot();

            // La operación .remove() es atómica. Si devuelve no-null, el clic fue exitoso y a tiempo.
            if (activeNails.remove(slot) != null) {
                hits++;
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 1.5f);
                inventory.setItem(slot, HIT_NAIL_ITEM);

                // Desaparece el clavo golpeado tras un breve instante.
                // Este es uno de los pocos usos aceptables de runTaskLater, ya que es para un efecto visual breve.
                new BukkitRunnable() {
                    @Override public void run() { if (inventory.getViewers().contains(player)) inventory.setItem(slot, GUI_FILLER); }
                }.runTaskLater(plugin, 5L);

                inventory.setItem(4, createGuiItem(Material.CLOCK, "§eRonda " + currentRound + "/" + TOTAL_ROUNDS, "§aClavos: " + hits + "/" + HITS_PER_ROUND));

                if (hits >= HITS_PER_ROUND) {
                    if (currentRound >= TOTAL_ROUNDS) {
                        succeed();
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1f, 1.5f);
                        // Doy una pequeña pausa antes de iniciar la siguiente ronda.
                        new BukkitRunnable() {
                            @Override public void run() { if (!resolved) nextRound(); }
                        }.runTaskLater(plugin, 20L);
                    }
                }
            }
            // Si .remove(slot) devuelve null, el clavo ya expiró. No se hace nada.
        }

        private void succeed() {
            if (resolved) return;
            this.resolved = true;

            if (gamePlayers.containsKey(player.getUniqueId())) {
                gamePlayers.get(player.getUniqueId()).stageProgress = TOTAL_ROUNDS;
            }

            completeMinigame(player, true, PlayerStage.ASSEMBLING_TABLE);
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1.2f);
        }

        private void fail() {
            if (resolved) return;
            this.resolved = true;

            completeMinigame(player, false, PlayerStage.ASSEMBLING_TABLE);
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.0f);
        }

        @Override
        public void cancel() {
            this.resolved = true;
            player.closeInventory();
        }

        @Override
        public void onPlayerInteract(PlayerInteractEvent event) { /* No aplica */ }

        @Override
        public String getStatus() { return String.format("§f(§e%d/%d Clavos§f)", hits, HITS_PER_ROUND); }
    }
}