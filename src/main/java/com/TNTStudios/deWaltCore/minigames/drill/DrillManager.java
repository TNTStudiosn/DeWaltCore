// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/drill/DrillManager.java
package com.TNTStudios.deWaltCore.minigames.drill;

import com.TNTStudios.deWaltCore.DeWaltCore;
import com.TNTStudios.deWaltCore.points.PointsManager;
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.items.ItemBuilder;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
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
 */
public class DrillManager {

    private final DeWaltCore plugin;
    private final PointsManager pointsManager;

    // --- MI NUEVA ESTRUCTURA DE JUEGO ---
    private enum GameState { INACTIVE, LOBBY, RUNNING }
    private GameState currentState = GameState.INACTIVE;

    private final Set<UUID> lobbyPlayers = new HashSet<>();
    private final Map<UUID, PlayerGameState> gamePlayers = new ConcurrentHashMap<>();

    private BukkitTask lobbyCountdownTask;
    private BukkitTask gameTimerTask;
    private int lobbyTimeLeft;
    private int gameTimeLeft;
    private int paintingsLeft;

    // --- CONSTANTES DE CONFIGURACIÓN DEL JUEGO ---
    private static final int LOBBY_DURATION_SECONDS = 60;
    private static final int GAME_DURATION_SECONDS = 180; // Aumento el tiempo de juego para que sea más estratégico
    private static final int MAX_PLAYERS = 20;
    private static final int TOTAL_PAINTINGS = 50;
    private static final Random random = new Random();

    // Artes permitidas (mantengo las de tamaño razonable)
    private static final List<Art> ALLOWED_ART = Arrays.asList(
            Art.KEBAB, Art.AZTEC, Art.ALBAN, Art.AZTEC2, Art.BOMB, Art.PLANT,
            Art.WASTELAND, Art.WANDERER, Art.GRAHAM, Art.MATCH, Art.BUST,
            Art.STAGE, Art.VOID, Art.SKULL_AND_ROSES
    );

    // Mi estado de jugador ahora incluye si tiene una pintura para colocar
    private static class PlayerGameState {
        int score = 0;
        boolean hasPainting = false; // true si el jugador ha recogido una pintura y no la ha colocado
        final List<Entity> placedPaintings = Collections.synchronizedList(new ArrayList<>());
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
        if (isPlayerInGame(player) || lobbyPlayers.contains(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Ya estás en la sala de espera.");
            return;
        }

        lobbyPlayers.add(player.getUniqueId());
        broadcastToLobby(ChatColor.AQUA + player.getName() + " ha entrado al lobby. (" + lobbyPlayers.size() + "/" + MAX_PLAYERS + ")");

        if (currentState == GameState.INACTIVE) {
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
                    broadcastToLobby(ChatColor.RED + "Todos los jugadores han salido. El inicio se ha cancelado.");
                    resetGame();
                    return;
                }

                if (lobbyTimeLeft <= 0) {
                    this.cancel();
                    startGame();
                    return;
                }

                if (lobbyTimeLeft % 10 == 0 || lobbyTimeLeft <= 5) {
                    broadcastToLobby(ChatColor.YELLOW + "El juego comenzará en " + ChatColor.WHITE + lobbyTimeLeft + " segundos...");
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

        // Muevo los jugadores del lobby al juego
        for (UUID uuid : lobbyPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                gamePlayers.put(uuid, new PlayerGameState());
                p.sendTitle(ChatColor.GREEN + "¡El juego ha comenzado!", ChatColor.WHITE + "¡Consigue pinturas de la Mesa de Trabajo!", 10, 60, 20);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            }
        }
        lobbyPlayers.clear();

        startGameTimer();
    }

