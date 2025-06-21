package com.TNTStudios.deWaltCore.minigames.maze;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Mis comandos /empezar y /detener para el minijuego del laberinto.
 */
public class MazeCommand implements CommandExecutor {

    private final MazeManager mazeManager;

    public MazeCommand(MazeManager mazeManager) {
        this.mazeManager = mazeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando solo puede ser ejecutado por un jugador.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("empezar")) {
            mazeManager.startMaze(player);
        } else if (command.getName().equalsIgnoreCase("detener")) {
            mazeManager.finishMaze(player);
        }

        return true;
    }
}