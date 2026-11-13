
package com.unknown.mod;

import com.unknown.mod.entity.EntityWatcher;
import com.unknown.mod.events.WatcherEventHandler;
import com.unknown.mod.proxy.CommonProxy;

import net.minecraft.util.ResourceLocation;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import com.unknown.mod.events.AnomalyEventHandler;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.Logger;

@Mod(
        modid = UnknownMod.MODID,
        name = UnknownMod.NAME,
        version = UnknownMod.VERSION
)
public class UnknownMod {

    public static final String MODID = "unknown";
    public static final String NAME = "UNKNOWN";
    public static final String VERSION = "1.0";

    @Mod.Instance(MODID)
    public static UnknownMod INSTANCE;

    @SidedProxy(
            clientSide = "com.unknown.mod.proxy.ClientProxy",
            serverSide = "com.unknown.mod.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    public static Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {

        logger = event.getModLog();

        EntityRegistry.registerModEntity(
                new ResourceLocation(MODID, "watcher"),
                EntityWatcher.class,
                "Watcher",
                1,
                this,
                192,
                1,
                true
        );

        proxy.registerRenderers();

        MinecraftForge.EVENT_BUS.register(new AnomalyEventHandler());
        MinecraftForge.EVENT_BUS.register(new WatcherEventHandler());

        logger.info("UNKNOWN is awakening...");
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        logger.info("Something is watching you.");
    }
}
