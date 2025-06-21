package com.TNTStudios.deWaltCore;

import com.TNTStudios.deWaltCore.minigames.MinigameListener;
import com.TNTStudios.deWaltCore.minigames.maze.MazeCommand;
import com.TNTStudios.deWaltCore.minigames.maze.MazeManager;
import com.TNTStudios.deWaltCore.points.PointsManager;
import com.TNTStudios.deWaltCore.registration.RegistrationListener;
import com.TNTStudios.deWaltCore.scoreboard.ScoreboardListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeWaltCore extends JavaPlugin {

    @Override
    public void onEnable() {
        // --- Scoreboard y Registro (Existente) ---
        getServer().getPluginManager().registerEvents(new ScoreboardListener(), this);
        // getServer().getPluginManager().registerEvents(new RegistrationListener(), this); // Sigue apagado

        // --- Sistema de Minijuego de Laberinto (Nuevo) ---
        // 1. Inicializo mis nuevos managers
        PointsManager pointsManager = new PointsManager(this);
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
        // Aquí podríamos guardar datos si fuera necesario, pero mi PointsManager guarda al instante.
    }
}