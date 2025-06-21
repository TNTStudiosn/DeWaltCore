package com.TNTStudios.deWaltCore.points;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PointsManager {
    private final JavaPlugin plugin;
    private final File playersFolder;
    private final Map<String, Integer> totalPoints = new HashMap<>();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("America/Mexico_City"));

    public PointsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.playersFolder = new File(plugin.getDataFolder(), "players");
        if (!playersFolder.exists()) {
            playersFolder.mkdirs();
        }
        loadAllPoints();
    }

    private void loadAllPoints() {
        File[] files = playersFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            String name = file.getName().substring(0, file.getName().length() - 4);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            int pts = config.getInt("points", 0);
            totalPoints.put(name, pts);
        }
    }

    public void saveAllPoints() {
        for (String name : totalPoints.keySet()) {
            savePlayer(name);
        }
    }

    private void savePlayer(String name) {
        File file = new File(playersFolder, name + ".yml");
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        config.set("points", totalPoints.getOrDefault(name, 0));
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getPoints(String name) {
        return totalPoints.getOrDefault(name, 0);
    }

    public void addPoints(String name, int amount, String minigame) {
        int newTotal = getPoints(name) + amount;
        totalPoints.put(name, newTotal);

        File file = new File(playersFolder, name + ".yml");
        YamlConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        config.set("points", newTotal);
        List<Map<?, ?>> list = config.getMapList("history");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("minigame", minigame);
        entry.put("points", amount);
        entry.put("time", formatter.format(Instant.now()));
        list.add(entry);
        config.set("history", list);
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public int getTopPosition(String name) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(totalPoints.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getKey().equalsIgnoreCase(name)) {
                return i + 1;
            }
        }
        return list.size() + 1;
    }
}
