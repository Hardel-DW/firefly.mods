package fr.hardel.firefly.engine;

import fr.hardel.firefly.Firefly;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = Firefly.MOD_ID)
public final class TestLamps {
    static final ResourceKey<Block> POSITION = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Firefly.MOD_ID, "test_position_lamp"));
    static final ResourceKey<Block> AUXILIARY = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Firefly.MOD_ID, "test_auxiliary_lamp"));

    private TestLamps() {
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.BLOCK, POSITION.identifier(), () -> new PositionLamp(BlockBehaviour.Properties.of().setId(POSITION)));
        event.register(Registries.BLOCK, AUXILIARY.identifier(), () -> new AuxiliaryLamp(BlockBehaviour.Properties.of().setId(AUXILIARY)));
    }
}
