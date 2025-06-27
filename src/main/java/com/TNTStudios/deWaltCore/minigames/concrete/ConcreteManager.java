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
 * -- REFACTORIZACIÓN PARA ALTO RENDIMIENTO (200 JUGADORES) --
 * 1.  Cambiado el límite de jugadores a 200.
 * 2.  OPTIMIZACIÓN CRÍTICA: Implementado un sistema de tarea única (tick manager) para el minijuego de ruptura.
 * Esto evita crear una tarea por jugador, reduciendo drásticamente la carga del scheduler del servidor.
 * 3.  OPTIMIZACIÓN CRÍTICA: La restauración de bloques ahora se hace en lotes para evitar picos de lag al final de la partida.
 * 4.  CORRECCIÓN DE SEGURIDAD: Eliminado el uso indebido de `runTaskAsynchronously` para la entrega de premios,
 * previniendo errores de concurrencia y optimizando la creación de tareas.
 * 5.  Optimización menor en la actualización del action bar para no crear tareas innecesarias.
 * 6.  MI MEJORA: Añadida gestión de empates, limpieza de inventario segura y anuncios de resultados.
 */
public class ConcreteManager {

  private final DeWaltCore plugin;
  private final PointsManager pointsManager;

  private enum GameState {INACTIVE, LOBBY, RUNNING}

  private volatile GameState currentState = GameState.INACTIVE;

  // Mis colecciones thread-safe para manejar los datos de los jugadores y el mundo.
  private final Set<UUID> lobbyPlayers = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Integer> gameScores = new ConcurrentHashMap<>();
  private final Map<Location, BlockData> brokenBlocks = new ConcurrentHashMap<>();

  private BukkitTask lobbyCountdownTask;
  private BukkitTask gameTimerTask;
  // --- MI NUEVA TAREA ÚNICA PARA EL MINIJUEGO DE RUPTURA ---
  private BukkitTask breakingMinigameManagerTask;

  private int lobbyTimeLeft;
  private int gameTimeLeft;

  // --- LÓGICA REFACTORIZADA PARA EL MINIJUEGO DE ROMPER BLOQUES ---
  private final Map<UUID, BlockBreakingAttempt> activeBreakingAttempts = new ConcurrentHashMap<>();
  private final Set<Location> targettedBlocks = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Long> playerBreakCooldowns = new ConcurrentHashMap<>();

  // --- CONSTANTES DE CONFIGURACIÓN DEL JUEGO (AHORA PARA 200 JUGADORES) ---
  private static final int MIN_PLAYERS = 1;
  private static final int MAX_PLAYERS = 20;
  private static final int LOBBY_DURATION_SECONDS = 60;
  private static final int GAME_DURATION_SECONDS = 120; // 2 minutos
  private static final String HAMMER_ITEM_ID = "martillo_demoledor";
  // MI NUEVA LÓGICA: El ID del casco de Oraxen que no se debe borrar.
  private static final String PROTECTIVE_HELMET_ID = "casco";
  // --- MI CONSTANTE PARA LA RESTAURACIÓN EN LOTES ---
  private static final int BLOCKS_TO_RESTORE_PER_TICK = 300; // Un valor seguro para no causar lag

  // --- ZONAS DEL MINIJUEGO ---
  private static final Location LOBBY_SPAWN_LOCATION = new Location(Bukkit.getWorld("DeWALTConcreto"), 9.48, 117.00, -3.23, 90, 0);
  private static final Location GAME_SPAWN_LOCATION = new Location(Bukkit.getWorld("DeWALTConcreto"), 9.48, 117.00, -3.23, 90, 0);
  private static final Location SAFE_EXIT_LOCATION = new Location(Bukkit.getWorld("DEWALT LOBBY"), -2.13, 78.00, 0.44, 90, 0);

  // --- CONSTANTES DEL MINIJUEGO DE RUPTURA ---
  private static final int BREAK_MINIGAME_DURATION_TICKS = 30;
  private static final int BREAK_SUCCESS_START_TICK = 18;
  private static final int BREAK_SUCCESS_END_TICK = 24;
  private static final long BREAK_COOLDOWN_MS = 200L;

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

