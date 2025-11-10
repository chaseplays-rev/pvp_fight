package com.example.pvpfight;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.example.pvpfight.PvPFightMod;

/**
 * FightCommands.java
 *
 * Defines all player and admin commands for the PvP Fight Mod.
 *
 *  PLAYER COMMANDS:
 *   - /fight <player>         → send fight request
 *   - /fight accept <player>  → accept a fight
 *   - /fight deny <player>    → deny a fight
 *   - /fight cancel           → cancel own request
 *   - /fight queue            → show queued fights
 *
 *  ADMIN COMMANDS (requires permission level 2+):
 *   - /fight abort            → immediately abort active fight
 *   - /fight reload           → reload config
 *   - /arena new <name>       → create a new arena (preferred)
 *   - /arena_new <name>       → create a new arena (legacy)
 *   - /arena setcorner 1|2    → define arena corners
 *   - /arena setspawn 1|2     → define arena spawn points
 *   - /arena info             → show arena info
 *   - /arena clear            → clear arena data
 */
@Mod.EventBusSubscriber
public class FightCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {

        // =======================
        // /fight main command
        // =======================
        dispatcher.register(
            Commands.literal("fight")
                // --- send fight request ---
                .then(Commands.argument("target", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayer from = ctx.getSource().getPlayerOrException();
                        String targetName = StringArgumentType.getString(ctx, "target");
                        ServerPlayer target = from.getServer().getPlayerList().getPlayerByName(targetName);

                        if (target == null) {
                            from.sendSystemMessage(Component.literal("§cPlayer not found: " + targetName));
                            return 0;
                        }
                        if (target == from) {
                            from.sendSystemMessage(Component.literal("§cYou cannot fight yourself."));
                            return 0;
                        }

                        PvPFightMod.getFightManager().sendRequest(from, target);
                        return 1;
                    }))
                
