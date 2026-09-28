package fr.hardel.firefly.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import fr.hardel.firefly.MinecraftBootstrap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MinecraftBootstrap.class)
class LightOracleTest {
    @Test
    void vanillaLightsLikeVanilla() {
        assertEquals(List.of(), LightOracle.differences(LightOracle.VANILLA, false));
    }

    @Test
    void aMissedUpdateIsCaught() {
        assertNotEquals(List.of(), LightOracle.differences(LightOracle.VANILLA, true));
    }
}
