package com.example.pvpfight;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * FightProtections.java
 *
 * Handles in-fight protections:
 *  - Prevents invalid damage / deaths
 *  - Prevents item toss or block edits in arena
 *  - Prevents leaving arena bounds
 *  - Cleans up arenas after fights
 *  - Deletes leftover tagged items when inventories open
 */
@Mod.EventBusSubscriber
public class FightProtections {

    private static final Logger LOGGER = LogUtils.getLogger();

    // =====================================================
    // === Combat Protection Events
    // =====================================================

    /** Prevents players from dying during a fight (1 HP limit). */
    @SubscribeEvent
    public static void onPlayerDamage(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!PvPFightMod.getFightManager().isPlayerInFight(sp)) return;

        DamageSource src = event.getSource();
        if (src == null) return;

        double newHealth = sp.getHealth() - event.getAmount();
        if (newHealth <= 1.0F) {
            event.setCanceled(true);
            sp.setHealth(1.0F);
            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou reached 1 HP! The fight ends now."));
            endFight(sp);
        }
    }

    /** Prevents dropping items outside the arena bounds. */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        ServerPlayer sp = (ServerPlayer) event.getPlayer();
        if (!PvPFightMod.getFightManager().isPlayerInFight(sp)) return;

        ItemEntity item = event.getEntity();
        ArenaData arena = PvPFightMod.getFightManager().getArenaForItem(item);
        if (arena == null || !arena.isInside(item.blockPosition())) {
            event.setCanceled(true);
            item.discard();
            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7You cannot throw items outside the arena."));
            LOGGER.debug("[FightProtections] Deleted item outside arena bounds from {}", sp.getName().getString());
        }
    }

    /** Prevents player death screen from triggering (since we handle 1 HP logic). */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (PvPFightMod.getFightManager().isPlayerInFight(sp)) {
            event.setCanceled(true);
            sp.setHealth(1.0F);
            endFight(sp);
        }
    }

    /** Ensures player is removed cleanly when disconnecting during a fight. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (PvPFightMod.getFightManager().isPlayerInFight(sp)) {
            PvPFightMod.getFightManager().onPlayerDisconnect(sp);
        }
    }

    // =====================================================
    // === NEW: Block Break / Place Protection
    // =====================================================

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!(player instanceof ServerPlayer sp)) return;
        if (PvPFightMod.getFightManager().isPlayerInFight(sp)) {
            event.setCanceled(true);
            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7You cannot break blocks during a fight."));
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (PvPFightMod.getFightManager().isPlayerInFight(sp)) {
            event.setCanceled(true);
            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7You cannot place blocks during a fight."));
        }
    }

    // =====================================================
    // === Combined Player Tick Handler (Freeze + Bounds)
    // =====================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
        if (sp.level().isClientSide) return;

        FightManager fightManager = PvPFightMod.getFightManager();
        if (fightManager == null) return;

        // --- Handle freeze enforcement ---
        if (fightManager.isFrozen(sp)) {
            sp.teleportTo(sp.getX(), sp.getY(), sp.getZ());
            sp.setDeltaMovement(0, 0, 0);
            return; // no further checks needed if frozen
        }

        // --- Handle arena bounds enforcement ---
        if (!fightManager.isPlayerInFight(sp)) return;

        ArenaData arena = fightManager.getArenaForPlayer(sp);
        if (arena == null) return;

        if (!arena.isInside(sp.blockPosition())) {
            LOGGER.debug("[FightProtections] {} left arena bounds, teleporting back.", sp.getName().getString());
            sp.teleportTo(sp.serverLevel(),
                    arena.getSpawn1().getX() + 0.5,
                    arena.getSpawn1().getY(),
                    arena.getSpawn1().getZ() + 0.5,
                    sp.getYRot(),
                    sp.getXRot());
            sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou cannot leave the arena!"));
        }
    }


    // =====================================================
    // === Post-Fight Cleanup Logic
    // =====================================================

    /** Cleans all dropped items inside a given arena after the fight ends. */
    public void cleanupArena(ServerLevel level, ArenaData arena) {
        if (arena == null || level == null) return;

        int removed = 0;
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(
                        arena.getCorner1().getX(), arena.getCorner1().getY(), arena.getCorner1().getZ(),
                        arena.getCorner2().getX(), arena.getCorner2().getY(), arena.getCorner2().getZ())
        );

        for (ItemEntity item : items) {
            item.discard();
            removed++;
        }

        LOGGER.info("[FightProtections] Cleaned up {} dropped items in arena '{}'", removed, arena.getName());
    }

    // =====================================================
    // === Internal Utility
    // =====================================================

   private static void endFight(ServerPlayer player) {
    ArenaData arena = PvPFightMod.getFightManager().getArenaForPlayer(player);
        if (arena == null) {
            LOGGER.warn("[FightProtections] Could not resolve arena for player {} during endFight()", player.getName().getString());
            return;
        }

        PvPFightMod.getFightManager().endFight(
            player.serverLevel(),
            PvPFightMod.getFightManager().getActiveFightFor(player)
        );
    }
    // =====================================================
    // === Global Tag Cleanup (anti-dupe)
    // =====================================================

    @SubscribeEvent
    public static void onContainerOpenCleanup(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        int removed = 0;
        final String TAG_KEY = "pvpfight_session";

        // check main inventory
        for (ItemStack stack : sp.getInventory().items)
            if (hasFightTag(stack, TAG_KEY)) { stack.setCount(0); removed++; }

        // armor
        for (ItemStack stack : sp.getInventory().armor)
            if (hasFightTag(stack, TAG_KEY)) { stack.setCount(0); removed++; }

        // offhand
        for (ItemStack stack : sp.getInventory().offhand)
            if (hasFightTag(stack, TAG_KEY)) { stack.setCount(0); removed++; }

        // opened container slots
        for (Slot slot : event.getContainer().slots) {
            ItemStack s = slot.getItem();
            if (hasFightTag(s, TAG_KEY)) {
                slot.set(ItemStack.EMPTY);
                removed++;
            }
        }

        if (removed > 0) {
            sp.inventoryMenu.broadcastChanges();
            sp.displayClientMessage(Component.literal("§cIllegal PvPFight items were removed!"), true);
            LOGGER.warn("[PvPFight] Removed {} tagged items from {}", removed, sp.getName().getString());
        }
    }

    private static boolean hasFightTag(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.hasTag() && stack.getTag().contains(key);
    }
}