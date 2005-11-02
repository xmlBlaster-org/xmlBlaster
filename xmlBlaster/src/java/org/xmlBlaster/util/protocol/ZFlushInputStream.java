package org.xmlBlaster.util.protocol;

import java.io.IOException;

import com.jcraft.jzlib.JZlib;
import com.jcraft.jzlib.ZStream;

/**
* @author Balázs Póka
*/
public class ZFlushInputStream extends java.io.FilterInputStream  {

    private byte[] buffer;
    private byte[] compBuffer;
    private int compWriteIndex;
    private int compReadIndex;
    private int readIndex;
    private int maxReadIndex;
    private ZStream inflater;

    public final static int RECBUFSIZE=20000;
    
    public ZFlushInputStream(java.io.InputStream in) {
        super(in);
        buffer=new byte[RECBUFSIZE];
        compBuffer=new byte[RECBUFSIZE];
        readIndex=0;
        maxReadIndex=0;
        compWriteIndex=0;
        compReadIndex=0;
        inflater=new ZStream();
        inflater.inflateInit();
    }

    public boolean markSupported() {
        return false;
    }

    public synchronized int available() throws IOException {
        if (maxReadIndex - readIndex == 0 && super.in.available()>0 && !readNextBuffer()) {
            return -1;
        } else {
            return maxReadIndex - readIndex;
        }
    }

    public synchronized int read() throws IOException {
        if (maxReadIndex == readIndex && !readNextBuffer()) {
            return -1;
        }

        byte b = buffer[readIndex++];

        if (b < 0) {
            return 256 + b;
        } else {
            return b;
        }
    }

    public synchronized int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    public synchronized int read(byte[] b, int off, int len) throws IOException {
        if (maxReadIndex == readIndex && !readNextBuffer()) {
            return -1;
        }

        int read = 0;
        int i = 0;
        while (i < len && readIndex < maxReadIndex) {
            b[off + i] = buffer[readIndex++];
            i++;
            read++;
        }

        return read;
    }
    
    public synchronized float getCompressionRatio() {
        return (float)inflater.total_in/inflater.total_out;
    }

    /**
     * This method is only called when the buffer is empty, otherwise bytes would be lost.
     * @return
     * @throws IOException
     */
    private boolean readNextBuffer() throws IOException {
        inflater.next_out=buffer;
        inflater.next_out_index=0;
        inflater.avail_out=buffer.length;
        
        do {
            // remaining output in buffer
            if (maxReadIndex==buffer.length) {
                inflateBuffer();
                if (maxReadIndex>0) break;
            }

            while ((compWriteIndex-compReadIndex==0) || (in.available()>0 && compWriteIndex<compBuffer.length)) {
                int res=in.read(compBuffer, compWriteIndex, compBuffer.length - compWriteIndex);
//                System.out.println("read="+res);
                if (res>0) {
                    compWriteIndex+=res;
                }
                else if (res<=0) {
                    return false;
                }
            }
            inflateBuffer();
        } while (maxReadIndex==0);

        readIndex=0;
//        System.out.println("compReadIndex="+compReadIndex+" compWriteIndex="+compWriteIndex+" maxReadIndex="+maxReadIndex);
        return true;
    }
    
    private void inflateBuffer() throws IOException {
        inflater.next_in=compBuffer;
        inflater.next_in_index=compReadIndex;
        inflater.avail_in=compWriteIndex-compReadIndex;
        do {
            int status=inflater.inflate(JZlib.Z_PARTIAL_FLUSH);
            if (status!=JZlib.Z_DATA_ERROR) {
                // no more data in inflater.
            }
            else if (status!=JZlib.Z_OK) {
                throw new IOException("JZlib error: "+status);
            }
        } while (inflater.avail_out>0 && inflater.avail_in>0);
        maxReadIndex=buffer.length-inflater.avail_out;
        if (inflater.avail_in==0) {
            compReadIndex=0;
            compWriteIndex=0;
        }
        else
            compReadIndex=inflater.next_in_index;        
    }

}
