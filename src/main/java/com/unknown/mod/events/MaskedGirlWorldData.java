package com.unknown.mod.events;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/**
 * Сохраняем состояние ритуала в данных мира, а не только в NBT игрока.
 * После завершения трёхдневного цикла захват становится постоянным.
 */
public class MaskedGirlWorldData extends WorldSavedData {

    private static final String DATA_NAME = "unknown_masked_girl_world";
    private static final int HAUNTING_DURATION = 3 * 24000;

    private boolean started;
    private boolean taken;
    private int remainingTicks;

    public MaskedGirlWorldData() {
        super(DATA_NAME);
    }

    public MaskedGirlWorldData(String name) {
        super(name);
    }

    public static MaskedGirlWorldData get(World world) {
        if (world == null || world.isRemote || world.getMinecraftServer() == null) return null;

        WorldServer overworld = world.getMinecraftServer().getWorld(0);
        if (overworld == null) return null;

        MapStorage storage = overworld.getPerWorldStorage();
        MaskedGirlWorldData data = (MaskedGirlWorldData) storage.getOrLoadData(
                MaskedGirlWorldData.class, DATA_NAME);
        if (data == null) {
            data = new MaskedGirlWorldData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public boolean isActive() {
        return started;
    }

    public boolean isTaken() {
        return taken;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    public int getStage() {
        if (!started) return 0;
        if (taken || remainingTicks <= 0) return 3;
        int elapsed = HAUNTING_DURATION - remainingTicks;
        return Math.min(3, elapsed / 24000 + 1);
    }

    public void begin() {
        if (started) return;
        started = true;
        taken = false;
        remainingTicks = HAUNTING_DURATION;
        markDirty();
    }

    public void tick() {
        if (!started || taken || remainingTicks <= 0) return;
        remainingTicks--;
        if (remainingTicks <= 0) {
            remainingTicks = 0;
            taken = true;
            markDirty();
        } else if (remainingTicks % 20 == 0) {
            // Сохраняем прогресс раз в секунду, а не помечаем данные грязными каждый тик.
            markDirty();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        started = compound.getBoolean("Started");
        taken = compound.getBoolean("Taken");
        remainingTicks = Math.max(0, compound.getInteger("RemainingTicks"));
        if (started && remainingTicks <= 0) taken = true;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setBoolean("Started", started);
        compound.setBoolean("Taken", taken);
        compound.setInteger("RemainingTicks", remainingTicks);
        return compound;
    }
}
