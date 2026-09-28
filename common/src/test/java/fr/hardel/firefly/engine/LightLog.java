package fr.hardel.firefly.engine;

import fr.hardel.firefly.storage.Nibbles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.jspecify.annotations.Nullable;

final class LightLog {
    private static final int CELLS = 4096;
    private static final int SHOWN = 5;

    private final LevelLightEngine engine;
    private final Map<Watched, byte @Nullable []> stable = new HashMap<>();
    private final Map<Watched, List<byte @Nullable []>> seen = new HashMap<>();

    LightLog(LevelLightEngine engine, int chunks) {
        this.engine = engine;
        for (LightLayer layer : LightLayer.values()) {
            for (int chunk = 0; chunk < chunks * chunks; chunk++) {
                for (int y = engine.getMinLightSection(); y < engine.getMaxLightSection(); y++) {
                    Watched watched = new Watched(layer, SectionPos.of(chunk / chunks, y, chunk % chunks));
                    this.stable.put(watched, read(watched));
                }
            }
        }
    }

    synchronized void notified(LightLayer layer, SectionPos section) {
        Watched watched = new Watched(layer, section);
        this.seen.computeIfAbsent(watched, _ -> new ArrayList<>()).add(read(watched));
    }

    synchronized List<String> settle(String moment) {
        List<String> flickering = new ArrayList<>();
        List<String> silent = new ArrayList<>();
        for (Map.Entry<Watched, byte @Nullable []> entry : this.stable.entrySet()) {
            Watched watched = entry.getKey();
            byte @Nullable [] now = read(watched);
            List<byte @Nullable []> seen = this.seen.getOrDefault(watched, List.of());
            if (seen.isEmpty() && !Arrays.equals(entry.getValue(), now)) {
                silent.add(watched.toString());
            }

            if (seen.stream().anyMatch(values -> flickers(entry.getValue(), values, now))) {
                flickering.add(watched.toString());
            }

            entry.setValue(now);
        }

        this.seen.clear();
        List<String> problems = new ArrayList<>();
        if (!flickering.isEmpty()) {
            problems.add("%s: %d sections flicker, first %s".formatted(moment, flickering.size(), flickering.subList(0, Math.min(SHOWN, flickering.size()))));
        }

        if (!silent.isEmpty()) {
            problems.add("%s: %d sections changed without notification, first %s".formatted(moment, silent.size(), silent.subList(0, Math.min(SHOWN, silent.size()))));
        }

        return problems;
    }

    private byte @Nullable [] read(Watched watched) {
        DataLayer layer = this.engine.getLayerListener(watched.layer).getDataLayerData(watched.section);
        return layer == null ? null : layer.copy().getData();
    }

    private static boolean flickers(byte @Nullable [] before, byte @Nullable [] seen, byte @Nullable [] after) {
        if (before == null || seen == null || after == null)
            return false;

        for (int cell = 0; cell < CELLS; cell++) {
            int from = Nibbles.get(before, cell);
            int to = Nibbles.get(after, cell);
            int level = Nibbles.get(seen, cell);
            if (level < Math.min(from, to) || level > Math.max(from, to)) {
                return true;
            }
        }

        return false;
    }

    private record Watched(LightLayer layer, SectionPos section) {
    }
}
