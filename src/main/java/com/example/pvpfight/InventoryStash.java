package com.example.pvpfight;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
public final class InventoryStash {
    private static final String ROOT_KEY = "pvp_fight:inv_stash";
    private static final String ITEMS_KEY = "Items";
    private static final String ARMOR_KEY = "Armor";
    private static final String OFF_KEY   = "Offhand";

    /** Idempotent: creates the stash only if absent; never overwrites. */
    public static boolean saveToPlayerTag(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (root.contains(ROOT_KEY)) {
            // Already snapshotted — do not overwrite!
            return false;
        }

        CompoundTag stash = new CompoundTag();
        CompoundTag itemsTag = new CompoundTag();
        CompoundTag armorTag = new CompoundTag();
        CompoundTag offTag   = new CompoundTag();

        ContainerHelper.saveAllItems(itemsTag, player.getInventory().items,  true);
        ContainerHelper.saveAllItems(armorTag, player.getInventory().armor,  true);
        ContainerHelper.saveAllItems(offTag,   player.getInventory().offhand,true);

        stash.put(ITEMS_KEY, itemsTag);
        stash.put(ARMOR_KEY, armorTag);
        stash.put(OFF_KEY,   offTag);

        root.put(ROOT_KEY, stash);
        return true;
    }

    public static boolean hasStash(ServerPlayer p) {
        return p.getPersistentData().contains(ROOT_KEY);
    }

    public static void restoreFromPlayerTag(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(ROOT_KEY)) return;

        CompoundTag stash = root.getCompound(ROOT_KEY);

        // atomic-ish swap into temps, then assign by slot
        NonNullList<ItemStack> tmpItems   = NonNullList.withSize(player.getInventory().items.size(), ItemStack.EMPTY);
        NonNullList<ItemStack> tmpArmor   = NonNullList.withSize(player.getInventory().armor.size(), ItemStack.EMPTY);
        NonNullList<ItemStack> tmpOffhand = NonNullList.withSize(player.getInventory().offhand.size(), ItemStack.EMPTY);

        if (stash.contains(ITEMS_KEY)) ContainerHelper.loadAllItems(stash.getCompound(ITEMS_KEY), tmpItems);
        if (stash.contains(ARMOR_KEY)) ContainerHelper.loadAllItems(stash.getCompound(ARMOR_KEY), tmpArmor);
        if (stash.contains(OFF_KEY))   ContainerHelper.loadAllItems(stash.getCompound(OFF_KEY),   tmpOffhand);

        // clear by slot (don’t use removeItem)
        for (int i = 0; i < player.getInventory().items.size(); i++)  player.getInventory().items.set(i, ItemStack.EMPTY);
        for (int i = 0; i < player.getInventory().armor.size(); i++)  player.getInventory().armor.set(i, ItemStack.EMPTY);
        for (int i = 0; i < player.getInventory().offhand.size(); i++) player.getInventory().offhand.set(i, ItemStack.EMPTY);

        // write restored stacks
        for (int i = 0; i < player.getInventory().items.size(); i++)  player.getInventory().items.set(i, tmpItems.get(i));
        for (int i = 0; i < player.getInventory().armor.size(); i++)  player.getInventory().armor.set(i, tmpArmor.get(i));
        for (int i = 0; i < player.getInventory().offhand.size(); i++) player.getInventory().offhand.set(i, tmpOffhand.get(i));

        player.containerMenu.setCarried(ItemStack.EMPTY);
        root.remove(ROOT_KEY);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }
}