  // --- 1. LÓGICA DEL LOBBY (Sin cambios mayores, ya era robusta) ---

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
          resetGame(); // resetGame ya cancela esta tarea
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
        p.teleport(GAME_SPAWN_LOCATION);
        // MI MEJORA: Limpio el inventario aquí para asegurar que entren sin items previos.
        clearPlayerInventory(p);
        p.getInventory().addItem(hammer.clone());
        p.sendTitle(ChatColor.GREEN + "¡A ROMPER!", ChatColor.WHITE + "¡Clic derecho para iniciar, y otra vez en el momento justo!", 10, 60, 20);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
      }
    }
    lobbyPlayers.clear();

    broadcastToGame(ChatColor.GOLD + "¡El juego ha comenzado! ¡El que más bloques rompa, gana!");
    startGameTimer();
    // INICIO MI NUEVA TAREA ADMINISTRADORA
    startBreakingMinigameManager();
  }

  private void startGameTimer() {
    gameTimerTask = new BukkitRunnable() {
      @Override
      public void run() {
        if (gameScores.isEmpty() || gameTimeLeft <= 0) {
          endGame(gameTimeLeft <= 0 ? "Se acabó el tiempo" : "Todos los jugadores salieron");
          // endGame se encarga de cancelar esta tarea
          return;
        }

        // MI OPTIMIZACIÓN: Actualizo la barra de acción directamente aquí.
        updateActionBarForAll();
        gameTimeLeft--;
      }
    }.runTaskTimer(plugin, 0L, 20L);
  }

  /**
   * MI NUEVO MÉTODO CENTRAL: Inicia la tarea que gestionará TODOS los minijuegos
   * de ruptura de bloques de forma centralizada y optimizada.
   */
  private void startBreakingMinigameManager() {
    breakingMinigameManagerTask = new BukkitRunnable() {
      @Override
      public void run() {
        if (currentState != GameState.RUNNING) {
          this.cancel();
          return;
        }
        // Uso un iterador para poder remover de forma segura mientras itero.
        Iterator<Map.Entry<UUID, BlockBreakingAttempt>> iterator = activeBreakingAttempts.entrySet().iterator();
        while (iterator.hasNext()) {
          BlockBreakingAttempt attempt = iterator.next().getValue();
          if (!attempt.tick()) { // El método tick() devuelve false si el intento ha terminado (éxito, fallo o cancelación).
            iterator.remove(); // Lo elimino del mapa de intentos activos.
          }
        }
      }
    }.runTaskTimer(plugin, 0L, 1L);
  }

  public void handleBlockBreak(BlockBreakEvent event) {
    Player player = event.getPlayer();
    if (isPlayerInGame(player)) {
      event.setCancelled(true);
      player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "¡Debes usar el clic derecho para romper bloques! Solo puedes romper: Ladrillos, Granito, Terracota"));
    }
  }

  public void handleRightClickBreak(PlayerInteractEvent event) {
    Player player = event.getPlayer();
    UUID playerUUID = player.getUniqueId();

    if (!isPlayerInGame(player) || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
      return;
    }

    event.setCancelled(true);

    if (activeBreakingAttempts.containsKey(playerUUID)) {
      activeBreakingAttempts.get(playerUUID).tryResolve();
      return;
    }

    long currentTime = System.currentTimeMillis();
    long lastBreak = playerBreakCooldowns.getOrDefault(playerUUID, 0L);
    if (currentTime - lastBreak < BREAK_COOLDOWN_MS) {
      return;
    }

    ItemStack itemInHand = player.getInventory().getItemInMainHand();
    if (!HAMMER_ITEM_ID.equals(OraxenItems.getIdByItem(itemInHand))) {
      player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "¡Usa el Martillo Demoledor!"));
      return;
    }

    Block block = event.getClickedBlock();
    if (block != null && TARGET_BLOCKS.contains(block.getType())) {
      if (brokenBlocks.containsKey(block.getLocation())) {
        return;
      }

      if (targettedBlocks.contains(block.getLocation())) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.YELLOW + "Alguien ya está rompiendo ese bloque."));
        return;
      }

      BlockBreakingAttempt attempt = new BlockBreakingAttempt(player, block);
      activeBreakingAttempts.put(playerUUID, attempt);
      targettedBlocks.add(block.getLocation());
    }
  }

  // --- 3. FINALIZACIÓN Y LIMPIEZA ---

  private void endGame(String reason) {
    if (currentState != GameState.RUNNING) return;
    currentState = GameState.INACTIVE;

    if (gameTimerTask != null) gameTimerTask.cancel();
    if (breakingMinigameManagerTask != null) breakingMinigameManagerTask.cancel();

    broadcastToGame(ChatColor.GOLD + "¡El juego ha terminado! Razón: " + reason + ". Calculando resultados...");

    activeBreakingAttempts.values().forEach(BlockBreakingAttempt::cancelCleanup);
    activeBreakingAttempts.clear();
    targettedBlocks.clear();

    List<Map.Entry<UUID, Integer>> sortedPlayers = gameScores.entrySet().stream()
            .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
            .collect(Collectors.toList());

    teleportPlayersToSafetyAndClearInventory();

    new BukkitRunnable() {
      @Override
      public void run() {
        awardPointsAndShowScoreboard(sortedPlayers);
        restoreBrokenBlocksInBatches();
        resetGameData();
      }
    }.runTaskLater(plugin, 60L); // 3 segundos de delay para que vean los títulos
  }

  /**
   * MI NUEVA LÓGICA: Este método se encarga de limpiar el inventario del jugador,
   * pero conservando el casco protector de Oraxen si lo tiene equipado.
   */
  private void clearPlayerInventory(Player player) {
    ItemStack specialHelmet = null;
    ItemStack helmetInSlot = player.getInventory().getHelmet();

    // Verifico si el casco que lleva el jugador es el que quiero proteger.
    if (helmetInSlot != null && PROTECTIVE_HELMET_ID.equals(OraxenItems.getIdByItem(helmetInSlot))) {
      specialHelmet = helmetInSlot.clone(); // Guardo una copia del casco.
    }

    player.getInventory().clear(); // Limpio todo el inventario.

    if (specialHelmet != null) {
      player.getInventory().setHelmet(specialHelmet); // Le devuelvo el casco.
    }
  }

  private void teleportPlayersToSafetyAndClearInventory() {
    new BukkitRunnable() {
      @Override
      public void run() {
        for (UUID uuid : gameScores.keySet()) {
          Player p = Bukkit.getPlayer(uuid);
          if (p != null) {
            p.teleport(SAFE_EXIT_LOCATION);
            // MI MEJORA: Uso mi nuevo método para una limpieza segura del inventario.
            clearPlayerInventory(p);
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1.0f, 1.0f);
          }
        }
      }
    }.runTask(plugin);
  }

  /**
   * MI MÉTODO COMPLETAMENTE REFACTORIZADO: Ahora maneja empates, anuncia los resultados
   * en el chat de forma clara para todos los participantes y actualiza los scoreboards.
   */
  private void awardPointsAndShowScoreboard(List<Map.Entry<UUID, Integer>> sortedPlayers) {
    // --- MI NUEVA LÓGICA DE EMPATES Y RANKING ---
    Map<UUID, Integer> playerRanks = new LinkedHashMap<>();
    int rank = 1;
    int lastScore = -1;
    for (Map.Entry<UUID, Integer> entry : sortedPlayers) {
      if (entry.getValue() != lastScore && lastScore != -1) {
        rank = playerRanks.size() + 1;
      }
      playerRanks.put(entry.getKey(), rank);
      lastScore = entry.getValue();
    }

    // --- MI NUEVA LÓGICA: Construyo el anuncio del Top 3 para el chat ---
    StringBuilder announcement = new StringBuilder();
    announcement.append("\n" + ChatColor.GOLD + "§m------------------[ " + ChatColor.AQUA + "Resultados de Concreto" + ChatColor.GOLD + " ]§m------------------\n \n");

    if (sortedPlayers.isEmpty()) {
      announcement.append(ChatColor.GRAY + "La partida terminó sin ganadores.\n ");
    } else {
      announcement.append(ChatColor.YELLOW + "Top de la partida:\n");
      int rankToShow = 1;
      for (Map.Entry<UUID, Integer> entry : sortedPlayers) {
        int currentRank = playerRanks.get(entry.getKey());
        if (currentRank > 3) break; // Solo muestro el top 3 de puestos.

        // Me aseguro de no repetir el puesto si hay un empate
        if (currentRank < rankToShow) continue;
        rankToShow = currentRank;

        // Agrupo a todos los jugadores que tienen el mismo rango.
        List<String> tiedNames = sortedPlayers.stream()
                .filter(e -> playerRanks.get(e.getKey()) == currentRank)
                .map(e -> Bukkit.getOfflinePlayer(e.getKey()).getName())
                .collect(Collectors.toList());

        announcement.append(String.format(" %s%d. %s%s %s- %s%d bloques\n",
                getRankColor(currentRank), currentRank, ChatColor.AQUA, String.join(", ", tiedNames), ChatColor.GRAY, ChatColor.YELLOW, entry.getValue()));
        rankToShow++;
      }
    }
    announcement.append("\n" + ChatColor.GOLD + "§m-----------------------------------------------------\n ");
    String finalAnnouncement = announcement.toString();

    // Doy puntos y envío mensajes/títulos individuales.
    List<PointsManager.PlayerScore> topBefore = pointsManager.getTopPlayers(3);

    for (Map.Entry<UUID, Integer> entry : sortedPlayers) {
      Player p = Bukkit.getPlayer(entry.getKey());
      if (p == null || !p.isOnline()) continue;

      int playerRank = playerRanks.get(entry.getKey());
      int pointsWon;
      String positionMessage;

      if (playerRank == 1) {
        pointsWon = 15;
        positionMessage = ChatColor.GOLD + "¡Ganaste! (1er Lugar)";
      } else if (playerRank == 2) {
        pointsWon = 7;
        positionMessage = ChatColor.GRAY + "¡Quedaste 2do!";
      } else if (playerRank == 3) {
        pointsWon = 2;
        positionMessage = ChatColor.DARK_RED + "¡Quedaste 3ro!";
      } else {
        pointsWon = 1;
        positionMessage = ChatColor.AQUA + "¡Buena partida!";
      }

      pointsManager.addPoints(p, pointsWon, "concrete_minigame", "Ranking final del minijuego");

      // MI MEJORA: El mensaje de título ahora es más específico.
      String finalMessage = String.format(ChatColor.YELLOW + "Puesto #%d con %d bloques. " + ChatColor.GREEN + "(+%d pts)",
              playerRank, entry.getValue(), pointsWon);
      p.sendTitle(positionMessage, finalMessage, 10, 80, 20);

      // Le envío el resumen final del chat a cada jugador.
      p.sendMessage(finalAnnouncement);
    }

    // Actualizo los scoreboards de forma inteligente.
    List<PointsManager.PlayerScore> topAfter = pointsManager.getTopPlayers(3);
    if (!topBefore.equals(topAfter)) {
      plugin.getLogger().info("El Top 3 ha cambiado. Actualizando scoreboard para todos los jugadores online...");
      for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
        int totalPoints = pointsManager.getTotalPoints(onlinePlayer);
        int topPosition = pointsManager.getPlayerRank(onlinePlayer);
        DeWaltScoreboardManager.showDefaultPage(onlinePlayer, topPosition, totalPoints, false, topAfter);
      }
    } else {
      for (Map.Entry<UUID, Integer> entry : sortedPlayers) {
        Player p = Bukkit.getPlayer(entry.getKey());
        if (p != null && p.isOnline()) {
          int totalPoints = pointsManager.getTotalPoints(p);
          int topPosition = pointsManager.getPlayerRank(p);
          DeWaltScoreboardManager.showDefaultPage(p, topPosition, totalPoints, false, topAfter);
        }
      }
    }
  }


  /**
   * MI NUEVO MÉTODO OPTIMIZADO: Restaura los bloques en lotes para evitar picos de lag.
   */
  private void restoreBrokenBlocksInBatches() {
    if (brokenBlocks.isEmpty()) return;

    // Uso una LinkedList como cola (Queue) para una extracción eficiente (O(1)).
    final Queue<Map.Entry<Location, BlockData>> blocksToRestore = new LinkedList<>(brokenBlocks.entrySet());
    brokenBlocks.clear(); // Limpio el mapa original.

    plugin.getLogger().info("Iniciando restauración en lotes de " + blocksToRestore.size() + " bloques...");

    new BukkitRunnable() {
      @Override
      public void run() {
        if (blocksToRestore.isEmpty()) {
          plugin.getLogger().info("Restauración de bloques completada.");
          this.cancel();
          return;
        }

        for (int i = 0; i < BLOCKS_TO_RESTORE_PER_TICK && !blocksToRestore.isEmpty(); i++) {
          Map.Entry<Location, BlockData> entry = blocksToRestore.poll();
          if (entry != null) {
            entry.getKey().getBlock().setBlockData(entry.getValue(), false);
          }
        }
      }
    }.runTaskTimer(plugin, 0L, 1L); // Ejecuto cada tick para una restauración rápida pero suave.
  }

  private void resetGame() {
    if (lobbyCountdownTask != null) lobbyCountdownTask.cancel();
    if (gameTimerTask != null) gameTimerTask.cancel();
    if (breakingMinigameManagerTask != null) breakingMinigameManagerTask.cancel();
    resetGameData();
  }

  private void resetGameData() {
    lobbyPlayers.clear();
    gameScores.clear();
    activeBreakingAttempts.clear();
    targettedBlocks.clear();
    playerBreakCooldowns.clear();
    currentState = GameState.INACTIVE;
  }

  // MI NUEVA LÓGICA: Un método específico para sacar a un jugador a la fuerza.
  public void forceLeaveGame(Player player, String reason) {
    UUID uuid = player.getUniqueId();
    boolean wasInLobby = lobbyPlayers.remove(uuid);
    if (wasInLobby) {
      player.sendMessage(ChatColor.RED + "Has salido de la cola para Concreto porque " + reason + ".");
      broadcastToLobby(ChatColor.AQUA + player.getName() + " ha salido de la cola.", null);
    }

    if (gameScores.containsKey(uuid)) {
      gameScores.remove(uuid);
      BlockBreakingAttempt attempt = activeBreakingAttempts.remove(uuid);
      if (attempt != null) {
        attempt.cancelCleanup();
      }
      player.sendMessage(ChatColor.RED + "Has sido retirado de la partida porque " + reason + ".");
      clearPlayerInventory(player);
      // No lo teletransporto aquí, ya que el comando que usó lo hará.
      broadcastToGame(ChatColor.YELLOW + player.getName() + " ha abandonado la partida.", null);
    }
  }


  public void handlePlayerQuit(Player player) {
    UUID uuid = player.getUniqueId();
    if (lobbyPlayers.remove(uuid)) {
      broadcastToLobby(ChatColor.YELLOW + player.getName() + " se ha desconectado.", null);
    }

    BlockBreakingAttempt attempt = activeBreakingAttempts.remove(uuid);
    if (attempt != null) {
      attempt.cancelCleanup();
    }
    if (gameScores.remove(uuid) != null) {
      broadcastToGame(ChatColor.YELLOW + player.getName() + " se ha desconectado.", null);
    }
  }

  // --- 4. MÉTODOS DE UTILIDAD Y NOTIFICACIÓN ---

  public boolean isPlayerInGame(Player player) {
    return gameScores.containsKey(player.getUniqueId());
  }

  // MI NUEVO MÉTODO DE UTILIDAD: para verificar si está en el lobby O en el juego.
  public boolean isPlayerInGameOrLobby(Player player) {
    UUID uuid = player.getUniqueId();
    return lobbyPlayers.contains(uuid) || gameScores.containsKey(uuid);
  }


  private void updateActionBarForAll() {
    // Lo ejecuto directamente en el hilo principal desde el gameTimer.
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

  private void broadcastToLobby(String message, UUID excludedPlayer) {
    lobbyPlayers.stream()
            .filter(uuid -> !uuid.equals(excludedPlayer))
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .forEach(p -> p.sendMessage(message));
  }

  private void broadcastToGame(String message) {
    broadcastToGame(message, null);
  }

  private void broadcastToGame(String message, UUID excludedPlayer) {
    gameScores.keySet().stream()
            .filter(uuid -> !uuid.equals(excludedPlayer))
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

  // MI MÉTODO DE UTILIDAD: para darle color a los puestos del ranking.
  private ChatColor getRankColor(int rank) {
    switch (rank) {
      case 1:
        return ChatColor.GOLD;
      case 2:
        return ChatColor.GRAY;
      case 3:
        return ChatColor.DARK_RED;
      default:
        return ChatColor.YELLOW;
    }
  }

  // --- 5. MI CLASE INTERNA REFACTORIZADA PARA EL MINIJUEGO DE RUPTURA ---
  // Ya no contiene su propia tarea Bukkit. Es solo un objeto de datos que
  // el manager procesa.
  private class BlockBreakingAttempt {
    private final Player player;
    private final Block block;
    private final UUID playerUUID;
    private int progress = 0;
    // MI CORRECCIÓN: Añado una bandera para asegurar que el intento solo se resuelva una vez.
    private boolean resolved = false;

    BlockBreakingAttempt(Player player, Block block) {
      this.player = player;
      this.block = block;
      this.playerUUID = player.getUniqueId();
    }

    /**
     * Procesado cada tick por el manager central.
     *
     * @return `true` si el intento debe continuar, `false` si ha terminado.
     */
    boolean tick() {
      // MI CORRECCIÓN: Si el intento ya se resolvió, le digo al manager que lo elimine.
      if (resolved) {
        return false;
      }

      if (progress > BREAK_MINIGAME_DURATION_TICKS) {
        fail("¡Demasiado lento!");
        return false; // fail() ahora marca como resuelto y esto elimina la tarea del manager.
      }

      displayProgressBar();
      progress++;
      return true;
    }

    void tryResolve() {
      // MI CORRECCIÓN: Si ya se resolvió, ignoro este y los siguientes clics.
      if (resolved) {
        return;
      }

      if (progress >= BREAK_SUCCESS_START_TICK && progress <= BREAK_SUCCESS_END_TICK) {
        succeed();
      } else {
        fail("¡Mal timing!");
      }
    }

    private void succeed() {
      // MI CORRECCIÓN: Una doble comprobación por seguridad.
      if (resolved) return;
      this.resolved = true; // Marco como resuelto INMEDIATAMENTE para evitar duplicados.

      this.cancelCleanup();
      playerBreakCooldowns.put(playerUUID, System.currentTimeMillis());
      brokenBlocks.putIfAbsent(block.getLocation(), block.getBlockData());
      block.setType(Material.AIR, true);
      player.playSound(player.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.2f);
      player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + "¡Perfecto!"));
      gameScores.computeIfPresent(playerUUID, (uuid, score) -> score + 1);
    }

    private void fail(String reason) {
      // MI CORRECCIÓN: También aplico la misma lógica de bloqueo aquí.
      if (resolved) return;
      this.resolved = true;

      this.cancelCleanup();
      player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f);
      player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + reason));
    }

    // Este método solo limpia los datos asociados al intento.
    void cancelCleanup() {
      // Ya no cancelo una tarea aquí.
      // El manager se encarga de remover el intento del mapa principal.
      targettedBlocks.remove(block.getLocation());
    }

    private void displayProgressBar() {
      if (player == null || !player.isOnline()) return;

      StringBuilder bar = new StringBuilder("§7[");
      int total_chars = 20;
      double percentage = (double) progress / BREAK_MINIGAME_DURATION_TICKS;
      int filled_chars = (int) (total_chars * percentage);

      double success_start_percent = (double) BREAK_SUCCESS_START_TICK / BREAK_MINIGAME_DURATION_TICKS;
      double success_end_percent = (double) BREAK_SUCCESS_END_TICK / BREAK_MINIGAME_DURATION_TICKS;

      for (int i = 0; i < total_chars; i++) {
        double current_pos_percent = (double) i / total_chars;
        if (i <= filled_chars) {
          // El color de la barra cambia a verde cuando el *progreso* entra en la ventana de éxito.
          if (percentage >= success_start_percent && percentage <= success_end_percent) {
            bar.append("§a|");
          } else {
            bar.append("§c|");
          }
        } else {
          // Pinto la "ventana de éxito" en la parte vacía de la barra para que el jugador sepa a dónde apuntar.
          if (current_pos_percent >= success_start_percent && current_pos_percent <= success_end_percent) {
            bar.append("§2-"); // Verde oscuro para la zona de éxito
          } else {
            bar.append("§8-");
          }
        }
      }
      bar.append("§7]");
      player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(bar.toString()));
    }
  }
}