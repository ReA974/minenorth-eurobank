package com.minenorth_eurobank;

import com.minenorth_eurobank.blocks.ModBlocks;
import com.minenorth_eurobank.items.ModItems;
import com.minenorth_eurobank.loan.Loan;
import com.minenorth_eurobank.loan.LoanService;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.registries.MissingMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
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
        // Braquage de banque (ex-mod minenorthbanque_braque)
        com.minenorth_eurobank.braquage.block.ModBlocks.BLOCKS.register(bus);
        com.minenorth_eurobank.braquage.block.entity.ModBlockEntities.BLOCK_ENTITIES.register(bus);
        com.minenorth_eurobank.braquage.item.ModItems.ITEMS.register(bus);
        com.minenorth_eurobank.braquage.item.ModCreativeTabs.TABS.register(bus);
        com.minenorth_eurobank.braquage.item.ModSounds.SOUNDS.register(bus);
        com.minenorth_eurobank.braquage.network.ModNetwork.register();
        FMLJavaModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,
                com.minenorth_eurobank.braquage.config.ModConfig.SPEC, "Minenorth-banque/coffre.toml");
        bus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);
        fr.minenorth.api.MineNorth.provide(fr.minenorth.api.BankService.class, new com.minenorth_eurobank.api.BankProvider());
    }

    private void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(Network::register);
    }

    /** Anciens identifiants du mod minenorthbanque_braque -> minenorth_eurobank (monde existant). */
    @SubscribeEvent
    public void onMissingMappings(MissingMappingsEvent e) {
        remap(e, ForgeRegistries.BLOCKS.getRegistryKey(), ForgeRegistries.BLOCKS);
        remap(e, ForgeRegistries.ITEMS.getRegistryKey(), ForgeRegistries.ITEMS);
        remap(e, ForgeRegistries.BLOCK_ENTITY_TYPES.getRegistryKey(), ForgeRegistries.BLOCK_ENTITY_TYPES);
        remap(e, ForgeRegistries.SOUND_EVENTS.getRegistryKey(), ForgeRegistries.SOUND_EVENTS);
    }

    private static <T> void remap(MissingMappingsEvent e, ResourceKey<? extends Registry<T>> key, IForgeRegistry<T> registry) {
        for (MissingMappingsEvent.Mapping<T> m : e.getMappings(key, "minenorthbanque_braque")) {
            T target = registry.getValue(new ResourceLocation(MODID, m.getKey().getPath()));
            if (target != null) m.remap(target);
        }
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
