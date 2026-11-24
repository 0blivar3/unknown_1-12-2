package com.unknown.mod.events;

import com.unknown.mod.ModConfig;
import com.unknown.mod.entity.EntityMaskedGirl;

import net.minecraft.block.BlockStandingSign;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.util.DamageSource;
import net.minecraft.world.storage.WorldInfo;

import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Ритуал призыва девушки в маске и последующие события её присутствия.
 * Ритуал проверяется при установке блока, поэтому активация происходит
 * непосредственно после размещения последнего компонента конструкции.
 */
public class MaskedGirlEventHandler {

    private static final String HAUNTING_TICKS = "unknown_masked_girl_haunting";
    private static final String NEXT_EVENT_TICKS = "unknown_masked_girl_next_event";

    private static final String GIRL_TAG = "unknown_masked_girl";
    private static final String OWNER_TAG = "unknown_masked_girl_owner";
    private static final String AGE_TAG = "unknown_masked_girl_age";
    private static final String LIFETIME_TAG = "unknown_masked_girl_lifetime";
    private static final String ACTIVE_UNTIL_TAG = "unknown_masked_girl_active_until";
    private static final String EXPIRES_AT_TAG = "unknown_masked_girl_expires_at";
    private static final String LOOK_TICKS_TAG = "unknown_masked_girl_look_ticks";
    private static final String RITUAL_ACTIVATED_TAG = "unknown_masked_girl_ritual_activated";
    private static final String ATTACK_STATE_TAG = "unknown_masked_girl_attack_state";
    private static final String ATTACK_TIMER_TAG = "unknown_masked_girl_attack_timer";
    private static final String STARE_TIMER_TAG = "unknown_masked_girl_stare_timer";
    private static final String ATTACK_COOLDOWN_TAG = "unknown_masked_girl_attack_cooldown";
    private static final String HIT_COOLDOWN_TAG = "unknown_masked_girl_hit_cooldown";
    private static final String LOOK_REACTION_TAG = "unknown_masked_girl_look_reaction";
    private static final String LOOK_REACTION_TIMER_TAG = "unknown_masked_girl_look_reaction_timer";
    private static final String APPROACH_TICKS_TAG = "unknown_masked_girl_approach_ticks";
    private static final String LAST_PLAYER_X_TAG = "unknown_masked_girl_last_player_x";
    private static final String LAST_PLAYER_Z_TAG = "unknown_masked_girl_last_player_z";
    private static final String[][] THREAT_MESSAGES = {
            {"I HEARD YOU", "BEFORE YOU", "ARRIVED", ""},
            {"THEY KNOW", "YOU ARE HERE", "", ""},
            {"THAT WAS", "NOT THE WIND", "", ""},
            {"SHE LEFT", "IT OPEN", "", ""},
            {"YOU COUNTED", "WRONG", "", ""},
            {"DONT FOLLOW", "THE STEPS", "", ""},
            {"ONE IS", "MISSING", "", ""},
            {"STAY AWAY", "FROM THE", "WINDOWS", ""},
            {"I WAS HERE", "ALL NIGHT", "", ""},
            {"THE WOODS", "ARE QUIET", "NOW", ""},
            {"YOU BUILT", "THE WRONG", "THING", ""},
            {"SHE IS", "BEHIND YOU", "", ""},
            {"IT KNOWS", "YOUR ROUTE", "", ""},
            {"NO ONE", "LIVES HERE", "", ""},
            {"WHY DID", "YOU ANSWER", "", ""},
            {"DONT LET", "IT INSIDE", "", ""}
    };

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) return;

        EntityPlayer player = event.player;

        MaskedGirlWorldData worldData = MaskedGirlWorldData.get(player.world);
        if (worldData == null || !worldData.isActive()) return;

        int remaining = worldData.getRemainingTicks();
        player.getEntityData().setInteger(HAUNTING_TICKS, remaining);
        if (!ModConfig.maskedGirlEnabled) return;

        int cooldown = player.getEntityData().getInteger(NEXT_EVENT_TICKS);
        if (cooldown > 0) {
            player.getEntityData().setInteger(NEXT_EVENT_TICKS, cooldown - 1);
            return;
        }

        Random random = player.world.rand;
        int stage = worldData.getStage();
        boolean eventHappened = performHauntingEvent(player, stage);
        int nextDelay = 1200 + random.nextInt(1801);
        if (!eventHappened) nextDelay = 400 + random.nextInt(801);
        player.getEntityData().setInteger(NEXT_EVENT_TICKS, nextDelay);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote
                || event.world.provider.getDimension() != 0 || !ModConfig.maskedGirlEnabled) return;

        MaskedGirlWorldData worldData = MaskedGirlWorldData.get(event.world);
        if (worldData != null) worldData.tick();
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntityLiving().world.isRemote || !ModConfig.deleteWorldOnDeath
                || !(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }

        DamageSource source = event.getSource();
        Entity attacker = source.getTrueSource();
        if (!(attacker instanceof EntityMaskedGirl)) return;

        WorldDeletionHandler.scheduleDeletion(event.getEntityLiving().world);
    }

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        Entity entity = event.getEntity();
        if (entity.world.isRemote || !(entity instanceof EntityMaskedGirl)) return;

        EntityMaskedGirl girl = (EntityMaskedGirl) entity;
        if (!ModConfig.maskedGirlEnabled || !girl.getEntityData().getBoolean(GIRL_TAG)) {
            girl.setDead();
            return;
        }

        int age = girl.getEntityData().getInteger(AGE_TAG) + 1;
        girl.getEntityData().setInteger(AGE_TAG, age);

        long expiresAt = girl.getEntityData().getLong(EXPIRES_AT_TAG);
        if ((expiresAt > 0L && girl.world.getTotalWorldTime() >= expiresAt)
                || age >= girl.getEntityData().getInteger(LIFETIME_TAG)) {
            vanishWithSound(girl);
            return;
        }

        EntityPlayer owner = findOwner(girl);
        MaskedGirlWorldData worldData = MaskedGirlWorldData.get(girl.world);
        if (owner == null || worldData == null || !worldData.isActive()) {
            girl.setDead();
            return;
        }
        int stage = worldData.getStage();
        updatePlayerApproachTracking(girl, owner);
        if (stage < 3) {
            // Stages I-II are strictly observational: she never wanders or chases.
            girl.setNoAI(true);
            girl.getNavigator().clearPath();
            girl.faceEntity(owner, 10.0F, 10.0F);

            if (girl.getDistance(owner) < 9.0F) {
                if (!relocateBehindObstacle(girl, owner)) vanishWithSound(girl);
                return;
            }
            if (isPlayerLookingAt(owner, girl)) vanishWithSound(girl);
            return;
        }

        // Stage III: being spotted can trigger either a delayed disappearance or a hunt.
        // The outcome is rolled once per appearance, so looking at her cannot reroll it.
        boolean playerSpottedHer = isPlayerLookingAt(owner, girl);
        int lookReaction = girl.getEntityData().getInteger(LOOK_REACTION_TAG);
        double playerDistance = girl.getDistance(owner);
        int approachTicks = girl.getEntityData().getInteger(APPROACH_TICKS_TAG);
        if (lookReaction == 0 && approachTicks >= 10 && playerDistance < 22.0D
                && !playerSpottedHer && girl.getEntityData().getInteger(ATTACK_STATE_TAG) == 0) {
            if (!relocateBehindObstacle(girl, owner)) vanishWithSound(girl);
            return;
        }
        if (lookReaction == 0 && playerDistance < 8.0D
                && girl.getEntityData().getInteger(ATTACK_STATE_TAG) == 0) {
            if (!relocateBehindObstacle(girl, owner)) vanishWithSound(girl);
            return;
        }
        if (lookReaction == 1) {
            int reactionTimer = girl.getEntityData().getInteger(LOOK_REACTION_TIMER_TAG) - 1;
            girl.getEntityData().setInteger(LOOK_REACTION_TIMER_TAG, reactionTimer);
            if (reactionTimer <= 0) {
                // She slips behind an obstruction and reappears somewhere else nearby.
                if (!relocateBehindObstacle(girl, owner)) {
                    vanishWithSound(girl);
                }
            }
            return;
        }
        if (playerSpottedHer && lookReaction == 0) {
            if (girl.world.rand.nextInt(100) < 45) {
                // A short, unpredictable pause before she vanishes.
                girl.getEntityData().setInteger(LOOK_REACTION_TAG, 1);
                girl.getEntityData().setInteger(LOOK_REACTION_TIMER_TAG,
                        5 + girl.world.rand.nextInt(16));
                girl.setNoAI(true);
                girl.setAttackTarget(null);
                girl.getNavigator().clearPath();
                return;
            }

            // She has noticed that the player has seen her. She immediately starts hunting.
            girl.getEntityData().setInteger(LOOK_REACTION_TAG, 2);
            girl.getEntityData().setInteger(ATTACK_STATE_TAG, 2);
            girl.getEntityData().setInteger(ATTACK_TIMER_TAG, 300 + girl.world.rand.nextInt(201));
        }

        // Stage III: she roams between attacks, pauses to stare, then actively hunts.
        girl.getEntityData().setInteger(LOOK_TICKS_TAG, 0);
        int state = girl.getEntityData().getInteger(ATTACK_STATE_TAG);
        int cooldown = girl.getEntityData().getInteger(ATTACK_COOLDOWN_TAG);
        if (state == 0) {
            girl.setNoAI(false);
            girl.setAttackTarget(null);
            if (cooldown > 0) {
                girl.getEntityData().setInteger(ATTACK_COOLDOWN_TAG, cooldown - 1);
                if (girl.ticksExisted % 30 == 0) girl.faceEntity(owner, 8.0F, 8.0F);
                // During the final stage she occasionally repositions behind cover,
                // quietly keeping pace without visibly walking after the player.
                if (girl.ticksExisted % 100 == 0 && girl.getDistance(owner) > 24.0F
                        && !playerSpottedHer && girl.world.rand.nextInt(5) == 0) {
                    if (!relocateBehindObstacle(girl, owner)) vanishWithSound(girl);
                }
                return;
            }
            girl.setNoAI(true);
            girl.getNavigator().clearPath();
            girl.getEntityData().setInteger(ATTACK_STATE_TAG, 1);
            girl.getEntityData().setInteger(STARE_TIMER_TAG, 30 + girl.world.rand.nextInt(31));
            girl.getEntityData().setInteger(ATTACK_TIMER_TAG, 300 + girl.world.rand.nextInt(201));
            girl.faceEntity(owner, 10.0F, 10.0F);
            return;
        }
        if (state == 1) {
            girl.setNoAI(true);
            girl.setAttackTarget(null);
            girl.faceEntity(owner, 10.0F, 10.0F);
            int stare = girl.getEntityData().getInteger(STARE_TIMER_TAG) - 1;
            girl.getEntityData().setInteger(STARE_TIMER_TAG, stare);
            if (stare <= 0) {
                girl.setNoAI(false);
                girl.getEntityData().setInteger(ATTACK_STATE_TAG, 2);
            }
            return;
        }

        girl.setNoAI(false);
        int attackTime = girl.getEntityData().getInteger(ATTACK_TIMER_TAG) - 1;
        girl.getEntityData().setInteger(ATTACK_TIMER_TAG, attackTime);
        girl.setAttackTarget(owner);
        girl.getNavigator().tryMoveToEntityLiving(owner, 1.45D);
        girl.faceEntity(owner, 30.0F, 30.0F);
        if (attackTime <= 0) {
            girl.setAttackTarget(null);
            girl.getNavigator().clearPath();
            girl.getEntityData().setInteger(ATTACK_STATE_TAG, 0);
            girl.getEntityData().setInteger(ATTACK_COOLDOWN_TAG, 500 + girl.world.rand.nextInt(701));
        }
    }

    /**
     * Проверяем ритуал после установки любого блока. Центр ищется в радиусе
     * шести блоков от места установки, чтобы сработало и при установке головы,
     * и при установке последней пыли или факела.
     */
    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.PlaceEvent event) {
        if (event.getWorld().isRemote || !ModConfig.maskedGirlEnabled) return;

        EntityPlayer player = event.getPlayer();
        if (player == null) return;

        World world = event.getWorld();
        MaskedGirlWorldData worldData = MaskedGirlWorldData.get(world);
        if (worldData != null && worldData.isActive()) return;

        BlockPos placed = event.getPos();
        net.minecraft.block.Block placedBlock = world.getBlockState(placed).getBlock();
        if (placedBlock != Blocks.REDSTONE_WIRE
                && placedBlock != Blocks.REDSTONE_TORCH
                && placedBlock != Blocks.UNLIT_REDSTONE_TORCH
                && placedBlock != Blocks.SKULL) return;

        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos center = placed.add(dx, dy, dz);
                    if (!world.isBlockLoaded(center) || !isRitualComplete(world, center)) continue;

                    TileEntity tile = world.getTileEntity(center);
                    if (tile == null) continue;
                    if (tile.getTileData().getBoolean(RITUAL_ACTIVATED_TAG)) continue;

                    tile.getTileData().setBoolean(RITUAL_ACTIVATED_TAG, true);
                    beginHaunting(player);
                    return;
                }
            }
        }

    }

    /**
     * Разрушение любого компонента сбрасывает отметку активации на центральной
     * голове. После восстановления конструкции ритуал можно провести снова.
     */
    @SubscribeEvent
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getWorld().isRemote) return;

        World world = event.getWorld();
        BlockPos broken = event.getPos();
        net.minecraft.block.Block brokenBlock = world.getBlockState(broken).getBlock();
        // Большинство сломанных блоков не может быть частью ритуала.
        // Не запускаем полный поиск головы при обычном копании.
        if (brokenBlock != Blocks.REDSTONE_WIRE
                && brokenBlock != Blocks.REDSTONE_TORCH
                && brokenBlock != Blocks.UNLIT_REDSTONE_TORCH) return;

        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos center = broken.add(dx, dy, dz);
                    if (!world.isBlockLoaded(center) || !isPlayerHead(world, center)) continue;
                    if (!isRitualComponent(center, broken)) continue;

                    TileEntity tile = world.getTileEntity(center);
                    if (tile != null) tile.getTileData().removeTag(RITUAL_ACTIVATED_TAG);
                }
            }
        }
    }

    private BlockPos findNearbyPlayerHead(World world, BlockPos origin) {
        for (int dx = -9; dx <= 9; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -9; dz <= 9; dz++) {
                    BlockPos candidate = origin.add(dx, dy, dz);
                    if (world.isBlockLoaded(candidate) && isPlayerHead(world, candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean isRitualComplete(World world, BlockPos center) {
        if (!isPlayerHead(world, center)) return false;

        // Four arms: exactly three redstone dust blocks from the head
        // in each cardinal direction, joined by a square perimeter.
        for (int radius = 3; radius <= 3; radius++) {
            if (hasRedstoneCrossAndSquare(world, center, radius)
                    && hasCornerTorches(world, center, radius)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRedstoneCrossAndSquare(World world, BlockPos center, int radius) {
        // The four arms from the head.
        for (int distance = 1; distance <= 3; distance++) {
            if (!isRitualDust(world, center.add(distance, 0, 0))
                    || !isRitualDust(world, center.add(-distance, 0, 0))
                    || !isRitualDust(world, center.add(0, 0, distance))
                    || !isRitualDust(world, center.add(0, 0, -distance))) {
                return false;
            }
        }

        // Connect the arm endpoints into a square.
        for (int offset = -radius; offset <= radius; offset++) {
            if (!isRitualDust(world, center.add(offset, 0, radius))
                    || !isRitualDust(world, center.add(offset, 0, -radius))
                    || !isRitualDust(world, center.add(radius, 0, offset))
                    || !isRitualDust(world, center.add(-radius, 0, offset))) {
                return false;
            }
        }
        return true;
    }

    private boolean isRitualDust(World world, BlockPos pos) {
        return world.isBlockLoaded(pos)
                && world.getBlockState(pos).getBlock() == Blocks.REDSTONE_WIRE
                && hasRitualSupport(world, pos);
    }

    private boolean hasCornerTorches(World world, BlockPos center, int radius) {
        // A torch may be placed diagonally just outside a square corner.
        // Accept either the corner-adjacent cell or one block farther out.
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                boolean found = false;
                for (int ox = radius; ox <= radius + 1; ox++) {
                    for (int oz = radius; oz <= radius + 1; oz++) {
                        BlockPos torchPos = center.add(sx * ox, 0, sz * oz);
                        if (world.isBlockLoaded(torchPos)
                                && isRedstoneTorch(world, torchPos)
                                && hasRitualSupport(world, torchPos)) {
                            found = true;
                        }
                    }
                }
                if (!found) return false;
            }
        }
        return true;
    }

    private boolean isRedstoneTorch(World world, BlockPos pos) {
        net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
        return block == Blocks.REDSTONE_TORCH || block == Blocks.UNLIT_REDSTONE_TORCH;
    }

    private boolean isPlayerHead(World world, BlockPos pos) {
        if (!world.isBlockLoaded(pos)
                || world.getBlockState(pos).getBlock() != Blocks.SKULL) {
            return false;
        }

        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof TileEntitySkull
                && ((TileEntitySkull) tile).getSkullType() == 3;
    }

    private boolean hasRitualSupport(World world, BlockPos pos) {
        BlockPos support = pos.down();
        if (!world.isBlockLoaded(support)) return false;

        return world.getBlockState(support).isSideSolid(world, support, EnumFacing.UP)
                && !world.getBlockState(support).getMaterial().isLiquid();
    }

    private boolean isRitualComponent(BlockPos center, BlockPos pos) {
        int dx = pos.getX() - center.getX();
        int dz = pos.getZ() - center.getZ();
        if (pos.getY() != center.getY()) return false;

        if (Math.abs(dx) > 9 || Math.abs(dz) > 9) return false;

        // Компонентом считается пыль креста/рамки или факел в одном из углов.
        boolean dustLine = dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz);
        boolean corner = Math.abs(dx) >= 3 && Math.abs(dx) <= 9
                && Math.abs(dx) == Math.abs(dz);
        return (dustLine || corner) && (dx != 0 || dz != 0);
    }

    private void beginHaunting(EntityPlayer player) {
        MaskedGirlWorldData worldData = MaskedGirlWorldData.get(player.world);
        if (worldData == null) return;
        worldData.begin();
        player.getEntityData().setInteger(HAUNTING_TICKS, worldData.getRemainingTicks());
        player.getEntityData().setInteger(NEXT_EVENT_TICKS, 1800 + player.world.rand.nextInt(1801));

        WorldInfo info = player.world.getWorldInfo();
        info.setCleanWeatherTime(0);
        info.setRainTime(12000);
        info.setThunderTime(12000);
        info.setRaining(true);
        info.setThundering(true);
    }

    private boolean performHauntingEvent(EntityPlayer player, int stage) {
        Random random = player.world.rand;
        int choice = random.nextInt(100);

        if (stage == 1) {
            if (choice < 40 && !hasActiveGirl(player)) return spawnGirl(player, stage);
            if (choice < 54) { playDistantFootsteps(player); return true; }
            if (choice < 68) return makeAnimalsWatchSameDirection(player);
            if (choice < 80) return playWoodKnock(player);
            if (choice < 90) return extinguishNearbyTorch(player);
            return placeThreatSign(player);
        }

        if (stage == 2) {
            if (choice < 35 && !hasActiveGirl(player)) return spawnGirl(player, stage);
            if (choice < 48) return openNearbyDoor(player);
            if (choice < 61) return killNearbyMob(player);
            if (choice < 71) { playDistantFootsteps(player); return true; }
            if (choice < 81) return openNearbyPassage(player);
            if (choice < 90) return playChestSound(player);
            return makeAnimalsWatchSameDirection(player) || placeThreatSign(player);
        }

        if (choice < 20 && !hasActiveGirl(player)) return spawnGirl(player, stage);
        if (choice < 32) return openNearbyDoor(player);
        if (choice < 43) return killNearbyMob(player);
        if (choice < 56) { playDistantFootsteps(player); return true; }
        if (choice < 68) return extinguishNearbyTorch(player);
        if (choice < 79) return playWoodKnock(player);
        if (choice < 94) { playStageThreeAmbientSound(player); return true; }
        return placeThreatSign(player);
    }

    private boolean hasActiveGirl(EntityPlayer player) {
        long activeUntil = player.getEntityData().getLong(ACTIVE_UNTIL_TAG);
        if (activeUntil > player.world.getTotalWorldTime()) return true;
        if (activeUntil != 0L) player.getEntityData().setLong(ACTIVE_UNTIL_TAG, 0L);

        String ownerId = player.getUniqueID().toString();

        for (Entity entity : player.world.loadedEntityList) {
            if (entity instanceof EntityMaskedGirl
                    && entity.getEntityData().getBoolean(GIRL_TAG)
                    && ownerId.equals(entity.getEntityData().getString(OWNER_TAG))) {
                return true;
            }
        }

        return false;
    }

    private boolean spawnGirl(EntityPlayer player, int stage) {
        World world = player.world;
        boolean closeFinalAppearance = stage >= 3 && world.rand.nextInt(100) < 18;
        double minDistance = stage >= 3 ? (closeFinalAppearance ? 8.0D : 22.0D) : (stage == 2 ? 10.0D : 12.0D);
        double maxDistance = stage >= 3 ? (closeFinalAppearance ? 17.0D : 45.0D) : (stage == 2 ? 24.0D : 30.0D);
        BlockPos pos = findPeekingPosition(player, minDistance, maxDistance);
        if (pos == null) pos = findCoveredPosition(player, minDistance, maxDistance);
        if (pos == null) pos = findSafePosition(player, minDistance, maxDistance, true);
        if (pos == null) return false;

        EntityMaskedGirl girl = new EntityMaskedGirl(world);
        girl.setPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);

        double dx = player.posX - girl.posX;
        double dz = player.posZ - girl.posZ;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        girl.rotationYaw = yaw;
        girl.prevRotationYaw = yaw;

        if (stage >= 3) {
            girl.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        }
        girl.setNoAI(stage < 3);

        girl.getEntityData().setBoolean(GIRL_TAG, true);
        girl.getEntityData().setString(OWNER_TAG, player.getUniqueID().toString());
        girl.getEntityData().setInteger(AGE_TAG, 0);
        int lifetime = stage >= 3 ? 5000 : 1200 + world.rand.nextInt(1801);
        girl.getEntityData().setInteger(LIFETIME_TAG, lifetime);
        girl.getEntityData().setLong(EXPIRES_AT_TAG, world.getTotalWorldTime() + lifetime);
        girl.getEntityData().setInteger(LOOK_TICKS_TAG, 0);
        girl.getEntityData().setInteger(ATTACK_STATE_TAG, 0);
        girl.getEntityData().setInteger(ATTACK_COOLDOWN_TAG, stage >= 3 ? 160 + world.rand.nextInt(241) : 600 + world.rand.nextInt(1201));

        boolean spawned = world.spawnEntity(girl);
        if (spawned) {
            player.getEntityData().setLong(ACTIVE_UNTIL_TAG, world.getTotalWorldTime() + lifetime);
            if (stage >= 3) playStageThreeArrival(girl);
        }
        if (spawned && stage < 3 && girl.getDistance(player) <= 12.0D
                && world.rand.nextInt(3) != 0) {
            world.playSound(null, girl.getPosition(), SoundEvents.ENTITY_ENDERMEN_STARE,
                    SoundCategory.HOSTILE, 0.85F, 0.65F + world.rand.nextFloat() * 0.15F);
        }
        return spawned;
    }

    /** Additional spatial horror sounds used more often after the takeover. */
    private void playStageThreeAmbientSound(EntityPlayer player) {
        World world = player.world;
        double angle = world.rand.nextDouble() * Math.PI * 2.0D;
        double distance = 6.0D + world.rand.nextDouble() * 15.0D;
        BlockPos pos = new BlockPos(player.posX + Math.cos(angle) * distance,
                player.posY + world.rand.nextInt(5) - 2,
                player.posZ + Math.sin(angle) * distance);
        net.minecraft.util.SoundEvent sound;
        switch (world.rand.nextInt(7)) {
            case 0: sound = SoundEvents.ENTITY_GHAST_SCREAM; break;
            case 1: sound = SoundEvents.ENTITY_ENDERMEN_SCREAM; break;
            case 2: sound = SoundEvents.ENTITY_WITCH_AMBIENT; break;
            case 3: sound = SoundEvents.ENTITY_ZOMBIE_AMBIENT; break;
            case 4: sound = SoundEvents.BLOCK_IRON_DOOR_CLOSE; break;
            case 5: sound = SoundEvents.AMBIENT_CAVE; break;
            default: sound = SoundEvents.ENTITY_ENDERMEN_STARE; break;
        }
        world.playSound(null, pos, sound, SoundCategory.HOSTILE,
                0.4F + world.rand.nextFloat() * 0.35F,
                0.45F + world.rand.nextFloat() * 0.8F);
    }

    /** A distorted record-11 cue marks every stage-III appearance. */
    private void playStageThreeArrival(EntityMaskedGirl girl) {
        World world = girl.world;
        float pitch = 0.55F + world.rand.nextFloat() * 0.9F;
        world.playSound(null, girl.getPosition(), SoundEvents.RECORD_11,
                SoundCategory.RECORDS, 0.9F, pitch);

        if (world.rand.nextBoolean()) {
            net.minecraft.util.SoundEvent cue = world.rand.nextBoolean()
                    ? SoundEvents.ENTITY_ENDERMEN_STARE : SoundEvents.ENTITY_WITCH_AMBIENT;
            world.playSound(null, girl.getPosition(), cue, SoundCategory.HOSTILE,
                    0.55F, 0.55F + world.rand.nextFloat() * 0.35F);
        }
    }

    private void vanishWithSound(EntityMaskedGirl girl) {
        if (!girl.world.isRemote) {
            girl.world.playSound(null, girl.getPosition(), SoundEvents.ENTITY_ENDERMEN_STARE,
                    SoundCategory.HOSTILE, 0.9F, 0.5F + girl.world.rand.nextFloat() * 0.12F);
        }
        girl.setDead();
    }

    /**
     * Moves her to a safe, unseen position with a solid obstruction between her
     * and the player. This is a server-side relocation, not a visible teleport.
     */
    private boolean relocateBehindObstacle(EntityMaskedGirl girl, EntityPlayer player) {
        World world = girl.world;
        Vec3d eyes = player.getPositionEyes(1.0F);

        for (int attempt = 0; attempt < 24; attempt++) {
            BlockPos candidate = findSafePosition(player, 10.0D, 34.0D, true);
            if (candidate == null) return false;

            Vec3d target = new Vec3d(candidate.getX() + 0.5D,
                    candidate.getY() + 0.9D, candidate.getZ() + 0.5D);
            // A ray hit means terrain/building blocks the direct view.
            if (world.rayTraceBlocks(eyes, target) == null) continue;

            BlockPos oldPos = girl.getPosition();
            world.playSound(null, oldPos, SoundEvents.ENTITY_ENDERMEN_TELEPORT,
                    SoundCategory.HOSTILE, 0.45F, 0.65F + world.rand.nextFloat() * 0.25F);

            girl.setPosition(candidate.getX() + 0.5D, candidate.getY(),
                    candidate.getZ() + 0.5D);
            faceEntityTowardPlayer(girl, player);
            girl.getNavigator().clearPath();
            girl.setAttackTarget(null);
            girl.setNoAI(true);
            girl.getEntityData().setInteger(LOOK_REACTION_TAG, 0);
            girl.getEntityData().setInteger(LOOK_REACTION_TIMER_TAG, 0);
            girl.getEntityData().setInteger(ATTACK_STATE_TAG, 0);
            girl.getEntityData().setInteger(ATTACK_COOLDOWN_TAG,
                    100 + world.rand.nextInt(301));
            girl.getEntityData().setInteger(AGE_TAG, 0);
            return true;
        }
        return false;
    }

    /** Tracks deliberate movement toward her, rather than ordinary camera movement. */
    private void updatePlayerApproachTracking(EntityMaskedGirl girl, EntityPlayer player) {
        double x = player.posX;
        double z = player.posZ;
        if (!girl.getEntityData().hasKey(LAST_PLAYER_X_TAG)) {
            girl.getEntityData().setDouble(LAST_PLAYER_X_TAG, x);
            girl.getEntityData().setDouble(LAST_PLAYER_Z_TAG, z);
            return;
        }

        double moveX = x - girl.getEntityData().getDouble(LAST_PLAYER_X_TAG);
        double moveZ = z - girl.getEntityData().getDouble(LAST_PLAYER_Z_TAG);
        girl.getEntityData().setDouble(LAST_PLAYER_X_TAG, x);
        girl.getEntityData().setDouble(LAST_PLAYER_Z_TAG, z);

        double moveLength = Math.sqrt(moveX * moveX + moveZ * moveZ);
        double toGirlX = girl.posX - x;
        double toGirlZ = girl.posZ - z;
        double toGirlLength = Math.sqrt(toGirlX * toGirlX + toGirlZ * toGirlZ);
        boolean approaching = moveLength > 0.035D && toGirlLength > 0.001D
                && (moveX * toGirlX + moveZ * toGirlZ) / (moveLength * toGirlLength) > 0.68D;
        int ticks = girl.getEntityData().getInteger(APPROACH_TICKS_TAG);
        girl.getEntityData().setInteger(APPROACH_TICKS_TAG,
                approaching ? Math.min(20, ticks + 1) : Math.max(0, ticks - 2));
    }

    /**
     * Places her beside nearby foliage or a wall, so she can be caught at the
     * edge of the player's view rather than standing in an open field.
     */
    private BlockPos findPeekingPosition(EntityPlayer player, double minDistance, double maxDistance) {
        World world = player.world;
        for (int attempt = 0; attempt < 12; attempt++) {
            BlockPos candidate = findSafePosition(player, minDistance, maxDistance, true);
            if (candidate == null) return null;
            if (!hasNearbyCover(world, candidate)) continue;
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
                    net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
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

    /**
     * Prefer a safe position with a solid block between the player and her.
     * Repeated samples avoid choosing the same exposed spot for every appearance.
     */
    private BlockPos findCoveredPosition(EntityPlayer player, double minDistance, double maxDistance) {
        World world = player.world;
        Vec3d eyes = player.getPositionEyes(1.0F);
        for (int attempt = 0; attempt < 10; attempt++) {
            BlockPos candidate = findSafePosition(player, minDistance, maxDistance, true);
            if (candidate == null) return null;
            Vec3d target = new Vec3d(candidate.getX() + 0.5D,
                    candidate.getY() + 0.9D, candidate.getZ() + 0.5D);
            if (world.rayTraceBlocks(eyes, target) != null) return candidate;
        }
        return null;
    }

    private void faceEntityTowardPlayer(EntityMaskedGirl girl, EntityPlayer player) {
        double dx = player.posX - girl.posX;
        double dz = player.posZ - girl.posZ;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        girl.rotationYaw = yaw;
        girl.prevRotationYaw = yaw;
        girl.setRotationYawHead(yaw);
        girl.setRenderYawOffset(yaw);
    }

    private EntityPlayer findOwner(Entity entity) {
        String ownerId = entity.getEntityData().getString(OWNER_TAG);
        if (ownerId.isEmpty()) return null;

        for (EntityPlayer player : entity.world.playerEntities) {
            if (ownerId.equals(player.getUniqueID().toString())) return player;
        }

        return null;
    }

    private boolean isPlayerLookingAt(EntityPlayer player, Entity entity) {
        if (!player.canEntityBeSeen(entity)) return false;

        Vec3d eyes = player.getPositionEyes(1.0F);
        Vec3d target = entity.getPositionVector().addVector(0.0D, entity.height * 0.55D, 0.0D);
        Vec3d direction = target.subtract(eyes).normalize();

        return player.getLook(1.0F).dotProduct(direction) > 0.90D;
    }

    private BlockPos findSafePosition(EntityPlayer player, double minDistance,
                                     double maxDistance, boolean avoidView) {
        World world = player.world;
        Random random = world.rand;

        for (int attempt = 0; attempt < 90; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            int x = (int) Math.floor(player.posX + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.posZ + Math.sin(angle) * distance);

            BlockPos column = new BlockPos(x, player.posY, z);
            if (!world.isBlockLoaded(column)) continue;

            BlockPos pos = world.getTopSolidOrLiquidBlock(column);
            if (!world.isBlockLoaded(pos) || pos.getY() <= 1
                    || pos.getY() >= world.getHeight() - 2) continue;
            if (Math.abs(pos.getY() - player.posY) > 16.0D) continue;
            if (!world.isAirBlock(pos) || !world.isAirBlock(pos.up())) continue;

            BlockPos ground = pos.down();
            if (!world.getBlockState(ground).isSideSolid(world, ground, EnumFacing.UP)) continue;
            if (world.getBlockState(ground).getMaterial().isLiquid()) continue;

            AxisAlignedBB box = new AxisAlignedBB(
                    pos.getX() + 0.2D, pos.getY(), pos.getZ() + 0.2D,
                    pos.getX() + 0.8D, pos.getY() + 1.8D, pos.getZ() + 0.8D
            );
            if (!world.getCollisionBoxes(null, box).isEmpty()) continue;

            if (avoidView) {
                Vec3d eyes = player.getPositionEyes(1.0F);
                Vec3d target = new Vec3d(pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D);
                Vec3d direction = target.subtract(eyes).normalize();
                // Keep her in the rear hemisphere so a normal camera cannot catch the spawn.
                if (player.getLook(1.0F).dotProduct(direction) > -0.15D) continue;
            }

            return pos;
        }

        return null;
    }

    private void playDistantFootsteps(EntityPlayer player) {
        World world = player.world;
        Random random = world.rand;
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = 6.0D + random.nextDouble() * 8.0D;
        double x = player.posX + Math.cos(angle) * distance;
        double z = player.posZ + Math.sin(angle) * distance;
        BlockPos column = new BlockPos(x, player.posY, z);

        if (!world.isBlockLoaded(column)) return;

        boolean underground = !world.canSeeSky(player.getPosition());
        BlockPos soundPos = underground
                ? new BlockPos(x, player.posY - 1.0D, z)
                : world.getTopSolidOrLiquidBlock(column);

        if (!world.isBlockLoaded(soundPos)) return;

        world.playSound(null, soundPos,
                underground ? SoundEvents.BLOCK_STONE_STEP : SoundEvents.BLOCK_GRASS_STEP,
                SoundCategory.BLOCKS, 0.55F, 1.0F);
    }

    /** A short, spatially localized knock from a nearby wooden surface. */
    private boolean playWoodKnock(EntityPlayer player) {
        World world = player.world;
        double angle = world.rand.nextDouble() * Math.PI * 2.0D;
        double distance = 7.0D + world.rand.nextDouble() * 9.0D;
        BlockPos soundPos = new BlockPos(
                player.posX + Math.cos(angle) * distance,
                player.posY + world.rand.nextInt(3) - 1,
                player.posZ + Math.sin(angle) * distance);
        if (!world.isBlockLoaded(soundPos)) return false;

        world.playSound(null, soundPos, SoundEvents.BLOCK_WOOD_HIT,
                SoundCategory.BLOCKS, 0.75F, 0.65F + world.rand.nextFloat() * 0.2F);
        return true;
    }

    /** Occasionally removes one ordinary torch, as if something snuffed it out. */
    private boolean extinguishNearbyTorch(EntityPlayer player) {
        World world = player.world;
        BlockPos base = player.getPosition();
        List<BlockPos> torches = new ArrayList<>();
        for (int x = -12; x <= 12; x++) {
            for (int y = -2; y <= 4; y++) {
                for (int z = -12; z <= 12; z++) {
                    BlockPos pos = base.add(x, y, z);
                    if (!world.isBlockLoaded(pos) || world.getBlockState(pos).getBlock() != Blocks.TORCH) continue;
                    if (pos.distanceSq(player.getPosition()) < 25.0D) continue;
                    if (isNearPlayerStructure(world, pos, 2)) continue;
                    torches.add(pos);
                }
            }
        }
        if (torches.isEmpty()) return false;

        BlockPos target = torches.get(world.rand.nextInt(torches.size()));
        if (!world.setBlockToAir(target)) return false;
        world.playSound(null, target, SoundEvents.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.BLOCKS, 0.55F, 0.8F + world.rand.nextFloat() * 0.25F);
        return true;
    }

    /** Makes a small group of animals stare in one shared direction. */
    private boolean makeAnimalsWatchSameDirection(EntityPlayer player) {
        World world = player.world;
        List<EntityAnimal> candidates = world.getEntitiesWithinAABB(
                EntityAnimal.class, player.getEntityBoundingBox().grow(30.0D, 8.0D, 30.0D));
        List<EntityAnimal> eligible = new ArrayList<>();
        for (EntityAnimal animal : candidates) {
            if (animal.isDead || animal instanceof EntityTameable || animal instanceof EntityHorse
                    || animal.hasCustomName() || animal.getLeashed()) continue;
            if (animal.getDistanceSq(player) < 49.0D || animal.getDistanceSq(player) > 900.0D) continue;
            if (isNearPlayerStructure(world, animal.getPosition(), 4)) continue;
            eligible.add(animal);
        }
        if (eligible.size() < 2) return false;

        Random random = world.rand;
        float sharedYaw = random.nextFloat() * 360.0F - 180.0F;
        int count = Math.min(eligible.size(), 3 + random.nextInt(4));
        for (int i = 0; i < count; i++) {
            EntityAnimal animal = eligible.remove(random.nextInt(eligible.size()));
            animal.getNavigator().clearPath();
            animal.rotationYaw = sharedYaw;
            animal.prevRotationYaw = sharedYaw;
            animal.rotationYawHead = sharedYaw;
            animal.renderYawOffset = sharedYaw;
        }
        return true;
    }

    /** Plays a chest-opening sound at a real chest without changing its contents. */
    private boolean playChestSound(EntityPlayer player) {
        World world = player.world;
        BlockPos base = player.getPosition();
        List<BlockPos> chests = new ArrayList<>();
        for (int x = -18; x <= 18; x++) {
            for (int y = -3; y <= 4; y++) {
                for (int z = -18; z <= 18; z++) {
                    BlockPos pos = base.add(x, y, z);
                    if (world.isBlockLoaded(pos) && world.getBlockState(pos).getBlock() == Blocks.CHEST) {
                        chests.add(pos);
                    }
                }
            }
        }
        if (chests.isEmpty()) return false;
        BlockPos chest = chests.get(world.rand.nextInt(chests.size()));
        world.playSound(null, chest, SoundEvents.BLOCK_CHEST_OPEN,
                SoundCategory.BLOCKS, 0.65F, 0.8F + world.rand.nextFloat() * 0.25F);
        return true;
    }

    /** Opens a nearby trapdoor or fence gate and plays its corresponding vanilla level event. */
    private boolean openNearbyPassage(EntityPlayer player) {
        World world = player.world;
        BlockPos base = player.getPosition();
        List<BlockPos> candidates = new ArrayList<>();
        for (int x = -16; x <= 16; x++) {
            for (int y = -3; y <= 4; y++) {
                for (int z = -16; z <= 16; z++) {
                    BlockPos pos = base.add(x, y, z);
                    if (!world.isBlockLoaded(pos)) continue;
                    net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
                    if (block instanceof net.minecraft.block.BlockTrapDoor
                            && !world.getBlockState(pos).getValue(net.minecraft.block.BlockTrapDoor.OPEN)
                            && !isNearPlayerStructure(world, pos, 3)) {
                        candidates.add(pos);
                    } else if (block instanceof net.minecraft.block.BlockFenceGate
                            && !world.getBlockState(pos).getValue(net.minecraft.block.BlockFenceGate.OPEN)
                            && !isNearPlayerStructure(world, pos, 3)) {
                        candidates.add(pos);
                    }
                }
            }
        }
        if (candidates.isEmpty()) return false;

        BlockPos pos = candidates.get(world.rand.nextInt(candidates.size()));
        net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
        net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
        if (!world.setBlockState(pos, state.withProperty(
                block instanceof net.minecraft.block.BlockTrapDoor
                        ? net.minecraft.block.BlockTrapDoor.OPEN
                        : net.minecraft.block.BlockFenceGate.OPEN, true), 2)) {
            return false;
        }

        int eventId = block instanceof net.minecraft.block.BlockFenceGate ? 1009
                : block == Blocks.IRON_TRAPDOOR ? 1013 : 1007;
        world.playEvent(null, eventId, pos, 0);
        return true;
    }

    private void makeAnimalsFollow(EntityPlayer player) {
        World world = player.world;
        List<EntityAnimal> candidates = world.getEntitiesWithinAABB(
                EntityAnimal.class,
                player.getEntityBoundingBox().grow(26.0D, 8.0D, 26.0D)
        );

        List<EntityAnimal> eligible = new ArrayList<>();
        for (EntityAnimal animal : candidates) {
            if (animal.isDead || animal instanceof EntityTameable || animal instanceof EntityHorse
                    || animal.hasCustomName() || animal.getLeashed()) continue;

            double distanceSq = animal.getDistanceSq(player);
            if (distanceSq < 36.0D || distanceSq > 676.0D) continue;
            if (isNearPlayerStructure(world, animal.getPosition(), 4)) continue;

            eligible.add(animal);
        }

        if (eligible.isEmpty()) return;

        Random random = world.rand;
        int count = Math.min(eligible.size(), 3 + random.nextInt(4));
        for (int i = 0; i < count; i++) {
            EntityAnimal animal = eligible.remove(random.nextInt(eligible.size()));

            if (random.nextInt(3) == 0) {
                // Часть животных просто поворачивается к игроку и неподвижно наблюдает.
                animal.getNavigator().clearPath();
                double dx = player.posX - animal.posX;
                double dz = player.posZ - animal.posZ;
                float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
                animal.rotationYaw = yaw;
            } else {
                // Остальные медленно начинают идти следом.
                animal.getNavigator().tryMoveToEntityLiving(player, 0.72D);
            }
        }
    }

    private boolean placeThreatSign(EntityPlayer player) {
        World world = player.world;
        BlockPos pos = findSafePosition(player, 10.0D, 18.0D, false);
        if (pos == null || isNearPlayerStructure(world, pos, 4)) return false;

        if (!world.setBlockState(pos, Blocks.STANDING_SIGN.getDefaultState()
                .withProperty(BlockStandingSign.ROTATION, world.rand.nextInt(16)), 3)) {
            return false;
        }

        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileEntitySign)) return false;

        String[] message = THREAT_MESSAGES[world.rand.nextInt(THREAT_MESSAGES.length)];
        TileEntitySign sign = (TileEntitySign) tile;
        for (int i = 0; i < 4; i++) {
            sign.signText[i] = new TextComponentString(message[i]);
        }
        sign.markDirty();
        return true;
    }


    /** Opens an actual nearby wooden or iron door and plays its matching vanilla sound. */
    private boolean openNearbyDoor(EntityPlayer player) {
        World world = player.world;
        BlockPos base = player.getPosition();
        List<BlockPos> doors = new ArrayList<>();
        for (int x = -18; x <= 18; x++) {
            for (int y = -3; y <= 4; y++) {
                for (int z = -18; z <= 18; z++) {
                    BlockPos pos = base.add(x, y, z);
                    if (!world.isBlockLoaded(pos)) continue;
                    net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
                    if (!(block instanceof net.minecraft.block.BlockDoor)) continue;
                    net.minecraft.block.state.IBlockState doorState = world.getBlockState(pos);
                    if (doorState.getValue(net.minecraft.block.BlockDoor.HALF)
                            != net.minecraft.block.BlockDoor.EnumDoorHalf.LOWER) continue;
                    if (doorState.getValue(net.minecraft.block.BlockDoor.OPEN)) continue;
                    doors.add(pos);
                }
            }
        }
        if (doors.isEmpty()) return false;

        int count = Math.min(doors.size(), world.rand.nextInt(3) == 0 ? 2 : 1);
        for (int i = 0; i < count; i++) {
            BlockPos doorPos = doors.remove(world.rand.nextInt(doors.size()));
            net.minecraft.block.Block doorBlock = world.getBlockState(doorPos).getBlock();
            if (!world.setBlockState(doorPos,
                    world.getBlockState(doorPos).withProperty(net.minecraft.block.BlockDoor.OPEN, true), 2)) {
                continue;
            }

            // Vanilla level events use distinct sounds for wooden and iron doors.
            world.playEvent(null, doorBlock == Blocks.IRON_DOOR ? 1011 : 1005, doorPos, 0);
        }
        return true;
    }

    /** Uses normal entity damage so the mob produces its ordinary drops. */
    private boolean killNearbyMob(EntityPlayer player) {
        World world = player.world;
        List<net.minecraft.entity.EntityLiving> candidates = world.getEntitiesWithinAABB(
                net.minecraft.entity.EntityLiving.class,
                player.getEntityBoundingBox().grow(24.0D, 8.0D, 24.0D));
        List<net.minecraft.entity.EntityLiving> eligible = new ArrayList<>();
        for (net.minecraft.entity.EntityLiving target : candidates) {
            if (target.isDead || target instanceof EntityMaskedGirl
                    || target instanceof net.minecraft.entity.passive.EntityAnimal
                    || target instanceof EntityTameable
                    || target instanceof net.minecraft.entity.passive.EntityVillager
                    || target instanceof net.minecraft.entity.monster.EntityIronGolem
                    || target.hasCustomName() || target.getLeashed()
                    || target.getDistanceSq(player) < 36.0D) continue;
            eligible.add(target);
        }
        if (eligible.isEmpty()) return false;
        net.minecraft.entity.EntityLiving target = eligible.get(world.rand.nextInt(eligible.size()));
        EntityMaskedGirl attacker = new EntityMaskedGirl(world);
        attacker.setPosition(target.posX + 1.2D, target.posY, target.posZ + 1.2D);
        attacker.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        attacker.getEntityData().setBoolean(GIRL_TAG, true);
        attacker.getEntityData().setString(OWNER_TAG, player.getUniqueID().toString());
        attacker.getEntityData().setInteger(AGE_TAG, 0);
        attacker.getEntityData().setInteger(LIFETIME_TAG, 400);
        if (!world.spawnEntity(attacker)) return false;
        attacker.setAttackTarget(target);
        return true;
    }

    private boolean isNearPlayerStructure(World world, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos check = center.add(x, y, z);
                    if (!world.isBlockLoaded(check)) continue;

                    net.minecraft.block.Block block = world.getBlockState(check).getBlock();
                    if (block == Blocks.PLANKS || block == Blocks.COBBLESTONE
                            || block == Blocks.CRAFTING_TABLE || block == Blocks.CHEST
                            || block == Blocks.FURNACE || block == Blocks.GLASS
                            || block == Blocks.GLASS_PANE || block == Blocks.STANDING_SIGN
                            || block == Blocks.WALL_SIGN || block == Blocks.BED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
