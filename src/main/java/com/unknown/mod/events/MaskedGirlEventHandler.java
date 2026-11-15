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
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldInfo;

import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Последствия активации Тотема бессмертия.
 *
 * В Forge 1.12.2 нет отдельного события LivingEntityUseTotemEvent.
 * Поэтому мы фиксируем потенциально смертельный урон, а на следующем тике
 * подтверждаем активацию по факту уменьшения количества тотемов в руках.
 */
public class MaskedGirlEventHandler {

    private static final String HAUNTING_TICKS = "unknown_masked_girl_haunting";
    private static final String NEXT_EVENT_TICKS = "unknown_masked_girl_next_event";
    private static final String PENDING_TOTEM = "unknown_masked_girl_pending_totem";
    private static final String PENDING_TOTEM_COUNT = "unknown_masked_girl_totem_count";

    private static final String GIRL_TAG = "unknown_masked_girl";
    private static final String OWNER_TAG = "unknown_masked_girl_owner";
    private static final String AGE_TAG = "unknown_masked_girl_age";
    private static final String LIFETIME_TAG = "unknown_masked_girl_lifetime";
    private static final String LOOK_TICKS_TAG = "unknown_masked_girl_look_ticks";

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
    public void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) event.getEntityLiving();
        if (player.world.isRemote || !ModConfig.maskedGirlEnabled) return;

        // LivingDamageEvent уже содержит итоговый урон после брони и поглощения.
        if (player.getHealth() > event.getAmount()) return;

        int heldTotems = getHeldTotemCount(player);
        if (heldTotems <= 0) return;

        player.getEntityData().setBoolean(PENDING_TOTEM, true);
        player.getEntityData().setInteger(PENDING_TOTEM_COUNT, heldTotems);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) return;

        EntityPlayer player = event.player;

        // Подтверждаем, что тотем действительно был потрачен, а не просто
        // находился в руке во время удара, который игрок пережил.
        if (player.getEntityData().getBoolean(PENDING_TOTEM)) {
            int previousCount = player.getEntityData().getInteger(PENDING_TOTEM_COUNT);
            int currentCount = getHeldTotemCount(player);

            player.getEntityData().removeTag(PENDING_TOTEM);
            player.getEntityData().removeTag(PENDING_TOTEM_COUNT);

            if (currentCount < previousCount && !player.isDead && player.getHealth() > 0.0F) {
                beginHaunting(player);
            }
        }

        int remaining = player.getEntityData().getInteger(HAUNTING_TICKS);
        if (remaining <= 0 || !ModConfig.maskedGirlEnabled) return;

        player.getEntityData().setInteger(HAUNTING_TICKS, remaining - 1);

        int cooldown = player.getEntityData().getInteger(NEXT_EVENT_TICKS);
        if (cooldown > 0) {
            player.getEntityData().setInteger(NEXT_EVENT_TICKS, cooldown - 1);
            return;
        }

        Random random = player.world.rand;
        boolean eventHappened = performHauntingEvent(player);
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
        if (owner == null || girl.getDistance(owner) < 9.0F) {
            girl.setDead();
            return;
        }

        if (isPlayerLookingAt(owner, girl)) {
            int lookTicks = girl.getEntityData().getInteger(LOOK_TICKS_TAG) + 1;
            girl.getEntityData().setInteger(LOOK_TICKS_TAG, lookTicks);
            if (lookTicks >= 15) girl.setDead();
        } else {
            girl.getEntityData().setInteger(LOOK_TICKS_TAG, 0);
        }
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

    private int getHeldTotemCount(EntityPlayer player) {
        int count = 0;
        ItemStack main = player.getHeldItemMainhand();
        ItemStack off = player.getHeldItemOffhand();

        if (!main.isEmpty() && main.getItem() == Items.TOTEM_OF_UNDYING) count += main.getCount();
        if (!off.isEmpty() && off.getItem() == Items.TOTEM_OF_UNDYING) count += off.getCount();

        return count;
    }

    private boolean performHauntingEvent(EntityPlayer player) {
        Random random = player.world.rand;
        int choice = random.nextInt(100);

        if (choice < 35 && !hasActiveGirl(player)) {
            if (spawnGirl(player)) return true;
        } else if (choice < 58) {
            playDistantFootsteps(player);
            return true;
        } else if (choice < 82) {
            makeAnimalsFollow(player);
            return true;
        } else {
            if (placeThreatSign(player)) return true;
        }

        return false;
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

    private boolean spawnGirl(EntityPlayer player) {
        World world = player.world;
        BlockPos pos = findSafePosition(player, 30.0D, 55.0D, true);
        if (pos == null) return false;

        EntityMaskedGirl girl = new EntityMaskedGirl(world);
        girl.setPosition(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);

        double dx = player.posX - girl.posX;
        double dz = player.posZ - girl.posZ;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        girl.rotationYaw = yaw;
        girl.prevRotationYaw = yaw;

        if (world.rand.nextBoolean()) {
            girl.setHeldItem(net.minecraft.util.EnumHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        }

        girl.getEntityData().setBoolean(GIRL_TAG, true);
        girl.getEntityData().setString(OWNER_TAG, player.getUniqueID().toString());
        girl.getEntityData().setInteger(AGE_TAG, 0);
        girl.getEntityData().setInteger(LIFETIME_TAG, 1200 + world.rand.nextInt(1801));
        girl.getEntityData().setInteger(LOOK_TICKS_TAG, 0);

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
            animal.getNavigator().tryMoveToEntityLiving(player, 0.72D);
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
