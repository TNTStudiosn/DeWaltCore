// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/drill/DrillManager.java
package com.TNTStudios.deWaltCore.minigames.drill;

// --- MANTÉN EL IMPORT DE LA API DE ORAXEN ---
import io.th0rgal.oraxen.api.OraxenItems;

import com.TNTStudios.deWaltCore.DeWaltCore;
import com.TNTStudios.deWaltCore.points.PointsManager;
import com.TNTStudios.deWaltCore.scoreboard.DeWaltScoreboardManager;
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

/**
 * Mi manager para el minijuego del Taladro.
 * Ahora con validaciones mejoradas para colocar pinturas y mensajes más limpios.
 */
public class DrillManager {

    private final DeWaltCore plugin;
    private final PointsManager pointsManager;
    private final Map<UUID, PlayerGameState> activePlayers = new ConcurrentHashMap<>();
    private BukkitTask globalTimerTask;

    private static final int GAME_DURATION_SECONDS = 60;
    private static final int PAINTING_LIFESPAN_SECONDS = 6;
    private static final Random random = new Random();

    // Solo usaré artes de 1x1, 1x2, 2x1 y 2x2 para que sea más fácil encontrarles sitio.
    private static final List<Art> ALLOWED_ART = Arrays.asList(
            // 1x1
            Art.KEBAB, Art.AZTEC, Art.ALBAN, Art.AZTEC2,
            // 1x2
            Art.BOMB, Art.PLANT, Art.WASTELAND,
            // 2x1
            Art.WANDERER, Art.GRAHAM,
            // 2x2
            Art.MATCH, Art.BUST, Art.STAGE, Art.VOID, Art.SKULL_AND_ROSES
    );

    private static class PlayerGameState {
        int score = 0;
        int timeLeft;
        final List<Entity> placedPaintings = Collections.synchronizedList(new ArrayList<>());
        PlayerGameState(int duration) { this.timeLeft = duration; }
    }

    public DrillManager(DeWaltCore plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
    }

    public void startMinigame(Player player) {
        if (isPlayerInGame(player)) {
            player.sendMessage(ChatColor.RED + "¡Ya estás participando en el minijuego del Taladro!");
            return;
        }

        // Ahora uso title y subtitle juntos para un mensaje de instrucciones más limpio.
        player.sendTitle(
                ChatColor.YELLOW + "¡Prepárate!",
                "Coloca tantas pinturas como puedas",
                10, 80, 20
        );

        new BukkitRunnable() {
            int countdown = 3;
            @Override
            public void run() {
                if (countdown > 0) {
                    player.sendTitle(ChatColor.YELLOW + "Empezando en...", ChatColor.GOLD.toString() + countdown, 0, 25, 5);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                    countdown--;
                } else {
                    this.cancel();
                    player.sendTitle(ChatColor.GREEN + "¡YA!", ChatColor.WHITE + "¡A taladrar!", 0, 40, 10);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                    giveDrillItem(player);
                    activePlayers.put(player.getUniqueId(), new PlayerGameState(GAME_DURATION_SECONDS));
                    startGlobalTimer();
                }
            }
        }.runTaskTimer(plugin, 40L, 20L); // Doy un poco más de tiempo para leer las instrucciones antes de la cuenta atrás.
    }

