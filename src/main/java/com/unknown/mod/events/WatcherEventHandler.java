package com.unknown.mod.events;

import com.unknown.mod.ModConfig;
import com.unknown.mod.entity.EntityWatcher;
import com.unknown.mod.events.MysteryProgression;

import net.minecraft.block.BlockDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;
import java.util.Random;

public class WatcherEventHandler {

    private static final String WATCHER_TAG = "unknown_watcher";
    private static final String OWNER_TAG = "unknown_watcher_owner";
    private static final String AGE_TAG = "unknown_watcher_age";
    private static final String LIFETIME_TAG = "unknown_watcher_lifetime";
    private static final String COOLDOWN_TAG = "unknown_watcher_cooldown";
    private static final String HIDDEN_TICKS_TAG = "unknown_hidden_ticks";
    private static final String MOVE_DELAY_TAG = "unknown_move_delay";
    private static final String ENCOUNTERS_TAG = "unknown_watcher_encounters";
    private static final String LOOK_TICKS_TAG = "unknown_watcher_look_ticks";
    private static final String LOOK_DESPAWN_DELAY_TAG = "unknown_watcher_look_delay";

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        EntityPlayer player = event.player;
        if (player.world.isRemote || !ModConfig.watcherEnabled) return;

        int mysteryLevel = MysteryProgression.getLevel(player.world);
        if (mysteryLevel <= 0) return; // Первый игровой день полностью безопасен.

        Random random = player.world.rand;
        int cooldown = player.getEntityData().getInteger(COOLDOWN_TAG);

        if (!player.getEntityData().hasKey(COOLDOWN_TAG)) {
            player.getEntityData().setInteger(
                    COOLDOWN_TAG,
                    MysteryProgression.getWatcherCooldown(random, mysteryLevel)
            );
            return;
        }

        if (cooldown > 0) {
            player.getEntityData().setInteger(COOLDOWN_TAG, cooldown - 1);
            return;
        }

        player.getEntityData().setInteger(
                COOLDOWN_TAG,
                MysteryProgression.getWatcherCooldown(random, mysteryLevel)
        );

        if (hasActiveWatcher(player)) return;

        // Даже на поздних этапах Наблюдатель остаётся редким событием.
        int encounters = player.getEntityData().getInteger(ENCOUNTERS_TAG);
        int spawnChance = MysteryProgression.getWatcherChance(mysteryLevel, encounters);

