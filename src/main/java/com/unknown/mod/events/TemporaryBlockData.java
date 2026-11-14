package com.unknown.mod.events;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Сохраняет временные аномальные блоки между перезапусками мира.
 * Если игрок заменил или изменил блок, трекер не удаляет его.
 */
public class TemporaryBlockData extends WorldSavedData {

    private static final String DATA_PREFIX = "unknown_temporary_blocks_";

    private static class Entry {
        private int ticks;
        private final int expectedStateId;

        private Entry(int ticks, int expectedStateId) {
            this.ticks = ticks;
            this.expectedStateId = expectedStateId;
        }
    }

    private final Map<BlockPos, Entry> entries = new HashMap<>();

    public TemporaryBlockData(String name) {
        super(name);
    }

    public static TemporaryBlockData get(World world) {
        String dataName = DATA_PREFIX + world.provider.getDimension();
        MapStorage storage = world.getPerWorldStorage();

        TemporaryBlockData data = (TemporaryBlockData)
                storage.getOrLoadData(TemporaryBlockData.class, dataName);

        if (data == null) {
            data = new TemporaryBlockData(dataName);
            storage.setData(dataName, data);
        }

        return data;
    }

    public void add(World world, BlockPos pos, int lifetime) {
        entries.put(pos.toImmutable(), new Entry(
                lifetime,
                Block.getStateId(world.getBlockState(pos))
        ));
        markDirty();
    }

    public void tick(World world) {
        Iterator<Map.Entry<BlockPos, Entry>> iterator = entries.entrySet().iterator();
        boolean changed = false;

        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Entry> entry = iterator.next();
            BlockPos pos = entry.getKey();
            Entry temporary = entry.getValue();

            // Выгруженный чанк не тикает; запись остаётся до его следующей загрузки.
            if (!world.isBlockLoaded(pos)) continue;

            temporary.ticks--;

            if (temporary.ticks <= 0) {
                int currentStateId = Block.getStateId(world.getBlockState(pos));

                if (currentStateId == temporary.expectedStateId) {
                    world.setBlockToAir(pos);
                }

                iterator.remove();
                changed = true;
            } else if (temporary.ticks % 20 == 0) {
                // Сохраняем оставшееся время примерно раз в секунду.
                changed = true;
            }
        }

        if (changed) markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        entries.clear();
        NBTTagList list = compound.getTagList("TemporaryBlocks", 10);

        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            long packedPos = tag.getLong("Pos");
            int ticks = tag.getInteger("Ticks");
            int stateId = tag.getInteger("State");

            if (ticks > 0) {
                entries.put(BlockPos.fromLong(packedPos), new Entry(ticks, stateId));
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagList list = new NBTTagList();

        for (Map.Entry<BlockPos, Entry> entry : entries.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("Pos", entry.getKey().toLong());
            tag.setInteger("Ticks", entry.getValue().ticks);
            tag.setInteger("State", entry.getValue().expectedStateId);
            list.appendTag(tag);
        }

        compound.setTag("TemporaryBlocks", list);
        return compound;
    }
}
