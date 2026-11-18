package com.unknown.mod.entity;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.world.World;

/**
 * The masked girl is normally a silent, distant observer.
 * Her melee task is only activated by MaskedGirlEventHandler during stage III.
 */
public class EntityMaskedGirl extends EntityMob {

    public EntityMaskedGirl(World worldIn) {
        super(worldIn);
        setSize(0.6F, 1.8F);
        setNoAI(false);
        setSilent(true);
        enablePersistence();
    }

    @Override
    protected void initEntityAI() {
        tasks.addTask(2, new EntityAIAttackMelee(this, 1.35D, false));
        tasks.addTask(7, new EntityAIWander(this, 0.72D));
        tasks.addTask(8, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.38D);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(4.0D);
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }
}