        if (random.nextInt(100) < spawnChance) {
            spawnWatcher(player, mysteryLevel);
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

    private void spawnWatcher(EntityPlayer player, int mysteryLevel) {
        Random random = player.world.rand;

        BlockPos pos;

        // В пещерах используется отдельный поиск: наземные координаты здесь не подходят.
        if (CaveWatcherBehavior.isPlayerInCave(player)) {
            pos = CaveWatcherBehavior.findPosition(player, 18.0D, 38.0D);
        } else {
            double distance = MysteryProgression.getWatcherSpawnDistance(random, mysteryLevel);
            // Лес, возвышенность, архитектура или укрытие.
            pos = findUnexpectedPosition(player, distance, true);
        }

        if (pos == null) return;

        EntityWatcher watcher = new EntityWatcher(player.world);
        watcher.setPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);

        // Корпус остаётся повёрнутым в сторону, а голова сможет следить за игроком.
        float bodyYaw = random.nextFloat() * 360.0F;
        watcher.rotationYaw = bodyYaw;

        watcher.getEntityData().setBoolean(WATCHER_TAG, true);
        watcher.getEntityData().setString(
                OWNER_TAG,
                player.getUniqueID().toString()
        );
        watcher.getEntityData().setInteger(AGE_TAG, 0);
        int minLifetime = ModConfig.watcherMinLifetimeSeconds * 20;
        int maxLifetime = ModConfig.watcherMaxLifetimeSeconds * 20;
        watcher.getEntityData().setInteger(
                LIFETIME_TAG,
                minLifetime + random.nextInt(maxLifetime - minLifetime + 1)
        );
        watcher.getEntityData().setInteger(
                MOVE_DELAY_TAG,
                100 + random.nextInt(121)
        );
        watcher.getEntityData().setInteger(LOOK_TICKS_TAG, 0);
        watcher.getEntityData().setInteger(
                LOOK_DESPAWN_DELAY_TAG,
                30 + random.nextInt(41)
        );

        if (player.world.spawnEntity(watcher)) {
            player.getEntityData().setInteger(ENCOUNTERS_TAG,
                    player.getEntityData().getInteger(ENCOUNTERS_TAG) + 1);
        }
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        Entity entity = event.getEntity();

        if (entity.world.isRemote) return;
        if (!(entity instanceof EntityWatcher)) return;
        if (!entity.getEntityData().getBoolean(WATCHER_TAG)) return;
        if (!ModConfig.mysteryEnabled || !ModConfig.watcherEnabled) {
            entity.setDead();
            return;
        }

        int age = entity.getEntityData().getInteger(AGE_TAG) + 1;
        entity.getEntityData().setInteger(AGE_TAG, age);

        int lifetime = entity.getEntityData().getInteger(LIFETIME_TAG);
        if (lifetime <= 0 || age >= lifetime) {
            entity.setDead();
            return;
        }

        EntityPlayer player = findOwner(entity);
        if (player == null) {
            entity.setDead();
            return;
        }

        if (entity.getDistance(player) < 10.0F) {
            entity.setDead();
            return;
        }

        // Не поворачивает голову за игроком: силуэт должен выглядеть как случайная деталь мира.

        if (canPlayerSee(player, entity)) {
            int lookTicks = entity.getEntityData().getInteger(LOOK_TICKS_TAG) + 1;
            entity.getEntityData().setInteger(LOOK_TICKS_TAG, lookTicks);
            entity.getEntityData().setInteger(HIDDEN_TICKS_TAG, 0);

            int lookDelay = entity.getEntityData().getInteger(LOOK_DESPAWN_DELAY_TAG);
            if (lookTicks >= lookDelay) {
                entity.setDead();
            }
            return;
        }

        // Если игрок отвёл взгляд до конца задержки, Наблюдатель остаётся.
        entity.getEntityData().setInteger(LOOK_TICKS_TAG, 0);

        int hiddenTicks = entity.getEntityData().getInteger(HIDDEN_TICKS_TAG) + 1;
        entity.getEntityData().setInteger(HIDDEN_TICKS_TAG, hiddenTicks);

        int moveDelay = entity.getEntityData().getInteger(MOVE_DELAY_TAG);
        if (moveDelay <= 0) moveDelay = 60 + entity.world.rand.nextInt(101);

        if (hiddenTicks < moveDelay) return;

        Random random = entity.world.rand;
        BlockPos newPos;

        if (CaveWatcherBehavior.isPlayerInCave(player)) {
            newPos = CaveWatcherBehavior.findPosition(player, 18.0D, 38.0D);
        } else {
            newPos = findUnexpectedPosition(
                    player,
                    MysteryProgression.getWatcherRelocationDistance(
                            random, MysteryProgression.getLevel(player.world)),
                    true
            );
        }

        if (newPos != null && relocateWatcher(entity, player, newPos)) {
            return;
        }

        // Если безопасное и незаметное место не найдено, попробуем снова позже.
        entity.getEntityData().setInteger(HIDDEN_TICKS_TAG, Math.max(0, moveDelay - 20));
        entity.getEntityData().setInteger(MOVE_DELAY_TAG, moveDelay);
    }

    /**
     * Пересоздаёт сущность на новой позиции вместо обычного телепорта.
     * Клиент получает удаление старого Watcher и появление нового на месте,
     * поэтому интерполяция не рисует его путь сквозь стены и блоки.
     */
    private boolean relocateWatcher(Entity oldWatcher, EntityPlayer player, BlockPos newPos) {
        EntityWatcher replacement = new EntityWatcher(oldWatcher.world);

        replacement.setPosition(
                newPos.getX() + 0.5D,
                newPos.getY(),
                newPos.getZ() + 0.5D
        );

        replacement.rotationYaw = oldWatcher.rotationYaw;
        replacement.prevRotationYaw = oldWatcher.prevRotationYaw;
        replacement.setHealth(((EntityLiving) oldWatcher).getHealth());

        // Не создаём новую сущность в открытом поле зрения игрока.
        if (canPlayerSee(player, replacement)) {
            return false;
        }

        // Переносим владельца, время жизни и счётчики, но начинаем новый цикл скрытого перемещения.
        replacement.getEntityData().merge(oldWatcher.getEntityData().copy());
        replacement.getEntityData().setInteger(HIDDEN_TICKS_TAG, 0);
        replacement.getEntityData().setInteger(
                MOVE_DELAY_TAG,
                60 + oldWatcher.world.rand.nextInt(101)
        );
        replacement.getEntityData().setInteger(LOOK_TICKS_TAG, 0);

        if (oldWatcher.world.spawnEntity(replacement)) {
            oldWatcher.setDead();
            return true;
        }

        return false;
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

        if (look.dotProduct(direction) < 0.97D) return false;

        RayTraceResult hit = player.world.rayTraceBlocks(
                eyes,
                target,
                false,
                true,
                false
        );

        // Стекло не должно полностью скрывать Наблюдателя от проверки взгляда.
        return hit == null
                || (hit.typeOfHit == RayTraceResult.Type.BLOCK
                && player.world.getBlockState(hit.getBlockPos()).getMaterial() == Material.GLASS);
    }

