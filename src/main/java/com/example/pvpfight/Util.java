package com.example.pvpfight;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
/**
 * Utility methods for PvPFight Mod — used for sending formatted messages to players.
 * Handles normal text, colored system messages, and clickable Accept/Deny buttons.
 */
public class Util {

    /**
     * Sends a normal colored chat message to a player.
     * @param player Target player
     * @param text Message text (supports § color codes)
     */
    public static void sendMessage(ServerPlayer player, String text) {
        // Replace § formatting with Minecraft JSON color components automatically
        if (player != null) {
            player.displayClientMessage(Component.literal(text), false);
        }
    }
    /**
     * Sends clickable Accept / Deny buttons to a challenged player.
     * Fixes the issue where click events didn’t trigger when sent via sendSystemMessage().
     *
     * @param target         The challenged player who receives the buttons
     * @param challengerName The name of the player who sent the request
     */
    public static void sendClickableAcceptDeny(ServerPlayer target, String challengerName) {
        Component accept = Component.literal("[Accept]")
            .withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fight accept " + challengerName))
            );

        Component deny = Component.literal("[Deny]")
            .withStyle(style -> style
                .withColor(ChatFormatting.RED)
                .withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fight deny " + challengerName))
            );

        Component message = Component.literal("§eClick to respond: ")
            .append(accept)
            .append(Component.literal(" "))
            .append(deny);

        // ✅ Correct for Forge 1.20.x
        target.displayClientMessage(message, false);
    }


    /**
     * Utility to broadcast a message to both players in a fight.
     */
    public static void sendFightMessage(ServerPlayer p1, ServerPlayer p2, String text) {
        sendMessage(p1, text);
        sendMessage(p2, text);
    }

    /**
     * Formats numbers or other display text consistently (optional helper).
     */
    public static String color(String text) {
        return text.replace("&", "§");
    }
    public static void announceToAll(MinecraftServer server, String message) {
        server.getPlayerList().getPlayers().forEach(p ->
            p.sendSystemMessage(Component.literal(message))
        );
    }
}
