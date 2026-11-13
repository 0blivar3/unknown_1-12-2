
package com.unknown.mod.client;

import com.unknown.mod.UnknownMod;
import com.unknown.mod.entity.EntityWatcher;

import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

public class RenderWatcher extends RenderLiving<EntityWatcher> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(
                    UnknownMod.MODID,
                    "textures/entity/watcher.png"
            );

    public RenderWatcher(RenderManager manager) {
        super(manager, new ModelPlayer(0.0F, false), 0.0F);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityWatcher entity) {
        return TEXTURE;
    }
}
