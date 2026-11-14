package com.unknown.mod.events;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Поиск безопасных точек для Наблюдателя в подземных пространствах.
 * Не использует getTopSolidOrLiquidBlock: он всегда ориентирован на поверхность.
 */
public final class CaveWatcherBehavior {

    private CaveWatcherBehavior() {
    }

    public static boolean isPlayerInCave(EntityPlayer player) {
        World world = player.world;
        BlockPos playerPos = player.getPosition();

        if (world.canSeeSky(playerPos)) return false;

        // Проверяем, что над игроком действительно есть потолок, а не только навес.
        for (int y = 2; y <= 10; y++) {
            BlockPos above = playerPos.up(y);
            if (!world.isBlockLoaded(above)) return false;

            IBlockState state = world.getBlockState(above);
            if (state.getMaterial().blocksMovement()) {
                return hasNearbyCaveWall(world, playerPos, 4);
            }
        }

        return false;
    }

    public static BlockPos findPosition(EntityPlayer player, double minDistance, double maxDistance) {
        World world = player.world;
        Random random = world.rand;

        for (int attempt = 0; attempt < 180; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);

            int x = MathHelperFloor(player.posX + Math.cos(angle) * distance);
            int z = MathHelperFloor(player.posZ + Math.sin(angle) * distance);
            int y = MathHelperFloor(player.posY + (random.nextDouble() * 24.0D - 12.0D));

            if (y < 5 || y >= world.getHeight() - 8) continue;

            BlockPos candidate = new BlockPos(x, y, z);
            if (!world.isBlockLoaded(candidate)) continue;
            if (world.canSeeSky(candidate)) continue;
            if (!isSafeCavePosition(world, candidate)) continue;

            // Не ставим силуэт прямо в центре взгляда игрока.
            double dx = candidate.getX() + 0.5D - player.posX;
            double dz = candidate.getZ() + 0.5D - player.posZ;
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDistance < minDistance || horizontalDistance > maxDistance) continue;

            return candidate;
        }

        return null;
    }

    private static boolean isSafeCavePosition(World world, BlockPos pos) {
        BlockPos feet = pos;
        BlockPos head = pos.up();
        BlockPos ground = pos.down();

        if (!world.isAirBlock(feet) || !world.isAirBlock(head)) return false;
        if (world.getBlockState(feet).getMaterial().isLiquid()
                || world.getBlockState(head).getMaterial().isLiquid()) return false;

        IBlockState floor = world.getBlockState(ground);
        if (!floor.isSideSolid(world, ground, EnumFacing.UP)) return false;
        if (floor.getMaterial().isLiquid()) return false;

        // Над головой должен быть потолок на расстоянии 2–6 блоков.
        boolean hasCeiling = false;
        for (int y = 2; y <= 6; y++) {
            BlockPos above = pos.up(y);
            if (!world.isBlockLoaded(above)) return false;
            if (world.getBlockState(above).getMaterial().blocksMovement()) {
                hasCeiling = true;
                break;
            }
        }
        if (!hasCeiling) return false;

        // Требуем хотя бы одну стену поблизости: это должна быть пещера или тоннель,
        // а не случайная воздушная полость в открытом пространстве.
        if (!hasNearbyCaveWall(world, pos, 3)) return false;

        AxisAlignedBB box = new AxisAlignedBB(
                pos.getX() + 0.2D, pos.getY(), pos.getZ() + 0.2D,
                pos.getX() + 0.8D, pos.getY() + 1.8D, pos.getZ() + 0.8D
        );

        return world.getCollisionBoxes(null, box).isEmpty();
    }

    private static boolean hasNearbyCaveWall(World world, BlockPos pos, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -1; y <= 3; y++) {
                    if (x == 0 && z == 0) continue;

                    BlockPos check = pos.add(x, y, z);
                    if (!world.isBlockLoaded(check)) continue;

                    if (world.getBlockState(check).getMaterial().blocksMovement()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static int MathHelperFloor(double value) {
        return (int) Math.floor(value);
    }
}
