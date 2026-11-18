package com.unknown.mod.client;

import com.unknown.mod.entity.EntityMaskedGirl;

import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

public class RenderMaskedGirl extends RenderLiving<EntityMaskedGirl> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("minecraft", "textures/entity/masked_girl.png");

    public RenderMaskedGirl(RenderManager manager) {
        super(manager, new ModelPlayer(0.0F, true), 0.0F);
        addLayer(new LayerMaskedFace((ModelPlayer) this.mainModel));
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityMaskedGirl entity) {
        return TEXTURE;
    }
}
