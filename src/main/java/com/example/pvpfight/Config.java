package com.example.pvpfight;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Serverkonfiguration für die PvPFight-Mod.
 * 
 * Diese Klasse definiert alle einstellbaren Optionen, die das Verhalten des Kampfsystems,
 * die Countdownzeiten, Arena-Optionen und allgemeine Parameter steuern.
 * 
 * Änderungen in der Konfigurationsdatei greifen nach einem Neustart des Servers.
 */
public class Config {

    // Forge ConfigSpec-Instanz
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // === Allgemeine Kampfparameter ===
    public static final ForgeConfigSpec.IntValue requestTimeoutSeconds;
    public static final ForgeConfigSpec.IntValue countdownSeconds;
    public static final ForgeConfigSpec.IntValue postWinInvulnerabilitySeconds;
    public static final ForgeConfigSpec.BooleanValue allowEnvironmentDamage;
    public static final ForgeConfigSpec.BooleanValue forfeitOnLeaveArena;
    public static final ForgeConfigSpec.BooleanValue announceToAll;
    // === Belohnungen (Rewards) ===
    public static final ForgeConfigSpec.IntValue winnerXp;
    public static final ForgeConfigSpec.IntValue loserXp;
    public static final ForgeConfigSpec.ConfigValue<String> winnerItem;
    public static final ForgeConfigSpec.ConfigValue<String> loserItem;

    static {
        BUILDER.push("general");

        requestTimeoutSeconds = BUILDER
                .comment("Time (seconds) after which a fight request automatically expires.")
                .defineInRange("requestTimeoutSeconds", 15, 1, 300);

        countdownSeconds = BUILDER
                .comment("Countdown (seconds) between acceptance and start of the fight.")
                .defineInRange("countdownSeconds", 3, 1, 30);

        postWinInvulnerabilitySeconds = BUILDER
                .comment("Duration (seconds) of the winner’s invulnerability after the fight ends.")
                .defineInRange("postWinInvulnerabilitySeconds", 5, 0, 30);

        allowEnvironmentDamage = BUILDER
                .comment("If true, environmental effects (fire, fall, lava, etc.) cause damage during fights.")
                .define("allowEnvironmentDamage", false);

        forfeitOnLeaveArena = BUILDER
                .comment("If true, a player automatically loses when leaving the arena.")
                .define("forfeitOnLeaveArena", true);

        announceToAll = BUILDER
                .comment("If true, fight victories are announced in the global chat.")
                .define("announceToAll", true);

        BUILDER.pop();
            // === Reward settings ===
        BUILDER.push("rewards");

        winnerXp = BUILDER
                .comment("How many XP points the winner gets after a fight.")
                .defineInRange("winnerXp", 100, 0, 10000);

        loserXp = BUILDER
                .comment("How many XP points the loser gets after a fight.")
                .defineInRange("loserXp", 25, 0, 10000);

        winnerItem = BUILDER
                .comment("Item given to the winner (use Minecraft item ID, e.g., 'minecraft:diamond'). Leave empty for none.")
                .define("winnerItem", "minecraft:diamond");

        loserItem = BUILDER
                .comment("Item given to the loser (use Minecraft item ID, e.g., 'minecraft:bread'). Leave empty for none.")
                .define("loserItem", "");

        BUILDER.pop(); // closes "rewards"



        SPEC = BUILDER.build();
    }

    public Config() {
        // keine Instanzierung erlaubt
    }

    // === Getter-Hilfsmethoden für besseren Zugriff ===

    public static int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds.get();
    }

    public static int getCountdownSeconds() {
        return countdownSeconds.get();
    }

    public static int getPostWinInvulnerabilitySeconds() {
        return postWinInvulnerabilitySeconds.get();
    }

    public static boolean isEnvironmentDamageAllowed() {
        return allowEnvironmentDamage.get();
    }

    public static boolean isForfeitOnLeaveArena() {
        return forfeitOnLeaveArena.get();
    }

    public static boolean isAnnounceToAll() {
        return announceToAll.get();
    }
    // === Reward Getter-Methoden ===

    public static int getWinnerXp() {
        return winnerXp.get();
    }

    public static int getLoserXp() {
        return loserXp.get();
    }

    public static String getWinnerItem() {
        return winnerItem.get();
    }

    public static String getLoserItem() {
        return loserItem.get();
    }
}
