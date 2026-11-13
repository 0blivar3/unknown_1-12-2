
package com.unknown.mod.proxy;

import com.unknown.mod.entity.EntityWatcher;
import com.unknown.mod.client.RenderWatcher;

import net.minecraft.client.renderer.entity.Render;
import net.minecraftforge.fml.client.registry.RenderingRegistry;

public class ClientProxy extends CommonProxy {

    @Override
    public void registerRenderers() {

        RenderingRegistry.registerEntityRenderingHandler(
                EntityWatcher.class,
                RenderWatcher::new
        );
    }
}
