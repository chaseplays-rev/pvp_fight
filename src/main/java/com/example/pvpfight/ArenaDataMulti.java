package com.example.pvpfight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ArenaDataMulti.java
 *
 * Handles creation, storage, and management of multiple PvP arenas.
 * All arenas are persistent via JSON and thread-safe.
 */
public class ArenaDataMulti {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File ARENA_FILE = new File("config/pvpfight/arenas.json");

    // Thread-safe map for all arenas
    private static final Map<String, ArenaData> ARENAS = new ConcurrentHashMap<>();

    // =========================================================
    // === ADMIN COMMAND METHODS
    // =========================================================

    /**
     * Creates a new arena with the given name.
     */
    public void createArena(ServerPlayer admin, String name) {
        if (name == null || name.isEmpty()) {
            if (admin != null)
                admin.sendSystemMessage(Component.literal("§cArena name cannot be empty."));
            return;
        }

        String key = name.toLowerCase();
        if (ARENAS.containsKey(key)) {
            if (admin != null)
                admin.sendSystemMessage(Component.literal("§eArena already exists: §6" + name));
            LOGGER.warn("[ArenaDataMulti] Arena '{}' already exists.", name);
            return;
        }

        ArenaData arena = new ArenaData(name);
        ARENAS.put(key, arena);
        LOGGER.info("[ArenaDataMulti] Created arena '{}', waiting for setup...", name);



        if (admin != null)
            admin.sendSystemMessage(Component.literal("§aArena created: §6" + name));
        LOGGER.info("[ArenaDataMulti] Arena '{}' created.", name);
    }
    
    public ArenaData getArenaByPlayer(ServerPlayer player) {
        for (ArenaData arena : ARENAS.values()) {
            if (arena.isOccupied() && arena.hasPlayer(player)) {
                return arena;
            }
        }
        return null;
    }

    /**
     * Sets corner 1 or 2 of the arena currently selected by the admin.
     */
    public void setCorner(ServerPlayer admin, int id) {
        ArenaData arena = getAdminArena(admin);
        if (arena == null) {
            admin.sendSystemMessage(Component.literal("§cYou must first select an arena (/arena_new <name>)."));
            return;
        }

        if (id == 1)
            arena.setCorner1(admin.blockPosition());
        else
            arena.setCorner2(admin.blockPosition());

        admin.sendSystemMessage(Component.literal("§aCorner §e" + id + " §aset for arena §6" + arena.getName()));
        LOGGER.info("[ArenaDataMulti] Admin {} set corner {} for arena '{}'", admin.getName().getString(), id, arena.getName());
    }

    /**
     * Sets spawn point 1 or 2 for the arena.
     */
    public void setSpawn(ServerPlayer admin, int id) {
        ArenaData arena = getAdminArena(admin);
        if (arena == null) {
            admin.sendSystemMessage(Component.literal("§cYou must first select an arena (/arena_new <name>)."));
            return;
        }

        if (id == 1)
            arena.setSpawn1(admin.blockPosition());
        else
            arena.setSpawn2(admin.blockPosition());

        admin.sendSystemMessage(Component.literal("§aSpawn §e" + id + " §aset for arena §6" + arena.getName()));
        LOGGER.info("[ArenaDataMulti] Admin {} set spawn {} for arena '{}'", admin.getName().getString(), id, arena.getName());
    }
    /**
     * Finalizes and saves an arena after all corners and spawns are set.
     */
    public void finalizeArena(ServerPlayer admin, String name) {
        ArenaData arena = getArena(name);
        if (arena == null) {
            admin.sendSystemMessage(Component.literal("§cArena not found: §e" + name));
            return;
        }

        if (!arena.isConfigured()) {
            admin.sendSystemMessage(Component.literal("§cArena §e" + name + " §cis not fully configured!"));
            admin.sendSystemMessage(Component.literal("§7You must set both corners and both spawns before finalizing."));
            return;
        }

        saveArenas();
        admin.sendSystemMessage(Component.literal("§aArena §6" + name + " §ahas been finalized and saved!"));
        LOGGER.info("[ArenaDataMulti] Arena '{}' finalized and saved successfully.", name);
    }

