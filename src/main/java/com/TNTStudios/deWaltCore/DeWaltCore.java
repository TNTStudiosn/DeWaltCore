package com.TNTStudios.deWaltCore;

import com.TNTStudios.deWaltCore.registration.RegistrationListener;
import com.TNTStudios.deWaltCore.scoreboard.ScoreboardListener;
import com.TNTStudios.deWaltCore.stats.PointsManager; // Importo mi nuevo manager
import org.bukkit.plugin.java.JavaPlugin;

public final class DeWaltCore extends JavaPlugin {

    // Creo una instancia estática para poder acceder al manager desde otras clases
    private static PointsManager pointsManager;

    @Override
    public void onEnable() {
        // Inicio el PointsManager para que cargue los datos antes que nada
        pointsManager = new PointsManager(this);

        // Registro los eventos del scoreboard
        getServer().getPluginManager().registerEvents(new ScoreboardListener(), this);

        // register apagado
        //getServer().getPluginManager().registerEvents(new RegistrationListener(), this);
    }

    @Override
    public void onDisable() {
        // Guardo todos los puntos cuando el servidor se apaga para que no se pierda nada
        pointsManager.savePoints();
    }

    // Con este método estático, cualquier otra clase puede obtener el PointsManager
    public static PointsManager getPointsManager() {
        return pointsManager;
    }
}