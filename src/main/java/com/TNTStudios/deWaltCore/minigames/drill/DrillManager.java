// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/drill/DrillManager.java
package com.TNTStudios.deWaltCore.minigames.drill;

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
 * Mi controlador para la lógica del minijuego del Taladro.
 * Está optimizado para alto rendimiento usando un temporizador global
 * y estructuras de datos concurrentes.
 */
public class DrillManager {

    private final DeWaltCore plugin;
    private final PointsManager pointsManager;
    private final Map<UUID, PlayerGameState> activePlayers = new ConcurrentHashMap<>();
    private BukkitTask globalTimerTask;

    private static final int GAME_DURATION_SECONDS = 60; // El minijuego durará 60 segundos.
    private static final int PAINTING_LIFESPAN_SECONDS = 6; // Las pinturas desaparecen a los 6 segundos.
    private static final String DRILL_ITEM_NAME_ID = "Taladro"; // El nombre que identifica mi item de Oraxen.
    private static final List<Art> ALLOWED_ART = Arrays.asList(
            Art.KEBAB, Art.AZTEC, Art.ALBAN, Art.AZTEC2, Art.BOMB, Art.PLANT, Art.WASTELAND,
            Art.WANDERER, Art.GRAHAM, Art.MATCH, Art.BUST, Art.STAGE, Art.VOID, Art.SKULL_AND_ROSES
    ); // Una lista de pinturas variadas (1x1, 1x2, 2x1, 2x2) para que el juego sea más dinámico.
    private static final Random random = new Random();


    // Un objeto interno para guardar el estado de cada jugador.
    private static class PlayerGameState {
        int score = 0;
        int timeLeft;
        // Guardo las entidades de las pinturas para poder eliminarlas después.
        final List<Entity> placedPaintings = Collections.synchronizedList(new ArrayList<>());

        PlayerGameState(int duration) {
            this.timeLeft = duration;
        }
    }

    public DrillManager(DeWaltCore plugin, PointsManager pointsManager) {
        this.plugin = plugin;
        this.pointsManager = pointsManager;
    }

