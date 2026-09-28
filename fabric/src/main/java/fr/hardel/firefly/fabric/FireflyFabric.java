package fr.hardel.firefly.fabric;

import fr.hardel.firefly.FireflyConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class FireflyFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        FireflyConfig.register(FabricLoader.getInstance().getConfigDir());
    }
}
