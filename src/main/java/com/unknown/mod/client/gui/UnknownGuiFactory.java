package com.unknown.mod.client.gui;

import com.unknown.mod.ModConfig;
import com.unknown.mod.UnknownMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Экран настроек UNKNOWN, доступный из списка модов Forge.
 */
public class UnknownGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraftInstance) {
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        List<IConfigElement> elements = new ArrayList<IConfigElement>();

        for (String category : ModConfig.getConfig().getCategoryNames()) {
            elements.add(new ConfigElement(ModConfig.getConfig().getCategory(category)));
        }

        return new GuiConfig(
                parentScreen,
                elements,
                UnknownMod.MODID,
                "main",
                false,
                false,
                "UNKNOWN Configuration"
        );
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }
}
