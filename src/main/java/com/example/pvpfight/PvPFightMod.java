package com.example.pvpfight;

import com.mojang.logging.LogUtils;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;


@Mod(PvPFightMod.MODID)
@Mod.EventBusSubscriber(modid = PvPFightMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public class PvPFightMod {

        public static final String MODID = "pvpfight";
        private static final Logger LOGGER = LogUtils.getLogger();

        private static FightManager fightManager;
        private static ArenaDataMulti arenaDataMulti;
        private static LobbyManager lobbyManager;
        private static Config config;

        public PvPFightMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(this);

        // Initialize managers once at startup
        arenaDataMulti = new ArenaDataMulti();
        lobbyManager = new LobbyManager();
        config = new Config();
        fightManager = new FightManager(arenaDataMulti, lobbyManager);

        LOGGER.info("[PvPFightMod] Initialized all core managers successfully.");
    }

    public static FightManager getFightManager() {
        return fightManager;
    }

    public static Config getConfig() {
    return config;
    }

        public static ArenaDataMulti getArenaDataMulti() {
    return arenaDataMulti;
    }


    public static LobbyManager getLobbyManager() {
        return lobbyManager;
    }

    // 🔧 Command Registration
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LOGGER.info("[PvPFight] Registering commands...");
        FightCommands.register(event.getDispatcher());
        LOGGER.info("[PvPFight] Commands registered successfully.");
    }

    // 🧱 Server Startup
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[PvPFight] Server is starting. Loading configuration and arena data...");

        try {
            // ✅ Load PvPFight config from /config/pvpfight/config.json
            PvPFightConfigManager cfg = PvPFightConfigManager.loadOrCreate();

            Config.requestTimeoutSeconds.set(cfg.requestTimeoutSeconds);
            Config.countdownSeconds.set(cfg.countdownSeconds);
            Config.postWinInvulnerabilitySeconds.set(cfg.postWinInvulnerabilitySeconds);
            Config.allowEnvironmentDamage.set(cfg.allowEnvironmentDamage);
            Config.forfeitOnLeaveArena.set(cfg.forfeitOnLeaveArena);
            Config.announceToAll.set(cfg.announceToAll);
            Config.winnerXp.set(cfg.winnerXp);
            Config.loserXp.set(cfg.loserXp);
            Config.winnerItem.set(cfg.winnerItem);
            Config.loserItem.set(cfg.loserItem);

            LOGGER.info("[PvPFight] All configurations, arenas, and lobby loaded successfully.");
        } catch (Exception e) {
            LOGGER.error("[PvPFight] Error during server startup: {}", e.getMessage());
            e.printStackTrace();
        }
        ArenaDataMulti.loadArenas();

        LobbyManager.loadLobby();
    }
}
