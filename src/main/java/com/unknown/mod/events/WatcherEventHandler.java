package com.unknown.mod.events;

import com.unknown.mod.entity.EntityWatcher;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;
import java.util.Random;
import java.util.UUID;

public class WatcherEventHandler {

    private static final String WATCHER_TAG = "unknown_watcher";
    private static final String OWNER_TAG = "unknown_watcher_owner";
    private static final String AGE_TAG = "unknown_watcher_age";
    private static final String LIFETIME_TAG = "unknown_watcher_lifetime";
    private static final String COOLDOWN_TAG = "unknown_watcher_cooldown";
    private static final String HIDDEN_TICKS_TAG = "unknown_hidden_ticks";
    private static final String MOVE_DELAY_TAG = "unknown_move_delay";

    private static final int MIN_LIFETIME_TICKS = 20 * 60;
    private static final int MAX_LIFETIME_TICKS = 20 * 120;

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        EntityPlayer player = event.player;
        if (player.world.isRemote) return;

        int cooldown = player.getEntityData().getInteger(COOLDOWN_TAG);

        if (!player.getEntityData().hasKey(COOLDOWN_TAG)) {
            player.getEntityData().setInteger(
                    COOLDOWN_TAG,
                    200 + player.world.rand.nextInt(200)
            );
            return;
        }

        if (cooldown > 0) {
            player.getEntityData().setInteger(COOLDOWN_TAG, cooldown - 1);
            return;
        }

        Random random = player.world.rand;
        player.getEntityData().setInteger(
                COOLDOWN_TAG,
                900 + random.nextInt(900)
        );

        // Не создаём второго Наблюдателя для того же игрока.
        if (hasActiveWatcher(player)) return;

