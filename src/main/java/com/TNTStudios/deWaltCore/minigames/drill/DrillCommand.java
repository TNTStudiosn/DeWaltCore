// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/drill/DrillCommand.java
package com.TNTStudios.deWaltCore.minigames.drill;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Mi comando /taladro para iniciar el minijuego.
 */
public class DrillCommand implements CommandExecutor {

    private final DrillManager drillManager;

    public DrillCommand(DrillManager drillManager) {
        this.drillManager = drillManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando solo puede ser ejecutado por un jugador.");
            return true;
        }

        // Simplemente le paso el control a mi manager.
        drillManager.startMinigame(player);
        return true;
    }
}