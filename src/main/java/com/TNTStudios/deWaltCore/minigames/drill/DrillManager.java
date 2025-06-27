// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/drill/DrillManager.java
package com.TNTStudios.deWaltCore.minigames.drill;

import com.TNTStudios.deWaltCore.DeWaltCore;
import com.TNTStudios.deWaltCore.points.PointsManager;
import com.TNTStudios.deWaltCore.scoreboard.DeWaltScoreboardManager;
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mi manager para el minijuego del Taladro.
 * REESTRUCTURADO: Ahora soporta un lobby de espera, recursos compartidos (pinturas)
 * y un sistema de puntuación por ranking para hacerlo más competitivo.
 * MEJORADO: Añadí validaciones para la colocación y mensajes más claros.
 * CORREGIDO: Ahora el scoreboard se actualiza al final del juego y las pinturas solo se pueden colocar en bloques sólidos.
 * ACTUALIZADO: Añadí teleports, limpieza de ítems, anuncio de ganadores, manejo de empates y salida por comando /spawn.
 *
 * --- OPTIMIZACIÓN PARA ALTO RENDIMIENTO (200 JUGADORES) ---
 * 1. Se eliminó el uso de `world.getNearbyEntities`, que es muy lento con muchos jugadores. Se reemplazó por un Set de localizaciones cacheadas.
 * 2. Se optimizó `isPaintingManaged` para que sea una operación O(1) usando un Set global de pinturas, en lugar de un bucle anidado.
 * 3. La difusión de mensajes y sonidos se realiza de forma asíncrona para reducir la carga sobre el hilo principal del servidor.
 * 4. Se utilizan estructuras de datos de `java.util.concurrent` para máxima seguridad y rendimiento en un entorno con muchos jugadores.
 */
public class DrillManager {

    private final DeWaltCore plugin;
    private final PointsManager pointsManager;

    private enum GameState { INACTIVE, LOBBY, RUNNING }
    private volatile GameState currentState = GameState.INACTIVE;

    private final Set<UUID> lobbyPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerGameState> gamePlayers = new ConcurrentHashMap<>();

    private final Set<Entity> allManagedPaintings = ConcurrentHashMap.newKeySet();
    private final Set<Location> allPaintingLocations = ConcurrentHashMap.newKeySet();

    private BukkitTask lobbyCountdownTask;
    private BukkitTask gameTimerTask;
    private int lobbyTimeLeft;
    private int gameTimeLeft;
    private int paintingsLeft;

    // --- MI NUEVA CONFIGURACIÓN DE UBICACIONES ---
    private static final Location LOBBY_LOCATION = new Location(Bukkit.getWorld("DeWALTTaladro"), -24.37, 4.00, -25.35, 90, 0);
    private static final Location GAME_START_LOCATION = new Location(Bukkit.getWorld("DeWALTTaladro"), -24.37, 4.00, -25.35, 90, 0);
    private static final Location END_GAME_LOCATION = new Location(Bukkit.getWorld("DEWALT LOBBY"), -2.13, 78.00, 0.44, 90, 0);


    // --- CONSTANTES DE CONFIGURACIÓN DEL JUEGO ---
    private static final int MAX_PLAYERS = 20;
    private static final int LOBBY_DURATION_SECONDS = 60;
    private static final int GAME_DURATION_SECONDS = 120;
    private static final int TOTAL_PAINTINGS = 80;
    private static final double MIN_DISTANCE_SQUARED = 3.5 * 3.5;
    private static final Random random = new Random();

    private static final List<Art> ALLOWED_ART = Arrays.asList(
            Art.KEBAB, Art.AZTEC, Art.ALBAN, Art.AZTEC2, Art.BOMB, Art.PLANT,
            Art.WASTELAND, Art.WANDERER, Art.GRAHAM, Art.MATCH, Art.BUST,
            Art.STAGE, Art.VOID, Art.SKULL_AND_ROSES
    );

    private static class PlayerGameState {
        int score = 0;
        boolean hasPainting = false;
    }

    public DrillManager(DeWaltCore plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
    }

    // --- 1. LÓGICA DEL LOBBY ---

