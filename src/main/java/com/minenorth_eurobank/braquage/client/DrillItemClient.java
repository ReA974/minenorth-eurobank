package com.minenorth_eurobank.braquage.client;

import com.minenorth_eurobank.EuroBank;
import com.minenorth_eurobank.braquage.item.DrillItem;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import java.util.function.Consumer;

/** Client-only item renderer wiring for the drill. */
public final class DrillItemClient {
    private DrillItemClient() {}

    public static void initialize(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private DrillItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new DrillItemRenderer();
                return renderer;
            }
        });
    }

    private static final class DrillItemRenderer extends GeoItemRenderer<DrillItem> {
        private DrillItemRenderer() {
            super(new DrillGeoModel());
        }
    }

    private static final class DrillGeoModel extends GeoModel<DrillItem> {
        @Override
        public net.minecraft.resources.ResourceLocation getModelResource(DrillItem animatable) {
            return new net.minecraft.resources.ResourceLocation(EuroBank.MODID, "geo/item/drill.geo.json");
        }

        @Override
        public net.minecraft.resources.ResourceLocation getTextureResource(DrillItem animatable) {
            return new net.minecraft.resources.ResourceLocation(EuroBank.MODID, animatable.getDrillLevel().texture);
        }

        @Override
        public net.minecraft.resources.ResourceLocation getAnimationResource(DrillItem animatable) {
            return new net.minecraft.resources.ResourceLocation(EuroBank.MODID, "animations/item/drill.animation.json");
        }
    }
}