    /**
     * Shows info about all loaded arenas to the admin.
     */
    public void printInfo(ServerPlayer admin) {
        if (ARENAS.isEmpty()) {
            admin.sendSystemMessage(Component.literal("§7No arenas available."));
            return;
        }

        admin.sendSystemMessage(Component.literal("§6==== Arena List ===="));
        for (ArenaData arena : ARENAS.values()) {
            String status = arena.isAvailable() ? "§aAVAILABLE" : "§cBUSY";
            admin.sendSystemMessage(Component.literal("§e" + arena.getName() + " §7- " + status));
        }
    }

    /**
     * Clears all arenas from memory and disk.
     */
    public void clear(ServerPlayer admin) {
        ARENAS.clear();
        saveArenas();
        admin.sendSystemMessage(Component.literal("§cAll arenas have been cleared."));
        LOGGER.info("[ArenaDataMulti] Admin {} cleared all arenas.", admin.getName().getString());
    }

    // =========================================================
    // === INTERNAL MANAGEMENT
    // =========================================================

    /**
     * Returns an arena by name (case-insensitive).
     */
    public static ArenaData getArena(String name) {
        if (name == null) return null;
        return ARENAS.get(name.toLowerCase());
    }

    /**
     * Removes an arena by name.
     */
    public static boolean removeArena(String name) {
        if (name == null) return false;
        ArenaData removed = ARENAS.remove(name.toLowerCase());
        if (removed != null) {
            saveArenas();
            LOGGER.info("[ArenaDataMulti] Arena '{}' removed.", name);
            return true;
        }
        return false;
    }

    /**
     * Returns all loaded arenas.
     */
    public static List<ArenaData> getAllArenas() {
        return new ArrayList<>(ARENAS.values());
    }

    /**
     * Returns all arena names for tab-completion.
     */
    public static List<String> getAllArenaNames() {
        return ARENAS.values().stream()
                .map(ArenaData::getName)
                .sorted(String::compareToIgnoreCase)
                .toList();
    }

    /**
     * Returns the first available (free) arena or null.
     */
    public static ArenaData getAvailableArena() {
        return ARENAS.values().stream()
                .filter(ArenaData::isAvailable)
                .findFirst()
                .orElse(null);
    }

    /**
     * Marks an arena as available or busy.
     */
    public static void setArenaAvailability(String name, boolean available) {
        ArenaData arena = getArena(name);
        if (arena != null) {
            arena.setAvailable(available);
            saveArenas();
            LOGGER.info("[ArenaDataMulti] Arena '{}' is now {}", name, available ? "AVAILABLE" : "BUSY");
        }
    }

    /**
     * Gets the arena the admin most recently created (for setting corners/spawns).
     * This is a simplified system that just uses the last created arena.
     */
    private ArenaData getAdminArena(ServerPlayer admin) {
        if (ARENAS.isEmpty()) return null;

        // For simplicity, return the most recently added arena
        ArenaData lastArena = null;
        for (ArenaData a : ARENAS.values()) {
            lastArena = a;
        }
        return lastArena;
    }

    // =========================================================
    // === PERSISTENCE
    // =========================================================

    public static void loadArenas() {
        try {
            if (!ARENA_FILE.exists()) {
                saveArenas();
                return;
            }

            Type listType = new TypeToken<List<ArenaData>>() {}.getType();
            List<ArenaData> loaded = GSON.fromJson(new FileReader(ARENA_FILE), listType);

            ARENAS.clear();
            for (ArenaData arena : loaded) {
                ARENAS.put(arena.getName().toLowerCase(), arena);
            }

            LOGGER.info("[ArenaDataMulti] Loaded {} arenas.", ARENAS.size());
        } catch (Exception e) {
            LOGGER.error("[ArenaDataMulti] Error loading arenas: {}", e.getMessage());
        }
    }

    public static void saveArenas() {
        try {
            File dir = ARENA_FILE.getParentFile();
            if (!dir.exists()) dir.mkdirs();

            List<ArenaData> arenasToSave = new ArrayList<>(ARENAS.values());
            FileWriter writer = new FileWriter(ARENA_FILE);
            GSON.toJson(arenasToSave, writer);
            writer.flush();
            writer.close();

            LOGGER.info("[ArenaDataMulti] Saved {} arenas.", arenasToSave.size());
        } catch (Exception e) {
            LOGGER.error("[ArenaDataMulti] Error saving arenas: {}", e.getMessage());
        }
    }

    public static void clearAll() {
        ARENAS.clear();
        saveArenas();
        LOGGER.info("[ArenaDataMulti] All arenas cleared from memory.");
    }
}
