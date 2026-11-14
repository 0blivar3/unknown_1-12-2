package com.unknown.mod.events;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockStandingSign;
import net.minecraft.block.BlockTorch;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class AnomalyEventHandler {

    private static final String COOLDOWN_TAG = "unknown_anomaly_cooldown";

    private final Map<Integer, Map<BlockPos, TemporaryBlock>> temporaryBlocks =
            new HashMap<>();

    private static final String[] SIGN_MESSAGES = {
            "I SEE YOU", "DON'T LOOK", "TURN BACK", "HELP ME",
            "...", "HE IS HERE", "RUN", "NOT ALONE",
            "BEHIND YOU", "STAY QUIET", "GO HOME", "NO ESCAPE",
            "WHY HERE?", "WAKE UP", "WRONG WORLD", "IT KNOWS",
            "STILL HERE", "KEEP WALKING", "DONT MOVE", "LISTEN",
            "COME CLOSER", "TOO LATE", "I REMEMBER", "LOOK UP",
            "NOT REAL", "YOU LEFT ME", "WHERE AM I", "HIDE",
            "NO ONE CAME", "ITS WATCHING", "TURN AROUND", "FORGIVE ME",
            "DONT TRUST", "I HEAR YOU", "FOUND YOU", "GO DEEPER",
            "ONE MORE DAY", "WHY ME?", "ITS INSIDE", "NEVER SLEEP"
    };

    private static class TemporaryBlock {
        private int ticks;
        private final Block expectedBlock;

        private TemporaryBlock(int ticks, Block expectedBlock) {
            this.ticks = ticks;
            this.expectedBlock = expectedBlock;
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.world.isRemote) return;

        EntityPlayer player = event.player;
        World world = player.world;
        Random random = world.rand;

        int mysteryLevel = MysteryProgression.getLevel(world);
        if (mysteryLevel <= 0) return;

        int cooldown = player.getEntityData().getInteger(COOLDOWN_TAG);

        if (!player.getEntityData().hasKey(COOLDOWN_TAG)) {
            player.getEntityData().setInteger(
                    COOLDOWN_TAG,
                    MysteryProgression.getAnomalyCooldown(random, mysteryLevel)
            );
            return;
        }

        if (cooldown > 0) {
            player.getEntityData().setInteger(COOLDOWN_TAG, cooldown - 1);
            return;
        }

        player.getEntityData().setInteger(
                COOLDOWN_TAG,
                MysteryProgression.getAnomalyCooldown(random, mysteryLevel)
        );

        if (random.nextInt(100) >= MysteryProgression.getAnomalyChance(mysteryLevel)) {
            return;
        }

        // Новые события открываются постепенно: день 2 — первые два,
        // день 3 — дверь и животные, день 4 — исчезновение животных.
        int anomalyCount = mysteryLevel == 1 ? 6 : (mysteryLevel == 2 ? 8 : 9);
        int anomaly = random.nextInt(anomalyCount);

        switch (anomaly) {
            case 0: spawnSign(player); break;
            case 1: spawnTorch(player); break;
            case 2: playCreepyNoise(player); break;
            case 3: playFootsteps(player); break;
            case 4: spawnLonelyBlock(player); break;
            case 5: spawnPresenceTrace(player); break;
            case 6: openNearbyDoor(player); break;
            case 7: disturbPeacefulAnimals(player); break;
            case 8: makeAnimalDisappear(player); break;
            default: break;
        }
    }

    private void spawnSign(EntityPlayer player) {
        World world = player.world;
        BlockPos pos = findPosition(player);
        if (pos == null) return;

        if (world.setBlockState(pos,
                Blocks.STANDING_SIGN.getDefaultState().withProperty(
                        BlockStandingSign.ROTATION, world.rand.nextInt(16)), 3)) {

            if (world.getTileEntity(pos) instanceof TileEntitySign) {
                TileEntitySign sign = (TileEntitySign) world.getTileEntity(pos);
                String message = SIGN_MESSAGES[world.rand.nextInt(SIGN_MESSAGES.length)];
                sign.signText[0] = new TextComponentString(message);
                sign.markDirty();
            }

            registerTemporaryBlock(world, pos, 600 + world.rand.nextInt(300));
        }
    }

    private void spawnTorch(EntityPlayer player) {
        World world = player.world;
        BlockPos pos = findPosition(player);
        if (pos == null) return;

        if (world.setBlockState(pos,
                Blocks.TORCH.getDefaultState().withProperty(BlockTorch.FACING, EnumFacing.UP), 3)) {
            registerTemporaryBlock(world, pos, 600 + world.rand.nextInt(300));
        }
    }

    // Один блок в месте, где он совершенно неуместен.
    private void spawnLonelyBlock(EntityPlayer player) {
        World world = player.world;
        BlockPos pos = findPosition(player);
        if (pos == null) return;

        Block[] choices = {
                Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
                Blocks.OAK_PLANKS, Blocks.GRAVEL
        };

        Block block = choices[world.rand.nextInt(choices.length)];
        placeTemporaryBlock(world, pos, block, 1200 + world.rand.nextInt(1201));
    }

    // Маленький след чьего-то присутствия: несколько блоков рядом.
    private void spawnPresenceTrace(EntityPlayer player) {
        World world = player.world;
        BlockPos origin = findPosition(player);
        if (origin == null) return;

        int lifetime = 1800 + world.rand.nextInt(1801);
        BlockPos[] positions = {
                origin,
                origin.east(),
                origin.east().south()
        };
        Block[] blocks = {
                Blocks.CRAFTING_TABLE,
                Blocks.OAK_PLANKS,
                Blocks.COBBLESTONE
        };

        int placed = 0;
        for (int i = 0; i < positions.length; i++) {
            if (placeTemporaryBlock(world, positions[i], blocks[i], lifetime)) {
                placed++;
            }
        }

        // Если место оказалось тесным, хотя бы один странный блок останется.
        if (placed == 0) {
            placeTemporaryBlock(world, origin, Blocks.COBBLESTONE, lifetime);
        }
    }

    private boolean placeTemporaryBlock(World world, BlockPos pos, Block block, int lifetime) {
        if (!world.isBlockLoaded(pos) || !world.isAirBlock(pos)) return false;
        if (!world.getBlockState(pos.down()).getMaterial().blocksMovement()) return false;
        if (world.getBlockState(pos.down()).getMaterial().isLiquid()) return false;

        if (!world.setBlockState(pos, block.getDefaultState(), 3)) return false;
        registerTemporaryBlock(world, pos, lifetime);
        return true;
    }

    // Открываем только найденную закрытую дверь; не меняем её обратно.
    private void openNearbyDoor(EntityPlayer player) {
        World world = player.world;
        Random random = world.rand;

        for (int attempt = 0; attempt < 35; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 7.0D + random.nextDouble() * 9.0D;
            int x = (int) Math.floor(player.posX + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.posZ + Math.sin(angle) * distance);
            int y = (int) Math.floor(player.posY) + random.nextInt(5) - 2;
            BlockPos pos = new BlockPos(x, y, z);

            if (!world.isBlockLoaded(pos) || !world.isBlockLoaded(pos.up())) continue;

            if (world.getBlockState(pos).getBlock() instanceof BlockDoor) {
                if (world.getBlockState(pos).getValue(BlockDoor.OPEN)) continue;

                world.setBlockState(pos,
                        world.getBlockState(pos).withProperty(BlockDoor.OPEN, true), 3);
                return;
            }
        }
    }

    // Животные внезапно замирают или одновременно смотрят в одну сторону.
    private void disturbPeacefulAnimals(EntityPlayer player) {
        World world = player.world;
        List<EntityAnimal> candidates = new ArrayList<>();

        for (EntityAnimal animal : world.getEntitiesWithinAABB(
                EntityAnimal.class, player.getEntityBoundingBox().grow(20.0D, 6.0D, 20.0D))) {

            if (animal.isDead || animal instanceof EntityTameable || animal instanceof EntityHorse) {
                continue;
            }

            double distanceSq = animal.getDistanceSq(player);
            if (distanceSq < 64.0D || distanceSq > 400.0D) continue;
            candidates.add(animal);
        }

        if (candidates.isEmpty()) return;

        Random random = world.rand;
        float strangeYaw = random.nextFloat() * 360.0F;
        int affected = Math.min(candidates.size(), 1 + random.nextInt(3));

        for (int i = 0; i < affected; i++) {
            EntityAnimal animal = candidates.get(random.nextInt(candidates.size()));
            candidates.remove(animal);

            animal.getNavigator().clearPath();

            if (random.nextBoolean()) {
                double dx = player.posX - animal.posX;
                double dz = player.posZ - animal.posZ;
                strangeYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            }

            animal.rotationYaw = strangeYaw;
            animal.rotationYawHead = strangeYaw;
            animal.renderYawOffset = strangeYaw;
        }
    }

    // Животное пропадает без смерти, звука, частиц и дропа.
    // Выбираем только обычное дикое животное, которого игрок сейчас не видит.
    private void makeAnimalDisappear(EntityPlayer player) {
        World world = player.world;
        List<EntityAnimal> candidates = new ArrayList<>();

        for (EntityAnimal animal : world.getEntitiesWithinAABB(
                EntityAnimal.class, player.getEntityBoundingBox().grow(22.0D, 7.0D, 22.0D))) {

            if (animal.isDead || animal instanceof EntityTameable || animal instanceof EntityHorse) {
                continue;
            }

            double distanceSq = animal.getDistanceSq(player);
            if (distanceSq < 100.0D || distanceSq > 484.0D) continue;
            if (player.canEntityBeSeen(animal)) continue;

            candidates.add(animal);
        }

        if (candidates.isEmpty()) return;

        EntityAnimal target = candidates.get(world.rand.nextInt(candidates.size()));
        target.setDead();
    }

    private void playCreepyNoise(EntityPlayer player) {
        World world = player.world;
        Random random = world.rand;

        // В пещерах оставляем глухую атмосферу, а на поверхности используем
        // редкие звуки, которые могут показаться обычным шумом окружения.
        boolean underground = !world.canSeeSky(player.getPosition());

        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = 14.0D + random.nextInt(19);
        double x = player.posX + Math.cos(angle) * distance;
        double z = player.posZ + Math.sin(angle) * distance;
        BlockPos pos = world.getTopSolidOrLiquidBlock(new BlockPos(x, player.posY, z));

        if (!world.isBlockLoaded(pos)) return;

        if (underground) {
            world.playSound(null, pos, SoundEvents.AMBIENT_CAVE,
                    SoundCategory.AMBIENT, 0.65F, 1.0F);
            return;
        }

        // Не каждый звук должен быть одинаковым: иногда это скрип,
        // иногда тяжёлый шаг или тихий удар издалека.
        int sound = random.nextInt(4);
        switch (sound) {
            case 0:
                world.playSound(null, pos, SoundEvents.AMBIENT_CAVE,
                        SoundCategory.AMBIENT, 0.55F, 1.0F);
                break;
            case 1:
                world.playSound(null, pos, SoundEvents.BLOCK_WOODEN_DOOR_OPEN,
                        SoundCategory.BLOCKS, 0.45F, 1.0F);
                break;
            case 2:
                world.playSound(null, pos, SoundEvents.BLOCK_STONE_STEP,
                        SoundCategory.BLOCKS, 0.35F, 1.0F);
                break;
            default:
                world.playSound(null, pos, SoundEvents.BLOCK_WOOD_STEP,
                        SoundCategory.BLOCKS, 0.4F, 1.0F);
                break;
        }
    }

    private void playFootsteps(EntityPlayer player) {
        World world = player.world;
        Random random = world.rand;

        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 5 + random.nextInt(9);
            double x = player.posX + Math.cos(angle) * distance;
            double z = player.posZ + Math.sin(angle) * distance;
            BlockPos pos = world.getTopSolidOrLiquidBlock(new BlockPos(x, player.posY, z));

            world.playSound(null, pos, SoundEvents.BLOCK_GRASS_STEP,
                    SoundCategory.BLOCKS, 0.45F, 1.0F);
        }
    }

    private BlockPos findPosition(EntityPlayer player) {
        World world = player.world;
        Random random = world.rand;

        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 8 + random.nextInt(9);
            double x = player.posX + Math.cos(angle) * distance;
            double z = player.posZ + Math.sin(angle) * distance;
            BlockPos pos = world.getTopSolidOrLiquidBlock(new BlockPos(x, player.posY, z));

            if (!world.isBlockLoaded(pos)) continue;
            if (!world.isAirBlock(pos)) continue;
            if (!world.getBlockState(pos.down()).getMaterial().blocksMovement()) continue;
            if (world.getBlockState(pos.down()).getMaterial().isLiquid()) continue;

            return pos;
        }

        return null;
    }

    private void registerTemporaryBlock(World world, BlockPos pos, int lifetime) {
        int dimension = world.provider.getDimension();
        Block expectedBlock = world.getBlockState(pos).getBlock();

        temporaryBlocks.computeIfAbsent(dimension, key -> new HashMap<>())
                .put(pos, new TemporaryBlock(lifetime, expectedBlock));
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) return;

        int dimension = event.world.provider.getDimension();
        Map<BlockPos, TemporaryBlock> blocks = temporaryBlocks.get(dimension);
        if (blocks == null) return;

        Iterator<Map.Entry<BlockPos, TemporaryBlock>> iterator = blocks.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<BlockPos, TemporaryBlock> entry = iterator.next();
            BlockPos pos = entry.getKey();
            TemporaryBlock temporary = entry.getValue();
            temporary.ticks--;

            if (temporary.ticks <= 0) {
                // Если игрок заменил блок, не трогаем его постройку.
                if (event.world.isBlockLoaded(pos)
                        && event.world.getBlockState(pos).getBlock() == temporary.expectedBlock) {
                    event.world.setBlockToAir(pos);
                }
                iterator.remove();
            }
        }

        if (blocks.isEmpty()) temporaryBlocks.remove(dimension);
    }
}
