package com.unknown.mod.entity;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.world.World;

/**
 * Тихая сущность, которая появляется после завершения ритуала.
 * Не атакует, не преследует игрока и не имеет собственного ИИ.
 */
public class EntityMaskedGirl extends EntityMob {

    public EntityMaskedGirl(World worldIn) {
        super(worldIn);
        setSize(0.6F, 1.8F);
        setNoAI(true);
        setSilent(true);
        enablePersistence();
    }

    @Override
    protected void initEntityAI() {
        // Персонаж только наблюдает за игроком.
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.0D);
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }
}
