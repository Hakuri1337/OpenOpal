package wtf.opal.client.renderer.shader;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

public final class CustomUniform {
    public static final int SIZE = new Std140SizeCalculator().putVec2().putFloat().putFloat().putInt().get();
    private GpuBuffer buffer;
    private boolean used;

    public void use(int width, int height, int blurRadius, Runnable runnable) {
        final GpuBuffer buffer = this.getOrCreateBuffer();
        this.used = true;
        try {
            try (MemoryStack memoryStack = MemoryStack.stackPush()) {
                ByteBuffer byteBuffer = Std140Builder.onStack(memoryStack, SIZE)
                        .putVec2(width, height)
                        .putFloat(0.0F)
                        .putFloat(0L)
                        .putInt(blurRadius)
                        .get();
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), byteBuffer);
            }
            runnable.run();
        } finally {
            this.used = false;
        }
    }

    public GpuBuffer getBuffer() {
        return this.getOrCreateBuffer();
    }

    public boolean isUsed() {
        return used;
    }

    private GpuBuffer getOrCreateBuffer() {
        if (this.buffer == null || this.buffer.isClosed()) {
            this.buffer = RenderSystem.getDevice().createBuffer(() -> "Opal UBO", 136, SIZE);
        }
        return this.buffer;
    }
}
