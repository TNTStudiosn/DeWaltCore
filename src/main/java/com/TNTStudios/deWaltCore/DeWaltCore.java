package com.TNTStudios.deWaltCore;

import com.TNTStudios.deWaltCore.points.PointsManager;
import com.TNTStudios.deWaltCore.registration.RegistrationListener;
import com.TNTStudios.deWaltCore.scoreboard.ScoreboardListener;
import com.TNTStudios.deWaltCore.minigames.laberinto.LaberintoBestTimeManager;
import com.TNTStudios.deWaltCore.minigames.laberinto.LaberintoTimerCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeWaltCore extends JavaPlugin {

    private static DeWaltCore instance;
    private PointsManager pointsManager;
    private LaberintoBestTimeManager bestTimeManager;
    private LaberintoTimerCommand timerCommand;

    public static DeWaltCore getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        pointsManager = new PointsManager(this);
        bestTimeManager = new LaberintoBestTimeManager(this);
        bestTimeManager.load();

        timerCommand = new LaberintoTimerCommand(this, bestTimeManager, pointsManager);

        getServer().getPluginManager().registerEvents(new ScoreboardListener(pointsManager), this);
        getServer().getPluginManager().registerEvents(timerCommand, this);

        getCommand("empezar").setExecutor(timerCommand);
        getCommand("detener").setExecutor(timerCommand);
        //register apagado
        //getServer().getPluginManager().registerEvents(new RegistrationListener(), this);
    }


    @Override
    public void onDisable() {
        bestTimeManager.save();
        pointsManager.saveAllPoints();
    }
}

