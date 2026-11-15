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
import net.minecraft.world.storage.WorldInfo;

import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
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
    private static final String LOOK_TICKS_TAG = "unknown_masked_girl_look_ticks";
    private static final String RITUAL_ACTIVATED_TAG = "unknown_masked_girl_ritual_activated";
    private static final String ATTACK_STATE_TAG = "unknown_masked_girl_attack_state";
    private static final String ATTACK_TIMER_TAG = "unknown_masked_girl_attack_timer";
    private static final String STARE_TIMER_TAG = "unknown_masked_girl_stare_timer";
    private static final String ATTACK_COOLDOWN_TAG = "unknown_masked_girl_attack_cooldown";
    private static final String HIT_COOLDOWN_TAG = "unknown_masked_girl_hit_cooldown";
    private static final int STAGE_LENGTH = 24000;

    private static final int HAUNTING_DURATION = 3 * 24000;

    private static final String[][] THREAT_MESSAGES = {
            {"YOU USED IT", "SHE SAW YOU", "", ""},
            {"DONT TURN", "BACK", "", ""},
            {"NOT YOURS", "TO USE", "", ""},
            {"SHE IS", "ALREADY HERE", "", ""},
            {"YOU LET", "HER IN", "", ""},
            {"WE FOLLOW", "YOU HOME", "", ""},
            {"TOO LATE", "TO HIDE", "", ""},
            {"DO NOT", "LOOK BACK", "", ""}
    };

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) return;

        EntityPlayer player = event.player;

        int remaining = player.getEntityData().getInteger(HAUNTING_TICKS);
        if (remaining <= 0 || !ModConfig.maskedGirlEnabled) return;

        player.getEntityData().setInteger(HAUNTING_TICKS, remaining - 1);

        int cooldown = player.getEntityData().getInteger(NEXT_EVENT_TICKS);
        if (cooldown > 0) {
            player.getEntityData().setInteger(NEXT_EVENT_TICKS, cooldown - 1);
            return;
        }

        Random random = player.world.rand;
        int elapsed = HAUNTING_DURATION - remaining;
        int stage = Math.min(3, elapsed / STAGE_LENGTH + 1);
        boolean eventHappened = performHauntingEvent(player, stage);
        int nextDelay = 3600 + random.nextInt(3601);
        if (!eventHappened) nextDelay = 800 + random.nextInt(801);
        player.getEntityData().setInteger(NEXT_EVENT_TICKS, nextDelay);
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

        if (age >= girl.getEntityData().getInteger(LIFETIME_TAG)) {
            girl.setDead();
            return;
        }

        EntityPlayer owner = findOwner(girl);
        if (owner == null) {
            girl.setDead();
            return;
        }
        int elapsed = HAUNTING_DURATION - owner.getEntityData().getInteger(HAUNTING_TICKS);
        int stage = Math.min(3, elapsed / STAGE_LENGTH + 1);
        if (stage < 3 && girl.getDistance(owner) < 9.0F) {
            girl.setDead();
            return;
        }
        if (stage < 3) {
            if (isPlayerLookingAt(owner, girl)) {
                int lookTicks = girl.getEntityData().getInteger(LOOK_TICKS_TAG) + 1;
                girl.getEntityData().setInteger(LOOK_TICKS_TAG, lookTicks);
                if (lookTicks >= 15) girl.setDead();
            } else {
                girl.getEntityData().setInteger(LOOK_TICKS_TAG, 0);
            }
            return;
        }

        // Stage III: stare briefly, then pursue for 40-60 in-game seconds.
        girl.getEntityData().setInteger(LOOK_TICKS_TAG, 0);
        int state = girl.getEntityData().getInteger(ATTACK_STATE_TAG);
        int cooldown = girl.getEntityData().getInteger(ATTACK_COOLDOWN_TAG);
        if (state == 0) {
            if (cooldown > 0) {
                girl.getEntityData().setInteger(ATTACK_COOLDOWN_TAG, cooldown - 1);
                girl.faceEntity(owner, 4.0F, 4.0F);
                return;
            }
            girl.getEntityData().setInteger(ATTACK_STATE_TAG, 1);
            girl.getEntityData().setInteger(STARE_TIMER_TAG, 60 + girl.world.rand.nextInt(61));
            girl.getEntityData().setInteger(ATTACK_TIMER_TAG, 800 + girl.world.rand.nextInt(401));
            girl.getNavigator().clearPath();
            return;
        }
        if (state == 1) {
            girl.faceEntity(owner, 6.0F, 6.0F);
            int stare = girl.getEntityData().getInteger(STARE_TIMER_TAG) - 1;
            girl.getEntityData().setInteger(STARE_TIMER_TAG, stare);
            if (stare <= 0) girl.getEntityData().setInteger(ATTACK_STATE_TAG, 2);
            return;
        }
        int attackTime = girl.getEntityData().getInteger(ATTACK_TIMER_TAG) - 1;
        girl.getEntityData().setInteger(ATTACK_TIMER_TAG, attackTime);
        girl.getNavigator().tryMoveToEntityLiving(owner, 1.15D);
        girl.faceEntity(owner, 30.0F, 30.0F);
        int hitCooldown = girl.getEntityData().getInteger(HIT_COOLDOWN_TAG);
        if (hitCooldown > 0) {
            girl.getEntityData().setInteger(HIT_COOLDOWN_TAG, hitCooldown - 1);
        } else if (girl.getDistance(owner) <= 2.4F) {
            girl.attackEntityAsMob(owner);
            girl.getEntityData().setInteger(HIT_COOLDOWN_TAG, 20);
        }
        if (attackTime <= 0) {
            girl.getNavigator().clearPath();
            girl.getEntityData().setInteger(ATTACK_STATE_TAG, 0);
            girl.getEntityData().setInteger(ATTACK_COOLDOWN_TAG, 1200 + girl.world.rand.nextInt(2401));
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
        BlockPos placed = event.getPos();

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
        player.getEntityData().setInteger(HAUNTING_TICKS, HAUNTING_DURATION);
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
            if (choice < 30 && !hasActiveGirl(player)) return spawnGirl(player, stage);
            if (choice < 55) { playDistantFootsteps(player); return true; }
            if (choice < 82) { makeAnimalsFollow(player); return true; }
            return placeThreatSign(player);
        }
        if (stage == 2) {
            if (choice < 22 && !hasActiveGirl(player)) return spawnGirl(player, stage);
            if (choice < 42) return openNearbyDoor(player);
            if (choice < 62) return killNearbyMob(player);
            if (choice < 82) { playDistantFootsteps(player); return true; }
            if (choice < 92) { makeAnimalsFollow(player); return true; }
            return placeThreatSign(player);
        }
        if (choice < 28 && !hasActiveGirl(player)) return spawnGirl(player, stage);
        if (choice < 48) return openNearbyDoor(player);
        if (choice < 68) return killNearbyMob(player);
        if (choice < 84) { playDistantFootsteps(player); return true; }
        return placeThreatSign(player);
    }

    private boolean hasActiveGirl(EntityPlayer player) {
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
        BlockPos pos = findSafePosition(player, stage >= 2 ? 22.0D : 30.0D,
                stage >= 2 ? 45.0D : 55.0D, true);
        if (pos == null) return false;

        EntityMaskedGirl girl = new EntityMaskedGirl(world);
        girl.setPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);

        double dx = player.posX - girl.posX;
        double dz = player.posZ - girl.posZ;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        girl.rotationYaw = yaw;
        girl.prevRotationYaw = yaw;

        if (stage >= 2 || world.rand.nextBoolean()) {
            girl.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        }

        girl.getEntityData().setBoolean(GIRL_TAG, true);
        girl.getEntityData().setString(OWNER_TAG, player.getUniqueID().toString());
        girl.getEntityData().setInteger(AGE_TAG, 0);
        girl.getEntityData().setInteger(LIFETIME_TAG, 1200 + world.rand.nextInt(1801));
        girl.getEntityData().setInteger(LOOK_TICKS_TAG, 0);
        girl.getEntityData().setInteger(ATTACK_STATE_TAG, 0);
        girl.getEntityData().setInteger(ATTACK_COOLDOWN_TAG, 600 + world.rand.nextInt(1201));

        return world.spawnEntity(girl);
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

        return player.getLook(1.0F).dotProduct(direction) > 0.94D;
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
                if (player.getLook(1.0F).dotProduct(direction) > 0.72D) continue;
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


    /** Opens an actual closed wooden door near the player. */
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
                    if (!(block instanceof net.minecraft.block.BlockDoor) || block == Blocks.IRON_DOOR) continue;
                    if (world.getBlockState(pos).getValue(net.minecraft.block.BlockDoor.OPEN)) continue;
                    doors.add(pos);
                }
            }
        }
        if (doors.isEmpty()) return false;
        BlockPos doorPos = doors.get(world.rand.nextInt(doors.size()));
        world.setBlockState(doorPos,
                world.getBlockState(doorPos).withProperty(net.minecraft.block.BlockDoor.OPEN, true), 2);
        world.playEvent(null, 1005, doorPos, 0);
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
                    || target instanceof net.minecraft.entity.passive.EntityVillager
                    || target instanceof net.minecraft.entity.monster.EntityIronGolem
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
        attacker.getEntityData().setInteger(LIFETIME_TAG, 100);
        if (!world.spawnEntity(attacker)) return false;
        target.attackEntityFrom(net.minecraft.util.DamageSource.causeMobDamage(attacker),
                target.getMaxHealth() + 20.0F);
        attacker.setDead();
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
