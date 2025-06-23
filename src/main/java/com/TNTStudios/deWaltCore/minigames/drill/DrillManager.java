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
 *
 * --- OPTIMIZACIÓN PARA ALTO RENDIMIENTO (200 JUGADORES) ---
 * 1.  Se eliminó el uso de `world.getNearbyEntities`, que es muy lento con muchos jugadores. Se reemplazó por un Set de localizaciones cacheadas.
 * 2.  Se optimizó `isPaintingManaged` para que sea una operación O(1) usando un Set global de pinturas, en lugar de un bucle anidado.
 * 3.  La difusión de mensajes y sonidos se realiza de forma asíncrona para reducir la carga sobre el hilo principal del servidor.
 * 4.  Se utilizan estructuras de datos de `java.util.concurrent` para máxima seguridad y rendimiento en un entorno con muchos jugadores.
 */
public class DrillManager {

    private final DeWaltCore plugin;
    private final PointsManager pointsManager;

    // --- MI NUEVA ESTRUCTURA DE JUEGO ---
    private enum GameState { INACTIVE, LOBBY, RUNNING }
    private volatile GameState currentState = GameState.INACTIVE;

    private final Set<UUID> lobbyPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerGameState> gamePlayers = new ConcurrentHashMap<>();

    // --- OPTIMIZACIÓN: ESTRUCTURAS DE DATOS PARA BÚSQUEDAS RÁPIDAS ---
    // Contiene TODAS las pinturas activas del minijuego para una comprobación O(1) instantánea.
    private final Set<Entity> allManagedPaintings = ConcurrentHashMap.newKeySet();
    // Contiene las localizaciones de las pinturas para evitar `getNearbyEntities`.
    private final Set<Location> allPaintingLocations = ConcurrentHashMap.newKeySet();


    private BukkitTask lobbyCountdownTask;
    private BukkitTask gameTimerTask;
    private int lobbyTimeLeft;
    private int gameTimeLeft;
    private int paintingsLeft;

    // --- CONSTANTES DE CONFIGURACIÓN DEL JUEGO ---
    // AUMENTO EL LÍMITE PARA LA PRUEBA DE ESTRÉS
    private static final int MAX_PLAYERS = 20;
    private static final int LOBBY_DURATION_SECONDS = 60;
    private static final int GAME_DURATION_SECONDS = 120;
    private static final int TOTAL_PAINTINGS = 50;
    private static final double MIN_DISTANCE_SQUARED = 3.5 * 3.5; // Usamos la distancia al cuadrado para evitar calcular raíces cuadradas (más rápido)
    private static final Random random = new Random();

    private static final List<Art> ALLOWED_ART = Arrays.asList(
            Art.KEBAB, Art.AZTEC, Art.ALBAN, Art.AZTEC2, Art.BOMB, Art.PLANT,
            Art.WASTELAND, Art.WANDERER, Art.GRAHAM, Art.MATCH, Art.BUST,
            Art.STAGE, Art.VOID, Art.SKULL_AND_ROSES
    );

    private static class PlayerGameState {
        int score = 0;
        boolean hasPainting = false;
        // Ya no necesitamos la lista de pinturas por jugador, se gestiona globalmente
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
        // `add` en un Set es O(1), muy rápido.
        if (!lobbyPlayers.add(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Ya estás en la sala de espera.");
            return;
        }

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
                    return; // La tarea se cancela en resetGame
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
                if (gamePlayers.isEmpty() || gameTimeLeft <= 0) {
                    endGame(gameTimeLeft <= 0 ? "Se acabó el tiempo" : "Todos los jugadores salieron");
                    return; // La tarea se cancela en endGame
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

        // --- OPTIMIZACIÓN: Comprobación de distancia ultrarrápida ---
        // En lugar de `getNearbyEntities`, iteramos nuestro Set cacheado que es mucho más pequeño.
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

        // Éxito
        state.score++;
        state.hasPainting = false;
        paintingsLeft--;

        // OPTIMIZACIÓN: Añadimos la pintura y su localización a nuestros registros rápidos.
        allManagedPaintings.add(painting);
        allPaintingLocations.add(painting.getLocation());

        removeDrillItem(player);
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 1.5f);
        updateActionBarForAll();

        if (paintingsLeft <= 0) {
            endGame("Se agotaron las pinturas");
        }
    }

    // --- 3. FINALIZACIÓN Y LIMPIEZA ---

