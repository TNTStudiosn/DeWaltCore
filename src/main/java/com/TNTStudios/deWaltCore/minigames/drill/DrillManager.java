// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/drill/DrillManager.java
package com.TNTStudios.deWaltCore.minigames.drill;

// --- AÑADE ESTE IMPORT DE LA API DE ORAXEN ---
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

public class DrillManager {

    private final DeWaltCore plugin;
    private final PointsManager pointsManager;
    private final Map<UUID, PlayerGameState> activePlayers = new ConcurrentHashMap<>();
    private BukkitTask globalTimerTask;

    private static final int GAME_DURATION_SECONDS = 60;
    private static final int PAINTING_LIFESPAN_SECONDS = 6;
    private static final Random random = new Random();

    private static final List<Art> ALLOWED_ART = Arrays.asList(
            Art.KEBAB, Art.AZTEC, Art.ALBAN, Art.AZTEC2, Art.BOMB, Art.PLANT, Art.WASTELAND,
            Art.WANDERER, Art.GRAHAM, Art.MATCH, Art.BUST, Art.STAGE, Art.VOID, Art.SKULL_AND_ROSES
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

        player.sendTitle(ChatColor.YELLOW + "¡Prepárate!", "El objetivo es colocar la mayor cantidad", 10, 80, 20);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GOLD + "de pinturas posibles antes de que se acabe el tiempo."));

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
        }.runTaskTimer(plugin, 40L, 20L);
    }

    private void finishMinigame(Player player, boolean cancelled) {
        PlayerGameState state = activePlayers.remove(player.getUniqueId());
        if (state == null) return;

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
        if (blockFace == BlockFace.UP || blockFace == BlockFace.DOWN) return;
        PlayerGameState state = activePlayers.get(player.getUniqueId());
        if (state == null) return;

        Location loc = clickedBlock.getRelative(blockFace).getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        Art randomArt = ALLOWED_ART.get(random.nextInt(ALLOWED_ART.size()));
        Painting painting = world.spawn(loc, Painting.class);
        painting.setFacingDirection(blockFace, true);

        if (!painting.setArt(randomArt, true)) {
            painting.remove();
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.2f);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "¡No hay suficiente espacio para esta pintura aquí!"));
            return;
        }

        if (!painting.isValid() || painting.isDead()) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.2f);
            return;
        }

        state.score++;
        state.placedPaintings.add(painting);
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 1.5f);

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

    public boolean isPlayerInGame(Player player) {
        return activePlayers.containsKey(player.getUniqueId());
    }

    public boolean isPaintingManaged(Entity entity) {
        if (!(entity instanceof Painting)) return false;
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
                for (UUID uuid : new ArrayList<>(activePlayers.keySet())) {
                    Player p = Bukkit.getPlayer(uuid);
                    PlayerGameState state = activePlayers.get(uuid);
                    if (p == null || !p.isOnline() || state == null) {
                        activePlayers.remove(uuid);
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
        // Itera de forma segura para encontrar y eliminar el ítem del taladro
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (isDrillItem(item)) { // Usa el método de comprobación oficial
                player.getInventory().setItem(i, null); // Elimina el ítem
                break; // Termina el bucle una vez que se ha eliminado un taladro
            }
        }
    }

    /**
     * Comprueba si un ItemStack es el taladro del minijuego usando la API oficial de Oraxen.
     * Este es el método más seguro y recomendado.
     */
    public boolean isDrillItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        // OraxenItems.getIdByItem devuelve el ID del item ("taladro") o null si no es un item de Oraxen.
        String oraxenId = OraxenItems.getIdByItem(item);
        return "taladro".equals(oraxenId);
    }
}