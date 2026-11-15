package com.unknown.mod.events;

import com.unknown.mod.ModConfig;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Общая система постепенного появления мистики.
 * Использует общее игровое время мира, которое не сбрасывается после сна.
 */
public final class MysteryProgression {

    private MysteryProgression() {
    }

    /**
     * Возвращает уровень мистики от 0 до 6.
     * 0 — первые 24 000 тиков (первый игровой день).
     * 1 — второй день, далее уровень растёт каждый день до максимума.
     */
    public static int getLevel(World world) {
        if (!ModConfig.mysteryEnabled) return 0;

        long currentDay = world.getTotalWorldTime() / 24000L + 1L;
        if (currentDay < ModConfig.mysteryStartDay) return 0;

        long level = ((currentDay - ModConfig.mysteryStartDay)
                / ModConfig.daysPerMysteryLevel) + 1L;
        return (int) Math.min((long) ModConfig.maxMysteryLevel, level);
    }

    public static int getWatcherCooldown(Random random, int level) {
        int min;
        int max;

        switch (level) {
            case 1: min = 4800; max = 8400; break; // 4–7 минут
            case 2: min = 4200; max = 7200; break;
            case 3: min = 3600; max = 6000; break;
            default: min = 3000; max = 5400; break;
        }

        int cooldown = min + random.nextInt(max - min + 1);
        return Math.max(1, (int) Math.round(cooldown / ModConfig.watcherCooldownMultiplier));
    }

    public static int getWatcherChance(int level, int encounters) {
        int baseChance = 2 + Math.max(0, level - 1) * 2;
        int chance = Math.min(18, baseChance + encounters);
        return Math.min(100, (int) Math.round(chance * ModConfig.watcherChanceMultiplier));
    }

    public static double getWatcherSpawnDistance(Random random, int level) {
        int stage = Math.min(5, Math.max(0, level - 1));
        int minimum = 55 + stage * 7;
        return (minimum + random.nextInt(21)) * ModConfig.watcherDistanceMultiplier;
    }

    public static int getWatcherRelocationDistance(Random random, int level) {
        int stage = Math.min(5, Math.max(0, level - 1));
        int minimum = 45 + stage * 6;
        return (int) Math.round((minimum + random.nextInt(21)) * ModConfig.watcherDistanceMultiplier);
    }

    public static int getAnomalyCooldown(Random random, int level) {
        int min;
        int max;

        switch (level) {
            case 1: min = 6000; max = 9000; break; // 5–7.5 минут
            case 2: min = 4800; max = 7200; break;
            case 3: min = 3600; max = 6000; break;
            default: min = 2400; max = 4800; break;
        }

        int cooldown = min + random.nextInt(max - min + 1);
        return Math.max(1, (int) Math.round(cooldown / ModConfig.anomalyCooldownMultiplier));
    }

    public static int getAnomalyChance(int level) {
        int chance;
        switch (level) {
            case 1: chance = 20; break;
            case 2: chance = 35; break;
            case 3: chance = 50; break;
            case 4: chance = 65; break;
            default: chance = 75; break;
        }
        return Math.min(100, (int) Math.round(chance * ModConfig.anomalyChanceMultiplier));
    }
}
