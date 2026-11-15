package com.unknown.mod.client;

import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import com.unknown.mod.entity.EntityMaskedGirl;

/**
 * Простая тёмная маска, закрывающая нижнюю часть лица Alex.
 */
public class LayerMaskedFace implements LayerRenderer<EntityMaskedGirl> {

    private final ModelPlayer playerModel;
    private final ModelRenderer mask;

    public LayerMaskedFace(ModelPlayer playerModel) {
        this.playerModel = playerModel;
        this.mask = new ModelRenderer(playerModel, 0, 0);
        this.mask.addBox(-3.0F, -1.0F, -4.35F, 6, 5, 1);
    }

    @Override
    public void doRenderLayer(EntityMaskedGirl entity, float limbSwing, float limbSwingAmount,
                              float partialTicks, float ageInTicks, float netHeadYaw,
                              float headPitch, float scale) {
        GlStateManager.pushMatrix();
        playerModel.bipedHead.postRender(scale);
        GlStateManager.disableTexture2D();
        GlStateManager.color(0.055F, 0.055F, 0.065F, 1.0F);
        mask.render(scale);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
