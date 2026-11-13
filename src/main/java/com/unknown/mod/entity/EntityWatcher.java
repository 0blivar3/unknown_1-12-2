
package com.unknown.mod.entity;

import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.world.World;

public class EntityWatcher extends EntityMob {

    public EntityWatcher(World worldIn) {
        super(worldIn);

        setSize(0.6F, 1.8F);
        setNoAI(true);
        setSilent(true);
        enablePersistence();
    }

    @Override
    protected void initEntityAI() {
        // У Наблюдателя нет стандартного ИИ.
        // Он не ходит, не атакует и не преследует игрока.
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();

        getEntityAttribute(
                SharedMonsterAttributes.MAX_HEALTH
        ).setBaseValue(20.0D);

        getEntityAttribute(
                SharedMonsterAttributes.MOVEMENT_SPEED
        ).setBaseValue(0.0D);
    }
}
