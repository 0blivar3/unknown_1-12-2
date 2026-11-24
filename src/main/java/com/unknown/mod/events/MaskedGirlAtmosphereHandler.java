package com.unknown.mod.events;

import com.unknown.mod.ModConfig;
import com.unknown.mod.entity.EntityMaskedGirl;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Дополнительная атмосфера после ритуала: адаптивные появления, ложные звуки,
 * наблюдающие мобы и небольшие изменения окружения. Все события серверные.
 */
public class MaskedGirlAtmosphereHandler {

    private static final String HAUNTING_TICKS = "unknown_masked_girl_haunting";
    private static final String GIRL_TAG = "unknown_masked_girl";
    private static final String OWNER_TAG = "unknown_masked_girl_owner";

    private static final String TICK_TAG = "unknown_mg_atmosphere_tick";
    private static final String COOLDOWN_TAG = "unknown_mg_atmosphere_cooldown";
    private static final String SLEEP_TAG = "unknown_mg_was_sleeping";
    private static final String FOREST_TAG = "unknown_mg_forest_habit";
    private static final String CAVE_TAG = "unknown_mg_cave_habit";
    private static final String HOME_TAG = "unknown_mg_home_habit";
    private static final String SCAN_TAG = "unknown_mg_scan_habit";
    private static final String LAST_YAW_TAG = "unknown_mg_last_yaw";
    private static final String LAST_PITCH_TAG = "unknown_mg_last_pitch";
    private static final String LAST_X_TAG = "unknown_mg_last_x";
    private static final String LAST_Y_TAG = "unknown_mg_last_y";
    private static final String LAST_Z_TAG = "unknown_mg_last_z";

