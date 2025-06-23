// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/concrete/ConcreteCommand.java
package com.TNTStudios.deWaltCore.minigames.concrete;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Mi comando /concreto para unirse al minijuego.
 */
public class ConcreteCommand implements CommandExecutor {

    private final ConcreteManager concreteManager;

    public ConcreteCommand(ConcreteManager concreteManager) {
        this.concreteManager = concreteManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando solo puede ser ejecutado por un jugador.");
            return true;
        }

        // Llevo al jugador al lobby del minijuego
        concreteManager.addPlayerToLobby(player);
        return true;
    }
}