        if (random.nextInt(2) == 0) {
            spawnWatcher(player);
        }
    }

    private boolean hasActiveWatcher(EntityPlayer player) {
        List<Entity> entities = player.world.loadedEntityList;
        String ownerId = player.getUniqueID().toString();

        for (Entity entity : entities) {
            if (!(entity instanceof EntityWatcher)) continue;
            if (!entity.getEntityData().getBoolean(WATCHER_TAG)) continue;

            if (ownerId.equals(entity.getEntityData().getString(OWNER_TAG))) {
                return true;
            }
        }

        return false;
    }

    private void spawnWatcher(EntityPlayer player) {
        Random random = player.world.rand;

        double distance = 45 + random.nextInt(26);
        BlockPos pos = findPosition(player, distance, true, true);

        if (pos == null) return;

        EntityWatcher watcher = new EntityWatcher(player.world);
        watcher.setPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);

        watcher.getEntityData().setBoolean(WATCHER_TAG, true);
        watcher.getEntityData().setString(
                OWNER_TAG,
                player.getUniqueID().toString()
        );
        watcher.getEntityData().setInteger(AGE_TAG, 0);
        watcher.getEntityData().setInteger(
                LIFETIME_TAG,
                MIN_LIFETIME_TICKS
                        + random.nextInt(MAX_LIFETIME_TICKS - MIN_LIFETIME_TICKS + 1)
        );
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

        // Время жизни отсчитывается независимо от того, рядом ли игрок.
        int age = entity.getEntityData().getInteger(AGE_TAG) + 1;
        entity.getEntityData().setInteger(AGE_TAG, age);

        int lifetime = entity.getEntityData().getInteger(LIFETIME_TAG);
        if (lifetime <= 0 || age >= lifetime) {
            entity.setDead();
            return;
        }

        EntityPlayer player = findOwner(entity);
        if (player == null) {
            // Не оставляем бесхозную сущность после выхода владельца.
            entity.setDead();
            return;
        }

        if (entity.getDistance(player) < 10.0F) {
            entity.setDead();
            return;
        }

        facePlayer((EntityLiving) entity, player);

        if (canPlayerSee(player, entity)) {
            entity.getEntityData().setInteger(HIDDEN_TICKS_TAG, 0);
            return;
        }

        int hiddenTicks = entity.getEntityData().getInteger(HIDDEN_TICKS_TAG) + 1;
        entity.getEntityData().setInteger(HIDDEN_TICKS_TAG, hiddenTicks);

        int moveDelay = entity.getEntityData().getInteger(MOVE_DELAY_TAG);
        if (moveDelay <= 0) moveDelay = 60 + entity.world.rand.nextInt(101);

        if (hiddenTicks < moveDelay) return;

        Random random = entity.world.rand;
        BlockPos newPos = findPosition(
                player,
                35 + random.nextInt(26),
                false,
                true
        );

        if (newPos != null) {
            entity.setPositionAndUpdate(
                    newPos.getX() + 0.5D,
                    newPos.getY(),
                    newPos.getZ() + 0.5D
            );
        }

        entity.getEntityData().setInteger(HIDDEN_TICKS_TAG, 0);
        entity.getEntityData().setInteger(
                MOVE_DELAY_TAG,
                60 + random.nextInt(101)
        );
    }

    private EntityPlayer findOwner(Entity watcher) {
        String ownerId = watcher.getEntityData().getString(OWNER_TAG);
        if (ownerId.isEmpty()) return null;

        for (EntityPlayer player : watcher.world.playerEntities) {
            if (ownerId.equals(player.getUniqueID().toString())) {
                return player;
            }
        }

        return null;
    }

    private void facePlayer(EntityLiving watcher, EntityPlayer player) {
        double dx = player.posX - watcher.posX;
        double dz = player.posZ - watcher.posZ;

        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;

        watcher.rotationYaw = yaw;
        watcher.rotationYawHead = yaw;
        watcher.renderYawOffset = yaw;
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

        // Угол обзора около 40 градусов от центра взгляда.
        if (look.dotProduct(direction) < 0.94D) return false;

        // Луч не должен упираться в блок между игроком и Наблюдателем.
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
            boolean preferBehind,
            boolean avoidView
    ) {
        Random random = player.world.rand;

        // Горизонтальное направление строго назад относительно поворота игрока.
        double yaw = Math.toRadians(player.rotationYaw);
        double behindX = Math.sin(yaw);
        double behindZ = -Math.cos(yaw);
        double behindAngle = Math.atan2(behindZ, behindX);

        for (int attempt = 0; attempt < 50; attempt++) {
            double angle;

            if (preferBehind && random.nextDouble() < 0.70D) {
                // 70% попыток — сектор позади игрока (примерно +/- 60 градусов).
                angle = behindAngle + (random.nextDouble() - 0.5D)
                        * (Math.PI / 3.0D);
            } else {
                angle = random.nextDouble() * Math.PI * 2.0D;
            }

            double x = player.posX + Math.cos(angle) * distance;
            double z = player.posZ + Math.sin(angle) * distance;

            BlockPos pos = player.world.getTopSolidOrLiquidBlock(
                    new BlockPos(x, player.posY, z)
            );

            if (!player.world.isBlockLoaded(pos)) continue;
            if (pos.getY() <= 1 || pos.getY() >= player.world.getHeight() - 2) continue;

            BlockPos feet = pos;
            BlockPos head = pos.up();
            BlockPos ground = pos.down();

            if (!player.world.isAirBlock(feet)) continue;
            if (!player.world.isAirBlock(head)) continue;

            IBlockState groundState = player.world.getBlockState(ground);
            if (!groundState.isSideSolid(player.world, ground, EnumFacing.UP)) continue;
            if (groundState.getMaterial().isLiquid()) continue;
            if (groundState.getBlock() == Blocks.CACTUS
                    || groundState.getBlock() == Blocks.MAGMA
                    || groundState.getBlock() == Blocks.FIRE
                    || groundState.getBlock() == Blocks.LAVA
                    || groundState.getBlock() == Blocks.FLOWING_LAVA) {
                continue;
            }

            if (player.world.getBlockState(feet).getMaterial().isLiquid()
                    || player.world.getBlockState(head).getMaterial().isLiquid()) {
                continue;
            }

            double centerX = pos.getX() + 0.5D;
            double centerZ = pos.getZ() + 0.5D;

            AxisAlignedBB box = new AxisAlignedBB(
                    centerX - 0.3D,
                    pos.getY(),
                    centerZ - 0.3D,
                    centerX + 0.3D,
                    pos.getY() + 1.8D,
                    centerZ + 0.3D
            );

            if (!player.world.getCollisionBoxes(null, box).isEmpty()) continue;

            if (avoidView) {
                Vec3d eyes = player.getPositionEyes(1.0F);
                Vec3d target = new Vec3d(centerX, pos.getY() + 0.9D, centerZ);
                Vec3d direction = target.subtract(eyes).normalize();

                // Не появляться в центре экрана или непосредственно перед игроком.
                if (player.getLook(1.0F).dotProduct(direction) > 0.75D) continue;
            }

            return pos;
        }

        return null;
    }
}
