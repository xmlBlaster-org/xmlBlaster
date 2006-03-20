package org.xmlBlaster.util.protocol;

import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;


/**
* @author Patrice Espié
* @author modifications by Balázs Póka
* Licensing: LGPL
*/
public class ZBlockInputStream extends java.io.FilterInputStream {

    private byte[] buffer;
    private byte[] compBuffer;
    private int readIndex;
    private int maxReadIndex;
    private Inflater inflater;

    public ZBlockInputStream(java.io.InputStream in) {
        super(in);
        buffer = new byte[ZBlockOutputStream.MAXBUFFERSIZE];
        compBuffer = new byte[ZBlockOutputStream.MAXBUFFERSIZE];
        readIndex = 0;
        maxReadIndex = 0;
        inflater = new Inflater();
    }

    public boolean markSupported() {
        return false;
    }

    public int available() throws IOException {
        if (maxReadIndex - readIndex == 0 && super.in.available()>0 && !readNextBuffer()) {
            return -1;
        } else {
            return maxReadIndex - readIndex;
        }
    }

    public int read() throws IOException {
        if (maxReadIndex == readIndex && !readNextBuffer()) {
            return -1;
        }

        byte b = buffer[readIndex++];

        if (b < 0) {
//            System.out.println("INPUT "+Integer.toHexString(b+256));
            return 256 + b;
        } else {
//            System.out.println("INPUT "+Integer.toHexString(b));
            return b;
        }
    }

    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    public int read(byte[] b, int off, int len) throws IOException {
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

    /**
     * This method is only called when the buffer is empty, otherwise bytes would be lost.
     * @return
     * @throws IOException
     */
    private boolean readNextBuffer() throws IOException {
        int compressionFlag = -1;
        compressionFlag = super.in.read();
        if (compressionFlag == -1) return false;

//        System.out.println("compflag "+compressionFlag);
        if (compressionFlag < 0 || compressionFlag > 1) {
            throw new IOException("Invalid stream input encountered.");
        }

        int newReadIndex = super.in.read() & 0xff;
        newReadIndex = newReadIndex << 8 | super.in.read() & 0xff;
        newReadIndex = newReadIndex << 8 | super.in.read() & 0xff;
        newReadIndex = newReadIndex << 8 | super.in.read() & 0xff;
        
        int compSize=0;
        if (compressionFlag == 1) {
            compSize = super.in.read() & 0xff;
            compSize = compSize << 8 | super.in.read() & 0xff;
            compSize = compSize << 8 | super.in.read() & 0xff;
            compSize = compSize << 8 | super.in.read() & 0xff;
        }
        
//        System.out.println("reading "+(compressionFlag==1 ? Integer.toString(compSize)+" compressed, " : "")
//                +newReadIndex+" deflated bytes");

        if (newReadIndex < ZBlockOutputStream.MAXBUFFERSIZE && compSize < ZBlockOutputStream.MAXBUFFERSIZE) {
            // seems to be ok...
            maxReadIndex=newReadIndex;
/*            if (buffer.length < maxReadIndex) {
                System.out.println("IN1 allocating "+(maxReadIndex + 40960));
                buffer = new byte[maxReadIndex + 40960];
            }*/

            if (compressionFlag == 1) {
/*                if (compBuffer.length < compSize) {
                    System.out.println("IN2 allocating "+(maxReadIndex + 40960));
                    compBuffer = new byte[compSize + 40960];
                }*/
                
                int read = 0;
                while (read < compSize) {
                    read += super.in.read(compBuffer, read, compSize - read);
                }
                
                inflater.reset();
                inflater.setInput(compBuffer, 0, compSize);
                
                try {
                    int output=inflater.inflate(buffer);
                    if (output!=newReadIndex) {
                        throw new IOException("Bad uncompressed size");
                    }
                } catch (DataFormatException ex) {
                    throw new IOException("Data format exception");
                }
            } else if (compressionFlag == 0) {
                int read = 0;
                while (read < maxReadIndex) {
                    read += super.in.read(buffer, read, maxReadIndex - read);
                }
            }
            readIndex = 0;
            return true;
        }
        else {
            throw new IOException("Invalid chunk length encountered.");
        }
    }

}
