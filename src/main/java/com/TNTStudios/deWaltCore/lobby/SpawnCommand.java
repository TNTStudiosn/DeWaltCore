package com.TNTStudios.deWaltCore.lobby;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Mi comando /spawn para llevar al jugador al lobby principal de forma segura.
 * No emite mensajes, ya que la limpieza y los mensajes de salida
 * los gestionan los listeners de los minijuegos correspondientes.
 */
public class SpawnCommand implements CommandExecutor {

    // Defino la ubicación del spawn como una constante estática.
    private static final Location SAFE_EXIT_LOCATION = new Location(Bukkit.getWorld("DEWALT LOBBY"), -2.13, 78.00, 0.44, 90, 0);

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Me aseguro de que quien ejecuta el comando es un jugador.
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Este comando solo puede ser ejecutado por un jugador.");
            return true;
        }

        // Simplemente teletransporto al jugador. Es rápido, directo y sin adornos.
        player.teleport(SAFE_EXIT_LOCATION);
        return true;
    }
}