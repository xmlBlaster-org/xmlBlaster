package org.xmlBlaster.util.protocol;

import java.io.IOException;
import java.util.zip.Deflater;

/**
* @author Patrice Espié
* @author modifications by Balázs Póka
* Licensing: LGPL
*/
public class ZBlockOutputStream extends java.io.FilterOutputStream {
    
    public static final int MAXBUFFERSIZE=20000;
    private int minCompress;
    
    private byte[] buffer;
    private byte[] compBuffer;
    private int writeIndex;
    private Deflater deflater;

    public ZBlockOutputStream(java.io.OutputStream out, int minCompress) {
        super(out);
        this.minCompress=minCompress;
        buffer=new byte[MAXBUFFERSIZE];
        compBuffer=new byte[MAXBUFFERSIZE];
        writeIndex=0;
        deflater=new Deflater(Deflater.BEST_COMPRESSION);
    }

    public synchronized void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public synchronized void write(byte[] b, int off, int len) throws IOException {
        int written=0;
        
        while(written < len) {
            if (writeIndex == buffer.length) {
                flush();
            }

            int toWrite = Math.min(len - written, buffer.length - writeIndex);
            System.arraycopy(b, off + written, buffer, writeIndex, toWrite);
            written += toWrite;
            writeIndex += toWrite;
        }
    }

    public synchronized void write(int b) throws IOException {
        if (writeIndex == buffer.length) {
            flush();
        }

        buffer[writeIndex++] = (byte)b;
    }

    public synchronized void flush() throws IOException {
        int compSize = 0;
        boolean sendCompressed;
        if (writeIndex == 0) return;

        if (writeIndex >= minCompress) {
            deflater.reset();
            deflater.setInput(buffer, 0, writeIndex);
            deflater.finish();

/*            if (compBuffer.length < writeIndex * 2 + 40960) {
//                System.out.println("OUT1 allocating "+(writeIndex * 2 + 40960));
                compBuffer = new byte[writeIndex * 2 + 40960];
            }
*/
            compSize = deflater.deflate(compBuffer);

            if (compSize <= 0) {
                throw new IOException("Compression exception, got 0 bytes output");
            }

            sendCompressed = compSize < writeIndex;
        } else {
            sendCompressed = false;
        }

        if (sendCompressed) {
//            System.out.println("Sending compressed "+writeIndex+"->"+compSize+" bytes");
            super.out.write(1);
            super.out.write(writeIndex >> 24 & 0xff);
            super.out.write(writeIndex >> 16 & 0xff);
            super.out.write(writeIndex >> 8 & 0xff);
            super.out.write(writeIndex & 0xff);
            super.out.write(compSize >> 24 & 0xff);
            super.out.write(compSize >> 16 & 0xff);
            super.out.write(compSize >> 8 & 0xff);
            super.out.write(compSize & 0xff);
            super.out.write(compBuffer, 0, compSize);
            super.out.flush();
            writeIndex = 0;
        }
        else {
//            System.out.println("Sending uncompressed "+writeIndex+" bytes");
            super.out.write(0);
            super.out.write(writeIndex >> 24 & 0xff);
            super.out.write(writeIndex >> 16 & 0xff);
            super.out.write(writeIndex >> 8 & 0xff);
            super.out.write(writeIndex & 0xff);
            super.out.write(buffer, 0, writeIndex);
            super.out.flush();
            writeIndex = 0;
        }
    }

}