    private void finishMinigame(Player player, boolean cancelled) {
        PlayerGameState state = activePlayers.remove(player.getUniqueId());
        if (state == null) return;

        // Limpio las pinturas del jugador de forma segura en el hilo principal.
        new BukkitRunnable() {
            @Override
            public void run() {
                state.placedPaintings.forEach(Entity::remove);
            }
        }.runTask(plugin);

        removeDrillItem(player);

        if (cancelled) {
            player.sendMessage(ChatColor.RED + "Has salido del minijuego del Taladro.");
            return;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        int pointsWon = pointsManager.recordScore(player, "drill", state.score);
        String finalMessage = String.format(ChatColor.GOLD + "¡Colocaste %d pinturas!", state.score);
        if (pointsWon > 0) {
            finalMessage += String.format(ChatColor.GREEN + " (+%d pts)", pointsWon);
        }
        player.sendTitle(ChatColor.YELLOW + "¡Tiempo!", finalMessage, 10, 80, 20);

        // Muestro el scoreboard después de un pequeño retraso.
        new BukkitRunnable() {
            @Override
            public void run() {
                int totalPoints = pointsManager.getTotalPoints(player);
                int topPosition = pointsManager.getPlayerRank(player);
                List<PointsManager.PlayerScore> topPlayers = pointsManager.getTopPlayers(3);
                DeWaltScoreboardManager.showDefaultPage(player, topPosition, totalPoints, false, topPlayers);
            }
        }.runTask(plugin);
    }

    public void handlePlayerQuit(Player player) {
        if (isPlayerInGame(player)) {
            finishMinigame(player, true);
        }
    }

    public void handlePaintingPlace(Player player, Block clickedBlock, BlockFace blockFace) {
        // Primero, me aseguro de que no se intente colocar en el suelo o en el techo.
        if (blockFace == BlockFace.UP || blockFace == BlockFace.DOWN) {
            sendPlacementError(player, "¡No puedes colocar pinturas en el suelo o techo!");
            return;
        }

        PlayerGameState state = activePlayers.get(player.getUniqueId());
        if (state == null) return;

        Location loc = clickedBlock.getRelative(blockFace).getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        // Intento colocar una pintura. Si no se puede, lo notifico y termino.
        Art randomArt = ALLOWED_ART.get(random.nextInt(ALLOWED_ART.size()));
        Painting painting = world.spawn(loc, Painting.class, p -> {
            p.setFacingDirection(blockFace, true);
            // Intento forzar el arte. Si no hay espacio en la pared, esto fallará.
            if (!p.setArt(randomArt, true)) {
                // Si falla, el objeto `p` (la pintura) se eliminará automáticamente.
            }
        });

        // Si la pintura no es válida después del spawn (porque no cupo), se habrá eliminado sola.
        if (!painting.isValid() || painting.isDead()) {
            sendPlacementError(player, "¡No hay suficiente espacio aquí!");
            return;
        }

        // Ahora, una segunda comprobación clave: ¿estoy superponiendo otra entidad?
        // Compruebo si hay otras entidades (especialmente pinturas) en el mismo espacio.
        // Aumento un poco el bounding box para evitar colisiones justas.
        boolean isOverlapping = world.getNearbyEntities(painting.getBoundingBox().expand(0.1))
                .stream()
                .anyMatch(entity -> entity instanceof Painting && !entity.getUniqueId().equals(painting.getUniqueId()));

        if (isOverlapping) {
            painting.remove(); // Elimino la pintura que acabo de poner.
            sendPlacementError(player, "¡Ya hay otra pintura en ese lugar!");
            return;
        }

        // ¡Éxito! La pintura se ha colocado correctamente.
        state.score++;
        state.placedPaintings.add(painting);
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 1.5f);

        // Programo la eliminación de la pintura después de su tiempo de vida.
        new BukkitRunnable() {
            @Override
            public void run() {
                if (painting.isValid() && !painting.isDead()) {
                    painting.remove();
                    state.placedPaintings.remove(painting);
                }
            }
        }.runTaskLater(plugin, PAINTING_LIFESPAN_SECONDS * 20L);
    }

    /**
     * Envía un mensaje de error de colocación al jugador usando un subtítulo temporal.
     */
    private void sendPlacementError(Player player, String message) {
        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.2f);
        // Uso un título vacío y un subtítulo para el mensaje de error. Es rápido y efectivo.
        player.sendTitle(" ", ChatColor.RED + message, 0, 40, 10);
    }

    public boolean isPlayerInGame(Player player) {
        return activePlayers.containsKey(player.getUniqueId());
    }

    public boolean isPaintingManaged(Entity entity) {
        if (!(entity instanceof Painting)) return false;
        // Compruebo si la entidad está en la lista de pinturas de cualquier jugador activo.
        return activePlayers.values().stream().anyMatch(state -> state.placedPaintings.contains(entity));
    }

    private void startGlobalTimer() {
        if (globalTimerTask != null && !globalTimerTask.isCancelled()) return;

        globalTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (activePlayers.isEmpty()) {
                    this.cancel();
                    globalTimerTask = null;
                    return;
                }

                // Itero de forma segura sobre una copia de las claves para evitar ConcurrentModificationException.
                for (UUID uuid : new ArrayList<>(activePlayers.keySet())) {
                    Player p = Bukkit.getPlayer(uuid);
                    PlayerGameState state = activePlayers.get(uuid);

                    if (p == null || !p.isOnline() || state == null) {
                        activePlayers.remove(uuid); // Limpio jugadores desconectados o con datos corruptos.
                        continue;
                    }

                    state.timeLeft--;
                    if (state.timeLeft <= 0) {
                        finishMinigame(p, false);
                    } else {
                        String actionBarMessage = String.format("§eTiempo restante: §f%ds §8| §ePinturas: §f%d", state.timeLeft, state.score);
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBarMessage));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void giveDrillItem(Player player) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "o give " + player.getName() + " taladro 1");
    }

    private void removeDrillItem(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isDrillItem(item)) {
                player.getInventory().setItem(i, null);
                break;
            }
        }
    }

    public boolean isDrillItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        String oraxenId = OraxenItems.getIdByItem(item);
        return "taladro".equals(oraxenId);
    }
}