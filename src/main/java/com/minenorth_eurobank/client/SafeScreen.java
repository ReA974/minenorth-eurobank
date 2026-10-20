package com.minenorth_eurobank.client;

import com.minenorth_eurobank.safe.SafeMenu;
import com.minenorth_eurobank.safe.SafeTier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Interface du coffre-fort, dessinée sans texture : panneau bleu nuit, liseré et emplacements aux couleurs du niveau. */
@OnlyIn(Dist.CLIENT)
public class SafeScreen extends AbstractContainerScreen<SafeMenu> {
    private static final int BLUE = 0xFF161048;
    private static final int SLOT = 0xFF0E0A34;

    private final int accent;

    public SafeScreen(SafeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = SafeMenu.WIDTH;
        this.imageHeight = menu.guiHeight();
        this.inventoryLabelY = menu.invLabelY();
        this.titleLabelY = 8;
        this.accent = switch (menu.tier()) {
            case BASIC -> 0xFF20AAEB;
            case ADVANCED -> 0xFFFFC94D;
            case EXPERT -> 0xFFFF5FA8;
        };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x - 3, y - 3, x + imageWidth + 3, y + imageHeight + 3, 0xFF0E0E10);
        g.fill(x, y, x + imageWidth, y + imageHeight, BLUE);
        g.fill(x, y, x + imageWidth, y + 2, accent);
        SafeTier tier = menu.tier();
        // zone du coffre
        int gridRows = tier.rows();
        g.fill(x + 6, y + 22, x + imageWidth - 6, y + 28 + gridRows * 18 + 4, 0xFF1B1560);
        for (Slot s : menu.slots) {
            boolean safeSlot = s.index < tier.slots && s.container != minecraft.player.getInventory();
            // cadre de 18x18 puis fond de 16x16 : les emplacements voisins restent séparés
            g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, safeSlot ? accent : 0xFF2A2468);
            g.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, SLOT);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, 8, 8, 0xFFFFFFFF, false);
        g.drawString(font, playerInventoryTitle, 16, inventoryLabelY, 0xFFCFE3FF, false);
    }
}
