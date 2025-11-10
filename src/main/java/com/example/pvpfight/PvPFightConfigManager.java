package com.example.pvpfight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
/**
 * Handles saving and loading PvPFight settings from config/pvpfight/config.json.
 * Allows admins to modify settings without editing the JAR.
 */
public class PvPFightConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config/pvpfight/config.json");
    private static final Path ARENA_PATH = Path.of("config/pvpfight/arena_data.json");

    
    private static List<ArenaData> arenas = new ArrayList<>();
    
    // === All settings (match Config.java values) ===
    public int requestTimeoutSeconds = 15;
    public int countdownSeconds = 3;
    public int postWinInvulnerabilitySeconds = 5;
    public boolean allowEnvironmentDamage = false;
    public boolean forfeitOnLeaveArena = true;
    public boolean announceToAll = true;
    // === Rewards ===
    public int winnerXp = 100;
    public int loserXp = 25;

    // Fixed reward (specific item + amount)
    public String winnerItem = "minecraft:diamond";
    public int winnerItemAmount = 1;
    public String loserItem = "minecraft:bread";
    public int loserItemAmount = 1;

    // Random reward pools (lists of item IDs)
    public List<String> winnerItemPool = List.of(
            "minecraft:diamond",
            "minecraft:emerald",
            "minecraft:gold_ingot"
    );
    public List<String> loserItemPool = List.of(
            "minecraft:iron_ingot",
            "minecraft:coal",
            "minecraft:apple"
    );

    /** Loads configuration or creates a default one if missing. */
    public static PvPFightConfigManager loadOrCreate() {
        try {
            if (Files.notExists(CONFIG_PATH)) {
                Files.createDirectories(CONFIG_PATH.getParent());
                PvPFightConfigManager defaults = new PvPFightConfigManager();
                save(defaults);
                return defaults;
            }
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                return GSON.fromJson(reader, PvPFightConfigManager.class);
            }
        } catch (Exception e) {
            System.err.println("[PvPFight] Failed to load config.json: " + e.getMessage());
            return new PvPFightConfigManager(); // fallback
        }
    }

    /** Saves current config to disk. */
    public static void save(PvPFightConfigManager config) {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(config, writer);
        } catch (Exception e) {
            System.err.println("[PvPFight] Failed to save config.json: " + e.getMessage());
        }
    }
    /** Loads all arenas from arena_data.json (creates file if missing). */
    public static void loadArenas() {
        try {
            if (Files.notExists(ARENA_PATH)) {
                Files.createDirectories(ARENA_PATH.getParent());
                Files.createFile(ARENA_PATH);
                saveArenas(); // create empty file
                System.out.println("[PvPFight] Created new arena_data.json");
                return;
            }

            try (Reader reader = Files.newBufferedReader(ARENA_PATH)) {
                Type listType = new TypeToken<List<ArenaData>>() {}.getType();
                List<ArenaData> loaded = GSON.fromJson(reader, listType);
                if (loaded != null) arenas = loaded;
                System.out.println("[PvPFight] Loaded " + arenas.size() + " arenas from arena_data.json");
            }

        } catch (Exception e) {
            System.err.println("[PvPFight] Failed to load arena_data.json: " + e.getMessage());
        }
    }

    /** Saves all current arenas to arena_data.json. */
    public static void saveArenas() {
        try (Writer writer = Files.newBufferedWriter(ARENA_PATH)) {
            GSON.toJson(arenas, writer);
        } catch (Exception e) {
            System.err.println("[PvPFight] Failed to save arena_data.json: " + e.getMessage());
        }
    }
    /** Returns all loaded arenas. */
    public static List<ArenaData> getArenas() {
        return arenas;
    }

    /** Finds an arena by name (case-insensitive). */
    public static ArenaData getArena(String name) {
        for (ArenaData arena : arenas) {
            if (arena.name.equalsIgnoreCase(name)) {
                return arena;
            }
        }
        return null;
    }

    /** Adds or replaces an arena in the list and saves immediately. */
    public static void addOrUpdateArena(ArenaData arena) {
        for (int i = 0; i < arenas.size(); i++) {
            if (arenas.get(i).name.equalsIgnoreCase(arena.name)) {
                arenas.set(i, arena);
                saveArenas();
                System.out.println("[PvPFight] Updated arena: " + arena.name);
                return;
            }
        }
        arenas.add(arena);
        saveArenas();
        System.out.println("[PvPFight] Added new arena: " + arena.name);
    }

    /** Removes an arena by name. */
    public static boolean removeArena(String name) {
        boolean removed = arenas.removeIf(a -> a.name.equalsIgnoreCase(name));
        if (removed) saveArenas();
        return removed;
    }
    // ... all your existing code (loadArenas, saveArenas, getArenas, etc.)

    /** Loads both config.json and arena_data.json at once. */
    public static void initializeAllConfigs() {
        loadOrCreate(); // loads main config.json
        loadArenas();   // loads arena_data.json
    }
    // === Utility: Pick a random reward from the pool ===
    public static String getRandomReward(boolean isWinner) {
        PvPFightConfigManager cfg = loadOrCreate();
        List<String> pool = isWinner ? cfg.winnerItemPool : cfg.loserItemPool;

        if (pool == null || pool.isEmpty()) {
            return isWinner ? cfg.winnerItem : cfg.loserItem;
        }

        java.util.Random rand = new java.util.Random();
        return pool.get(rand.nextInt(pool.size()));
    }


}
