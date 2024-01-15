package testing;

import org.openjdk.jmh.annotations.*;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.file.StandardOpenOption.*;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 3, time = 3)
@Fork(value = 1, jvmArgsPrepend = {"--enable-preview"})
public class MSReadAlignment {

    private float[] a, b;

    private MemorySegment segment;

    @Param({"1", "4", "6", "8", "13", "16", "25", "32", "64", "100", "128", "207", "256", "300", "512", "702", "1024"})
    //@Param({"1", "4", "6", "8", "13", "16", "25", "32", "64", "100" })
    //@Param({"702", "1024"})
    //@Param({"16", "32", "64"})
    int size;

    @Setup(Level.Trial)
    public void init() throws IOException {
        float[] tmpA = new float[size];
        float[] tmpB = new float[size];
        for (int i = 0; i < size; ++i) {
            tmpA[i] = ThreadLocalRandom.current().nextFloat();
            tmpB[i] = ThreadLocalRandom.current().nextFloat();
        }
        Path p = Path.of("vector.data");
        try (FileChannel fc = FileChannel.open(p, CREATE, READ, WRITE)) {
            ByteBuffer buf = ByteBuffer.allocate(size * 2 * Float.BYTES);
            buf.order(LITTLE_ENDIAN);
            buf.asFloatBuffer().put(0, tmpA);
            buf.asFloatBuffer().put(size, tmpB);
            int n = fc.write(ByteBuffer.wrap(new byte[] { 0x00 }));
            if (n != 1) {
                throw new AssertionError("expected n=1, got:" + n);
            }
            n = fc.write(buf);
            if (n != size * 2 * Float.BYTES) {
                throw new AssertionError("expected n=" + size * 2 * Float.BYTES + ", got:" + n);
            }

            Arena arena = Arena.global();
            segment = fc.map(FileChannel.MapMode.READ_ONLY, 0, size * 2L * Float.BYTES + 1, arena);
        }

        // Thread local buffers
        a = new float[size];
        b = new float[size];

//        float[] f1 = readAligned();
//        float[] f2 = readUnaligned();
//        if (Arrays.equals(f1, f2) == false) {
//            throw new AssertionError("arrays not equal:\n" + Arrays.toString(f1) + "\n" + Arrays.toString(f2));
//        }
    }


    static final ValueLayout.OfFloat LAYOUT_LE_FLOAT =
            ValueLayout.JAVA_FLOAT.withOrder(ByteOrder.LITTLE_ENDIAN);

    static final ValueLayout.OfFloat LAYOUT_LE_FLOAT_UNALIGNED =
            ValueLayout.JAVA_FLOAT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);

    @Benchmark
    public float[] readAligned() {
        MemorySegment.copy(segment, LAYOUT_LE_FLOAT, 0, a, 0, size);
        return a;
    }

    @Benchmark
    public float[] readUnaligned() {
        MemorySegment.copy(segment, LAYOUT_LE_FLOAT_UNALIGNED, 1, b, 0, size);
        return b;
    }

    public static void main(String... args) throws Exception {
        var x = new MSReadAlignment();
        x.size = 1024;
        x.init();
        x.readAligned();
        x.readUnaligned();
    }
}
