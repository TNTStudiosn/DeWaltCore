package com.TNTStudios.deWaltCore;

import com.TNTStudios.deWaltCore.scoreboard.ScoreboardListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeWaltCore extends JavaPlugin {

    @Override
    public void onEnable() {
        // Plugin startup logic
        getServer().getPluginManager().registerEvents(new ScoreboardListener(), this);
    }


    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}