    private void startGameTimer() {
        gameTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (gamePlayers.isEmpty()) {
                    endGame("Todos los jugadores salieron");
                    return;
                }

                if (gameTimeLeft <= 0) {
                    endGame("Se acabó el tiempo");
                    return;
                }

                gameTimeLeft--;
                updateActionBarForAll();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void handlePaintingPickup(Player player) {
        if (!isPlayerInGame(player) || currentState != GameState.RUNNING) return;

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
        giveDrillItem(player); // El "taladro" ahora significa que tiene una pintura.
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
        player.sendMessage(ChatColor.GREEN + "¡Has recogido una pintura! Ahora colócala.");
    }

    public void handlePaintingPlace(Player player, BlockFace blockFace) {
        if (!isPlayerInGame(player) || currentState != GameState.RUNNING) return;

        PlayerGameState state = gamePlayers.get(player.getUniqueId());
        if (state == null) return;

        // El jugador debe tener una pintura (representada por el ítem del taladro)
        if (!state.hasPainting) {
            player.sendMessage(ChatColor.RED + "¡Primero debes recoger una pintura de la Mesa de Trabajo!");
            return;
        }

        // ... (La lógica de spawn de la pintura es la misma, solo la adapto)

        Location loc = player.getTargetBlock(null, 5).getRelative(blockFace).getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        Art randomArt = ALLOWED_ART.get(random.nextInt(ALLOWED_ART.size()));
        Painting painting = world.spawn(loc, Painting.class, p -> {
            p.setFacingDirection(blockFace, true);
            if (!p.setArt(randomArt, true)) {
                // Si falla al setear el arte, la entidad se invalida y no se spawnea.
            }
        });

        if (!painting.isValid() || painting.isDead()) {
            player.sendTitle(" ", ChatColor.RED + "¡No hay suficiente espacio aquí!", 0, 40, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.2f);
            return;
        }

        // Éxito
        state.score++;
        state.hasPainting = false;
        state.placedPaintings.add(painting);

        paintingsLeft--; // Reduzco el contador global

        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 1.5f);
        removeDrillItem(player); // Le quito el item para que tenga que ir por otro.

        updateActionBarForAll(); // Actualizo la info para todos

        if (paintingsLeft <= 0) {
            endGame("Se agotaron las pinturas");
        }
    }

    // --- 3. FINALIZACIÓN Y LIMPIEZA ---

