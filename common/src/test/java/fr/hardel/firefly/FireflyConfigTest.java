package fr.hardel.firefly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void invalidFilesFailTheBootNamingFileAndCause(@TempDir Path directory) throws IOException {
        record Invalid(String json, String cause) {
        }

        List<Invalid> cases = List.of(
            new Invalid("{\"threads\": 0}", "threads"),
            new Invalid("{\"threads\": 1025}", "threads"),
            new Invalid("{\"threads\": \"lots\"}", null),
            new Invalid("{oops", null),
            new Invalid("", null)
        );

        for (Invalid invalid : cases) {
            Path file = directory.resolve("firefly.json");
            Files.writeString(file, invalid.json());

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> FireflyConfig.load(file), invalid.json());
            assertTrue(exception.getMessage().contains(file.toString()), invalid.json());
            if (invalid.cause() != null) {
                assertTrue(exception.getMessage().contains(invalid.cause()), invalid.json());
            }
        }
    }
}
