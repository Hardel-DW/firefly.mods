package fr.hardel.firefly;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record FireflyConfig(int threads) {
    public static final int THIRD_OF_CORES = -1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Codec<FireflyConfig> CODEC = RecordCodecBuilder.create(builder -> builder.group(
        Codec.INT.validate(FireflyConfig::validThreads).optionalFieldOf("threads", THIRD_OF_CORES).forGetter(FireflyConfig::threads)
    ).apply(builder, FireflyConfig::new));

    private static FireflyConfig instance = defaults();

    public static void register(Path configDir) {
        instance = load(configDir.resolve(Firefly.MOD_ID + ".json"));
    }

    public static FireflyConfig get() {
        return instance;
    }

    public static FireflyConfig defaults() {
        return CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
    }

    static FireflyConfig load(Path file) {
        return Files.notExists(file) ? write(file, defaults()) : parse(file, read(file));
    }

    public int effectiveThreads() {
        return this.threads > 0 ? this.threads : Math.max(1, Runtime.getRuntime().availableProcessors() / 3);
    }

    private static DataResult<Integer> validThreads(int threads) {
        if (threads > 1024)
            return DataResult.error(() -> "threads must be at most 1024, negative uses a third of the cores");

        if (threads == 0)
            return DataResult.error(() -> "threads 0 is invalid: negative uses a third of the cores");

        return DataResult.success(threads);
    }

    private static JsonObject encode(FireflyConfig config) {
        return CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow().getAsJsonObject();
    }

    private static FireflyConfig parse(Path file, JsonElement json) {
        return CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(error -> new IllegalArgumentException("Config %s is invalid: %s".formatted(file, error)));
    }

    private static JsonElement read(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file));
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Config %s is not valid JSON".formatted(file), exception);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read %s".formatted(file), exception);
        }
    }

    private static FireflyConfig write(Path file, FireflyConfig config) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "%s%s".formatted(GSON.toJson(encode(config)), System.lineSeparator()));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write %s".formatted(file), exception);
        }

        return config;
    }
}
