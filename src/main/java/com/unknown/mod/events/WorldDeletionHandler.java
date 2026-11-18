package com.unknown.mod.events;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Удаляет только текущий одиночный мир после полного завершения integrated server.
 * Вызов выполняется исключительно при явно включённой настройке deleteWorldOnDeath.
 */
public final class WorldDeletionHandler {

    private static volatile File pendingWorldDirectory;

    private WorldDeletionHandler() {
    }

    public static void scheduleDeletion(World world) {
        if (world == null || world.isRemote) return;

        MinecraftServer server = world.getMinecraftServer();
        if (server == null || server.isDedicatedServer()) return;

        try {
            File savesDirectory = FMLCommonHandler.instance().getSavesDirectory().getCanonicalFile();
            World overworld = server.getWorld(0);
            if (overworld == null) return;

            File worldDirectory = overworld.getSaveHandler().getWorldDirectory().getCanonicalFile();
            File parent = worldDirectory.getParentFile();

            // Защита: цель должна быть ровно одной папкой внутри .minecraft/saves
            // и содержать level.dat. Папка saves целиком никогда не удаляется.
            if (parent == null || !parent.equals(savesDirectory)
                    || !worldDirectory.isDirectory()
                    || !new File(worldDirectory, "level.dat").isFile()) {
                return;
            }

            pendingWorldDirectory = worldDirectory;
            server.initiateShutdown();
        } catch (IOException exception) {
            pendingWorldDirectory = null;
        }
    }

    /**
     * Forge вызывает этот метод после полного завершения сервера и сохранения мира.
     * Затем удаляется только заранее проверенная папка текущего сохранения.
     */
    public static void deletePendingWorld() {
        File target = pendingWorldDirectory;
        pendingWorldDirectory = null;
        if (target == null) return;

        try {
            final Path root = target.toPath();
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                    if (exception != null) throw exception;
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            // Не завершаем процесс до попытки удаления всех доступных файлов.
            exception.printStackTrace();
        }

        // Жёсткое завершение JVM после остановки сервера и попытки удаления мира.
        Runtime.getRuntime().halt(1);
    }
}