    private void endGame(String reason) {
        if (currentState != GameState.RUNNING) return;

        currentState = GameState.INACTIVE; // Marco el juego como terminado para evitar dobles llamadas
        if (gameTimerTask != null) gameTimerTask.cancel();

        broadcastToGame(ChatColor.GOLD + "¡El juego ha terminado! Razón: " + reason + ".");

        // Ordeno a los jugadores por su puntuación de mayor a menor
        List<Map.Entry<UUID, PlayerGameState>> sortedPlayers = gamePlayers.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<UUID, PlayerGameState> entry) -> entry.getValue().score).reversed())
                .collect(Collectors.toList());

        // Reparto los puntos
        for (int i = 0; i < sortedPlayers.size(); i++) {
            Map.Entry<UUID, PlayerGameState> entry = sortedPlayers.get(i);
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null) continue;

            int pointsWon;
            String positionMessage;

            if (i == 0) { // 1er lugar
                pointsWon = 20;
                positionMessage = ChatColor.GOLD + "¡Ganaste! (1er Lugar)";
            } else if (i == 1) { // 2do lugar
                pointsWon = 10;
                positionMessage = ChatColor.GRAY + "¡Quedaste 2do!";
            } else if (i == 2) { // 3er lugar
                pointsWon = 5;
                positionMessage = ChatColor.DARK_RED + "¡Quedaste 3ro!";
            } else { // El resto
                pointsWon = 1;
                positionMessage = ChatColor.AQUA + "¡Buena participación!";
            }

            pointsManager.addPoints(p, pointsWon, "drill_competitive", "Ranking final del minijuego");

            String finalMessage = String.format(ChatColor.YELLOW + "Colocaste %d pinturas. " + ChatColor.GREEN + "(+%d pts)", entry.getValue().score, pointsWon);
            p.sendTitle(positionMessage, finalMessage, 10, 80, 20);
        }

        // Limpio a todos los jugadores después de un breve momento
        new BukkitRunnable() {
            @Override
            public void run() {
                // Hago una copia de las keys para evitar problemas al modificar el mapa mientras itero
                new HashSet<>(gamePlayers.keySet()).forEach(uuid -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        cleanUpPlayer(p, false);
                    }
                });
                resetGame();
            }
        }.runTaskLater(plugin, 100L); // 5 segundos después
    }

    private void cleanUpPlayer(Player player, boolean cancelled) {
        PlayerGameState state = gamePlayers.remove(player.getUniqueId());
        if (state == null) return;

        // Limpio las pinturas del jugador
        new BukkitRunnable() {
            @Override
            public void run() {
                state.placedPaintings.forEach(Entity::remove);
            }
        }.runTask(plugin);

        removeDrillItem(player);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("")); // Limpio la actionbar

        if (cancelled) {
            player.sendMessage(ChatColor.RED + "Has salido del minijuego del Taladro.");
            broadcastToGame(ChatColor.YELLOW + player.getName() + " ha abandonado la partida.");
        }
    }

    private void resetGame() {
        if (lobbyCountdownTask != null) lobbyCountdownTask.cancel();
        if (gameTimerTask != null) gameTimerTask.cancel();

        lobbyPlayers.clear();
        gamePlayers.clear(); // Me aseguro de que esté vacío
        currentState = GameState.INACTIVE;
    }

    public void handlePlayerQuit(Player player) {
        if (lobbyPlayers.contains(player.getUniqueId())) {
            lobbyPlayers.remove(player.getUniqueId());
            broadcastToLobby(ChatColor.YELLOW + player.getName() + " ha salido del lobby.");
        } else if (isPlayerInGame(player)) {
            cleanUpPlayer(player, true);
        }
    }

    // --- 4. MÉTODOS DE UTILIDAD ---

    public boolean isPlayerInGame(Player player) {
        return gamePlayers.containsKey(player.getUniqueId());
    }

    public boolean isPaintingManaged(Entity entity) {
        if (!(entity instanceof Painting)) return false;
        return gamePlayers.values().stream().anyMatch(state -> state.placedPaintings.contains(entity));
    }

    private void updateActionBarForAll() {
        String actionBarMessage = String.format("§eTiempo restante: §f%ds §8| §ePinturas restantes: §f%d/%d", gameTimeLeft, paintingsLeft, TOTAL_PAINTINGS);
        broadcastToGame(ChatMessageType.ACTION_BAR, new TextComponent(actionBarMessage));
    }

    private void broadcastToLobby(String message) {
        lobbyPlayers.forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(message);
        });
    }

    private void broadcastToGame(String message) {
        gamePlayers.keySet().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(message);
        });
    }

    private void broadcastToGame(ChatMessageType type, TextComponent component) {
        gamePlayers.keySet().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.spigot().sendMessage(type, component);
        });
    }

    private void playSoundForLobby(Sound sound, float pitch) {
        lobbyPlayers.forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if(p != null) p.playSound(p.getLocation(), sound, 1.0f, pitch);
        });
    }

    public boolean isDrillItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String oraxenId = OraxenItems.getIdByItem(item);
        return "taladro".equals(oraxenId);
    }

    private void giveDrillItem(Player player) {
        // ========== MI CORRECCIÓN #1 ==========
        // El método giveItem(Player, String) ya no existe.
        // Ahora obtenemos el ItemBuilder, construimos el ItemStack y lo añadimos al inventario.
        ItemBuilder itemBuilder = OraxenItems.getItemById("taladro");
        if (itemBuilder != null) {
            player.getInventory().addItem(itemBuilder.build());
        } else {
            plugin.getLogger().warning("Se intentó dar el ítem 'taladro' pero no se encontró en Oraxen.");
        }
    }

    private void removeDrillItem(Player player) {
        // ========== MI CORRECCIÓN #2 ==========
        // Hacemos el proceso más robusto para evitar errores si el item no existe.
        ItemBuilder itemBuilder = OraxenItems.getItemById("taladro");
        if (itemBuilder != null) {
            player.getInventory().remove(itemBuilder.build());
        }
    }
}