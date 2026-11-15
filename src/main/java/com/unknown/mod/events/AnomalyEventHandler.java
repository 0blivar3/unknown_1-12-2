package com.unknown.mod.events;

import com.unknown.mod.ModConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockStandingSign;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AnomalyEventHandler {

    private static final String COOLDOWN_TAG = "unknown_anomaly_cooldown";

    // Надписи становятся всё более личными по мере роста уровня мистики.
    // Каждая фраза разбита на четыре строки, чтобы помещаться на табличке.
    private static final String[][] EARLY_SIGN_MESSAGES = {
            {"I LEFT", "THE LAMP", "ON AGAIN", ""},
            {"THE PATH", "WAS SHORTER", "YESTERDAY", ""},
            {"SOMEONE", "USED THIS", "CAMPFIRE", ""},
            {"I HEARD", "MINING", "LAST NIGHT", ""},
            {"THE DOOR", "WAS OPEN", "WHEN I CAME", ""},
            {"I THOUGHT", "THIS PLACE", "WAS EMPTY", ""},
            {"THERE WAS", "A WINDOW", "HERE BEFORE", ""},
            {"I LOST", "THE WAY", "BACK HOME", ""},
            {"THE COWS", "WERE HERE", "A MOMENT AGO", ""},
            {"I PUT IT", "BACK WHERE", "IT BELONGS", ""},
            {"THE RAIN", "STOPPED", "TOO QUICKLY", ""},
            {"I DON'T", "REMEMBER", "THIS HILL", ""}
    };

    private static final String[][] MID_SIGN_MESSAGES = {
            {"THERE WERE", "FOOTSTEPS", "BESIDE MINE", ""},
            {"I SAW", "A LIGHT", "UNDERGROUND", ""},
            {"SOMEONE", "KEEPS MOVING", "MY CHESTS", ""},
            {"THE SAME", "TREE WAS", "HERE TWICE", ""},
            {"I HEARD", "MY DOOR", "FROM OUTSIDE", ""},
            {"IT WAITS", "WHERE THE", "TORCH ENDS", ""},
            {"I COUNTED", "THREE", "SHADOWS", ""},
            {"THE ANIMALS", "WON'T CROSS", "THAT STREAM", ""},
            {"I FOUND", "MY OWN", "FOOTPRINTS", "AHEAD"},
            {"SOMETHING", "ANSWERED", "FROM BELOW", ""},
            {"THE MAP", "IS MISSING", "A CHUNK", ""},
            {"I WAS SURE", "I CLOSED", "THAT DOOR", ""}
    };

    private static final String[][] LATE_SIGN_MESSAGES = {
            {"YOU BUILT", "THIS ROOM", "BEFORE", ""},
            {"I REMEMBER", "WHEN YOU", "FIRST CAME", ""},
            {"THERE IS", "NO MINE", "UNDER HERE", ""},
            {"I CAN HEAR", "THE WORLD", "LOADING", ""},
            {"YOU LEFT", "THE LIGHT", "ON FOR ME", ""},
            {"WE USED", "TO LIVE", "IN THIS SEED", ""},
            {"I WAS HERE", "BEFORE THE", "FIRST NIGHT", ""},
            {"DON'T REMOVE", "THE BLOCK", "BEHIND YOU", ""},
            {"YOU FOUND", "THE WRONG", "VERSION", ""},
            {"I KNOW", "WHICH HOUSE", "IS YOURS", ""},
            {"IT STARTED", "WHEN YOU", "SLEPT", ""},
            {"THIS IS", "NOT WHERE", "YOU LEFT ME", ""}
    };

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.world.isRemote) return;

        EntityPlayer player = event.player;
        World world = player.world;
        Random random = world.rand;

        int mysteryLevel = MysteryProgression.getLevel(world);
        if (mysteryLevel <= 0 || !ModConfig.anomaliesEnabled) return;

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

        List<Integer> availableAnomalies = getAvailableAnomalies(mysteryLevel);
        if (availableAnomalies.isEmpty()) return;
        int anomaly = availableAnomalies.get(random.nextInt(availableAnomalies.size()));

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
            case 9: playDistantGhastScream(player); break;
            default: break;
        }
    }

    private List<Integer> getAvailableAnomalies(int mysteryLevel) {
        int unlockedCount = mysteryLevel == 1 ? 6 : (mysteryLevel == 2 ? 8 : 10);
        List<Integer> available = new ArrayList<>();

        for (int id = 0; id < unlockedCount; id++) {
            if (isAnomalyEnabled(id)) available.add(id);
        }
        return available;
    }

    private boolean isAnomalyEnabled(int id) {
        switch (id) {
            case 0: return ModConfig.signAnomaly;
            case 1: return ModConfig.torchAnomaly;
            case 2: return ModConfig.creepyNoiseAnomaly;
            case 3: return ModConfig.footstepsAnomaly;
            case 4: return ModConfig.lonelyBlockAnomaly;
            case 5: return ModConfig.presenceTraceAnomaly;
            case 6: return ModConfig.openDoorAnomaly;
            case 7: return ModConfig.animalBehaviorAnomaly;
            case 8: return ModConfig.animalDisappearanceAnomaly;
            case 9: return ModConfig.ghastScreamAnomaly;
            default: return false;
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
                int level = MysteryProgression.getLevel(world);
                String[][] messagePool = level <= 2
                        ? EARLY_SIGN_MESSAGES
                        : (level <= 4 ? MID_SIGN_MESSAGES : LATE_SIGN_MESSAGES);

                String[] message = messagePool[world.rand.nextInt(messagePool.length)];
                for (int line = 0; line < 4; line++) {
                    sign.signText[line] = new TextComponentString(message[line]);
                }
                sign.markDirty();
            }

            registerTemporaryBlock(world, pos, 600 + world.rand.nextInt(300));
        }
    }

    // Факел появляется в естественном, тёмном месте вдали от построек и других источников света.
    private void spawnTorch(EntityPlayer player) {
        World world = player.world;
        BlockPos pos = findTorchPosition(player);
        if (pos == null) return;

        if (world.setBlockState(pos,
                Blocks.TORCH.getDefaultState().withProperty(BlockTorch.FACING, EnumFacing.UP), 3)) {
            registerTemporaryBlock(world, pos, 1200 + world.rand.nextInt(1201));
        }
    }

    private BlockPos findTorchPosition(EntityPlayer player) {
        World world = player.world;
        Random random = world.rand;

        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 10.0D + random.nextDouble() * 14.0D;
            int x = (int) Math.floor(player.posX + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.posZ + Math.sin(angle) * distance);
            BlockPos column = new BlockPos(x, player.posY, z);
            if (!world.isBlockLoaded(column)) continue;
            BlockPos pos = world.getTopSolidOrLiquidBlock(column);

            if (!world.isAirBlock(pos)) continue;
            if (Math.abs(pos.getY() - player.posY) > 10.0D) continue;

            IBlockState floor = world.getBlockState(pos.down());
            if (!floor.getMaterial().blocksMovement() || floor.getMaterial().isLiquid()) continue;
            if (!floor.isSideSolid(world, pos.down(), EnumFacing.UP)) continue;

            // Не ставим факел на искусственные полы и рядом с базами.
            Block floorBlock = floor.getBlock();
            if (floorBlock == Blocks.PLANKS || floorBlock == Blocks.COBBLESTONE
                    || floorBlock == Blocks.MOSSY_COBBLESTONE
                    || floorBlock == Blocks.BRICK_BLOCK
                    || floorBlock == Blocks.STONEBRICK
                    || floorBlock == Blocks.CRAFTING_TABLE
                    || floorBlock == Blocks.FURNACE
                    || floorBlock == Blocks.CHEST) continue;

            if (world.getLightFor(EnumSkyBlock.BLOCK, pos) > 4) continue;
            if (hasNearbyTorch(world, pos, 5)) continue;
            if (hasNearbyPlayerStructure(world, pos, 3)) continue;

            return pos;
        }

        return null;
    }

    private boolean hasNearbyTorch(World world, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos check = center.add(x, y, z);
                    if (!world.isBlockLoaded(check)) continue;
                    if (world.getBlockState(check).getBlock() instanceof BlockTorch) return true;
                }
            }
        }
        return false;
    }

    private boolean hasNearbyPlayerStructure(World world, BlockPos center, int radius) {
        return hasNearbyPlayerStructure(world, center, radius, false);
    }

    private boolean hasNearbyPlayerStructure(World world, BlockPos center, int radius, boolean ignoreDoors) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos check = center.add(x, y, z);
                    if (!world.isBlockLoaded(check)) continue;

                    Block block = world.getBlockState(check).getBlock();
                    if (block == Blocks.PLANKS || block == Blocks.COBBLESTONE
                            || block == Blocks.MOSSY_COBBLESTONE
                            || block == Blocks.CRAFTING_TABLE || block == Blocks.FURNACE
                            || block == Blocks.CHEST || block == Blocks.STANDING_SIGN
                            || block == Blocks.WALL_SIGN || block == Blocks.GLASS
                            || block == Blocks.GLASS_PANE
                            || (!ignoreDoors && block instanceof BlockDoor)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Один блок в месте, где он совершенно неуместен.
    private void spawnLonelyBlock(EntityPlayer player) {
        World world = player.world;
        BlockPos pos = findPosition(player);
        if (pos == null) return;

        Block[] choices = {
                Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
                Blocks.PLANKS, Blocks.GRAVEL
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
                Blocks.PLANKS,
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

            IBlockState state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof BlockDoor)) continue;

            BlockDoor door = (BlockDoor) state.getBlock();
            BlockPos lowerPos = state.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER
                    ? pos.down() : pos;
            if (!world.isBlockLoaded(lowerPos) || !world.isBlockLoaded(lowerPos.up())) continue;

            IBlockState lowerState = world.getBlockState(lowerPos);
            if (lowerState.getBlock() != door || lowerState.getValue(BlockDoor.OPEN)) continue;

            // Не открываем двери, встроенные в постройки игрока.
            if (hasNearbyPlayerStructure(world, lowerPos, 3, true)) continue;

            // toggleDoor корректно обрабатывает верхнюю и нижнюю половины двери.
            door.toggleDoor(world, pos, true);
            return;
        }
    }

    // Животные иногда синхронно замирают, смотрят на игрока или на одну пустую точку.
    private void disturbPeacefulAnimals(EntityPlayer player) {
        World world = player.world;
        List<EntityAnimal> candidates = new ArrayList<>();

        for (EntityAnimal animal : world.getEntitiesWithinAABB(
                EntityAnimal.class, player.getEntityBoundingBox().grow(22.0D, 7.0D, 22.0D))) {

            if (animal.isDead || animal instanceof EntityTameable || animal instanceof EntityHorse
                    || animal.hasCustomName() || animal.getLeashed()) {
                continue;
            }

            double distanceSq = animal.getDistanceSq(player);
            if (distanceSq < 64.0D || distanceSq > 484.0D) continue;

            BlockPos animalPos = animal.getPosition();
            if (hasNearbyPlayerStructure(world, animalPos, 3)
                    || isNearFence(world, animalPos, 3)) continue;

            candidates.add(animal);
        }

        if (candidates.isEmpty()) return;

        Random random = world.rand;
        int behavior = random.nextInt(5);
        int affected = behavior == 1 ? 1 : Math.min(candidates.size(), 1 + random.nextInt(3));

        // Одна воображаемая точка заставляет несколько животных смотреть строго в одном направлении.
        double sharedAngle = random.nextDouble() * Math.PI * 2.0D;
        double focusX = player.posX + (random.nextDouble() - 0.5D) * 18.0D;
        double focusZ = player.posZ + (random.nextDouble() - 0.5D) * 18.0D;

        for (int i = 0; i < affected; i++) {
            EntityAnimal animal = candidates.remove(random.nextInt(candidates.size()));
            animal.getNavigator().clearPath();

            double dx;
            double dz;

            switch (behavior) {
                case 0:
                    // Все выбранные животные смотрят в одну случайную сторону.
                    dx = Math.cos(sharedAngle);
                    dz = Math.sin(sharedAngle);
                    break;
                case 1:
                    // Одно животное неподвижно смотрит прямо на игрока.
                    dx = player.posX - animal.posX;
                    dz = player.posZ - animal.posZ;
                    break;
                case 2:
                    // Животные смотрят в одну точку, где ничего нет.
                    dx = focusX - animal.posX;
                    dz = focusZ - animal.posZ;
                    break;
                case 3:
                    // Животные отворачиваются от игрока.
                    dx = animal.posX - player.posX;
                    dz = animal.posZ - player.posZ;
                    break;
                default:
                    // Несколько животных одновременно поворачиваются к игроку.
                    dx = player.posX - animal.posX;
                    dz = player.posZ - animal.posZ;
                    break;
            }

            float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            animal.rotationYaw = yaw;
            animal.rotationYawHead = yaw;
            animal.renderYawOffset = yaw;
        }
    }

    // Животное пропадает без смерти, звука, частиц и дропа.
    // Выбираем только обычное дикое животное, которого игрок сейчас не видит.
    private void makeAnimalDisappear(EntityPlayer player) {
        World world = player.world;
        List<EntityAnimal> candidates = new ArrayList<>();

        for (EntityAnimal animal : world.getEntitiesWithinAABB(
                EntityAnimal.class, player.getEntityBoundingBox().grow(22.0D, 7.0D, 22.0D))) {

            if (animal.isDead || animal instanceof EntityTameable || animal instanceof EntityHorse
                    || animal.hasCustomName() || animal.getLeashed()) {
                continue;
            }

            double distanceSq = animal.getDistanceSq(player);
            if (distanceSq < 100.0D || distanceSq > 484.0D) continue;
            if (player.canEntityBeSeen(animal)) continue;

            // Не исчезают животные рядом с базой, в загонах или у ограждений.
            BlockPos animalPos = animal.getPosition();
            if (hasNearbyPlayerStructure(world, animalPos, 3)
                    || isNearFence(world, animalPos, 3)) continue;

            candidates.add(animal);
        }

        if (candidates.isEmpty()) return;

        EntityAnimal target = candidates.get(world.rand.nextInt(candidates.size()));
        target.setDead();
    }

    private boolean isNearFence(World world, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos check = center.add(x, y, z);
                    if (!world.isBlockLoaded(check)) continue;

                    Block block = world.getBlockState(check).getBlock();
                    if (block instanceof BlockFence || block instanceof BlockFenceGate) {
                        return true;
                    }
                }
            }
        }
        return false;
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
        BlockPos column = new BlockPos(x, player.posY, z);
        if (!world.isBlockLoaded(column)) return;

        // В пещере звук должен находиться рядом с уровнем игрока,
        // а не на поверхности над его головой.
        BlockPos pos = underground
                ? new BlockPos(x, player.posY + random.nextInt(7) - 3, z)
                : world.getTopSolidOrLiquidBlock(column);
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

    // Очень далёкий крик без появления самого гаста.
    private void playDistantGhastScream(EntityPlayer player) {
        World world = player.world;
        Random random = world.rand;

        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = 24.0D + random.nextDouble() * 13.0D;
        int x = (int) Math.floor(player.posX + Math.cos(angle) * distance);
        int z = (int) Math.floor(player.posZ + Math.sin(angle) * distance);

        int y;
        if (world.canSeeSky(player.getPosition())) {
            BlockPos column = new BlockPos(x, player.posY, z);
            if (!world.isBlockLoaded(column)) return;
            BlockPos surface = world.getTopSolidOrLiquidBlock(column);
            y = surface.getY();
        } else {
            y = Math.max(5, Math.min(world.getHeight() - 2,
                    (int) Math.floor(player.posY + random.nextInt(13) - 6)));
        }

        BlockPos soundPos = new BlockPos(x, y, z);
        if (!world.isBlockLoaded(soundPos)) return;

        world.playSound(null, soundPos, SoundEvents.ENTITY_GHAST_SCREAM,
                SoundCategory.HOSTILE, 2.5F, 1.0F);
    }

    private void playFootsteps(EntityPlayer player) {
        World world = player.world;
        Random random = world.rand;

        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 5 + random.nextInt(9);
            double x = player.posX + Math.cos(angle) * distance;
            double z = player.posZ + Math.sin(angle) * distance;
            BlockPos column = new BlockPos(x, player.posY, z);
            if (!world.isBlockLoaded(column)) continue;

            BlockPos pos = world.canSeeSky(player.getPosition())
                    ? world.getTopSolidOrLiquidBlock(column)
                    : new BlockPos(x, player.posY, z);
            if (!world.isBlockLoaded(pos)) continue;

            world.playSound(null, pos,
                    world.canSeeSky(player.getPosition())
                            ? SoundEvents.BLOCK_GRASS_STEP
                            : SoundEvents.BLOCK_STONE_STEP,
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
            BlockPos column = new BlockPos(x, player.posY, z);
            if (!world.isBlockLoaded(column)) continue;
            BlockPos pos = world.getTopSolidOrLiquidBlock(column);

            if (!world.isBlockLoaded(pos)) continue;
            if (!world.isAirBlock(pos)) continue;
            if (!world.getBlockState(pos.down()).getMaterial().blocksMovement()) continue;
            if (world.getBlockState(pos.down()).getMaterial().isLiquid()) continue;
            // Не размещаем аномалии вплотную к постройкам и хранилищам игрока.
            if (hasNearbyPlayerStructure(world, pos, 3)) continue;

            return pos;
        }

        return null;
    }

    private void registerTemporaryBlock(World world, BlockPos pos, int lifetime) {
        TemporaryBlockData.get(world).add(world, pos, lifetime);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        TemporaryBlockData.get(event.world).tick(event.world);
    }
}
