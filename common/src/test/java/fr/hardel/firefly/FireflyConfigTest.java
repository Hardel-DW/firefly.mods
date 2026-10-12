package fr.hardel.firefly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FireflyConfigTest {
    @Test
    void missingFileWritesDefaultsThatReloadIdentically(@TempDir Path directory) {
        Path file = directory.resolve("firefly.json");

        FireflyConfig written = FireflyConfig.load(file);

        assertTrue(Files.exists(file));
        assertEquals(written, FireflyConfig.load(file));
        assertEquals(FireflyConfig.defaults(), written);
        assertEquals(FireflyConfig.THIRD_OF_CORES, written.threads());
    }

    @Test
    void negativeThreadsUseAThirdOfTheCores(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("firefly.json");
        Files.writeString(file, "{\"threads\": -4}");
        assertEquals(Math.max(1, Runtime.getRuntime().availableProcessors() / 3), FireflyConfig.load(file).effectiveThreads());

        Files.writeString(file, "{\"threads\": 6}");
        assertEquals(6, FireflyConfig.load(file).effectiveThreads());
    }

    @Test
    void anInvalidEntryTakesItsDefaultAndKeepsTheOthers(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("firefly.json");
        for (String threads : List.of("0", "1025", "\"lots\"")) {
            Files.writeString(file, "{\"threads\": %s, \"shared_threads\": false, \"unknown\": 3}".formatted(threads));
            assertEquals(new FireflyConfig(FireflyConfig.THIRD_OF_CORES, false), FireflyConfig.load(file), threads);
        }

        for (String broken : List.of("{oops", "", "[1, 2]")) {
            Files.writeString(file, broken);
            assertEquals(FireflyConfig.defaults(), FireflyConfig.load(file), broken);
            assertEquals(broken, Files.readString(file));
        }
    }
}