    /**
     * Выбирает один из нескольких сценариев появления, чтобы Наблюдатель
     * не всегда оказывался просто в случайной точке за спиной.
     */
    private BlockPos findUnexpectedPosition(
            EntityPlayer player,
            double distance,
            boolean avoidView
    ) {
        Random random = player.world.rand;
        int scenario = random.nextInt(100);
        BlockPos pos = null;

        if (scenario < 25) {
            pos = findTreePosition(player, distance, avoidView);
        } else if (scenario < 45) {
            pos = findElevatedPosition(player, distance, avoidView);
        } else if (scenario < 65) {
            pos = findArchitecturalPosition(player, distance, avoidView);
        }

        if (pos == null) {
            pos = findPosition(player, distance, true, avoidView);
        }

        return pos;
    }

    /**
     * Ищет безопасную точку рядом с деревьями — например, между стволами
     * или на краю лесной поляны.
     */
    private BlockPos findTreePosition(
            EntityPlayer player,
            double distance,
            boolean avoidView
    ) {
        Random random = player.world.rand;

        for (int attempt = 0; attempt < 35; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int x = (int) Math.floor(player.posX + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.posZ + Math.sin(angle) * distance);

            BlockPos pos = player.world.getTopSolidOrLiquidBlock(
                    new BlockPos(x, player.posY, z)
            );

            if (!player.world.isBlockLoaded(pos)) continue;
            if (Math.abs(pos.getY() - player.posY) > 8.0D) continue;
            if (!isSafeStandingPosition(player, pos)) continue;
            if (!hasNearbyTree(player, pos)) continue;
            if (!isOutsidePlayerView(player, pos, avoidView)) continue;

            return pos;
        }

        return null;
    }

    /**
     * Ищет точку на холме или другом естественном возвышении.
     */
    private BlockPos findElevatedPosition(
            EntityPlayer player,
            double distance,
            boolean avoidView
    ) {
        Random random = player.world.rand;

        for (int attempt = 0; attempt < 45; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int x = (int) Math.floor(player.posX + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.posZ + Math.sin(angle) * distance);

            BlockPos pos = player.world.getTopSolidOrLiquidBlock(
                    new BlockPos(x, player.posY, z)
            );

            if (!player.world.isBlockLoaded(pos)) continue;

            double heightDifference = pos.getY() - player.posY;
            if (heightDifference < 3.0D || heightDifference > 12.0D) continue;
            if (!isSafeStandingPosition(player, pos)) continue;
            if (!hasNearbyCover(player, pos)) continue;
            if (!isOutsidePlayerView(player, pos, avoidView)) continue;

            return pos;
        }

        return null;
    }

