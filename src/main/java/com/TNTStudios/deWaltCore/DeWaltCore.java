package com.TNTStudios.deWaltCore;

import com.TNTStudios.deWaltCore.registration.RegistrationListener;
import com.TNTStudios.deWaltCore.scoreboard.ScoreboardListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeWaltCore extends JavaPlugin {

    @Override
    public void onEnable() {

        getServer().getPluginManager().registerEvents(new ScoreboardListener(), this);

        getServer().getPluginManager().registerEvents(new ScoreboardListener(), this);

        //register apagado
        //getServer().getPluginManager().registerEvents(new RegistrationListener(), this);
    }


    @Override
    public void onDisable() {

    }
}

