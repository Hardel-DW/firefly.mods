package fr.hardel.firefly.storage;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import org.jspecify.annotations.Nullable;

public final class LightSections {
    private static final VarHandle SECTIONS = MethodHandles.arrayElementVarHandle(byte[][].class);

    private final byte[][] sections;
    private volatile boolean enabled;

    public LightSections(int count) {
        this.sections = new byte[count][];
    }

    public byte @Nullable [] get(int index) {
        return (byte[]) SECTIONS.getAcquire(this.sections, index);
    }

    public void publish(int index, byte @Nullable [] data) {
        SECTIONS.setRelease(this.sections, index, data);
    }

    public int count() {
        return this.sections.length;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
