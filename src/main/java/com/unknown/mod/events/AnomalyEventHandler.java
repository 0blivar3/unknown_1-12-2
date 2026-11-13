
package com.unknown.mod.events;

import com.unknown.mod.events.MysteryProgression;

import net.minecraft.block.BlockStandingSign;
import net.minecraft.block.BlockTorch;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import net.minecraft.entity.player.EntityPlayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

public class AnomalyEventHandler {

    private static final String COOLDOWN_TAG =
            "unknown_anomaly_cooldown";

    // Временные блоки и время до их исчезновения.
    private final Map<Integer, Map<BlockPos, Integer>> temporaryBlocks =
            new HashMap<>();

    private static final String[] SIGN_MESSAGES = {
            "I SEE YOU",
            "DON'T LOOK",
            "TURN BACK",
            "HELP ME",
            "...",
            "HE IS HERE",
            "RUN"
    };

    // Проверяем игроков.
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {

        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.world.isRemote) return;

        EntityPlayer player = event.player;
        Random random = player.world.rand;

        int mysteryLevel = MysteryProgression.getLevel(player.world);
        if (mysteryLevel <= 0) return; // В первый игровой день аномалий нет.

        int cooldown = player.getEntityData().getInteger(COOLDOWN_TAG);

        if (!player.getEntityData().hasKey(COOLDOWN_TAG)) {

            player.getEntityData().setInteger(
                    COOLDOWN_TAG,
                    MysteryProgression.getAnomalyCooldown(random, mysteryLevel)
            );

            return;
        }

        if (cooldown > 0) {

            player.getEntityData().setInteger(
                    COOLDOWN_TAG,
                    cooldown - 1
            );

            return;
        }

        // Интервал постепенно сокращается по мере развития мистики.
        player.getEntityData().setInteger(
                COOLDOWN_TAG,
                MysteryProgression.getAnomalyCooldown(random, mysteryLevel)
        );

        // Даже после окончания таймера событие может не произойти.
        if (random.nextInt(100) >= MysteryProgression.getAnomalyChance(mysteryLevel)) {
            return;
        }

        // Выбираем случайную аномалию.
        int anomaly = random.nextInt(4);

        switch (anomaly) {

            case 0:
                spawnSign(player);
                break;

            case 1:
                spawnTorch(player);
                break;

            case 2:
                playCreepyNoise(player);
                break;

            case 3:
                playFootsteps(player);
                break;
        }
    }

    // Появление таблички.
    private void spawnSign(EntityPlayer player) {

        World world = player.world;

        BlockPos pos = findPosition(player);

        if (pos == null) return;

        world.setBlockState(
                pos,
                Blocks.STANDING_SIGN.getDefaultState()
                        .withProperty(
                                BlockStandingSign.ROTATION,
                                world.rand.nextInt(16)
                        ),
                3
        );

        if (world.getTileEntity(pos) instanceof TileEntitySign) {

            TileEntitySign sign =
                    (TileEntitySign) world.getTileEntity(pos);

            String message = SIGN_MESSAGES[
                    world.rand.nextInt(SIGN_MESSAGES.length)
                    ];

            sign.signText[0] = new TextComponentString(message);
            sign.markDirty();
        }

        registerTemporaryBlock(world, pos, 600 + world.rand.nextInt(300));
    }

    // Появление одинокого факела.
    private void spawnTorch(EntityPlayer player) {

        World world = player.world;

        BlockPos pos = findPosition(player);

        if (pos == null) return;

        world.setBlockState(
                pos,
                Blocks.TORCH.getDefaultState()
                        .withProperty(BlockTorch.FACING, EnumFacing.UP),
                3
        );

        registerTemporaryBlock(world, pos, 600 + world.rand.nextInt(300));
    }

    // Жуткий звук издалека.
    private void playCreepyNoise(EntityPlayer player) {

        World world = player.world;
        Random random = world.rand;

        double angle = random.nextDouble() * Math.PI * 2.0D;
        double distance = 12 + random.nextInt(15);

        double x = player.posX + Math.cos(angle) * distance;
        double z = player.posZ + Math.sin(angle) * distance;

        BlockPos pos = world.getTopSolidOrLiquidBlock(
                new BlockPos(x, player.posY, z)
        );

        world.playSound(
                null,
                pos,
                SoundEvents.AMBIENT_CAVE,
                SoundCategory.AMBIENT,
                0.8F,
                0.7F + random.nextFloat() * 0.5F
        );
    }

    // Фальшивые шаги вокруг игрока.
    private void playFootsteps(EntityPlayer player) {

        World world = player.world;
        Random random = world.rand;

        for (int i = 0; i < 3; i++) {

            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 5 + random.nextInt(9);

            double x = player.posX + Math.cos(angle) * distance;
            double z = player.posZ + Math.sin(angle) * distance;

            BlockPos pos = world.getTopSolidOrLiquidBlock(
                    new BlockPos(x, player.posY, z)
            );

            world.playSound(
                    null,
                    pos,
                    SoundEvents.BLOCK_GRASS_STEP,
                    SoundCategory.BLOCKS,
                    0.45F,
                    0.65F + random.nextFloat() * 0.5F
            );
        }
    }

    // Поиск подходящего места на поверхности.
    private BlockPos findPosition(EntityPlayer player) {

        World world = player.world;
        Random random = world.rand;

        for (int attempt = 0; attempt < 20; attempt++) {

            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 8 + random.nextInt(9);

            double x = player.posX + Math.cos(angle) * distance;
            double z = player.posZ + Math.sin(angle) * distance;

            BlockPos pos = world.getTopSolidOrLiquidBlock(
                    new BlockPos(x, player.posY, z)
            );

            if (!world.isBlockLoaded(pos)) continue;

            if (!world.isAirBlock(pos)) continue;

            if (!world.getBlockState(pos.down())
                    .getMaterial().blocksMovement()) continue;

            // Не ставим аномалии в воду или лаву.
            if (world.getBlockState(pos.down()).getMaterial().isLiquid()) {
                continue;
            }

            return pos;
        }

        return null;
    }

    // Запоминаем временный блок.
    private void registerTemporaryBlock(
            World world,
            BlockPos pos,
            int lifetime
    ) {

        int dimension = world.provider.getDimension();

        temporaryBlocks
                .computeIfAbsent(dimension, key -> new HashMap<>())
                .put(pos, lifetime);
    }

    // Удаляем временные блоки после окончания таймера.
    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {

        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isRemote) return;

        int dimension = event.world.provider.getDimension();

        Map<BlockPos, Integer> blocks =
                temporaryBlocks.get(dimension);

        if (blocks == null) return;

        Iterator<Map.Entry<BlockPos, Integer>> iterator =
                blocks.entrySet().iterator();

        while (iterator.hasNext()) {

            Map.Entry<BlockPos, Integer> entry = iterator.next();

            BlockPos pos = entry.getKey();
            int ticks = entry.getValue() - 1;

            if (ticks <= 0) {

                // Удаляем только наши таблички и факелы.
                if (event.world.getBlockState(pos).getBlock()
                        == Blocks.STANDING_SIGN
                        || event.world.getBlockState(pos).getBlock()
                        == Blocks.TORCH) {

                    event.world.setBlockToAir(pos);
                }

                iterator.remove();

            } else {

                entry.setValue(ticks);
            }
        }

        if (blocks.isEmpty()) {
            temporaryBlocks.remove(dimension);
        }
    }
}
