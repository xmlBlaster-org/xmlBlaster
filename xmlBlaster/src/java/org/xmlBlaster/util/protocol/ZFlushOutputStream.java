package org.xmlBlaster.util.protocol;

import java.io.IOException;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZStream;

/**
* @author Balázs Póka
*/
public class ZFlushOutputStream extends java.io.FilterOutputStream {
    
    public static final int MAXBUFFERSIZE=20000;
    
    private byte[] buffer;
    private byte[] compBuffer;
    private int writeIndex;
    private ZStream deflater;

    public ZFlushOutputStream(java.io.OutputStream out) {
        super(out);
        buffer=new byte[MAXBUFFERSIZE];
        compBuffer=new byte[MAXBUFFERSIZE];
        writeIndex=0;
        deflater=new ZStream();
        deflater.deflateInit(JZlib.Z_BEST_COMPRESSION);
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
        if (writeIndex == 0) return;
        
        deflater.next_in=buffer;
        deflater.next_in_index=0;
        deflater.avail_in=writeIndex;
        deflater.next_out=compBuffer;
        deflater.next_out_index=0;
        deflater.avail_out=compBuffer.length;
        while (deflater.avail_in>0) {
            int status=deflater.deflate(JZlib.Z_PARTIAL_FLUSH);
            if (status!=JZlib.Z_OK) {
                System.out.println("error1 deflate");
            }
            if (deflater.avail_out==0) {
                super.out.write(compBuffer, 0, compBuffer.length);
                deflater.next_out_index=0;
                deflater.avail_out=compBuffer.length;                
                compSize+=compBuffer.length;
            }
        }
        int lastCompSize=compBuffer.length-deflater.avail_out;
        compSize+=lastCompSize;
        
        super.out.write(compBuffer, 0, lastCompSize);
        super.out.flush();

        if (compSize <= 0) {
            throw new IOException("Compression exception, got 0 bytes output");
        }
        
        writeIndex = 0;
    }
    
    public synchronized float getCompressionRatio() {
        return (float)deflater.total_out/deflater.total_in;
    }

}
