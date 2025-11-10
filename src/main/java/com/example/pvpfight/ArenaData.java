package com.example.pvpfight;

import net.minecraft.server.level.ServerPlayer;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;


public class ArenaData {

    private transient static final Logger LOGGER = LogUtils.getLogger();

    private transient ServerPlayer player1;
    private transient ServerPlayer player2;
    public String name;
    public Pos corner1;
    public Pos corner2;
    public Pos spawn1;
    public Pos spawn2;
    public boolean available = true;

    // === Konstruktor ===
    public ArenaData() {}
    
    public ArenaData(String name) {
        this.name = name;
        this.available = true;
    }

    // === Getter / Setter ===

    public String getName() {
        return name;
    }
    
    public BlockPos getCorner1() {
        return corner1 != null ? corner1.toBlockPos() : null;
    }

    public BlockPos getCorner2() {
        return corner2 != null ? corner2.toBlockPos() : null;
    }

    public BlockPos getSpawn1() {
        return spawn1 != null ? spawn1.toBlockPos() : null;
    }

    public BlockPos getSpawn2() {
        return spawn2 != null ? spawn2.toBlockPos() : null;
    }


    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
        LOGGER.info("[ArenaData] Arena '{}' ist jetzt {}.", name, available ? "frei" : "belegt");
    }

    public void setCorner1(BlockPos pos) {
        this.corner1 = Pos.from(pos);
        LOGGER.info("[ArenaData] Corner 1 for arena '{}' set to {}", name, corner1);
    }

    public void setCorner2(BlockPos pos) {
        this.corner2 = Pos.from(pos);
        LOGGER.info("[ArenaData] Corner 2 for arena '{}' set to {}", name, corner2);
    }

    public void setSpawn1(BlockPos pos) {
        this.spawn1 = Pos.from(pos);
        LOGGER.info("[ArenaData] Spawn point 1 for arena '{}' set to {}", name, spawn1);
    }

    public void setSpawn2(BlockPos pos) {
        this.spawn2 = Pos.from(pos);
        LOGGER.info("[ArenaData] Spawn point 2 for arena '{}' set to {}", name, spawn2);
    }

    // === Validierung ===

    /**
     * Prüft, ob die Arena vollständig konfiguriert ist.
     * Alle Ecken und Spawns müssen gesetzt sein.
     */
    public boolean isConfigured() {
        boolean ready = corner1 != null && corner2 != null && spawn1 != null && spawn2 != null;
        if (!ready) {
            LOGGER.warn("[ArenaData] Arena '{}' ist unvollständig konfiguriert!", name);
        }
        return ready;
    }

    /**
     * Prüft, ob ein bestimmter Punkt innerhalb der Arenagrenzen liegt.
     */
    public boolean isInside(BlockPos pos) {
        if (corner1 == null || corner2 == null) return false;

        int minX = Math.min(corner1.getX(), corner2.getX());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int minY = Math.min(corner1.getY(), corner2.getY());
        int maxY = Math.max(corner1.getY(), corner2.getY());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());

        return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    // === Hilfsmethoden ===

    private String formatPos(Pos pos) {
        return pos == null ? "(null)" : pos.x + " " + pos.y + " " + pos.z;
    }

    @Override
    public String toString() {
        return "ArenaData{" +
                "name='" + name + '\'' +
                ", corner1=" + formatPos(corner1) +
                ", corner2=" + formatPos(corner2) +
                ", spawn1=" + formatPos(spawn1) +
                ", spawn2=" + formatPos(spawn2) +
                ", available=" + available +
                '}';
    }

    public boolean isOccupied() {
        return player1 != null && player2 != null;
    }

    public boolean hasPlayer(ServerPlayer player) {
        return player1 != null && player1.equals(player) || player2 != null && player2.equals(player);
    }
    // === Serializable inner helper class for JSON ===
    public static class Pos {
        public int x, y, z;

        public Pos() {} // required for Gson

        public Pos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public static Pos from(BlockPos pos) {
            if (pos == null) return null;
            return new Pos(pos.getX(), pos.getY(), pos.getZ());
        }

        public BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }
        // ✅ Add these getters so existing code using getX/Y/Z compiles fine
        public int getX() { return x; }
        public int getY() { return y; }
        public int getZ() { return z; }

        @Override
        public String toString() {
            return "(" + x + "," + y + "," + z + ")";
        }
    }
}