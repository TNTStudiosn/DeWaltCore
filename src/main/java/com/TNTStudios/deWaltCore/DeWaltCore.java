package com.TNTStudios.deWaltCore;

// Importo las nuevas clases del minijuego del concreto
import com.TNTStudios.deWaltCore.helmet.HelmetListener;
import com.TNTStudios.deWaltCore.helmet.HelmetManager;
import com.TNTStudios.deWaltCore.minigames.concrete.ConcreteCommand;
import com.TNTStudios.deWaltCore.minigames.concrete.ConcreteListener;
import com.TNTStudios.deWaltCore.minigames.concrete.ConcreteManager;
import com.TNTStudios.deWaltCore.minigames.drill.DrillCommand;
import com.TNTStudios.deWaltCore.minigames.drill.DrillListener;
import com.TNTStudios.deWaltCore.minigames.drill.DrillManager;
import com.TNTStudios.deWaltCore.minigames.woodcutter.WoodcutterCommand;
import com.TNTStudios.deWaltCore.minigames.woodcutter.WoodcutterListener;
import com.TNTStudios.deWaltCore.minigames.woodcutter.WoodcutterManager;
import com.TNTStudios.deWaltCore.minigames.maze.MazeCommand;
import com.TNTStudios.deWaltCore.minigames.maze.MazeManager;
import com.TNTStudios.deWaltCore.minigames.MinigameListener;
import com.TNTStudios.deWaltCore.points.PointsManager;
import com.TNTStudios.deWaltCore.scoreboard.ScoreboardListener;
import com.TNTStudios.deWaltCore.registration.EmailValidator;
import com.TNTStudios.deWaltCore.registration.RegistrationListener;
import com.TNTStudios.deWaltCore.registration.RegistrationManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class DeWaltCore extends JavaPlugin {

    private static PointsManager pointsManager;
    private MazeManager mazeManager;
    private DrillManager drillManager;
    private WoodcutterManager woodcutterManager;
    private ConcreteManager concreteManager;
    private RegistrationManager registrationManager;
    private EmailValidator emailValidator;
    private HelmetManager helmetManager;

    @Override
    public void onEnable() {
        // Guardar la configuración por defecto (crea config.yml si no existe)
        saveDefaultConfig();

        // --- MI NUEVO SISTEMA DE CASCO PERMANENTE ---
        // Lo inicializo al principio para que esté disponible para todos los demás sistemas.
        getLogger().info("Inicializando el sistema de casco permanente...");
        this.helmetManager = new HelmetManager(this);
        getServer().getPluginManager().registerEvents(new HelmetListener(this.helmetManager, this), this);
        getLogger().info("Sistema de casco permanente cargado.");

        // --- MI NUEVO SISTEMA DE REGISTRO ---
        getLogger().info("Inicializando el sistema de registro de jugadores...");
        this.registrationManager = new RegistrationManager(this);
        getServer().getPluginManager().registerEvents(
                new RegistrationListener(this.registrationManager), this
        );
        getLogger().info("Sistema de registro cargado correctamente.");

        // --- Scoreboard ---
        getServer().getPluginManager().registerEvents(
                new ScoreboardListener(), this
        );

        // --- Sistema de Puntos y Minijuegos ---
        pointsManager = new PointsManager(this);
        mazeManager = new MazeManager(this, pointsManager);
        drillManager = new DrillManager(this, pointsManager);
        woodcutterManager = new WoodcutterManager(this, pointsManager);
        concreteManager = new ConcreteManager(this, pointsManager);

        // --- Comandos ---
        // Laberinto
        MazeCommand mazeCommand = new MazeCommand(mazeManager);
        getCommand("empezar").setExecutor(mazeCommand);
        getCommand("detener").setExecutor(mazeCommand);

        // Taladro
        DrillCommand drillCommand = new DrillCommand(drillManager);
        getCommand("taladro").setExecutor(drillCommand);

        // Concreto
        ConcreteCommand concreteCommand = new ConcreteCommand(concreteManager);
        getCommand("concreto").setExecutor(concreteCommand);

        // Cortador de madera
        WoodcutterCommand woodcutterCommand = new WoodcutterCommand(woodcutterManager);
        getCommand("madera").setExecutor(woodcutterCommand);

        // --- Listeners de Minijuegos ---
        getServer().getPluginManager().registerEvents(
                new MinigameListener(mazeManager), this
        );
        getServer().getPluginManager().registerEvents(
                new DrillListener(drillManager), this
        );
        getServer().getPluginManager().registerEvents(
                new ConcreteListener(concreteManager), this
        );
        getServer().getPluginManager().registerEvents(
                new WoodcutterListener(woodcutterManager), this
        );

        // Tarea periódica para guardar el leaderboard automáticamente
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

    /**
     * Acceso estático al PointsManager desde otras clases.
     */
    public static PointsManager getPointsManager() {
        return pointsManager;
    }

    /**
     * Acceso al RegistrationManager para registros externos.
     */
    public RegistrationManager getRegistrationManager() {
        return registrationManager;
    }
}
