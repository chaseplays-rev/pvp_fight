package com.example.pvpfight;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/**
 * LobbyManager.java
 * 
 * Manages the global lobby spawn position.
 * Players are teleported here after fights.
 * Data is saved persistently under: config/pvpfight/lobby.json
 */
public class LobbyManager {

    private static BlockPos lobbyPos;
    private static String lobbyWorld;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File LOBBY_FILE = new File("config/pvpfight/lobby.json");

    // === LOBBY MANAGEMENT ===

    /**
     * Sets the lobby position at the admin's current location and saves it to file.
     */
    public void setLobbyPosition(ServerPlayer admin) {
        if (admin == null) return;

        lobbyPos = admin.blockPosition();
        lobbyWorld = admin.serverLevel().dimension().location().toString();

        saveLobby();
        Util.sendMessage(admin, "§aLobby position set at your current location!");
        LOGGER.info("[LobbyManager] Lobby set at {} in world {}", formatPos(lobbyPos), lobbyWorld);
    }

    /**
     * Teleports a player to the saved lobby position, if it exists.
     */
    public void teleportToLobby(ServerPlayer player) {
        if (player == null) return;

        if (lobbyPos == null) {
            Util.sendMessage(player, "§cLobby not set yet!");
            LOGGER.warn("[LobbyManager] Lobby not set – cannot teleport player '{}'.",
                    player.getName().getString());
            return;
        }

        ServerLevel overworld = player.server.getLevel(ServerLevel.OVERWORLD);
        if (overworld != null) {
            player.teleportTo(overworld,
                    lobbyPos.getX() + 0.5,
                    lobbyPos.getY(),
                    lobbyPos.getZ() + 0.5,
                    player.getYRot(),
                    player.getXRot());
            Util.sendMessage(player, "§aTeleported to the lobby!");
        } else {
            LOGGER.error("[LobbyManager] Overworld not found – teleport failed!");
        }
    }

    /**
     * Returns the current lobby position.
     */
    public BlockPos getLobbyPosition() {
        return lobbyPos;
    }

    /**
     * Clears the in-memory lobby data (used when server stops).
     */
    public void clear() {
        lobbyPos = null;
        lobbyWorld = null;
        LOGGER.info("[LobbyManager] Lobby cleared from memory.");
    }

    // === PERSISTENCE ===

    /**
     * Loads the lobby data from JSON.
     */
    public static void loadLobby() {
        try {
            if (!LOBBY_FILE.exists()) {
                LOGGER.info("[LobbyManager] No saved lobby found – creating new file.");
                saveLobby();
                return;
            }

            LobbyData data = GSON.fromJson(new FileReader(LOBBY_FILE), LobbyData.class);
            if (data != null) {
                lobbyPos = new BlockPos(data.x, data.y, data.z);
                lobbyWorld = data.world;
                LOGGER.info("[LobbyManager] Lobby loaded at {} in world {}.", formatPos(lobbyPos), lobbyWorld);
            } else {
                LOGGER.warn("[LobbyManager] Failed to read lobby data (null).");
            }

        } catch (Exception e) {
            LOGGER.error("[LobbyManager] Error loading lobby: {}", e.getMessage());
        }
    }

    /**
     * Saves the current lobby data to JSON.
     */
    public static void saveLobby() {
        try {
            File dir = LOBBY_FILE.getParentFile();
            if (!dir.exists()) dir.mkdirs();

            if (lobbyPos == null) {
                LOGGER.info("[LobbyManager] No lobby set – file will remain empty.");
                return;
            }

            LobbyData data = new LobbyData(
                    lobbyPos.getX(),
                    lobbyPos.getY(),
                    lobbyPos.getZ(),
                    lobbyWorld
            );

            FileWriter writer = new FileWriter(LOBBY_FILE);
            GSON.toJson(data, writer);
            writer.flush();
            writer.close();

            LOGGER.info("[LobbyManager] Lobby saved at {} in world {}.", formatPos(lobbyPos), lobbyWorld);
        } catch (Exception e) {
            LOGGER.error("[LobbyManager] Error saving lobby: {}", e.getMessage());
        }
    }

    // === INNER DATA CLASS ===

    private static class LobbyData {
        int x, y, z;
        String world;

        public LobbyData(int x, int y, int z, String world) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.world = world;
        }
    }

    // === HELPER METHODS ===

    private static String formatPos(BlockPos pos) {
        return pos == null ? "(null)" : pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
}
