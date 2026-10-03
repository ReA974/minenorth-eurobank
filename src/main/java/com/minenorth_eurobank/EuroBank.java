package com.minenorth_eurobank;

import com.minenorth_eurobank.blocks.ModBlocks;
import com.minenorth_eurobank.items.ModItems;
import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.loan.LoanService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod(EuroBank.MODID)
public class EuroBank {
    private int tickCounter;

    public static final String MODID = "minenorth_eurobank";

    public EuroBank() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModTabs.TABS.register(bus);
        bus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(Network::register);
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent e) {
        BankCommands.register(e.getDispatcher());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            BankData d = BankData.get(sp.server);
            if (d.has(sp.getUUID())) d.rename(sp.getUUID(), sp.getGameProfile().getName());
            if (LoanService.isBanker(sp)) {
                long pending = d.loans().stream().filter(l -> l.status == Loan.PENDING).count();
                if (pending > 0) {
                    sp.sendSystemMessage(Component.literal("[Banque] " + pending
                            + " demande(s) de prêt en attente. Ouvrez un ATM ou tapez /bank banquier."));
                }
            }
        }
    }

    /** Chaque tick : pousse les mises à jour aux écrans ouverts ; chaque minute : prélève les échéances de prêt. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        tickCounter++;
        if (tickCounter >= 1200) {
            tickCounter = 0;
            LoanService.sweep(server);
        }
        if (BankData.get(server).consumeRefresh()) Network.refreshWatchers(server, false);
        else if (tickCounter % 20 == 0) Network.refreshWatchers(server, true);   // espèces sur soi
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        Network.SESSIONS.remove(e.getEntity().getUUID());
        Network.unwatchAll(e.getEntity().getUUID());
    }
}
