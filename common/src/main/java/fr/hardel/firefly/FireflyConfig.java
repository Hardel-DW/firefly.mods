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

public record FireflyConfig(int threads, boolean sharedThreads) {
    public static final int THIRD_OF_CORES = -1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Codec<FireflyConfig> CODEC = RecordCodecBuilder.create(builder -> builder.group(
        Codec.INT.validate(FireflyConfig::validThreads).lenientOptionalFieldOf("threads", THIRD_OF_CORES).forGetter(FireflyConfig::threads),
        Codec.BOOL.lenientOptionalFieldOf("shared_threads", true).forGetter(FireflyConfig::sharedThreads)
    ).apply(builder, FireflyConfig::new));

    private static FireflyConfig instance = defaults();

    public static void register(Path configDir) {
        instance = load(configDir.resolve("%s.json".formatted(Firefly.MOD_ID)));
    }

    public static FireflyConfig get() {
        return instance;
    }

    public static FireflyConfig defaults() {
        return CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
    }

    static FireflyConfig load(Path file) {
        return Files.notExists(file) ? write(file, defaults()) : parse(read(file));
    }

    public int effectiveThreads() {
        return this.threads > 0 ? this.threads : Math.max(1, Runtime.getRuntime().availableProcessors() / 3);
    }

    private static DataResult<Integer> validThreads(int threads) {
        return threads == 0 || threads > 1024 ? DataResult.error(() -> "threads must be negative or between 1 and 1024") : DataResult.success(threads);
    }

    private static FireflyConfig parse(JsonElement json) {
        return CODEC.parse(JsonOps.INSTANCE, json).result().orElseGet(FireflyConfig::defaults);
    }

    private static JsonElement read(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file));
        } catch (JsonParseException exception) {
            return new JsonObject();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to read %s".formatted(file), exception);
        }
    }

    private static FireflyConfig write(Path file, FireflyConfig config) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "%s%s".formatted(GSON.toJson(CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow()), System.lineSeparator()));
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write %s".formatted(file), exception);
        }

        return config;
    }
}
