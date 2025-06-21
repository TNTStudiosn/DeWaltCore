package com.TNTStudios.deWaltCore;

import com.TNTStudios.deWaltCore.minigames.MinigameListener;
import com.TNTStudios.deWaltCore.minigames.maze.MazeCommand;
import com.TNTStudios.deWaltCore.minigames.maze.MazeManager;
import com.TNTStudios.deWaltCore.points.PointsManager;
import com.TNTStudios.deWaltCore.registration.RegistrationListener;
import com.TNTStudios.deWaltCore.scoreboard.ScoreboardListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeWaltCore extends JavaPlugin {

    // Lo hago estático para poder accederlo desde mis listeners.
    private static PointsManager pointsManager;

    @Override
    public void onEnable() {
        // --- Scoreboard y Registro (Existente) ---
        getServer().getPluginManager().registerEvents(new ScoreboardListener(), this);
        // getServer().getPluginManager().registerEvents(new RegistrationListener(), this); // Sigue apagado

        // --- Sistema de Minijuego de Laberinto (Nuevo) ---
        // 1. Inicializo mis nuevos managers
        pointsManager = new PointsManager(this); // Lo asigno a mi variable estática
        MazeManager mazeManager = new MazeManager(this, pointsManager);

        // 2. Registro los nuevos comandos
        MazeCommand mazeCommand = new MazeCommand(mazeManager);
        getCommand("empezar").setExecutor(mazeCommand);
        getCommand("detener").setExecutor(mazeCommand);

        // 3. Registro el nuevo listener para eventos de minijuegos
        getServer().getPluginManager().registerEvents(new MinigameListener(mazeManager), this);
    }

    @Override
    public void onDisable() {
        // Guardo el leaderboard al apagar el servidor para asegurar que todo esté al día.
        if (pointsManager != null) {
            pointsManager.saveLeaderboard();
        }
    }

    // Mi getter estático para que otras clases accedan al PointsManager.
    public static PointsManager getPointsManager() {
        return pointsManager;
    }
}