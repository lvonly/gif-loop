package me.kined.java.image.gif;

import com.google.common.io.ByteStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class GifFilter {
    private static final Logger logger = LoggerFactory.getLogger(GifFilter.class);

    private static OutputStream DUMMY_OUTPUT = new OutputStream() {
        @Override
        public void write(int b) throws IOException {
            // do nothing
        }
    };

    private InputStream in;
    private OutputStream out;
    private GifFilterListener listener;
    private ByteBuffer outBuffer;

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {
        private OutputStream out = DUMMY_OUTPUT;
        private GifFilterListener listener;

        public Builder setOutputStream(OutputStream out) {
            this.out = out;
            return this;
        }

        public Builder setListener(GifFilterListener listener) {
            this.listener = listener;
            return this;
        }

        public GifFilter build(InputStream in) {
            return new GifFilter(in, out, listener);
        }

        public GifFilter build(String fileName) throws FileNotFoundException {
            return new GifFilter(new FileInputStream(fileName), out, listener);
        }
    }

    private GifFilter(InputStream in, OutputStream out, GifFilterListener listener) {
        this.in = in;
        this.out = out;
        this.listener = listener;


    }

    private int readShort() throws IOException {
        return in.read() | (in.read() << 8);
    }

    private void writeShort(int value) throws IOException {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }

    public void filter() throws IOException {
        // id
        byte[] id = new byte[6];
        ByteStreams.readFully(in, id);

        String idString = new String(id);
        if (!idString.startsWith("GIF")) {
            throw new IOException("invalid id=" + idString);
        }
        out.write(id);



        // LSD (Logical Screen Descriptor)
        int width = readShort();
        int height = readShort();
        logger.debug("width={}, height={}", width, height);

        writeShort(width);
        writeShort(height);

        int packed = in.read();
        boolean gctFlag = (packed & 0x80) != 0; // 1   : global color table flag
        // 2-4 : color resolution
        // 5   : gct sort flag
        int gctSize = 2 << (packed & 7); // 6-8 : gct size
        out.write(packed);

        int bgIndex = in.read();
        out.write(bgIndex);
        int pixelAspect = in.read();
        out.write(pixelAspect);


        if (gctFlag) {
            readColorTable(gctSize);
        }


        int frame = 0;
        boolean seenAppExt = false;
        boolean done = false;
        while (!done) {
            int code = in.read();
            out.write(code);

            switch (code) {
                case 0x2C: // image separator
                    readImage();
                    logger.debug("Image Frame={}", frame++);
                    break;

                case 0x21: // extension
                    code = in.read();
                    switch (code) {
                        case 0xF9: // graphics control extension
                            if (!seenAppExt) {
                                writeNetscapeExtension();
                                seenAppExt = true;
                            }
                            out.write(code);
                            readGraphicControlExtension();
                            break;
                        case 0xFF: // application extension
                            logger.debug("application extention");
                            out.write(code);
                            byte[] block = readBlock();
                            if (block != null) {
                                out.write(block.length);
                                out.write(block);

                                String app = new String(block, 0, 11);
                                logger.debug("app={}", app);
                                if (app.equals("NETSCAPE2.0")) {
                                    seenAppExt = true;
                                    readNetscapeExtension();
                                } else {
                                    skip();
                                }
                            }
                            break;
                        default : // uninteresting extension
                            out.write(code);
                            skip();
                    }
                    break;
                case 0x3b: // terminator
                    done = true;
                    break;

                case 0x00: // bad byte, but keep going and see what happens
                    break;
                default:
                    throw new IOException("invalid code=" + code);

            }

        }
    }

    private void readGraphicControlExtension() throws IOException {
        logger.debug("readGraphicControlExtension");
        int blockSize = in.read(); // block size
        out.write(blockSize);

        int packed = in.read(); // packed fields
        out.write(packed);

        int delay = readShort() * 10; // delay in milliseconds
        if (listener != null) {
            delay = listener.onDelay(delay);
        }
        writeShort(delay / 10);

        int transIndex = in.read(); // transparent color index
        out.write(transIndex);

        int v = in.read(); // block terminator
        out.write(0);
    }

    private void writeNetscapeExtension() throws IOException {
        logger.debug("writeNetscapeExtension");
        out.write(0xFF);

        out.write(11);
        out.write("NETSCAPE2.0".getBytes());

        int loopCount = listener.onLoopCount(1);
        byte[] data = new byte[3];
        data[0] = 1;
        data[1] = (byte) (loopCount & 0xff);
        data[2] = (byte) ((loopCount >> 8) & 0xff);
        out.write(data.length);
        out.write(data);
        out.write(0);

        out.write(0x21);
    }

    private void readNetscapeExtension() throws IOException {
        logger.debug("readNetscapeExtension");
        while (true) {
            byte[] data = readBlock();
            if (data == null) {
                out.write(0);
                return;
            }
            if (data[0] == 1) {
                // loop count sub-block
                int b1 = ((int) data[1]) & 0xff;
                int b2 = ((int) data[2]) & 0xff;
                int loopCount = (b2 << 8) | b1;
                if (listener != null) {
                    loopCount = listener.onLoopCount(loopCount);
                }
                logger.debug("loopCount={}", loopCount);
                data[1] = (byte) (loopCount & 0xff);
                data[2] = (byte) ((loopCount >> 8) & 0xff);
            }
            logger.debug("data length={}", data.length);
            out.write(data.length);
            out.write(data);
        }
    }

    private void readImage() throws IOException {
        int x = readShort();
        int y = readShort();
        int width = readShort();
        int height = readShort();

        writeShort(x);
        writeShort(y);
        writeShort(width);
        writeShort(height);

        int packed = in.read();
        out.write(packed);
        boolean lctFlag = (packed & 0x80) != 0; // 1 - local color table flag
        // 3 - sort flag
        // 4-5 - reserved
        int lctSize = 2 << (packed & 7); // 6-8 - local color table size

        if (lctFlag) {
            readColorTable(lctSize); // read table
        }

        decodeImageData(); // decode pixel data
    }

    private void decodeImageData() throws IOException {
        int dataSize = in.read();
        out.write(dataSize);
        int size = skip();
        logger.debug("ImageData size={}", size);
    }

    private void readColorTable(int nColors) throws IOException {
        int nBytes = 3 * nColors;
        byte[] c = new byte[nBytes];
        ByteStreams.readFully(in, c);
        out.write(c);
    }

    private int skip() throws IOException {
        int skipBytes = 0;
        while (true) {
            byte[] data = readBlock();
            if (data == null) {
                out.write(0);
                return skipBytes;
            }
            skipBytes += data.length;
            out.write(data.length);
            out.write(data);
        }
    }

    private byte[] readBlock() throws IOException {
        int blockSize = in.read();
        if (blockSize == 0) {
            return null;
        }
        byte[] block = new byte[blockSize];
        ByteStreams.readFully(in, block);
        return block;
    }
}
