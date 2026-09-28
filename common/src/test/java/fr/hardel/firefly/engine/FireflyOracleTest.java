package fr.hardel.firefly.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fr.hardel.firefly.MinecraftBootstrap;
import fr.hardel.firefly.server.FireflyLightEngine;
import fr.hardel.firefly.storage.Nibbles;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Tag("mod")
@ExtendWith(MinecraftBootstrap.class)
class FireflyOracleTest {
    private static final LightOracle.Engine FIREFLY = (world, sky) -> new FireflyLightEngine(world, null, sky, null, null, LightEmission.VANILLA, _ -> {
    });

    @Test
    void fireflyLightsLikeVanillaAndKeepsSharedSections() {
        assertEquals(List.of(), LightOracle.differences(FIREFLY, false));
        assertEquals(List.of(0), distinctBytes(Nibbles.ZERO));
        assertEquals(List.of(-1), distinctBytes(Nibbles.FULL));
    }

    private static List<Integer> distinctBytes(byte[] data) {
        return IntStream.range(0, data.length).map(index -> data[index]).distinct().boxed().toList();
    }
}
