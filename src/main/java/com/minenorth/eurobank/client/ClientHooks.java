package com.minenorth.eurobank.client;

import com.minenorth.eurobank.packet.AdminListPacket;
import com.minenorth.eurobank.packet.StatePacket;
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

    public static void handleAdmin(AdminListPacket m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AdminScreen s) s.update(m);
        else if (m.open) mc.setScreen(new AdminScreen(m, mc.screen instanceof AtmScreen a ? a : null));
    }
}
