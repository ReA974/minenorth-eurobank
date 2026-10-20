package com.minenorth_eurobank.client;

import com.minenorth_eurobank.packet.DeskPacket;
import com.minenorth_eurobank.packet.StatePacket;
import com.minenorth_eurobank.packet.TabletDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientHooks {
    private ClientHooks() {}

    public static void handleState(StatePacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AtmScreen s) s.update(m);
        else if (m.open) mc.setScreen(new AtmScreen(m));
    }

    public static void handleTablet(TabletDataPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TabletScreen s) s.update(m.data);
        else if (m.open) mc.setScreen(new TabletScreen(m.data));
    }

    public static void handleDesk(DeskPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof LoanDeskScreen s) s.update(m);
        else if (m.open) mc.setScreen(new LoanDeskScreen(m));
    }
}