    public void addPlayerToLobby(Player player) {
        if (currentState == GameState.RUNNING) {
            player.sendMessage(ChatColor.RED + "¡El minijuego del Taladro ya ha comenzado! Espera a que termine.");
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

        player.teleport(LOBBY_LOCATION);
        player.sendMessage(ChatColor.AQUA + "¡Bienvenido al lobby del minijuego del Taladro!");
        player.sendMessage(ChatColor.YELLOW + "Objetivo: Consigue pinturas en la 'Mesa de Trabajo' y colócalas en las paredes. ¡Quien coloque más pinturas cuando se acabe el tiempo, gana!");

        broadcastToLobby(ChatColor.AQUA + player.getName() + " ha entrado al lobby. (" + lobbyPlayers.size() + "/" + MAX_PLAYERS + ")", player.getUniqueId());

        if (currentState == GameState.INACTIVE && lobbyPlayers.size() > 0) {
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
                    broadcastToLobby(ChatColor.RED + "Todos los jugadores han salido. El inicio se ha cancelado.", null);
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
        paintingsLeft = TOTAL_PAINTINGS;

        for (UUID uuid : lobbyPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                gamePlayers.put(uuid, new PlayerGameState());
                p.teleport(GAME_START_LOCATION);
                p.sendTitle(ChatColor.GREEN + "¡A JUGAR!", ChatColor.WHITE + "¡Consigue pinturas de la Mesa de Trabajo!", 10, 60, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            }
        }
        lobbyPlayers.clear();

        broadcastToGame(ChatColor.GOLD + "¡El juego ha comenzado! ¡Corran a la Mesa de Trabajo por la primera pintura!");
        startGameTimer();
    }

    private void startGameTimer() {
        gameTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (gamePlayers.isEmpty() || gameTimeLeft <= 0 || paintingsLeft <= 0) {
                    String reason = "Se acabó el tiempo";
                    if (gamePlayers.isEmpty()) reason = "Todos los jugadores salieron";
                    if (paintingsLeft <= 0) reason = "Se agotaron las pinturas";
                    endGame(reason);
                    return;
                }

                gameTimeLeft--;
                updateActionBarForAll();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void handlePaintingPickup(Player player) {
        if (!isPlayerInGame(player)) return;
        PlayerGameState state = gamePlayers.get(player.getUniqueId());
        if (state == null) return;

        if (state.hasPainting) {
            player.sendMessage(ChatColor.RED + "¡Ya tienes una pintura! Ve a colocarla primero.");
            return;
        }

        if (paintingsLeft <= 0) {
            player.sendMessage(ChatColor.RED + "¡Se han agotado todas las pinturas!");
            return;
        }

        state.hasPainting = true;
        giveDrillItem(player);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        player.sendMessage(ChatColor.GREEN + "¡Has recogido una pintura! Ahora busca un buen lugar en una pared.");
    }

    public void handlePaintingPlace(Player player, BlockFace blockFace) {
        if (!isPlayerInGame(player)) return;
        PlayerGameState state = gamePlayers.get(player.getUniqueId());
        if (state == null || !state.hasPainting) {
            player.sendMessage(ChatColor.RED + "¡Primero debes recoger una pintura de la Mesa de Trabajo!");
            return;
        }

        if (blockFace == BlockFace.UP || blockFace == BlockFace.DOWN) {
            player.sendMessage(ChatColor.RED + "¡No puedes colocar pinturas en el suelo o en el techo!");
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.2f);
            return;
        }

        Block targetBlock = player.getTargetBlock(null, 5);
        if (targetBlock == null || !targetBlock.getType().isOccluding()) {
            player.sendMessage(ChatColor.RED + "¡Solo puedes colocar pinturas en bloques de pared sólidos y completos!");
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.2f);
            return;
        }

        Location loc = targetBlock.getRelative(blockFace).getLocation();

        for (Location placedLoc : allPaintingLocations) {
            if (placedLoc.getWorld().equals(loc.getWorld()) && placedLoc.distanceSquared(loc) < MIN_DISTANCE_SQUARED) {
                player.sendMessage(ChatColor.RED + "¡Estás demasiado cerca de otra pintura! Busca otro lugar.");
                player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.2f);
                return;
            }
        }

        Art randomArt = ALLOWED_ART.get(random.nextInt(ALLOWED_ART.size()));
        World world = loc.getWorld();
        if (world == null) return;

        Painting painting = world.spawn(loc, Painting.class, p -> {
            p.setFacingDirection(blockFace, true);
            p.setArt(randomArt, true);
        });

        if (!painting.isValid() || painting.isDead()) {
            player.sendTitle(" ", ChatColor.RED + "¡No hay suficiente espacio aquí!", 0, 40, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.2f);
            return;
        }

        state.score++;
        state.hasPainting = false;
        paintingsLeft--;

        allManagedPaintings.add(painting);
        allPaintingLocations.add(painting.getLocation());

        // --- MODIFICADO --- Se usa el nuevo método para limpiar solo el taladro.
        removeDrillItem(player);
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 1.5f);
        updateActionBarForAll();

