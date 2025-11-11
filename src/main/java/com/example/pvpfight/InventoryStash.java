package com.example.pvpfight;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class InventoryStash {
    private static final String ROOT_KEY   = "pvp_fight:inv_stash";
    private static final String ITEMS_KEY  = "Items";
    private static final String ARMOR_KEY  = "Armor";
    private static final String OFF_KEY    = "Offhand";

    private InventoryStash() {}

    public static void saveToPlayerTag(Player player) {
        CompoundTag root = player.getPersistentData();
        CompoundTag stash = new CompoundTag();

        CompoundTag itemsTag = new CompoundTag();
        CompoundTag armorTag = new CompoundTag();
        CompoundTag offTag   = new CompoundTag();

        ContainerHelper.saveAllItems(itemsTag, player.getInventory().items, true);
        ContainerHelper.saveAllItems(armorTag, player.getInventory().armor, true);
        ContainerHelper.saveAllItems(offTag,   player.getInventory().offhand, true);

        stash.put(ITEMS_KEY, itemsTag);
        stash.put(ARMOR_KEY, armorTag);
        stash.put(OFF_KEY,   offTag);

        root.put(ROOT_KEY, stash);
    }

    public static void restoreFromPlayerTag(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(ROOT_KEY)) return;

        CompoundTag stash = root.getCompound(ROOT_KEY);

        NonNullList<ItemStack> items   = player.getInventory().items;
        NonNullList<ItemStack> armor   = player.getInventory().armor;
        NonNullList<ItemStack> offhand = player.getInventory().offhand;

        if (stash.contains(ITEMS_KEY)) {
            ContainerHelper.loadAllItems(stash.getCompound(ITEMS_KEY), items);
        }
        if (stash.contains(ARMOR_KEY)) {
            ContainerHelper.loadAllItems(stash.getCompound(ARMOR_KEY), armor);
        }
        if (stash.contains(OFF_KEY)) {
            ContainerHelper.loadAllItems(stash.getCompound(OFF_KEY), offhand);
        }

        player.inventoryMenu.broadcastChanges();
        clearStash(root);
    }

    public static void clearStash(CompoundTag root) {
        root.remove(ROOT_KEY);
    }
}