    private void endGame(String reason) {
        if (currentState != GameState.RUNNING) return;
        currentState = GameState.INACTIVE;
        if (gameTimerTask != null) gameTimerTask.cancel();

        broadcastToGame(ChatColor.GOLD + "¡El juego ha terminado! Razón: " + reason + ". Calculando resultados...");

        List<Map.Entry<UUID, PlayerGameState>> sortedPlayers = gamePlayers.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<UUID, PlayerGameState> entry) -> entry.getValue().score).reversed())
                .collect(Collectors.toList());

        // Reparto de puntos y mensajes
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < sortedPlayers.size(); i++) {
                    Map.Entry<UUID, PlayerGameState> entry = sortedPlayers.get(i);
                    Player p = Bukkit.getPlayer(entry.getKey());
                    if (p == null) continue;

                    int pointsWon = (i == 0) ? 20 : (i == 1) ? 10 : (i == 2) ? 5 : 1;
                    String positionMessage = (i == 0) ? ChatColor.GOLD + "¡Ganaste! (1er Lugar)"
                            : (i == 1) ? ChatColor.GRAY + "¡Quedaste 2do!"
                            : (i == 2) ? ChatColor.DARK_RED + "¡Quedaste 3ro!"
                            : ChatColor.AQUA + "¡Buena participación!";

                    pointsManager.addPoints(p, pointsWon, "drill_competitive", "Ranking final del minijuego");
                    String finalMessage = String.format(ChatColor.YELLOW + "Colocaste %d pinturas. " + ChatColor.GREEN + "(+%d pts)", entry.getValue().score, pointsWon);
                    p.sendTitle(positionMessage, finalMessage, 10, 80, 20);

                    // Actualizamos el scoreboard en el hilo principal
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
        }.runTaskAsynchronously(plugin); // La lógica de premios puede ser asíncrona, pero la actualización del scoreboard debe volver al hilo principal.

        // Limpieza final tras un breve retardo
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanUpGameData();
                resetGame();
            }
        }.runTaskLater(plugin, 120L); // 6 segundos de retardo
    }

    private void cleanUpPlayer(Player player, boolean wasDisconnected) {
        if (gamePlayers.remove(player.getUniqueId()) != null && wasDisconnected) {
            broadcastToGame(ChatColor.YELLOW + player.getName() + " ha abandonado la partida.");
        }
        removeDrillItem(player);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
    }

    // Limpia todas las entidades del juego
    private void cleanUpGameData() {
        new BukkitRunnable() {
            @Override
            public void run() {
                allManagedPaintings.forEach(Entity::remove);
                allManagedPaintings.clear();
                allPaintingLocations.clear();
            }
        }.runTask(plugin); // La eliminación de entidades DEBE ocurrir en el hilo principal.
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
        if (lobbyPlayers.remove(player.getUniqueId())) {
            broadcastToLobby(ChatColor.YELLOW + player.getName() + " ha salido del lobby.", null);
        } else if (isPlayerInGame(player)) {
            cleanUpPlayer(player, true);
        }
    }

    // --- 4. MÉTODOS DE UTILIDAD OPTIMIZADOS ---

    public boolean isPlayerInGame(Player player) {
        return gamePlayers.containsKey(player.getUniqueId());
    }

    // --- OPTIMIZACIÓN: Esta comprobación es ahora O(1), increíblemente rápida.
    public boolean isPaintingManaged(Entity entity) {
        return allManagedPaintings.contains(entity);
    }

    private void updateActionBarForAll() {
        String message = String.format("§eTiempo restante: §f%ds §8| §ePinturas restantes: §f%d/%d", gameTimeLeft, paintingsLeft, TOTAL_PAINTINGS);
        broadcastToGame(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    // --- MI REFINAMIENTO (100% SEGURO) ---
    private void broadcastToLobby(String message, UUID excludedPlayer) {
        // Hago una copia de la lista de UUIDs para trabajar sobre ella de forma segura.
        List<UUID> lobbyPlayersCopy = new ArrayList<>(lobbyPlayers);

        new BukkitRunnable() {
            @Override
            public void run() {
                // Este bucle se ejecuta en el hilo principal, por lo que es totalmente seguro.
                for (UUID uuid : lobbyPlayersCopy) {
                    if (!uuid.equals(excludedPlayer)) {
                        Player p = Bukkit.getPlayer(uuid);
                        // Compruebo que el jugador sigue online antes de enviarle nada.
                        if (p != null && p.isOnline()) {
                            p.sendMessage(message);
                        }
                    }
                }
            }
        }.runTask(plugin); // La clave es ejecutar la tarea en el hilo principal con runTask().
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

    public boolean isDrillItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return "taladro".equals(OraxenItems.getIdByItem(item));
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
        // Este método necesita ejecutarse en el hilo principal si hay dudas sobre la seguridad
        // de la API de inventario, pero `remove` suele ser seguro. Para máxima precaución:
        new BukkitRunnable() {
            @Override
            public void run() {
                ItemBuilder itemBuilder = OraxenItems.getItemById("taladro");
                if (itemBuilder != null) {
                    player.getInventory().remove(itemBuilder.build());
                }
            }
        }.runTask(plugin);
    }
}