    /**
     * Inicia el minijuego para un jugador. Muestra instrucciones y una cuenta atrás.
     */
    public void startMinigame(Player player) {
        if (isPlayerInGame(player)) {
            player.sendMessage(ChatColor.RED + "¡Ya estás participando en el minijuego del Taladro!");
            return;
        }

        player.sendTitle(ChatColor.YELLOW + "¡Prepárate!", "El objetivo es colocar la mayor cantidad", 10, 80, 20);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GOLD + "de pinturas posibles antes de que se acabe el tiempo."));

        // Empiezo una cuenta regresiva antes de iniciar.
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
                    startGlobalTimer(); // Me aseguro de que el temporizador global esté activo.
                }
            }
        }.runTaskTimer(plugin, 40L, 20L); // Empieza tras 2 segundos de las instrucciones, y se repite cada segundo.
    }

    /**
     * Termina el minijuego para un jugador, ya sea porque se acabó el tiempo o porque se canceló.
     */
    private void finishMinigame(Player player, boolean cancelled) {
        PlayerGameState state = activePlayers.remove(player.getUniqueId());
        if (state == null) {
            return; // El jugador no estaba jugando.
        }

        // Limpio todas las pinturas que el jugador haya colocado.
        // Lo hago en el hilo principal para evitar problemas con las entidades.
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

        // Si no fue cancelado, proceso los puntos.
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        int pointsWon = pointsManager.recordScore(player, "drill", state.score);

        String finalMessage = String.format(ChatColor.GOLD + "¡Colocaste %d pinturas!", state.score);
        if (pointsWon > 0) {
            finalMessage += String.format(ChatColor.GREEN + " (+%d pts)", pointsWon);
        }

        player.sendTitle(ChatColor.YELLOW + "¡Tiempo!", finalMessage, 10, 80, 20);

        // --- MI CORRECCIÓN 1: ACTUALIZAR EL SCOREBOARD ---
        // Al igual que en el laberinto, necesito forzar la actualización del scoreboard
        // para que el jugador vea sus nuevos puntos y ranking al instante.
        new BukkitRunnable() {
            @Override
            public void run() {
                int totalPoints = pointsManager.getTotalPoints(player);
                int topPosition = pointsManager.getPlayerRank(player);
                List<PointsManager.PlayerScore> topPlayers = pointsManager.getTopPlayers(3);
                // Llamo al método estático del manager del scoreboard para mostrar la página por defecto.
                DeWaltScoreboardManager.showDefaultPage(player, topPosition, totalPoints, false, topPlayers);
            }
        }.runTask(plugin); // Lo ejecuto en el siguiente tick para asegurar que todos los datos estén guardados y la UI se actualice en el hilo principal.
    }

    public void handlePlayerQuit(Player player) {
        if (isPlayerInGame(player)) {
            finishMinigame(player, true);
        }
    }

    /**
     * Se activa cuando un jugador con el taladro hace clic derecho en un bloque.
     */
    public void handlePaintingPlace(Player player, Block clickedBlock, BlockFace blockFace) {
        if (blockFace == BlockFace.UP || blockFace == BlockFace.DOWN) {
            return; // No se pueden colocar pinturas en el techo o el suelo.
        }

        PlayerGameState state = activePlayers.get(player.getUniqueId());
        if (state == null) return; // No está en el juego.

        Location loc = clickedBlock.getRelative(blockFace).getLocation();
        World world = loc.getWorld();

        if (world == null) return;

        Art randomArt = ALLOWED_ART.get(random.nextInt(ALLOWED_ART.size()));

        // --- MI CORRECCIÓN 2: MÉTODO DE SPAWN DE PINTURA MÁS ROBUSTO ---
        // En lugar de configurar la pintura dentro del spawn, lo que puede ser inconsistente,
        // la creo primero, ajusto su dirección y luego intento establecer el arte.
        // El método setArt(art, force) devuelve un booleano que me indica si tuvo éxito.
        Painting painting = world.spawn(loc, Painting.class);
        painting.setFacingDirection(blockFace, true);

        // Si no se puede colocar el arte (ej. no hay espacio), el método devuelve falso.
        if (!painting.setArt(randomArt, true)) {
            painting.remove(); // Elimino la entidad de pintura fallida para no dejar basura.
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.2f);
            return; // No se pudo colocar, no sumo puntos.
        }

        // Por seguridad, compruebo de nuevo si la pintura es válida antes de continuar.
        if (!painting.isValid() || painting.isDead()) {
            player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.2f);
            return;
        }

        state.score++;
        state.placedPaintings.add(painting);
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 1.0f, 1.5f);

        // Programo la eliminación de la pintura después de 6 segundos.
        new BukkitRunnable() {
            @Override
            public void run() {
                // Me aseguro de que la pintura todavía exista antes de intentar quitarla.
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
        // Reviso si la pintura fue colocada por alguno de los jugadores activos.
        return activePlayers.values().stream().anyMatch(state -> state.placedPaintings.contains(entity));
    }

    /**
     * Inicia el temporizador global si no está activo. Es la clave para la optimización.
     */
    private void startGlobalTimer() {
        if (globalTimerTask != null && !globalTimerTask.isCancelled()) {
            return; // El temporizador ya está corriendo.
        }

        globalTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (activePlayers.isEmpty()) {
                    this.cancel(); // No hay jugadores, apago el temporizador para ahorrar recursos.
                    globalTimerTask = null;
                    return;
                }

                // Itero sobre una copia de las claves para evitar problemas si un jugador sale durante la iteración.
                for (UUID uuid : new ArrayList<>(activePlayers.keySet())) {
                    Player p = Bukkit.getPlayer(uuid);
                    PlayerGameState state = activePlayers.get(uuid);

                    if (p == null || !p.isOnline() || state == null) {
                        activePlayers.remove(uuid); // Limpio jugadores desconectados o con estado corrupto.
                        continue;
                    }

                    state.timeLeft--;

                    if (state.timeLeft <= 0) {
                        finishMinigame(p, false); // Se acabó el tiempo.
                    } else {
                        // Actualizo la action bar del jugador con su tiempo y puntuación.
                        String actionBarMessage = String.format("§eTiempo restante: §f%ds §8| §ePinturas: §f%d", state.timeLeft, state.score);
                        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(actionBarMessage));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Se ejecuta cada segundo.
    }

    private void giveDrillItem(Player player) {
        // Ejecuto el comando de Oraxen desde la consola para darle el taladro al jugador.
        // Esta es la forma más compatible de interactuar con otros plugins.
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "o give " + player.getName() + " taladro 1");
    }

    private void removeDrillItem(Player player) {
        // Para quitar el taladro, itero sobre su inventario.
        // Así evito quitar un stack completo si el jugador de alguna forma consiguió más de uno.
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isDrillItem(item)) { // Reutilizo mi método de comprobación
                player.getInventory().remove(item); // Uso el método remove que es más seguro que setAmount(0)
                break; // Solo quito uno.
            }
        }
        player.updateInventory();
    }

    // Método para saber si el item es un taladro. Lo usaré en el listener.
    public boolean isDrillItem(ItemStack item) {
        return item != null && item.getType() == Material.PAPER && item.hasItemMeta() && item.getItemMeta().getDisplayName().contains(DRILL_ITEM_NAME_ID);
    }
}