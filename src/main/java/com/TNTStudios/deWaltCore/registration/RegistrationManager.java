package com.TNTStudios.deWaltCore.registration;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class RegistrationManager {

    private static final File REGISTRATION_FOLDER = new File(Bukkit.getPluginManager().getPlugin("DeWaltCore").getDataFolder(), "registrations");

    static {
        if (!REGISTRATION_FOLDER.exists()) {
            REGISTRATION_FOLDER.mkdirs();
        }
    }

    public static boolean isRegistered(Player player) {
        File file = new File(REGISTRATION_FOLDER, player.getName() + ".yml");
        return file.exists();
    }

    public static void register(Player player, String name, String email, int age) {
        File file = new File(REGISTRATION_FOLDER, player.getName() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        config.set("name", name);
        config.set("email", email);
        config.set("age", age);

        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static YamlConfiguration getRegistration(Player player) {
        File file = new File(REGISTRATION_FOLDER, player.getName() + ".yml");
        return YamlConfiguration.loadConfiguration(file);
    }
}
