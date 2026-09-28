package fr.hardel.firefly.neoforge;

import fr.hardel.firefly.Firefly;
import fr.hardel.firefly.FireflyConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

@Mod(Firefly.MOD_ID)
public final class FireflyNeoForge {
    public FireflyNeoForge() {
        FireflyConfig.register(FMLPaths.CONFIGDIR.get());
    }
}
