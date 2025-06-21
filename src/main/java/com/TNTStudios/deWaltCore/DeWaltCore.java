// FILE: src/main/java/com/TNTStudios/deWaltCore/DeWaltCore.java
package com.TNTStudios.deWaltCore;

// Importo las nuevas clases del minijuego del taladro
import com.TNTStudios.deWaltCore.minigames.drill.DrillCommand;
import com.TNTStudios.deWaltCore.minigames.drill.DrillListener;
import com.TNTStudios.deWaltCore.minigames.drill.DrillManager;
import com.TNTStudios.deWaltCore.minigames.MinigameListener;
import com.TNTStudios.deWaltCore.minigames.maze.MazeCommand;
import com.TNTStudios.deWaltCore.minigames.maze.MazeManager;
import com.TNTStudios.deWaltCore.points.PointsManager;
import com.TNTStudios.deWaltCore.scoreboard.ScoreboardListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class DeWaltCore extends JavaPlugin {

    private static PointsManager pointsManager;
    private MazeManager mazeManager;
    // --- MI NUEVO MANAGER ---
    private DrillManager drillManager;

    @Override
    public void onEnable() {
        // --- Scoreboard y Registro ---
        getServer().getPluginManager().registerEvents(new ScoreboardListener(), this);

        // --- Sistema de Puntos y Minijuegos ---
        pointsManager = new PointsManager(this);
        mazeManager = new MazeManager(this, pointsManager);
        // Inicializo mi nuevo manager del taladro
        drillManager = new DrillManager(this, pointsManager);

        // --- Comandos ---
        // Laberinto
        MazeCommand mazeCommand = new MazeCommand(mazeManager);
        getCommand("empezar").setExecutor(mazeCommand);
        getCommand("detener").setExecutor(mazeCommand);

        // Taladro (necesito añadir 'taladro' a mi plugin.yml)
        DrillCommand drillCommand = new DrillCommand(drillManager);
        getCommand("taladro").setExecutor(drillCommand);


        // --- Listeners ---
        getServer().getPluginManager().registerEvents(new MinigameListener(mazeManager), this);
        // Registro el nuevo listener para el taladro
        getServer().getPluginManager().registerEvents(new DrillListener(drillManager), this);

        // Tarea periódica para guardar el leaderboard de forma segura.
        // Se ejecuta cada 5 minutos (6000 ticks = 20 ticks/seg * 60 seg/min * 5 min).
        new BukkitRunnable() {
            @Override
            public void run() {
                if (pointsManager != null) {
                    pointsManager.saveLeaderboardAsync(); // Ahora llamo al método asíncrono explícitamente.
                    getLogger().info("El leaderboard ha sido guardado automáticamente en segundo plano.");
                }
            }
        }.runTaskTimerAsynchronously(this, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        // Guardo el leaderboard una última vez al apagar, de forma síncrona para asegurar que se complete.
        if (pointsManager != null) {
            getLogger().info("Guardando leaderboard final antes de apagar...");
            // --- MI CAMBIO ---
            // Llamo al nuevo método síncrono. Esto es crucial para que el guardado
            // se complete antes de que el servidor se apague por completo.
            pointsManager.saveLeaderboardSync();
            getLogger().info("Leaderboard guardado correctamente.");
        }
    }

    // El getter estático sigue siendo útil para el acceso simple desde otras clases.
    public static PointsManager getPointsManager() {
        return pointsManager;
    }
}