    private boolean hasNearbyTree(EntityPlayer player, BlockPos pos) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = -1; y <= 4; y++) {
                    IBlockState state = player.world.getBlockState(
                            pos.add(x, y, z)
                    );
                    Material material = state.getMaterial();

                    if (material == Material.LEAVES || material == Material.WOOD) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isOutsidePlayerView(
            EntityPlayer player,
            BlockPos pos,
            boolean avoidView
    ) {
        if (!avoidView) return true;

        Vec3d eyes = player.getPositionEyes(1.0F);
        Vec3d target = new Vec3d(
                pos.getX() + 0.5D,
                pos.getY() + 0.9D,
                pos.getZ() + 0.5D
        );
        Vec3d direction = target.subtract(eyes).normalize();

        return player.getLook(1.0F).dotProduct(direction) <= 0.75D;
    }

    private BlockPos findPosition(
            EntityPlayer player,
            double distance,
            boolean preferBehind,
            boolean avoidView
    ) {
        Random random = player.world.rand;

        // При перемещении он тоже может выбрать окно или открытый дверной проём.
        if (random.nextInt(100) < 20) {
            BlockPos architectural = findArchitecturalPosition(player, distance, avoidView);
            if (architectural != null) return architectural;
        }

        double yaw = Math.toRadians(player.rotationYaw);
        double behindX = Math.sin(yaw);
        double behindZ = -Math.cos(yaw);
        double behindAngle = Math.atan2(behindZ, behindX);

        for (int attempt = 0; attempt < 70; attempt++) {
            double angle;

            double roll = random.nextDouble();
            if (preferBehind && roll < 0.45D) {
                // Часто — за спиной, но не всегда в одной и той же точке.
                angle = behindAngle + (random.nextDouble() - 0.5D)
                        * (Math.PI / 2.0D);
            } else if (preferBehind && roll < 0.90D) {
                // Иногда — сбоку, на периферии кадра.
                double frontAngle = Math.atan2(Math.cos(yaw), -Math.sin(yaw));
                double side = random.nextBoolean() ? 1.0D : -1.0D;
                angle = frontAngle + side * (0.65D + random.nextDouble() * 0.45D);
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

            // Чаще выбираем позицию у ствола, стены или скалы, чтобы силуэт был частично скрыт.
            if (random.nextDouble() < 0.65D && !hasNearbyCover(player, pos)) continue;

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

                if (player.getLook(1.0F).dotProduct(direction) > 0.75D) continue;
            }

            return pos;
        }

        return null;
    }

    /**
     * Ищет точку за окном или рядом с открытой дверью.
     * Наблюдатель оказывается внутри строения, а игрок видит его через проём.
     */
    private BlockPos findArchitecturalPosition(
            EntityPlayer player,
            double distance,
            boolean avoidView
    ) {
        Random random = player.world.rand;

        for (int attempt = 0; attempt < 45; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int x = (int) Math.floor(player.posX + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.posZ + Math.sin(angle) * distance);

            BlockPos surface = player.world.getTopSolidOrLiquidBlock(
                    new BlockPos(x, player.posY, z)
            );

            if (!player.world.isBlockLoaded(surface)) continue;

            int minY = Math.max(2, surface.getY() - 14);
            int maxY = Math.min(player.world.getHeight() - 2, surface.getY() + 2);

            for (int y = minY; y <= maxY; y++) {
                BlockPos wall = new BlockPos(x, y, z);
                IBlockState state = player.world.getBlockState(wall);

                boolean window = state.getMaterial() == Material.GLASS;
                boolean openDoor = state.getBlock() instanceof BlockDoor
                        && state.getValue(BlockDoor.OPEN);

                if (!window && !openDoor) continue;

                double dx = wall.getX() + 0.5D - player.posX;
                double dz = wall.getZ() + 0.5D - player.posZ;

                EnumFacing awayFromPlayer;
                if (Math.abs(dx) > Math.abs(dz)) {
                    awayFromPlayer = dx > 0.0D ? EnumFacing.EAST : EnumFacing.WEST;
                } else {
                    awayFromPlayer = dz > 0.0D ? EnumFacing.SOUTH : EnumFacing.NORTH;
                }

                BlockPos standing = wall.offset(awayFromPlayer);

                if (!isSafeStandingPosition(player, standing)) continue;

                double px = standing.getX() + 0.5D;
                double pz = standing.getZ() + 0.5D;
                double actualDistance = Math.sqrt(
                        (px - player.posX) * (px - player.posX)
                                + (pz - player.posZ) * (pz - player.posZ)
                );

                if (actualDistance < 35.0D || actualDistance > 75.0D) continue;

                if (avoidView) {
                    Vec3d eyes = player.getPositionEyes(1.0F);
                    Vec3d target = new Vec3d(px, standing.getY() + 0.9D, pz);
                    Vec3d direction = target.subtract(eyes).normalize();
                    if (player.getLook(1.0F).dotProduct(direction) > 0.75D) continue;
                }

                return standing;
            }
        }

        return null;
    }

    private boolean isSafeStandingPosition(EntityPlayer player, BlockPos pos) {
        if (pos.getY() <= 1 || pos.getY() >= player.world.getHeight() - 2) return false;
        if (!player.world.isBlockLoaded(pos)) return false;

        BlockPos head = pos.up();
        BlockPos ground = pos.down();

        if (!player.world.isAirBlock(pos) || !player.world.isAirBlock(head)) return false;

        IBlockState groundState = player.world.getBlockState(ground);
        if (!groundState.isSideSolid(player.world, ground, EnumFacing.UP)) return false;
        if (groundState.getMaterial().isLiquid()) return false;

        if (groundState.getBlock() == Blocks.CACTUS
                || groundState.getBlock() == Blocks.MAGMA
                || groundState.getBlock() == Blocks.FIRE
                || groundState.getBlock() == Blocks.LAVA
                || groundState.getBlock() == Blocks.FLOWING_LAVA) {
            return false;
        }

        AxisAlignedBB box = new AxisAlignedBB(
                pos.getX() + 0.2D,
                pos.getY(),
                pos.getZ() + 0.2D,
                pos.getX() + 0.8D,
                pos.getY() + 1.8D,
                pos.getZ() + 0.8D
        );

        return player.world.getCollisionBoxes(null, box).isEmpty();
    }

    private boolean hasNearbyCover(EntityPlayer player, BlockPos pos) {
        BlockPos[] sides = new BlockPos[] {
                pos.north(),
                pos.south(),
                pos.east(),
                pos.west(),
                pos.north().up(),
                pos.south().up(),
                pos.east().up(),
                pos.west().up()
        };

        for (BlockPos side : sides) {
            IBlockState state = player.world.getBlockState(side);
            if (state.getMaterial().blocksMovement()) {
                return true;
            }
        }

        return false;
    }
}
