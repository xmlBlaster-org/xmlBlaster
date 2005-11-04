/*
 * @(#)ByteArray.java       1.45 01/12/03
 *
 * Copyright 2002 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package org.xmlBlaster.util.xbformat;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * <b>
 * Derived from Suns ByteArrayOutputStream,
 * added method insert() and toByteArray(int len). 
 * Removed synchronized - this is not thread save anymore but better
 * performing!
 * </b>
 * <br />
 * <br />
 * This class implements an output stream in which the data is 
 * written into a byte array. The buffer automatically grows as data 
 * is written to it. 
 * The data can be retrieved using <code>toByteArray()</code> and
 * <code>toString()</code>.
 * <p>
 * Closing a <tt>ByteArray</tt> has no effect. The methods in
 * this class can be called after the stream has been closed without
 * generating an <tt>IOException</tt>.
 *
 * @author  Arthur van Hoff
 * @version 1.45, 12/03/01
 * @since   JDK1.0
 */

public class ByteArray extends OutputStream {

    /** 
     * The buffer where data is stored. 
     */
    protected byte buf[];

    /**
     * The number of valid bytes in the buffer. 
     */
    protected int count;

    /**
     * Creates a new byte array output stream. The buffer capacity is 
     * initially 32 bytes, though its size increases if necessary. 
     */
    public ByteArray() {
        this(32);
    }

    /**
     * Creates a new byte array output stream, with a buffer capacity of 
     * the specified size, in bytes. 
     *
     * @param   size   the initial size.
     * @exception  IllegalArgumentException if size is negative.
     */
    public ByteArray(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("Negative initial size: "
                                               + size);
        }
        buf = new byte[size];
    }

    /**
     * xmlBlaster extension: Read all from is into a byte[] until EOF==-1 arrives.  
     * @param size
     * @param is
     */
    public ByteArray(int size, InputStream is) throws IOException {
       if (size < 0) {
           throw new IllegalArgumentException("Negative initial size: "
                                              + size);
       }
       buf = new byte[size];
       
       
       byte[] b = new byte[size];
       int c;
       while ((c = is.read(b, 0, size)) != -1) {
          write(b, 0, c);
       }
   }

    /**
     * Get from current position the len bytes
     */
    public byte[] toByteArray(int len) {
        
        byte newbuf[] = new byte[len];
        System.arraycopy(buf, count, newbuf, 0, len);
        count += len;
        return newbuf;
    }

    /**
     * Get the inner byte array buffer, handle with care. 
     * AWARE: The buf.length is usually longer than size() and
     * you should not access past the size() end.
     */
    public byte[] getByteArray() {
        return buf;
    }

    /**
     * Insert byte at position
     */
    public void insert(int index, byte b) {
        if (index < 0 || index >= buf.length) {
            throw new IllegalArgumentException("Index is too small or too big: " + index);
        }
        buf[index] = b;
    }

    /**
     * Insert byte at position
     */
    public void insert(int index, byte[] b) {
        if (index < 0 || (index+b.length) >= buf.length) {
            throw new IllegalArgumentException("Index is too small or too big: " + index);
        }
        for (int ii=0; ii<b.length; ii++) {
           buf[index] = b[ii];
           index++;
        }
    }

    /**
     * Writes the specified byte to this byte array output stream. 
     *
     * @param   b   the byte to be written.
     */
    public final void write(int b) {
        int newcount = count + 1;
        if (newcount > buf.length) {
            byte newbuf[] = new byte[Math.max(buf.length << 1, newcount)];
            System.arraycopy(buf, 0, newbuf, 0, count);
            buf = newbuf;
        }
        buf[count] = (byte)b;
        count = newcount;
    }

    /**
     * Writes <code>len</code> bytes from the specified byte array 
     * starting at offset <code>off</code> to this byte array output stream.
     *
     * @param   b     the data.
     * @param   off   the start offset in the data.
     * @param   len   the number of bytes to write.
     */
    public final void write(byte b[], int off, int len) {
        if ((off < 0) || (off > b.length) || (len < 0) ||
            ((off + len) > b.length) || ((off + len) < 0)) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }
        int newcount = count + len;
        if (newcount > buf.length) {
            byte newbuf[] = new byte[Math.max(buf.length << 1, newcount)];
            System.arraycopy(buf, 0, newbuf, 0, count);
            buf = newbuf;
        }
        System.arraycopy(b, off, buf, count, len);
        count = newcount;
    }

    /**
     * Writes the complete contents of this byte array output stream to 
     * the specified output stream argument, as if by calling the output 
     * stream's write method using <code>out.write(buf, 0, count)</code>.
     *
     * @param      out   the output stream to which to write the data.
     * @exception  IOException  if an I/O error occurs.
     */
    public void writeTo(OutputStream out) throws IOException {
        out.write(buf, 0, count);
    }

    /**
     * Resets the <code>count</code> field of this byte array output 
     * stream to zero, so that all currently accumulated output in the 
     * ouput stream is discarded. The output stream can be used again, 
     * reusing the already allocated buffer space. 
     */
    public void reset() {
        count = 0;
    }

    /**
     * Creates a newly allocated byte array. Its size is the current 
     * size of this output stream and the valid contents of the buffer 
     * have been copied into it. 
     *
     * @return  the current contents of this output stream, as a byte array.
     */
    public byte[] toByteArray() {
        byte newbuf[] = new byte[count];
        System.arraycopy(buf, 0, newbuf, 0, count);
        return newbuf;
    }

    /**
     * Returns the current size of the buffer.
     *
     * @return  the value of the <code>count</code> field, which is the number
     *          of valid bytes in this output stream.
     */
    public int size() {
        return count;
    }

    /**
     * Converts the buffer's contents into a string, translating bytes into
     * characters according to the platform's default character encoding.
     *
     * @return String translated from the buffer's contents.
     * @since   JDK1.1
     */
    public String toString() {
        return new String(buf, 0, count);
    }

    /**
     * Converts the buffer's contents into a string, translating bytes into
     * characters according to the specified character encoding.
     *
     * @param   enc  a character-encoding name.
     * @return String translated from the buffer's contents.
     * @throws UnsupportedEncodingException
     *         If the named encoding is not supported.
     * @since   JDK1.1
     */
    public String toString(String enc) throws UnsupportedEncodingException {
        return new String(buf, 0, count, enc);
    }

    /**
     * Closing a <tt>ByteArray</tt> has no effect. The methods in
     * this class can be called after the stream has been closed without
     * generating an <tt>IOException</tt>.
     * <p>
     *
     */
    public void close() throws IOException {
    }

}