                // --- set lobby (admin only) ---
                .then(Commands.literal("setlobby")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> {
                        ServerPlayer admin = ctx.getSource().getPlayerOrException();
                        PvPFightMod.getLobbyManager().setLobbyPosition(admin);
                        admin.sendSystemMessage(Component.literal("§aLobby position has been set successfully."));
                        return 1;
                    }))
                
                // --- accept ---
                .then(Commands.literal("accept")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer self = ctx.getSource().getPlayerOrException();
                            String challengerName = StringArgumentType.getString(ctx, "player");
                            ServerPlayer challenger = self.getServer().getPlayerList().getPlayerByName(challengerName);

                            if (challenger == null) {
                                self.sendSystemMessage(Component.literal("§cPlayer not found: " + challengerName));
                                return 0;
                            }

                            PvPFightMod.getFightManager().accept(self, challenger);
                            return 1;
                        })))

                // --- deny ---
                .then(Commands.literal("deny")
                    .then(Commands.argument("player", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer self = ctx.getSource().getPlayerOrException();
                            String challengerName = StringArgumentType.getString(ctx, "player");
                            ServerPlayer challenger = self.getServer().getPlayerList().getPlayerByName(challengerName);

                            if (challenger == null) {
                                self.sendSystemMessage(Component.literal("§cPlayer not found: " + challengerName));
                                return 0;
                            }

                            PvPFightMod.getFightManager().deny(self, challenger);
                            return 1;
                        })))

                // --- cancel own request ---
                .then(Commands.literal("cancel")
                    .executes(ctx -> {
                        ServerPlayer self = ctx.getSource().getPlayerOrException();
                        PvPFightMod.getFightManager().cancelOwnRequest(self);
                        return 1;
                    }))

                // --- queue ---
                .then(Commands.literal("queue")
                    .executes(ctx -> {
                        ServerPlayer self = ctx.getSource().getPlayerOrException();
                        String info = PvPFightMod.getFightManager().getQueueStatus();
                        self.sendSystemMessage(Component.literal(info));
                        return 1;
                    }))

                // --- abort (admin only) ---
                .then(Commands.literal("abort")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> {
                        ServerPlayer admin = ctx.getSource().getPlayerOrException();
                        PvPFightMod.getFightManager().abort(admin.serverLevel());
                        admin.sendSystemMessage(Component.literal("§cFight aborted by admin."));
                        return 1;
                    }))

                // --- reload config (admin only) ---
                .then(Commands.literal("reload")
                    .requires(src -> src.hasPermission(2))
                    .executes(ctx -> {
                        ctx.getSource().sendSuccess(() -> Component.literal("§aPvP Fight config reloaded."), true);
                        return 1;
                    }))
        );

        // =======================
        // /arena command (admin)
        // =======================
        dispatcher.register(
            Commands.literal("arena")
                .requires(src -> src.hasPermission(2))
           
               .then(Commands.literal("finalize")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer admin = ctx.getSource().getPlayerOrException();
                            String name = StringArgumentType.getString(ctx, "name");
                            PvPFightMod.getArenaDataMulti().finalizeArena(admin, name);
                            return 1;
                        })
                    )
                )

                .then(Commands.literal("new")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer admin = ctx.getSource().getPlayerOrException();
                            String name = StringArgumentType.getString(ctx, "name");
                            PvPFightMod.getArenaDataMulti().createArena(admin, name);
                            admin.sendSystemMessage(Component.literal("§aArena '" + name + "' has been created."));
                            return 1;
                        })
                    )
                )
                // setcorner 1|2
                .then(Commands.literal("setcorner")
                    .then(Commands.argument("id", IntegerArgumentType.integer(1, 2))
                        .executes(ctx -> {
                            ServerPlayer admin = ctx.getSource().getPlayerOrException();
                            int id = IntegerArgumentType.getInteger(ctx, "id");
                            PvPFightMod.getArenaDataMulti().setCorner(admin, id);
                            return 1;
                        })))
                // setspawn 1|2
                .then(Commands.literal("setspawn")
                    .then(Commands.argument("id", IntegerArgumentType.integer(1, 2))
                        .executes(ctx -> {
                            ServerPlayer admin = ctx.getSource().getPlayerOrException();
                            int id = IntegerArgumentType.getInteger(ctx, "id");
                            PvPFightMod.getArenaDataMulti().setSpawn(admin, id);
                            return 1;
                        })))
                // info
                .then(Commands.literal("info")
                    .executes(ctx -> {
                        ServerPlayer admin = ctx.getSource().getPlayerOrException();
                        PvPFightMod.getArenaDataMulti().printInfo(admin);
                        return 1;
                    }))
                // clear
                .then(Commands.literal("clear")
                    .executes(ctx -> {
                        ServerPlayer admin = ctx.getSource().getPlayerOrException();
                        PvPFightMod.getArenaDataMulti().clear(admin);
                        return 1;
                    }))
        );

        // =======================
        // /arena_new <name> (admin, legacy)
        // =======================
        dispatcher.register(
            Commands.literal("arena_new")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> {
                        ServerPlayer admin = ctx.getSource().getPlayerOrException();
                        String name = StringArgumentType.getString(ctx, "name");
                        PvPFightMod.getArenaDataMulti().createArena(admin, name);
                        admin.sendSystemMessage(Component.literal("§aArena '" + name + "' has been created (legacy command)."));
                        return 1;
                    })
                )
        );

        // =======================
        // /lobby set (admin only)
        // =======================
        dispatcher.register(
            Commands.literal("lobby")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("set")
                    .executes(ctx -> {
                        ServerPlayer admin = ctx.getSource().getPlayerOrException();
                        PvPFightMod.getLobbyManager().setLobbyPosition(admin);
                        admin.sendSystemMessage(Component.literal("§aLobby position has been saved."));
                        return 1;
                    })
                )
        );
    }
}
