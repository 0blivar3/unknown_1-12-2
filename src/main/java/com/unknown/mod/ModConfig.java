package com.unknown.mod;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;

/**
 * Настройки UNKNOWN. Значения по умолчанию сохраняют баланс первой версии.
 */
public final class ModConfig {

    private static Configuration config;

    public static boolean mysteryEnabled = true;
    public static boolean anomaliesEnabled = true;

    public static boolean watcherEnabled = true;

    public static boolean signAnomaly = true;
    public static boolean torchAnomaly = true;
    public static boolean creepyNoiseAnomaly = true;
    public static boolean footstepsAnomaly = true;
    public static boolean lonelyBlockAnomaly = true;
    public static boolean presenceTraceAnomaly = true;
    public static boolean openDoorAnomaly = true;
    public static boolean animalBehaviorAnomaly = true;
    public static boolean animalDisappearanceAnomaly = true;
    public static boolean ghastScreamAnomaly = true;

    public static int mysteryStartDay = 2;
    public static int daysPerMysteryLevel = 1;
    public static int maxMysteryLevel = 6;

    public static double anomalyCooldownMultiplier = 1.0D;
    public static double anomalyChanceMultiplier = 1.0D;
    public static double watcherCooldownMultiplier = 1.0D;
    public static double watcherChanceMultiplier = 1.0D;
    public static double watcherDistanceMultiplier = 1.0D;

    public static int watcherMinLifetimeSeconds = 15;
    public static int watcherMaxLifetimeSeconds = 90;

    private ModConfig() {
    }

    public static void load(File file) {
        config = new Configuration(file);
        sync();
    }

    public static Configuration getConfig() {
        return config;
    }

    public static void sync() {
        if (config == null) return;

        config.load();

        String general = "general";
        mysteryEnabled = config.get(general, "mysteryEnabled", true,
                "Включает общую систему постепенного появления мистики.").getBoolean(true);
        anomaliesEnabled = config.get(general, "anomaliesEnabled", true,
                "Глобально включает или отключает все случайные аномалии.").getBoolean(true);

        String characters = "characters";
        watcherEnabled = config.get(characters, "watcherEnabled", true,
                "Разрешает появление и существование Наблюдателя.").getBoolean(true);

        String anomalies = "anomalies";
        signAnomaly = getBoolean(anomalies, "signs", true, "Появление загадочных табличек.");
        torchAnomaly = getBoolean(anomalies, "torches", true, "Появление и исчезновение факелов.");
        creepyNoiseAnomaly = getBoolean(anomalies, "creepyNoise", true, "Случайные тревожные звуки.");
        footstepsAnomaly = getBoolean(anomalies, "footsteps", true, "Ложные шаги поблизости.");
        lonelyBlockAnomaly = getBoolean(anomalies, "lonelyBlocks", true, "Появление одиноких блоков.");
        presenceTraceAnomaly = getBoolean(anomalies, "presenceTraces", true, "Появление небольших следов присутствия.");
        openDoorAnomaly = getBoolean(anomalies, "openDoors", true, "Самопроизвольное открытие дверей.");
        animalBehaviorAnomaly = getBoolean(anomalies, "animalBehavior", true, "Странное поведение мирных животных.");
        animalDisappearanceAnomaly = getBoolean(anomalies, "animalDisappearance", true, "Бесследное исчезновение животных.");
        ghastScreamAnomaly = getBoolean(anomalies, "distantGhastScream", true, "Далёкий крик гаста без появления самого гаста.");

        String progression = "progression";
        mysteryStartDay = config.get(progression, "startDay", 2,
                "Игровой день, начиная с которого начинается мистика.", 1, 1000).getInt(2);
        daysPerMysteryLevel = config.get(progression, "daysPerLevel", 1,
                "Количество игровых дней на повышение одного уровня мистики.", 1, 100).getInt(1);
        maxMysteryLevel = config.get(progression, "maxLevel", 6,
                "Максимальный уровень мистики (от 1 до 6).", 1, 6).getInt(6);

        String frequency = "frequency";
        anomalyCooldownMultiplier = getMultiplier(frequency, "anomalyCooldownMultiplier",
                "Множитель частоты проверок аномалий. Больше 1 — чаще.");
        anomalyChanceMultiplier = getMultiplier(frequency, "anomalyChanceMultiplier",
                "Множитель шанса срабатывания аномалии.");
        watcherCooldownMultiplier = getMultiplier(frequency, "watcherCooldownMultiplier",
                "Множитель частоты проверок появления Наблюдателя. Больше 1 — чаще.");
        watcherChanceMultiplier = getMultiplier(frequency, "watcherChanceMultiplier",
                "Множитель шанса появления Наблюдателя.");
        watcherDistanceMultiplier = getMultiplier(frequency, "watcherDistanceMultiplier",
                "Множитель дистанции появления и перемещения Наблюдателя.");

        String watcher = "watcher";
        Property minLifetime = config.get(watcher, "minLifetimeSeconds", 15,
                "Минимальное время существования Наблюдателя в секундах.", 1, 120);
        Property maxLifetime = config.get(watcher, "maxLifetimeSeconds", 90,
                "Максимальное время существования Наблюдателя в секундах.", 1, 180);
        watcherMinLifetimeSeconds = minLifetime.getInt(15);
        watcherMaxLifetimeSeconds = maxLifetime.getInt(90);

        // Миграция старых значений по умолчанию (8–24 сек.) на согласованные 15–90 сек.
        // Пользовательские значения не затрагиваются.
        if (watcherMinLifetimeSeconds == 8 && watcherMaxLifetimeSeconds == 24) {
            watcherMinLifetimeSeconds = 15;
            watcherMaxLifetimeSeconds = 90;
            minLifetime.set(watcherMinLifetimeSeconds);
            maxLifetime.set(watcherMaxLifetimeSeconds);
        }

        if (watcherMaxLifetimeSeconds < watcherMinLifetimeSeconds) {
            watcherMaxLifetimeSeconds = watcherMinLifetimeSeconds;
            maxLifetime.set(watcherMaxLifetimeSeconds);
        }

        if (config.hasChanged()) {
            config.save();
        }
    }

    private static boolean getBoolean(String category, String key, boolean defaultValue, String comment) {
        return config.get(category, key, defaultValue, comment).getBoolean(defaultValue);
    }

    private static double getMultiplier(String category, String key, String comment) {
        return config.get(category, key, 1.0D, comment, 0.1D, 5.0D).getDouble(1.0D);
    }
}
