
package com.unknown.mod.proxy;

import com.unknown.mod.ModConfig;
import com.unknown.mod.UnknownMod;
import com.unknown.mod.entity.EntityWatcher;
import com.unknown.mod.client.RenderWatcher;

import net.minecraft.client.renderer.entity.Render;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy extends CommonProxy {

    @Override
    public void registerRenderers() {

        RenderingRegistry.registerEntityRenderingHandler(
                EntityWatcher.class,
                RenderWatcher::new
        );

        // ConfigChangedEvent относится к клиентской GUI-системе Forge.
        // Регистрируем обработчик только в ClientProxy, чтобы dedicated server
        // не загружал клиентские классы.
        MinecraftForge.EVENT_BUS.register(new ConfigChangedHandler());
    }

    private static class ConfigChangedHandler {
        @SubscribeEvent
        public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (UnknownMod.MODID.equals(event.getModID())) {
                ModConfig.sync();
            }
        }
    }
}
