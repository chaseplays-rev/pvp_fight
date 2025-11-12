package com.example.pvpfight;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import org.slf4j.Logger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FightManager.java
 *
 * Handles full PvP fight lifecycle:
 *  - Requests / accept / deny / cancel
 *  - Teleport → countdown → start flow
 *  - Arena assignment & release
 *  - Disconnect handling
 *  - Inventory backup / restore
 *  - Anti-duplication tagging system
 */
public class FightManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<UUID, ActiveFight> activeFights = new ConcurrentHashMap<>();
    private final Map<UUID, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Set<UUID> frozenPlayers = ConcurrentHashMap.newKeySet();
    private final Queue<PendingRequest> queuedFights = new LinkedList<>();


    private final ArenaDataMulti arenaManager;
    private final LobbyManager lobbyManager;

    private final int REQUEST_TIMEOUT = Config.getRequestTimeoutSeconds();
    private final int COUNTDOWN_SECONDS = Config.getCountdownSeconds();

    public FightManager(ArenaDataMulti arenaManager, LobbyManager lobbyManager) {
        this.arenaManager = arenaManager;
        this.lobbyManager = lobbyManager;
        LOGGER.info("[FightManager] Initialized.");
    }

    // =====================================================
    // === Requests / Accept / Deny / Cancel
    // =====================================================

    public void sendRequest(ServerPlayer from, ServerPlayer target) {
        if (pendingRequests.containsKey(target.getUUID())) {
            Util.sendMessage(from, "§cThat player already has a pending fight request!");
            return;
        }

        PendingRequest req = new PendingRequest(from, target, System.currentTimeMillis());
        pendingRequests.put(target.getUUID(), req);

        Util.sendMessage(from, "§eYou challenged §6" + target.getName().getString() + "§e!");
        Util.sendMessage(target, "§6" + from.getName().getString() + " §ewants to fight you!");
        Util.sendClickableAcceptDeny(target, from.getName().getString());

        LOGGER.info("[FightManager] Fight request sent from {} to {}", from.getName().getString(), target.getName().getString());

        new Timer(true).schedule(new TimerTask() {
            @Override public void run() {
                if (pendingRequests.remove(target.getUUID()) != null) {
                    Util.sendMessage(from, "§7Your fight request to §e" + target.getName().getString() + " §7has expired.");
                    Util.sendMessage(target, "§7The fight request from §e" + from.getName().getString() + " §7has expired.");
                    LOGGER.info("[FightManager] Request timed out between {} and {}", from.getName().getString(), target.getName().getString());
                }
            }
        }, REQUEST_TIMEOUT * 1000L);
    }

    public void accept(ServerPlayer acceptor, ServerPlayer challenger) {
        PendingRequest req = pendingRequests.remove(acceptor.getUUID());
        if (req == null || !req.from.equals(challenger)) {
            Util.sendMessage(acceptor, "§cNo valid fight request from that player.");
            return;
        }

        ArenaData arena = arenaManager.getAvailableArena();
            if (arena == null) {
            Util.sendMessage(acceptor, "§eNo arena is currently free. You’ve been added to the waiting queue.");
            Util.sendMessage(challenger, "§eNo arena is currently free. You’ll be teleported once one opens.");
            queuedFights.add(new PendingRequest(challenger, acceptor, System.currentTimeMillis()));
            LOGGER.info("[FightManager] Queued fight between {} and {}", challenger.getName().getString(), acceptor.getName().getString());
            return;
        }

        arena.setAvailable(false);

        Util.sendMessage(acceptor, "§aYou accepted the challenge! Teleporting...");
        Util.sendMessage(challenger, "§aYour challenge was accepted! Teleporting...");

        startTeleportAndCountdown(challenger, acceptor, arena);
    }

    public void deny(ServerPlayer denier, ServerPlayer challenger) {
        PendingRequest removed = pendingRequests.remove(denier.getUUID());
        if (removed == null) {
            Util.sendMessage(denier, "§cNo fight request to deny.");
            return;
        }

        Util.sendMessage(denier, "§cYou denied the fight request from §6" + challenger.getName().getString());
        Util.sendMessage(challenger, "§6" + denier.getName().getString() + " §crefused your challenge.");
        LOGGER.info("[FightManager] Request denied between {} and {}", challenger.getName().getString(), denier.getName().getString());
    }

    public void cancelOwnRequest(ServerPlayer player) {
        PendingRequest toRemove = null;
        UUID keyToRemove = null;

        for (Map.Entry<UUID, PendingRequest> e : pendingRequests.entrySet()) {
            if (e.getValue().from.equals(player)) {
                keyToRemove = e.getKey();
                toRemove = e.getValue();
                break;
            }
        }

        if (toRemove != null && keyToRemove != null) {
            pendingRequests.remove(keyToRemove);
            Util.sendMessage(player, "§eYou cancelled your fight request to §6" + toRemove.target.getName().getString());
        } else {
            Util.sendMessage(player, "§7You have no active fight requests.");
        }
    }

    // =====================================================
    // === Teleport → Countdown → Start
    // =====================================================

    private void startTeleportAndCountdown(ServerPlayer p1, ServerPlayer p2, ArenaData arena) {
        MinecraftServer server = p1.server;
        ActiveFight fight = new ActiveFight(p1, p2, arena);
        activeFights.put(p1.getUUID(), fight);
        activeFights.put(p2.getUUID(), fight);

        InventoryStash.saveToPlayerTag(p1);
        InventoryStash.saveToPlayerTag(p2);

        if(!p1.level().dimension().equals(Level.OVERWORLD)) {
            p1.changeDimension(Objects.requireNonNull(server.getLevel(Level.OVERWORLD)));
        }
        if(!p2.level().dimension().equals(Level.OVERWORLD)) {
            p2.changeDimension(Objects.requireNonNull(server.getLevel(Level.OVERWORLD)));
        }

        p1.teleportTo(p1.serverLevel(),
                arena.getSpawn1().getX() + 0.5, arena.getSpawn1().getY(), arena.getSpawn1().getZ() + 0.5,
                p1.getYRot(), p1.getXRot());
        p2.teleportTo(p2.serverLevel(),
                arena.getSpawn2().getX() + 0.5, arena.getSpawn2().getY(), arena.getSpawn2().getZ() + 0.5,
                p2.getYRot(), p2.getXRot());

        Util.announceToAll(server, "§6" + p1.getName().getString() + " §7and §6" + p2.getName().getString() +
                " §7entered arena §e" + arena.getName());

        setFrozen(p1, true);
        setFrozen(p2, true);

        new Thread(() -> {
            try {
                for (int i = COUNTDOWN_SECONDS; i > 0; i--) {
                    int sec = i;
                    server.execute(() -> {
                        Util.sendMessage(p1, "§eFight starts in §6" + sec + "§e...");
                        Util.sendMessage(p2, "§eFight starts in §6" + sec + "§e...");
                    });
                    Thread.sleep(1000);
                }

                server.execute(() -> {
                    setFrozen(p1, false);
                    setFrozen(p2, false);
                    startFight(p1, p2, arena);
                });

            } catch (InterruptedException ignored) {}
        }, "PvPFight-Countdown").start();
    }

    private void startFight(ServerPlayer p1, ServerPlayer p2, ArenaData arena) {
        Util.sendMessage(p1, "§aFight started! Good luck!");
        Util.sendMessage(p2, "§aFight started! Fight fair!");
        Util.announceToAll(p1.server, "§6" + p1.getName().getString() + " §7vs §6" + p2.getName().getString() +
                " §7in arena §e" + arena.getName());
        LOGGER.info("[FightManager] Fight started between {} and {} in arena {}", p1.getName().getString(), p2.getName().getString(), arena.getName());
    }

    // =====================================================
    // === Fight End / Abort / Disconnect
    // =====================================================

    public void endFight(ServerLevel level, ActiveFight fight) {
        if (fight == null) return;

        ServerPlayer p1 = fight.player1;
        ServerPlayer p2 = fight.player2;
        ArenaData arena = fight.arena;

        Util.sendMessage(p1, "§eThe fight has ended!");
        Util.sendMessage(p2, "§eThe fight has ended!");

        InventoryStash.restoreFromPlayerTag(p1);
        InventoryStash.restoreFromPlayerTag(p2);

        rewardPlayers(p1, p2);

        if (Config.isAnnounceToAll()) {
            ServerPlayer winner = (p1.getHealth() >= p2.getHealth()) ? p1 : p2;
            ServerPlayer loser = (winner == p1) ? p2 : p1;

            Util.announceToAll(winner.server,
                    "§6⚔ " + winner.getName().getString() + " §7defeated §c" + loser.getName().getString() +
                    " §7in arena §e" + fight.arena.getName() + "§7!");
        }

        p1.setHealth(p1.getMaxHealth());
        p2.setHealth(p2.getMaxHealth());

        lobbyManager.teleportToLobby(p1);
        lobbyManager.teleportToLobby(p2);

        new FightProtections().cleanupArena(level, arena);
        arena.setAvailable(true);

        activeFights.remove(p1.getUUID());
        activeFights.remove(p2.getUUID());

        // Check if there are queued fights waiting for a free arena
        if (!queuedFights.isEmpty()) {
            PendingRequest next = queuedFights.poll();
            if (next != null) {
                ServerPlayer q1 = next.from;
                ServerPlayer q2 = next.target;
                ArenaData nextArena = arenaManager.getAvailableArena();
                if (q1 != null && q2 != null && nextArena != null) {
                    nextArena.setAvailable(false);
                    Util.sendMessage(q1, "§aAn arena just freed up! Starting your fight...");
                    Util.sendMessage(q2, "§aAn arena just freed up! Starting your fight...");
                    startTeleportAndCountdown(q1, q2, nextArena);
                    LOGGER.info("[FightManager] Queued fight between {} and {} has started.", p1.getName().getString(), p2.getName().getString());
                } else {
                    queuedFights.add(next); // Requeue if players missing or no arena yet
                }
            }
        }

        LOGGER.info("[FightManager] Fight ended in arena {}", arena.getName());
    }

    public void onPlayerDisconnect(ServerPlayer player) {
        ActiveFight fight = activeFights.get(player.getUUID());
        if (fight != null) {
            ServerPlayer opponent = (fight.player1.equals(player)) ? fight.player2 : fight.player1;
            Util.sendMessage(opponent, "§eYour opponent disconnected. You win!");
            endFight(opponent.serverLevel(), fight);
        }
    }

    public void abort(ServerLevel level) {
        for (ActiveFight fight : new ArrayList<>(activeFights.values())) {
            endFight(level, fight);
        }
        activeFights.clear();
        LOGGER.info("[FightManager] All fights aborted by admin.");
    }
 
    public String getQueueStatus() {
        return "§eQueued fights: §6" + queuedFights.size() + " §7waiting pair(s).";
    }

    public boolean isPlayerInFight(ServerPlayer player) {
        return player != null && activeFights.containsKey(player.getUUID());
    }
    public ActiveFight getActiveFightFor(ServerPlayer player) {
        if (player == null) return null;
        ActiveFight f = activeFights.get(player.getUUID());
        if (f != null) return f;
        for (ActiveFight fight : activeFights.values())
            if (fight.player1.equals(player) || fight.player2.equals(player))
                return fight;
        return null;
    }

    public ArenaData getArenaForPlayer(ServerPlayer player) {
        ActiveFight fight = getActiveFightFor(player);
        return (fight != null) ? fight.arena : null;
    }

    public ArenaData getArenaForItem(ItemEntity item) {
        if (item == null) return null;
        for (ArenaData arena : arenaManager.getAllArenas())
            if (arena.isConfigured() && arena.isInside(item.blockPosition()))
                return arena;
        return null;
    }

    public boolean isFrozen(ServerPlayer player) {
        return frozenPlayers.contains(player.getUUID());
    }

    public void setFrozen(ServerPlayer player, boolean frozen) {
        if (frozen) frozenPlayers.add(player.getUUID());
        else frozenPlayers.remove(player.getUUID());
    }

    private static class PendingRequest {
        final ServerPlayer from;
        final ServerPlayer target;
        final long timestamp;
        PendingRequest(ServerPlayer from, ServerPlayer target, long timestamp) {
            this.from = from;
            this.target = target;
            this.timestamp = timestamp;
        }
    }

    private static class ActiveFight {
        final ServerPlayer player1;
        final ServerPlayer player2;
        final ArenaData arena;
        public ActiveFight(ServerPlayer player1, ServerPlayer player2, ArenaData arena) {
            this.player1 = player1;
            this.player2 = player2;
            this.arena = arena;
        }
    }

    private void rewardPlayers(ServerPlayer p1, ServerPlayer p2) {
        ServerPlayer winner = (p1.getHealth() >= p2.getHealth()) ? p1 : p2;
        ServerPlayer loser = (winner == p1) ? p2 : p1;

        PvPFightConfigManager cfg = PvPFightConfigManager.loadOrCreate();

        // --- XP rewards ---
        winner.giveExperiencePoints(cfg.winnerXp);
        loser.giveExperiencePoints(cfg.loserXp);

        // --- Item rewards (fixed + random pool) ---
        String winItemId = PvPFightConfigManager.getRandomReward(true);
        int winAmount = cfg.winnerItemAmount;

        String loseItemId = PvPFightConfigManager.getRandomReward(false);
        int loseAmount = cfg.loserItemAmount;

        if (winItemId != null && !winItemId.isEmpty()) {
            giveItemReward(winner, winItemId, winAmount);
        }
        if (loseItemId != null && !loseItemId.isEmpty()) {
            giveItemReward(loser, loseItemId, loseAmount);
        }

        Util.sendMessage(winner, "§6You won the fight and received your reward!");
        Util.sendMessage(loser, "§7You lost the fight but received a consolation reward.");
    }


    private void giveItemReward(ServerPlayer player, String itemId, int amount) {
        var maybe = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(
                new net.minecraft.resources.ResourceLocation(itemId)
        );
        maybe.ifPresent(item -> {
            ItemStack stack = new ItemStack(item, Math.max(1, amount));
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        });
    }

}