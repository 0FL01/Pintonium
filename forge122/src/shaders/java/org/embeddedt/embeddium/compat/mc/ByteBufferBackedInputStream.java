package org.embeddedt.embeddium.compat.mc;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class ByteBufferBackedInputStream extends InputStream {
    private final ByteBuffer buffer;

    public ByteBufferBackedInputStream(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public int read() throws IOException {
        if (!this.buffer.hasRemaining()) {
            return -1;
        }

        return this.buffer.get() & 0xFF;
    }

    @Override
    public int read(byte @NotNull [] bytes, int offset, int length) throws IOException {
        if (!this.buffer.hasRemaining()) {
            return -1;
        }

        length = Math.min(length, this.buffer.remaining());
        this.buffer.get(bytes, offset, length);
        return length;
    }
}