    private static final String OBSERVER_TAG = "unknown_mg_observer";
    private static final String OBSERVER_OWNER_TAG = "unknown_mg_observer_owner";
    private static final String OBSERVER_AGE_TAG = "unknown_mg_observer_age";
    private static final String OBSERVER_LIFE_TAG = "unknown_mg_observer_life";
    private static final String OBSERVER_EXPIRES_TAG = "unknown_mg_observer_expires_at";
    private static final String GIRL_EXPIRES_TAG = "unknown_masked_girl_expires_at";
    private static final String GIRL_ACTIVE_UNTIL_TAG = "unknown_masked_girl_active_until";

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) return;

        EntityPlayer player = event.player;
        MaskedGirlWorldData worldData = MaskedGirlWorldData.get(player.world);
        if (!ModConfig.maskedGirlEnabled || worldData == null || !worldData.isActive()) return;

        player.getEntityData().setInteger(HAUNTING_TICKS, worldData.getRemainingTicks());
        updateHabits(player);

        if (!player.getEntityData().hasKey(COOLDOWN_TAG)) {
            player.getEntityData().setInteger(COOLDOWN_TAG, 1200 + player.world.rand.nextInt(1201));
        }

        boolean sleeping = player.isPlayerSleeping();
        boolean wasSleeping = player.getEntityData().getBoolean(SLEEP_TAG);
        player.getEntityData().setBoolean(SLEEP_TAG, sleeping);
        if (wasSleeping && !sleeping) {
            // После пробуждения она может оказаться там, где игрок её не ожидает.
            if (!spawnAtmosphericGirl(player, true)) playBreathBehindPlayer(player);
            player.getEntityData().setInteger(COOLDOWN_TAG, 1000 + player.world.rand.nextInt(1000));
            return;
        }

        int tick = player.getEntityData().getInteger(TICK_TAG) + 1;
        if (tick < 20) {
            player.getEntityData().setInteger(TICK_TAG, tick);
            return;
        }
        player.getEntityData().setInteger(TICK_TAG, 0);

        int cooldown = player.getEntityData().getInteger(COOLDOWN_TAG);
        if (cooldown > 0) {
            player.getEntityData().setInteger(COOLDOWN_TAG, Math.max(0, cooldown - 20));
            return;
        }

        performAtmosphereEvent(player);
        player.getEntityData().setInteger(COOLDOWN_TAG, 1400 + player.world.rand.nextInt(1801));
    }

    private void updateHabits(EntityPlayer player) {
        if (player.ticksExisted % 20 != 0) return;

        World world = player.world;
        String biome = world.getBiome(player.getPosition()).getBiomeName().toLowerCase();
        boolean forest = biome.contains("forest") || biome.contains("wood")
                || biome.contains("jungle") || biome.contains("taiga");
        boolean cave = !world.canSeeSky(player.getPosition());

        int forestHabit = player.getEntityData().getInteger(FOREST_TAG);
        int caveHabit = player.getEntityData().getInteger(CAVE_TAG);
        int homeHabit = player.getEntityData().getInteger(HOME_TAG);
        int scanHabit = player.getEntityData().getInteger(SCAN_TAG);

        player.getEntityData().setInteger(FOREST_TAG,
                forest ? Math.min(2400, forestHabit + 1) : Math.max(0, forestHabit - 2));
        player.getEntityData().setInteger(CAVE_TAG,
                cave ? Math.min(2400, caveHabit + 1) : Math.max(0, caveHabit - 2));

        if (player.ticksExisted % 100 == 0) {
            boolean nearHome = countShelterBlocks(world, player.getPosition(), 6) >= 5;
            player.getEntityData().setInteger(HOME_TAG,
                    nearHome ? Math.min(2400, homeHabit + 5) : Math.max(0, homeHabit - 10));
        }

        float yaw = player.rotationYaw;
        float pitch = player.rotationPitch;
        float oldYaw = player.getEntityData().getFloat(LAST_YAW_TAG);
        float oldPitch = player.getEntityData().getFloat(LAST_PITCH_TAG);
        float yawChange = Math.abs(wrapDegrees(yaw - oldYaw));
        float pitchChange = Math.abs(pitch - oldPitch);
        if (player.ticksExisted > 40 && yawChange + pitchChange > 32.0F) {
            scanHabit = Math.min(2400, scanHabit + 3);
        } else {
            scanHabit = Math.max(0, scanHabit - 1);
        }
        player.getEntityData().setInteger(SCAN_TAG, scanHabit);
        player.getEntityData().setFloat(LAST_YAW_TAG, yaw);
        player.getEntityData().setFloat(LAST_PITCH_TAG, pitch);
    }

    private int countShelterBlocks(World world, BlockPos center, int radius) {
        int count = 0;
        for (int x = -radius; x <= radius; x += 2) {
            for (int y = -1; y <= 3; y += 2) {
                for (int z = -radius; z <= radius; z += 2) {
                    BlockPos pos = center.add(x, y, z);
                    if (!world.isBlockLoaded(pos)) continue;
                    Block block = world.getBlockState(pos).getBlock();
                    if (block == Blocks.PLANKS || block == Blocks.COBBLESTONE
                            || block == Blocks.STONEBRICK || block == Blocks.BRICK_BLOCK
                            || block == Blocks.LOG || block == Blocks.LOG2
                            || block == Blocks.GLASS || block == Blocks.GLASS_PANE
                            || block == Blocks.CHEST || block == Blocks.BED
                            || block == Blocks.CRAFTING_TABLE) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private float wrapDegrees(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) angle -= 360.0F;
        if (angle < -180.0F) angle += 360.0F;
        return angle;
    }

    private void performAtmosphereEvent(EntityPlayer player) {
        Random random = player.world.rand;
        int choice = random.nextInt(100);
        int forest = player.getEntityData().getInteger(FOREST_TAG);
        int cave = player.getEntityData().getInteger(CAVE_TAG);
        int home = player.getEntityData().getInteger(HOME_TAG);
        int scanning = player.getEntityData().getInteger(SCAN_TAG);

        // Habits affect the event pool, but never force an event on every check.
        if (cave > 500 && choice < 22) {
            playCaveWhisper(player);
        } else if (forest > 500 && choice < 28) {
            spawnAtmosphericGirl(player, false);
        } else if (home > 500 && choice < 22) {
            if (!placeStrangeItem(player)) playDoorCreak(player);
        } else if (scanning > 500 && choice < 25) {
            playBreathBehindPlayer(player);
        } else if (choice < 15) {
            playBreathBehindPlayer(player);
        } else if (choice < 27) {
            playFalseFootsteps(player);
        } else if (choice < 38) {
            playRecordEleven(player);
        } else if (choice < 49) {
            playNearbyBlockBreak(player);
        } else if (choice < 61) {
            spawnObserverMobs(player);
        } else if (choice < 72) {
            spawnAtmosphericGirl(player, false);
        } else if (choice < 82) {
            playDoorCreak(player);
        } else if (choice < 91) {
            playCaveWhisper(player);
        } else if (choice < 96) {
            playDreadSound(player);
        } else {
            if (!placeStrangeItem(player)) playFalseFootsteps(player);
        }
    }

    private boolean spawnAtmosphericGirl(EntityPlayer player, boolean afterSleep) {
        World world = player.world;
        if (hasActiveGirl(player)) return false;

        int scanHabit = player.getEntityData().getInteger(SCAN_TAG);
        MaskedGirlWorldData worldData = MaskedGirlWorldData.get(world);
        int remaining = worldData == null ? player.getEntityData().getInteger(HAUNTING_TICKS)
                : worldData.getRemainingTicks();
        boolean finalStage = worldData != null ? worldData.getStage() >= 3 : remaining <= 24000;
        double min = scanHabit > 500 ? 27.0D : (finalStage ? 22.0D : (afterSleep ? 10.0D : 13.0D));
        double max = scanHabit > 500 ? 42.0D : (finalStage ? 45.0D : (afterSleep ? 20.0D : 31.0D));

        BlockPos pos = findRememberedPosition(player, min, max);
        if (pos == null) pos = findWindowPosition(player, min, max);
        if (pos == null && world.rand.nextInt(100) < 55) pos = findNaturalPosition(player, min, max, true);
        if (pos == null) pos = findCoveredPosition(player, min, max);
        if (pos == null) pos = findNaturalPosition(player, min, max,
                player.getEntityData().getInteger(FOREST_TAG) > 500);
        if (pos == null) return false;

        EntityMaskedGirl girl = new EntityMaskedGirl(world);
        girl.setPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        if (finalStage) {
            girl.setHeldItem(EnumHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        }
        girl.setNoAI(!finalStage);
        facePlayer(girl, player);
        girl.getEntityData().setBoolean(GIRL_TAG, true);
        girl.getEntityData().setString(OWNER_TAG, player.getUniqueID().toString());
        int girlLifetime = 900 + world.rand.nextInt(1201);
        girl.getEntityData().setInteger("unknown_masked_girl_age", 0);
        girl.getEntityData().setInteger("unknown_masked_girl_lifetime", girlLifetime);
        girl.getEntityData().setLong(GIRL_EXPIRES_TAG, world.getTotalWorldTime() + girlLifetime);
        girl.getEntityData().setInteger("unknown_masked_girl_look_ticks", 0);
        girl.getEntityData().setInteger("unknown_masked_girl_attack_state", 0);
        girl.getEntityData().setInteger("unknown_masked_girl_attack_cooldown", 1200);

        boolean spawned = world.spawnEntity(girl);
        if (spawned) {
            player.getEntityData().setLong(GIRL_ACTIVE_UNTIL_TAG,
                    world.getTotalWorldTime() + girlLifetime);
            player.getEntityData().setInteger(LAST_X_TAG, pos.getX());
            player.getEntityData().setInteger(LAST_Y_TAG, pos.getY());
            player.getEntityData().setInteger(LAST_Z_TAG, pos.getZ());
            if (finalStage) {
                float recordPitch = 0.55F + world.rand.nextFloat() * 0.9F;
                world.playSound(null, pos, SoundEvents.RECORD_11,
                        SoundCategory.RECORDS, 0.9F, recordPitch);
            } else if (afterSleep || girl.getDistance(player) < 16.0D) {
                world.playSound(null, pos, SoundEvents.ENTITY_ENDERMEN_STARE,
                        SoundCategory.HOSTILE, 0.55F, 0.62F + world.rand.nextFloat() * 0.18F);
            }
        }
        return spawned;
    }

    private boolean hasActiveGirl(EntityPlayer player) {
        long activeUntil = player.getEntityData().getLong(GIRL_ACTIVE_UNTIL_TAG);
        if (activeUntil > player.world.getTotalWorldTime()) return true;
        if (activeUntil != 0L) player.getEntityData().setLong(GIRL_ACTIVE_UNTIL_TAG, 0L);

        String owner = player.getUniqueID().toString();
        for (Entity entity : player.world.loadedEntityList) {
            if (entity instanceof EntityMaskedGirl && entity.getEntityData().getBoolean(GIRL_TAG)
                    && owner.equals(entity.getEntityData().getString(OWNER_TAG))) return true;
        }
        return false;
    }

    /** Finds a visible but partially sheltered position beside trees or structures. */
    private BlockPos findPeekingPosition(EntityPlayer player, double min, double max) {
        World world = player.world;
        for (int attempt = 0; attempt < 80; attempt++) {
            double angle = world.rand.nextDouble() * Math.PI * 2.0D;
            double distance = min + world.rand.nextDouble() * (max - min);
            BlockPos column = new BlockPos(
                    (int) Math.floor(player.posX + Math.cos(angle) * distance),
                    (int) player.posY,
                    (int) Math.floor(player.posZ + Math.sin(angle) * distance));
            if (!world.isBlockLoaded(column)) continue;
            BlockPos candidate = world.getTopSolidOrLiquidBlock(column);
            if (!isSafeSpawn(player, candidate, min, max) || !hasNearbyCover(world, candidate)) continue;
            Vec3d eyes = player.getPositionEyes(1.0F);
            Vec3d target = new Vec3d(candidate.getX() + 0.5D,
                    candidate.getY() + 0.9D, candidate.getZ() + 0.5D);
            if (world.rayTraceBlocks(eyes, target) == null) return candidate;
        }
        return null;
    }

    private boolean hasNearbyCover(World world, BlockPos center) {
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 3; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (x == 0 && z == 0) continue;
                    BlockPos pos = center.add(x, y, z);
                    if (!world.isBlockLoaded(pos)) continue;
                    Block block = world.getBlockState(pos).getBlock();
                    if (block == Blocks.LOG || block == Blocks.LOG2
                            || block == Blocks.LEAVES || block == Blocks.LEAVES2
                            || block == Blocks.PLANKS || block == Blocks.COBBLESTONE
                            || block == Blocks.STONE || block == Blocks.STONEBRICK
                            || block == Blocks.BRICK_BLOCK) return true;
                }
            }
        }
        return false;
    }

    /** Finds a rear-hemisphere spawn with terrain or a structure obscuring the view. */
    private BlockPos findCoveredPosition(EntityPlayer player, double min, double max) {
        World world = player.world;
        Vec3d eyes = player.getPositionEyes(1.0F);
        for (int attempt = 0; attempt < 80; attempt++) {
            double angle = world.rand.nextDouble() * Math.PI * 2.0D;
            double distance = min + world.rand.nextDouble() * (max - min);
            BlockPos column = new BlockPos(
                    (int) Math.floor(player.posX + Math.cos(angle) * distance),
                    (int) player.posY,
                    (int) Math.floor(player.posZ + Math.sin(angle) * distance));
            if (!world.isBlockLoaded(column)) continue;
            BlockPos candidate = world.getTopSolidOrLiquidBlock(column);
            if (!isSafeSpawn(player, candidate, min, max)) continue;
            Vec3d target = new Vec3d(candidate.getX() + 0.5D,
                    candidate.getY() + 0.9D, candidate.getZ() + 0.5D);
            if (world.rayTraceBlocks(eyes, target) != null) return candidate;
        }
        return null;
    }

    private BlockPos findRememberedPosition(EntityPlayer player, double min, double max) {
        if (!player.getEntityData().hasKey(LAST_X_TAG)) return null;
        BlockPos remembered = new BlockPos(
                player.getEntityData().getInteger(LAST_X_TAG),
                player.getEntityData().getInteger(LAST_Y_TAG),
                player.getEntityData().getInteger(LAST_Z_TAG));
        if (remembered.distanceSq(player.getPosition()) < min * min
                || remembered.distanceSq(player.getPosition()) > max * max
                || !player.world.isBlockLoaded(remembered)) return null;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos candidate = remembered.add(dx, 0, dz);
                if (isSafeSpawn(player, candidate, min, max)) return candidate;
            }
        }
        return null;
    }

    private BlockPos findWindowPosition(EntityPlayer player, double min, double max) {
        World world = player.world;
        BlockPos base = player.getPosition();
        List<BlockPos> candidates = new ArrayList<>();

        for (int x = -24; x <= 24; x += 2) {
            for (int y = -3; y <= 5; y++) {
                for (int z = -24; z <= 24; z += 2) {
                    BlockPos glass = base.add(x, y, z);
                    if (!world.isBlockLoaded(glass)) continue;
                    Block block = world.getBlockState(glass).getBlock();
                    if (block != Blocks.GLASS && block != Blocks.GLASS_PANE) continue;

                    for (EnumFacing facing : EnumFacing.HORIZONTALS) {
                        BlockPos feet = glass.down().offset(facing);
                        if (isSafeSpawn(player, feet, min, max)) candidates.add(feet);
                    }
                }
            }
        }
        return candidates.isEmpty() ? null : candidates.get(world.rand.nextInt(candidates.size()));
    }

    private BlockPos findNaturalPosition(EntityPlayer player, double min, double max, boolean preferTrees) {
        World world = player.world;
        Random random = world.rand;
        for (int attempt = 0; attempt < 100; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = min + random.nextDouble() * (max - min);
            BlockPos column = new BlockPos(
                    (int) Math.floor(player.posX + Math.cos(angle) * distance),
                    (int) player.posY,
                    (int) Math.floor(player.posZ + Math.sin(angle) * distance));
            if (!world.isBlockLoaded(column)) continue;
            BlockPos feet = world.getTopSolidOrLiquidBlock(column);
            if (!isSafeSpawn(player, feet, min, max)) continue;
            if (preferTrees && !isNearTree(world, feet)) continue;
            return feet;
        }
        return null;
    }

    private boolean isNearTree(World world, BlockPos pos) {
        for (int x = -3; x <= 3; x++) {
            for (int y = -1; y <= 5; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos checkPos = pos.add(x, y, z);
                    if (!world.isBlockLoaded(checkPos)) continue;
                    Block check = world.getBlockState(checkPos).getBlock();
                    if (check == Blocks.LOG || check == Blocks.LOG2
                            || check == Blocks.LEAVES || check == Blocks.LEAVES2) return true;
                }
            }
        }
        return false;
    }

    private boolean isSafeSpawn(EntityPlayer player, BlockPos pos, double min, double max) {
        World world = player.world;
        if (!world.isBlockLoaded(pos) || pos.getY() <= 1 || pos.getY() >= world.getHeight() - 2) return false;
        if (!world.isAirBlock(pos) || !world.isAirBlock(pos.up())) return false;
        BlockPos ground = pos.down();
        if (!world.getBlockState(ground).isSideSolid(world, ground, EnumFacing.UP)
                || world.getBlockState(ground).getMaterial().isLiquid()) return false;

        double distanceSq = pos.distanceSq(player.getPosition());
        if (distanceSq < min * min || distanceSq > max * max) return false;

        Vec3d eyes = player.getPositionEyes(1.0F);
        Vec3d target = new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D);
        if (player.getLook(1.0F).dotProduct(target.subtract(eyes).normalize()) > -0.12D) return false;

        AxisAlignedBB box = new AxisAlignedBB(pos.getX() + 0.2D, pos.getY(), pos.getZ() + 0.2D,
                pos.getX() + 0.8D, pos.getY() + 1.8D, pos.getZ() + 0.8D);
        return world.getCollisionBoxes(null, box).isEmpty();
    }

    private void facePlayer(EntityLivingBase entity, EntityPlayer player) {
        double dx = player.posX - entity.posX;
        double dz = player.posZ - entity.posZ;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        entity.rotationYaw = yaw;
        entity.prevRotationYaw = yaw;
        entity.setRotationYawHead(yaw);
        entity.setRenderYawOffset(yaw);
    }

    /** A small pool of vanilla horror cues with randomized pitch and distance. */
    private void playDreadSound(EntityPlayer player) {
        World world = player.world;
        double angle = world.rand.nextDouble() * Math.PI * 2.0D;
        double distance = 7.0D + world.rand.nextDouble() * 12.0D;
        BlockPos pos = new BlockPos(player.posX + Math.cos(angle) * distance,
                player.posY + world.rand.nextInt(5) - 2,
                player.posZ + Math.sin(angle) * distance);
        SoundEvent sound;
        switch (world.rand.nextInt(6)) {
            case 0: sound = SoundEvents.ENTITY_GHAST_SCREAM; break;
            case 1: sound = SoundEvents.ENTITY_ENDERMEN_SCREAM; break;
            case 2: sound = SoundEvents.ENTITY_WITCH_AMBIENT; break;
            case 3: sound = SoundEvents.ENTITY_ZOMBIE_AMBIENT; break;
            case 4: sound = SoundEvents.BLOCK_IRON_DOOR_CLOSE; break;
            default: sound = SoundEvents.AMBIENT_CAVE; break;
        }
        world.playSound(null, pos, sound, SoundCategory.HOSTILE,
                0.35F + world.rand.nextFloat() * 0.3F,
                0.45F + world.rand.nextFloat() * 0.75F);
    }

    private void playBreathBehindPlayer(EntityPlayer player) {
        Vec3d look = player.getLook(1.0F);
        BlockPos pos = new BlockPos(player.posX - look.x * 2.8D,
                player.posY + 1.0D, player.posZ - look.z * 2.8D);
        SoundEvent sound = player.world.rand.nextBoolean()
                ? SoundEvents.ENTITY_ENDERMEN_STARE : SoundEvents.ENTITY_WITCH_AMBIENT;
        player.world.playSound(null, pos, sound, SoundCategory.HOSTILE,
                0.35F + player.world.rand.nextFloat() * 0.2F,
                0.55F + player.world.rand.nextFloat() * 0.22F);
    }

    private void playCaveWhisper(EntityPlayer player) {
        World world = player.world;
        double angle = world.rand.nextDouble() * Math.PI * 2.0D;
        double distance = 5.0D + world.rand.nextDouble() * 9.0D;
        BlockPos pos = new BlockPos(player.posX + Math.cos(angle) * distance,
                player.posY + world.rand.nextInt(5) - 2,
                player.posZ + Math.sin(angle) * distance);
        SoundEvent sound = world.rand.nextInt(3) == 0
                ? SoundEvents.AMBIENT_CAVE : SoundEvents.ENTITY_WITCH_AMBIENT;
        world.playSound(null, pos, sound, SoundCategory.AMBIENT,
                0.25F + world.rand.nextFloat() * 0.25F,
                0.45F + world.rand.nextFloat() * 0.3F);
    }

    private void playFalseFootsteps(EntityPlayer player) {
        World world = player.world;
        double angle = world.rand.nextDouble() * Math.PI * 2.0D;
        double distance = 6.0D + world.rand.nextDouble() * 12.0D;
        BlockPos pos = new BlockPos(player.posX + Math.cos(angle) * distance,
                player.posY, player.posZ + Math.sin(angle) * distance);
        SoundEvent sound = world.rand.nextBoolean()
                ? SoundEvents.BLOCK_STONE_STEP : SoundEvents.BLOCK_WOOD_STEP;
        world.playSound(null, pos, sound, SoundCategory.BLOCKS,
                0.35F, 0.65F + world.rand.nextFloat() * 0.3F);
        if (world.rand.nextBoolean()) {
            BlockPos second = pos.add(world.rand.nextInt(3) - 1, 0, world.rand.nextInt(3) - 1);
            world.playSound(null, second, sound, SoundCategory.BLOCKS, 0.3F, 0.7F);
        }
    }

    private void playDoorCreak(EntityPlayer player) {
        World world = player.world;
        Vec3d look = player.getLook(1.0F);
        BlockPos pos = new BlockPos(player.posX + look.x * 7.0D,
                player.posY, player.posZ + look.z * 7.0D);
        world.playSound(null, pos, SoundEvents.BLOCK_WOODEN_DOOR_OPEN,
                SoundCategory.BLOCKS, 0.45F, 0.55F + world.rand.nextFloat() * 0.25F);
    }

    private void playRecordEleven(EntityPlayer player) {
        World world = player.world;
        double angle = world.rand.nextDouble() * Math.PI * 2.0D;
        double distance = 10.0D + world.rand.nextDouble() * 18.0D;
        BlockPos pos = new BlockPos(player.posX + Math.cos(angle) * distance,
                player.posY, player.posZ + Math.sin(angle) * distance);
        float pitch = 0.55F + world.rand.nextFloat() * 0.9F;
        world.playSound(null, pos, SoundEvents.RECORD_11,
                SoundCategory.RECORDS, 0.75F, pitch);
    }

    private void playNearbyBlockBreak(EntityPlayer player) {
        World world = player.world;
        BlockPos base = player.getPosition();
        BlockPos selected = null;
        int eligible = 0;

        // Reservoir sampling: выбираем случайный подходящий блок без большого списка
        // и не сканируем область дальше необходимого радиуса.
        for (int x = -6; x <= 6; x++) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -6; z <= 6; z++) {
                    BlockPos pos = base.add(x, y, z);
                    if (!world.isBlockLoaded(pos) || pos.distanceSq(base) < 9.0D) continue;

                    IBlockState state = world.getBlockState(pos);
                    Block block = state.getBlock();
                    if (block.isAir(state, world, pos) || state.getMaterial().isLiquid()
                            || block == Blocks.BEDROCK || block == Blocks.BARRIER
                            || block == Blocks.COMMAND_BLOCK || block == Blocks.CHAIN_COMMAND_BLOCK
                            || block == Blocks.REPEATING_COMMAND_BLOCK
                            || block.getBlockHardness(state, world, pos) < 0.0F
                            || block.hasTileEntity(state)) continue;

                    eligible++;
                    if (world.rand.nextInt(eligible) == 0) selected = pos;
                }
            }
        }

        if (selected == null) return;

        IBlockState state = world.getBlockState(selected);
        net.minecraft.block.SoundType sound = state.getBlock().getSoundType(state, world, selected, player);
        world.playSound(null, selected, sound.getBreakSound(), SoundCategory.BLOCKS,
                (sound.getVolume() + 1.0F) * 0.5F,
                sound.getPitch() * (0.8F + world.rand.nextFloat() * 0.4F));
    }

    private void spawnObserverMobs(EntityPlayer player) {
        World world = player.world;
        int nearby = 0;
        for (Entity entity : world.loadedEntityList) {
            if ((entity instanceof EntityZombie || entity instanceof EntitySkeleton)
                    && entity.getEntityData().getBoolean(OBSERVER_TAG)
                    && player.getUniqueID().toString().equals(
                    entity.getEntityData().getString(OBSERVER_OWNER_TAG))) nearby++;
        }
        if (nearby >= 2 || world.getLight(player.getPosition()) > 8) return;

        int count = 1 + world.rand.nextInt(2);
        for (int i = 0; i < count && nearby < 2; i++) {
            double angle = world.rand.nextDouble() * Math.PI * 2.0D;
            double distance = 22.0D + world.rand.nextDouble() * 14.0D;
            BlockPos column = new BlockPos(player.posX + Math.cos(angle) * distance,
                    player.posY, player.posZ + Math.sin(angle) * distance);
            if (!world.isBlockLoaded(column)) continue;
            BlockPos pos = world.getTopSolidOrLiquidBlock(column);
            if (!isSafeSpawn(player, pos, 18.0D, 42.0D)) continue;
            // Зомби и скелеты не должны появляться под прямым дневным небом:
            // иначе ванильная механика поджига уничтожит их почти сразу.
            if (world.isDaytime() && world.canSeeSky(pos)) continue;

            net.minecraft.entity.monster.EntityMob observer;
            if (world.rand.nextBoolean()) observer = new EntityZombie(world);
            else observer = new EntitySkeleton(world);
            observer.setPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            observer.setNoAI(true);
            observer.setSilent(true);
            observer.enablePersistence();
            facePlayer(observer, player);
            observer.getEntityData().setBoolean(OBSERVER_TAG, true);
            observer.getEntityData().setString(OBSERVER_OWNER_TAG, player.getUniqueID().toString());
            int observerLifetime = 700 + world.rand.nextInt(700);
            observer.getEntityData().setInteger(OBSERVER_AGE_TAG, 0);
            observer.getEntityData().setInteger(OBSERVER_LIFE_TAG, observerLifetime);
            observer.getEntityData().setLong(OBSERVER_EXPIRES_TAG,
                    world.getTotalWorldTime() + observerLifetime);
            if (world.spawnEntity(observer)) nearby++;
        }
    }

    @SubscribeEvent
    public void onObserverTick(net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent event) {
        Entity entity = event.getEntity();
        if (entity.world.isRemote || !entity.getEntityData().getBoolean(OBSERVER_TAG)) return;

        int age = entity.getEntityData().getInteger(OBSERVER_AGE_TAG) + 1;
        entity.getEntityData().setInteger(OBSERVER_AGE_TAG, age);
        long expiresAt = entity.getEntityData().getLong(OBSERVER_EXPIRES_TAG);
        if ((expiresAt > 0L && entity.world.getTotalWorldTime() >= expiresAt)
                || age >= entity.getEntityData().getInteger(OBSERVER_LIFE_TAG)) {
            entity.setDead();
            return;
        }

        if (!ModConfig.maskedGirlEnabled) {
            entity.setDead();
            return;
        }

        String ownerId = entity.getEntityData().getString(OBSERVER_OWNER_TAG);
        for (EntityPlayer player : entity.world.playerEntities) {
            if (ownerId.equals(player.getUniqueID().toString())) {
                MaskedGirlWorldData worldData = MaskedGirlWorldData.get(entity.world);
                if (worldData == null || !worldData.isActive()) {
                    entity.setDead();
                    return;
                }
                if (entity instanceof EntityLivingBase) {
                    facePlayer((EntityLivingBase) entity, player);
                }
                return;
            }
        }
    }

    private boolean placeStrangeItem(EntityPlayer player) {
        World world = player.world;
        BlockPos base = player.getPosition();
        List<BlockPos> spots = new ArrayList<>();
        for (int x = -8; x <= 8; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -8; z <= 8; z++) {
                    BlockPos pos = base.add(x, y, z);
                    if (!world.isBlockLoaded(pos)) continue;
                    Block block = world.getBlockState(pos).getBlock();
                    if ((block == Blocks.BED || block == Blocks.CHEST || block == Blocks.CRAFTING_TABLE)
                            && world.isAirBlock(pos.up()) && world.isAirBlock(pos.up(2))) {
                        spots.add(pos.up());
                    }
                }
            }
        }
        if (spots.isEmpty()) return false;

        BlockPos pos = spots.get(world.rand.nextInt(spots.size()));
        ItemStack stack = new ItemStack(world.rand.nextBoolean() ? Items.BONE : Items.STRING, 1);
        EntityItem item = new EntityItem(world, pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, stack);
        item.setPickupDelay(40);
        return world.spawnEntity(item);
    }
}