        if (paintingsLeft <= 0) {
            broadcastToGame(ChatColor.GOLD + "¡Se han colocado todas las pinturas! El juego terminará en breve...");
        }
    }

    // --- 3. FINALIZACIÓN Y LIMPIEZA ---

    private void endGame(String reason) {
        if (currentState != GameState.RUNNING) return;
        currentState = GameState.INACTIVE;
        if (gameTimerTask != null) gameTimerTask.cancel();

        broadcastToGame(ChatColor.GOLD + "¡El juego ha terminado! Razón: " + reason + ". Calculando resultados...");

        Set<UUID> finalPlayers = new HashSet<>(gamePlayers.keySet());

        List<Map.Entry<UUID, PlayerGameState>> sortedPlayers = gamePlayers.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<UUID, PlayerGameState> entry) -> entry.getValue().score).reversed())
                .collect(Collectors.toList());

        announceResultsAndGivePrizes(sortedPlayers);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : finalPlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        p.teleport(END_GAME_LOCATION);
                        // --- MODIFICADO --- Ahora usamos el método que limpia todo el inventario excepto el casco.
                        limpiarInventario(p);
                    }
                }
                cleanUpGameData();
                resetGame();
            }
        }.runTaskLater(plugin, 140L); // 7 segundos de retardo
    }

    private void announceResultsAndGivePrizes(List<Map.Entry<UUID, PlayerGameState>> sortedPlayers) {
        if (sortedPlayers.isEmpty()) return;

        StringBuilder top3Message = new StringBuilder();
        top3Message.append(ChatColor.GOLD).append("--- Resultados Finales (Taladro) ---\n");
        int lastScore = -1;
        int currentRank = 0;
        List<String> topPlayerNames = new ArrayList<>();

        for (int i = 0; i < sortedPlayers.size(); i++) {
            Map.Entry<UUID, PlayerGameState> entry = sortedPlayers.get(i);
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;

            if (entry.getValue().score != lastScore) {
                currentRank = i + 1;
                lastScore = entry.getValue().score;
            }

            if (currentRank <= 3) {
                topPlayerNames.add(String.format(" %s%d. %s%s %s- %d pinturas",
                        ChatColor.GREEN, currentRank, ChatColor.AQUA, p.getName(), ChatColor.GRAY, entry.getValue().score));
            }
        }

        if (topPlayerNames.isEmpty()) {
            top3Message.append(ChatColor.GRAY).append("No hubo ganadores claros esta ronda.\n");
        } else {
            top3Message.append(String.join("\n", topPlayerNames)).append("\n");
        }
        top3Message.append(ChatColor.GOLD).append("------------------------------------");

        new BukkitRunnable() {
            @Override
            public void run() {
                int lastScore = -1;
                int currentRank = 0;

                for (int i = 0; i < sortedPlayers.size(); i++) {
                    Map.Entry<UUID, PlayerGameState> entry = sortedPlayers.get(i);
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p == null || !p.isOnline()) continue;

                    if (entry.getValue().score != lastScore) {
                        currentRank = i + 1;
                        lastScore = entry.getValue().score;
                    }

                    int pointsWon = (currentRank == 1) ? 20 : (currentRank == 2) ? 10 : (currentRank == 3) ? 5 : 1;
                    String positionMessage = (currentRank == 1) ? ChatColor.GOLD + "¡Ganaste! (1er Lugar)"
                            : (currentRank == 2) ? ChatColor.GRAY + "¡Quedaste 2do!"
                            : (currentRank == 3) ? ChatColor.DARK_RED + "¡Quedaste 3ro!"
                            : ChatColor.AQUA + "¡Buena participación!";

                    String personalMessage = String.format("\n%s¡Quedaste en el puesto #%d con %d pinturas! %s(+%d pts)",
                            ChatColor.YELLOW, currentRank, entry.getValue().score, ChatColor.GREEN, pointsWon);

                    p.sendMessage(top3Message.toString() + personalMessage);

                    pointsManager.addPoints(p, pointsWon, "drill_competitive", "Ranking final del minijuego");
                    p.sendTitle(positionMessage, String.format(ChatColor.YELLOW + "Colocaste %d pinturas.", entry.getValue().score), 10, 80, 20);

                    updatePlayerScoreboard(p);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void updatePlayerScoreboard(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    int totalPoints = pointsManager.getTotalPoints(player);
                    int topPosition = pointsManager.getPlayerRank(player);
                    List<PointsManager.PlayerScore> topPlayers = pointsManager.getTopPlayers(3);
                    DeWaltScoreboardManager.showDefaultPage(player, topPosition, totalPoints, false, topPlayers);
                }
            }
        }.runTask(plugin);
    }

    public void removePlayerFromGame(Player player, boolean wasDisconnected) {
        boolean wasInLobby = lobbyPlayers.remove(player.getUniqueId());
        boolean wasInGame = gamePlayers.remove(player.getUniqueId()) != null;

        if (wasInLobby) {
            broadcastToLobby(ChatColor.YELLOW + player.getName() + " ha salido del lobby.", null);
        } else if (wasInGame && wasDisconnected) {
            broadcastToGame(ChatColor.YELLOW + player.getName() + " ha abandonado la partida.");
        }

        if (wasInLobby || wasInGame) {
            // --- MODIFICADO --- Se usa el método general de limpieza de inventario.
            limpiarInventario(player);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
            player.teleport(END_GAME_LOCATION);
        }
    }


    private void cleanUpGameData() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Esta lógica es correcta, se ejecuta en el thread principal y elimina las entidades.
                allManagedPaintings.forEach(Entity::remove);
                allManagedPaintings.clear();
                allPaintingLocations.clear();
            }
        }.runTask(plugin);
    }

    private void resetGame() {
        if (lobbyCountdownTask != null) lobbyCountdownTask.cancel();
        if (gameTimerTask != null) gameTimerTask.cancel();
        lobbyCountdownTask = null;
        gameTimerTask = null;

        gamePlayers.clear();
        lobbyPlayers.clear();
        currentState = GameState.INACTIVE;
    }

    public void handlePlayerQuit(Player player) {
        removePlayerFromGame(player, true);
    }

    public void handlePlayerCommand(Player player) {
        player.sendMessage(ChatColor.RED + "Has sido retirado del minijuego por usar un comando.");
        removePlayerFromGame(player, false);
    }

    // --- 4. MÉTODOS DE UTILIDAD OPTIMIZADOS ---

    public boolean isPlayerInLobby(Player player) {
        return lobbyPlayers.contains(player.getUniqueId());
    }

    public boolean isPlayerInGame(Player player) {
        return gamePlayers.containsKey(player.getUniqueId());
    }

    public boolean isPaintingManaged(Entity entity) {
        return allManagedPaintings.contains(entity);
    }

    private void updateActionBarForAll() {
        String message = String.format("§eTiempo restante: §f%ds §8| §ePinturas restantes: §f%d/%d", gameTimeLeft, paintingsLeft, TOTAL_PAINTINGS);
        broadcastToGame(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    // --- DIFUSIÓN ASÍNCRONA DE MENSAJES Y SONIDOS ---

    private void broadcastToLobby(String message, UUID excludedPlayer) {
        List<UUID> lobbyPlayersCopy = new ArrayList<>(lobbyPlayers);
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : lobbyPlayersCopy) {
                    if (!uuid.equals(excludedPlayer)) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && p.isOnline()) {
                            p.sendMessage(message);
                        }
                    }
                }
            }
        }.runTask(plugin);
    }

    private void broadcastToGame(String message) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : gamePlayers.keySet()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) p.sendMessage(message);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void broadcastToGame(ChatMessageType type, TextComponent component) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : gamePlayers.keySet()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) p.spigot().sendMessage(type, component);
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
                    if(p != null) p.playSound(p.getLocation(), sound, 1.0f, pitch);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    // --- MANEJO DE ÍTEMS ---

    public boolean isOraxenItem(ItemStack item, String id) {
        if (item == null || item.getType() == Material.AIR) return false;
        return id.equals(OraxenItems.getIdByItem(item));
    }

    private void giveDrillItem(Player player) {
        ItemBuilder itemBuilder = OraxenItems.getItemById("taladro");
        if (itemBuilder != null) {
            player.getInventory().addItem(itemBuilder.build());
        } else {
            plugin.getLogger().warning("Se intentó dar el ítem 'taladro' pero no se encontró en Oraxen.");
        }
    }

    private void removeDrillItem(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.getInventory().remove(OraxenItems.getItemById("taladro").build());
            }
        }.runTask(plugin);
    }

    /**
     * --- NUEVO MÉTODO ---
     * Limpia el inventario completo de un jugador, incluyendo la armadura,
     * excepto por el ítem de Oraxen con el ID 'casco'.
     * Este método es seguro de usar en cualquier momento para limpiar a un jugador.
     *
     * @param player El jugador cuyo inventario será limpiado.
     */
    private void limpiarInventario(Player player) {
        new BukkitRunnable() {
            @Override
            public void run() {
                PlayerInventory inventory = player.getInventory();
                List<ItemStack> itemsToRemove = new ArrayList<>();

                // Revisar inventario principal
                for (ItemStack item : inventory.getContents()) {
                    if (item != null && !isOraxenItem(item, "casco")) {
                        itemsToRemove.add(item);
                    }
                }
                // Revisar armadura
                for (ItemStack item : inventory.getArmorContents()) {
                    if (item != null && !isOraxenItem(item, "casco")) {
                        itemsToRemove.add(item);
                    }
                }

                // Eliminar los ítems recolectados
                for(ItemStack item : itemsToRemove){
                    inventory.remove(item);
                }
            }
        }.runTask(plugin);
    }
}