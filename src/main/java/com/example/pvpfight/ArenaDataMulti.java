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
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final File ARENA_FILE = new File("config/pvpfight/arenas.json");

    // Thread-safe map for all arenas
    private static final Map<String, ArenaData> ARENAS = new ConcurrentHashMap<>();

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

    public static void loadArenas() {
        final File file = ARENA_FILE;

        if (!file.exists()) {
            LOGGER.info("[ArenaDataMulti] Arenas file not found at {} (starting empty).", file.getAbsolutePath());
            ARENAS.clear();
            return;
        }

        try (var reader = new java.io.InputStreamReader(
                new java.io.FileInputStream(file),
                java.nio.charset.StandardCharsets.UTF_8)) {

            com.google.gson.JsonElement root = com.google.gson.JsonParser.parseReader(reader);
            java.util.Map<String, ArenaData> temp = new java.util.HashMap<>();

            if (root == null || root.isJsonNull()) {
                LOGGER.warn("[ArenaDataMulti] Empty/null JSON at {}", file.getAbsolutePath());
            } else if (root.isJsonArray()) {
                // Case A: bare array
                var arr = root.getAsJsonArray();
                for (var el : arr) {
                    ArenaData a = GSON.fromJson(el, ArenaData.class);
                    if (a != null && a.getName() != null && !a.getName().isBlank()) {
                        temp.put(a.getName().toLowerCase(java.util.Locale.ROOT), a);
                    }
                }
            } else if (root.isJsonObject()) {
                var obj = root.getAsJsonObject();

                // Case B: root-wrapped array: { "arenas": [ ... ] }
                if (obj.has("arenas") && obj.get("arenas").isJsonArray()) {
                    var arr = obj.getAsJsonArray("arenas");
                    for (var el : arr) {
                        ArenaData a = GSON.fromJson(el, ArenaData.class);
                        if (a != null && a.getName() != null && !a.getName().isBlank()) {
                            temp.put(a.getName().toLowerCase(java.util.Locale.ROOT), a);
                        }
                    }
                } else {
                    // Case C: map: { "duel": {ArenaData}, "pit": {ArenaData} }
                    for (var entry : obj.entrySet()) {
                        String keyName = entry.getKey();
                        ArenaData a = GSON.fromJson(entry.getValue(), ArenaData.class);
                        if (a != null) {
                            // If JSON omitted "name", use the map key
                            if (a.getName() == null || a.getName().isBlank()) {
                                try {
                                    java.lang.reflect.Field f = ArenaData.class.getDeclaredField("name");
                                    f.setAccessible(true);
                                    f.set(a, keyName);
                                } catch (Exception ignore) { /* fallback below */ }
                            }
                            String finalName = (a.getName() != null && !a.getName().isBlank()) ? a.getName() : keyName;
                            temp.put(finalName.toLowerCase(java.util.Locale.ROOT), a);
                        }
                    }
                }
            } else {
                LOGGER.error("[ArenaDataMulti] Unsupported JSON root at {}: {}", file.getAbsolutePath(), root.getClass());
            }

            ARENAS.clear();
            ARENAS.putAll(temp);
            LOGGER.info("[ArenaDataMulti] Loaded {} arenas from {}.", ARENAS.size(), file.getAbsolutePath());

        } catch (com.google.gson.JsonSyntaxException js) {
            LOGGER.error("[ArenaDataMulti] JSON syntax error at {}.", file.getAbsolutePath(), js);
        } catch (Exception e) {
            LOGGER.error("[ArenaDataMulti] Error loading arenas from {}.", file.getAbsolutePath(), e);
        }
    }


    public static void saveArenas() {
        try {
            File dir = ARENA_FILE.getParentFile();
            if (!dir.exists() && !dir.mkdirs()) {
                LOGGER.error("[ArenaDataMulti] Could not create config directory: {}", dir);
                return;
            }
            List<ArenaData> arenasToSave = new ArrayList<>(ARENAS.values());
            try (var writer = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(ARENA_FILE),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                GSON.toJson(arenasToSave, writer); // or write the Map, see D)
            }
            LOGGER.info("[ArenaDataMulti] Saved {} arenas to {}", arenasToSave.size(), ARENA_FILE.getAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("[ArenaDataMulti] Error saving arenas", e);
        }
    }



}
