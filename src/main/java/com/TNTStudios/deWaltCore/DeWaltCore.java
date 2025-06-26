// FILE: src/main/java/com/TNTStudios/deWaltCore/DeWaltCore.java
package com.TNTStudios.deWaltCore;

// Importo las nuevas clases del minijuego del concreto
import com.TNTStudios.deWaltCore.minigames.concrete.ConcreteCommand;
import com.TNTStudios.deWaltCore.minigames.concrete.ConcreteListener;
import com.TNTStudios.deWaltCore.minigames.concrete.ConcreteManager;
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
import com.TNTStudios.deWaltCore.minigames.woodcutter.WoodcutterCommand;
import com.TNTStudios.deWaltCore.minigames.woodcutter.WoodcutterListener;
import com.TNTStudios.deWaltCore.minigames.woodcutter.WoodcutterManager;

public final class DeWaltCore extends JavaPlugin {

    private static PointsManager pointsManager;
    private MazeManager mazeManager;
    private DrillManager drillManager;
    private WoodcutterManager woodcutterManager;
    // --- MI NUEVO MANAGER ---
    private ConcreteManager concreteManager;

    @Override
    public void onEnable() {
        // --- Scoreboard y Registro ---
        getServer().getPluginManager().registerEvents(new ScoreboardListener(), this);

        // --- Sistema de Puntos y Minijuegos ---
        pointsManager = new PointsManager(this);
        mazeManager = new MazeManager(this, pointsManager);
        drillManager = new DrillManager(this, pointsManager);
        // Inicializo mi nuevo manager del concreto
        concreteManager = new ConcreteManager(this, pointsManager);
        woodcutterManager = new WoodcutterManager(this, pointsManager);

        // --- Comandos ---
        // Laberinto
        MazeCommand mazeCommand = new MazeCommand(mazeManager);
        getCommand("empezar").setExecutor(mazeCommand);
        getCommand("detener").setExecutor(mazeCommand);

        // Taladro
        DrillCommand drillCommand = new DrillCommand(drillManager);
        getCommand("taladro").setExecutor(drillCommand);

        // Concreto (necesito añadir 'concreto' a mi plugin.yml)
        ConcreteCommand concreteCommand = new ConcreteCommand(concreteManager);
        getCommand("concreto").setExecutor(concreteCommand);

        WoodcutterCommand woodcutterCommand = new WoodcutterCommand(woodcutterManager);
        getCommand("madera").setExecutor(woodcutterCommand);


        // --- Listeners ---
        getServer().getPluginManager().registerEvents(new MinigameListener(mazeManager), this);
        getServer().getPluginManager().registerEvents(new DrillListener(drillManager), this);
        getServer().getPluginManager().registerEvents(new ConcreteListener(concreteManager), this);
        getServer().getPluginManager().registerEvents(new WoodcutterListener(woodcutterManager), this);

        // Tarea periódica para guardar el leaderboard de forma segura.
        new BukkitRunnable() {
            @Override
            public void run() {
                if (pointsManager != null) {
                    pointsManager.saveLeaderboardAsync();
                    getLogger().info("El leaderboard ha sido guardado automáticamente en segundo plano.");
                }
            }
        }.runTaskTimerAsynchronously(this, 6000L, 6000L);
    }

    @Override
    public void onDisable() {
        if (pointsManager != null) {
            getLogger().info("Guardando leaderboard final antes de apagar...");
            pointsManager.saveLeaderboardSync();
            getLogger().info("Leaderboard guardado correctamente.");
        }
    }

    public static PointsManager getPointsManager() {
        return pointsManager;
    }
}