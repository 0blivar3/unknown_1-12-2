package com.unknown.mod.events;

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
        long elapsedDays = world.getTotalWorldTime() / 24000L;
        return (int) Math.min(6L, elapsedDays);
    }

    public static int getWatcherCooldown(Random random, int level) {
        int min;
        int max;

        switch (level) {
            case 1: min = 3600; max = 6000; break; // 3–5 минут
            case 2: min = 3000; max = 4800; break;
            case 3: min = 2400; max = 4200; break;
            default: min = 1800; max = 3600; break;
        }

        return min + random.nextInt(max - min + 1);
    }

    public static int getWatcherChance(int level, int encounters) {
        int baseChance = 8 + Math.max(0, level - 1) * 9;
        return Math.min(70, baseChance + encounters * 2);
    }

    public static double getWatcherSpawnDistance(Random random, int level) {
        int stage = Math.min(5, Math.max(0, level - 1));
        int minimum = 75 + stage * 12;
        return minimum + random.nextInt(21);
    }

    public static int getWatcherRelocationDistance(Random random, int level) {
        int stage = Math.min(5, Math.max(0, level - 1));
        int minimum = 55 + stage * 8;
        return minimum + random.nextInt(26);
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

        return min + random.nextInt(max - min + 1);
    }

    public static int getAnomalyChance(int level) {
        switch (level) {
            case 1: return 20;
            case 2: return 35;
            case 3: return 50;
            case 4: return 65;
            default: return 75;
        }
    }
}
