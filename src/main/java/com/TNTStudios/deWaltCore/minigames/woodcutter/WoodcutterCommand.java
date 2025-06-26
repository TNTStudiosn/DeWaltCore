// FILE: src/main/java/com/TNTStudios/deWaltCore/minigames/woodcutter/WoodcutterCommand.java
package com.TNTStudios.deWaltCore.minigames.woodcutter;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Mi comando /madera para unirse al minijuego de la Cortadora.
 */
public class WoodcutterCommand implements CommandExecutor {

    private final WoodcutterManager woodcutterManager;

    public WoodcutterCommand(WoodcutterManager woodcutterManager) {
        this.woodcutterManager = woodcutterManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando solo puede ser ejecutado por un jugador.");
            return true;
        }

        // Simplemente llamo al manager para que añada al jugador al lobby.
        woodcutterManager.addPlayerToLobby(player);
        return true;
    }
}