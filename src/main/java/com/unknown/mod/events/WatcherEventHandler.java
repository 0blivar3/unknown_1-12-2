
package com.unknown.mod.events;

import com.unknown.mod.entity.EntityWatcher;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraft.entity.EntityLiving;

import java.util.Random;

public class WatcherEventHandler {

    private static final String WATCHER_TAG = "unknown_watcher";
    private static final String COOLDOWN_TAG = "unknown_watcher_cooldown";
    private static final String HIDDEN_TICKS_TAG = "unknown_hidden_ticks";
    private static final String MOVE_DELAY_TAG = "unknown_move_delay";

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {

        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.world.isRemote) return;

        EntityPlayer player = event.player;

        int cooldown = player.getEntityData().getInteger(COOLDOWN_TAG);

        if (cooldown <= 0) {

            Random random = player.world.rand;

            if (!player.getEntityData().hasKey(COOLDOWN_TAG)) {
                player.getEntityData().setInteger(
                        COOLDOWN_TAG,
                        200 + random.nextInt(200)
                );
                return;
            }

            player.getEntityData().setInteger(
                    COOLDOWN_TAG,
                    900 + random.nextInt(900)
            );

            if (random.nextInt(2) == 0) {
                spawnWatcher(player);
            }

        } else {
            player.getEntityData().setInteger(
                    COOLDOWN_TAG,
                    cooldown - 1
            );
        }
    }

    private void spawnWatcher(EntityPlayer player) {

        Random random = player.world.rand;

        BlockPos pos = findPosition(
                player,
                45 + random.nextInt(26),
                true
        );

        if (pos == null) return;

        EntityWatcher watcher = new EntityWatcher(player.world);

        watcher.setPosition(
                pos.getX() + 0.5,
                pos.getY(),
                pos.getZ() + 0.5
        );

        watcher.getEntityData().setBoolean(WATCHER_TAG, true);
        watcher.getEntityData().setInteger(
                MOVE_DELAY_TAG,
                60 + random.nextInt(101)
        );

        player.world.spawnEntity(watcher);
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {

        Entity entity = event.getEntity();

        if (entity.world.isRemote) return;
        if (!(entity instanceof EntityWatcher)) return;

        if (!entity.getEntityData().getBoolean(WATCHER_TAG)) return;

        EntityPlayer player = entity.world.getClosestPlayerToEntity(
                entity,
                128.0D
        );

        if (player == null) return;

        if (entity.getDistance(player) < 10.0F) {
            entity.setDead();
            return;
        }

        // Поворачиваем Наблюдателя к игроку.
        double dx = player.posX - entity.posX;
        double dz = player.posZ - entity.posZ;

        float yaw = (float) (
                Math.atan2(dz, dx) * 180.0D / Math.PI
        ) - 90.0F;

        EntityLiving living = (EntityLiving) entity;

        living.rotationYaw = yaw;
        living.rotationYawHead = yaw;
        living.renderYawOffset = yaw;

        // Проверяем, видит ли игрок Наблюдателя.
        boolean visible = canPlayerSee(player, entity);

        if (visible) {
            entity.getEntityData().setInteger(HIDDEN_TICKS_TAG, 0);
            return;
        }

        int hiddenTicks = entity.getEntityData().getInteger(
                HIDDEN_TICKS_TAG
        ) + 1;

        entity.getEntityData().setInteger(
                HIDDEN_TICKS_TAG,
                hiddenTicks
        );

        int moveDelay = entity.getEntityData().getInteger(
                MOVE_DELAY_TAG
        );

        if (hiddenTicks >= moveDelay) {

            Random random = entity.world.rand;

            BlockPos newPos = findPosition(
                    player,
                    35 + random.nextInt(26),
                    true
            );

            if (newPos != null) {

                entity.setPositionAndUpdate(
                        newPos.getX() + 0.5,
                        newPos.getY(),
                        newPos.getZ() + 0.5
                );
            }

            // Следующее перемещение через 3-8 секунд.
            entity.getEntityData().setInteger(
                    HIDDEN_TICKS_TAG,
                    0
            );

            entity.getEntityData().setInteger(
                    MOVE_DELAY_TAG,
                    60 + random.nextInt(101)
            );
        }
    }

    private boolean canPlayerSee(EntityPlayer player, Entity watcher) {

        Vec3d eyes = player.getPositionEyes(1.0F);
        Vec3d look = player.getLook(1.0F);

        Vec3d target = watcher.getPositionVector().addVector(
                0.0D,
                watcher.height * 0.5D,
                0.0D
        );

        Vec3d direction = target.subtract(eyes);
        double distance = direction.lengthVector();

        if (distance <= 0.0D) return true;

        direction = direction.normalize();

        // Проверяем, направлен ли взгляд на Наблюдателя.
        double dot = look.dotProduct(direction);

        if (dot < 0.94D) return false;

        // Проверяем препятствия между игроком и сущностью.
        return player.world.rayTraceBlocks(
                eyes,
                target,
                false,
                true,
                false
        ) == null;
    }

    private BlockPos findPosition(
            EntityPlayer player,
            double distance,
            boolean avoidView
    ) {

        Random random = player.world.rand;

        for (int attempt = 0; attempt < 30; attempt++) {

            double angle = random.nextDouble() * Math.PI * 2.0D;

            double x = player.posX + Math.cos(angle) * distance;
            double z = player.posZ + Math.sin(angle) * distance;

            BlockPos pos = player.world.getTopSolidOrLiquidBlock(
                    new BlockPos(x, player.posY, z)
            );

            if (!player.world.isBlockLoaded(pos)) continue;

            if (!player.world.isAirBlock(pos)) continue;
            if (!player.world.isAirBlock(pos.up())) continue;

            if (!player.world.getBlockState(pos.down())
                    .getMaterial().blocksMovement()) continue;

            if (avoidView) {

                Vec3d eyes = player.getPositionEyes(1.0F);

                Vec3d target = new Vec3d(
                        pos.getX() + 0.5D,
                        pos.getY() + 0.9D,
                        pos.getZ() + 0.5D
                );

                Vec3d direction = target.subtract(eyes).normalize();

                if (player.getLook(1.0F).dotProduct(direction) > 0.75D) {
                    continue;
                }
            }

            return pos;
        }

        return null;
    }
}