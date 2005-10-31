/*------------------------------------------------------------------------------
Name:      XBBytesMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ObjectOutputStream;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;

import org.xmlBlaster.util.def.ErrorCode;

/**
 * XBBytesMessage
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBBytesMessage extends XBMessage implements BytesMessage {
   private final static String ME = "XBBytesMessage";
   private DataInputStream is;
   private DataOutputStream os;
   private ByteArrayOutputStream baos;
   
   /**
    * If the content is empty it will be considered a message for a producer, i.e. a message to
    * be filled and sent. If the content is not null, then it is assumed to be a message for a
    * consumer
    * @param global
    * @param key
    * @param content
    * @param qos
    */
   XBBytesMessage(XBSession session, byte[] content) throws JMSException {
      super(session, content, XBMessage.BYTES);
      if (this.content != null) {
         // this.readOnly = true; 
         // this.writeOnly = false;
         this.is = new DataInputStream(new ByteArrayInputStream(this.content));
      }
      else {
         // this.readOnly = false;
         // this.writeOnly = true;
         this.baos = new ByteArrayOutputStream(); 
         this.os = new DataOutputStream(this.baos);   
      }
   }

   private void getterCheck(String methodName) throws MessageNotReadableException {
      if (this.writeOnly) {
         throw new MessageNotReadableException(ME + " writeonly message: not allowed to read on operation '" + methodName + "'");
      }
   }

   private void setterCheck(String methodName) throws MessageNotWriteableException {
      if (this.readOnly) {
         throw new MessageNotWriteableException("could not invoke '" + methodName + "' since the message is in readonly mode"); 
      }
   }

   public long getBodyLength() throws JMSException {
      if (this.content == null) {
         if (this.os != null)
            return this.os.size();
         else return 0L;
      }
      return this.content.length;
   }

   public boolean readBoolean() throws JMSException {
      getterCheck("readBoolean");
      try {
         return this.is.readBoolean();
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".readBoolean: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public byte readByte() throws JMSException {
      getterCheck("readByte");
      try {
         return this.is.readByte();
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".readByte: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public int readUnsignedByte() throws JMSException {
      getterCheck("readUnsignedByte");
      try {
         return this.is.readUnsignedByte();
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".readUnsignedByte: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public short readShort() throws JMSException {
      getterCheck("readShort");
      try {
         return this.is.readShort();
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".readShort: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public int readUnsignedShort() throws JMSException {
      getterCheck("readUnsignedShort");
      try {
         return this.is.readUnsignedShort();
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".readUnsignedShort: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public char readChar() throws JMSException {
      getterCheck("readChar");
      try {
         return this.is.readChar();
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".readChar: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public int readInt() throws JMSException {
      getterCheck("readInt");
      try {
         return this.is.readInt();
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".readInt: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public long readLong() throws JMSException {
      getterCheck("readLong");
      try {
         return this.is.readLong();
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".readLong: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public float readFloat() throws JMSException {
      getterCheck("readFloat");
      try {
         return this.is.readFloat();
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".readFloat: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public double readDouble() throws JMSException {
      getterCheck("readDouble");
      try {
         return this.is.readDouble();
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".readDouble: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public String readUTF() throws JMSException {
      getterCheck("readUTF");
      try {
         return this.is.readUTF();
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".readUTF: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public int readBytes(byte[] value) throws JMSException {
      return readBytes(value, value.length);
   }

   /**
    * TODO fix this with an own stream implementation since here a double copy of the
    * entire array is done.
    * 
    * @param value
    * @param length
    * @throws JMSException
    */
   int readWrittenBytes(byte[] value, int length) throws JMSException {
      if (this.baos == null)
         throw new XBException("internal", "XBBytesMessage.readWrittenBytes: the output stream is null, can nor read written data");
      byte[] tmpContent = this.baos.toByteArray();
      if (tmpContent.length < length)
         length = tmpContent.length;
      for(int i=0; i < length; i++)
         value[i] = tmpContent[i];
      return length; 
   }
   
   
   public int readBytes(byte[] value, int length) throws JMSException {
      getterCheck("readBytes");
      if (length < 0 || length > value.length)
         throw new IndexOutOfBoundsException(ME + ".readBytes: length='" + length + "' array length='" + value.length + "'");
      if (this.is == null)
         return readWrittenBytes(value, length);
      try {
         int size = 0;
         int offset = 0;
         while ( (size=this.is.available()) > 0) {
            if (offset + size > length) size = length - offset;
            if (size < 1) break;
            this.is.read(value, offset, size);
         }
         int sum = size + offset; 
         if (sum < 1) return -1;
         return sum;
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".readBytes: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeBoolean(boolean value) throws JMSException {
      setterCheck("writeBoolean");
      try {
         this.os.writeBoolean(value);
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".writeBoolean: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeByte(byte value) throws JMSException {
      setterCheck("writeByte");
      try {
         this.os.writeByte(value);
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".writeByte: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeShort(short value) throws JMSException {
      setterCheck("writeShort");
      try {
         this.os.writeShort(value);
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".writeShort: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeChar(char value) throws JMSException {
      setterCheck("writeChar");
      try {
         this.os.writeChar(value);
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".writeChar: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeInt(int value) throws JMSException {
      setterCheck("writeInt");
      try {
         this.os.writeInt(value);
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".writeInt: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeLong(long value) throws JMSException {
      setterCheck("writeLong");
      try {
         this.os.writeLong(value);
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".writeLong: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeFloat(float value) throws JMSException {
      setterCheck("writeFloat");
      try {
         this.os.writeFloat(value);
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".writeFloat: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeDouble(double value) throws JMSException {
      setterCheck("writeDouble");
      try {
         this.os.writeDouble(value);
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".writeDouble: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeUTF(String value) throws JMSException {
      setterCheck("writeUTF");
      if (value == null) throw new NullPointerException(ME + ".writeUTF");
      try {
         this.os.writeUTF(value);
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".writeUTF: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeBytes(byte[] value) throws JMSException {
      setterCheck("writeBytes");
      if (value == null) throw new NullPointerException(ME + ".writeBytes");
      try {
         this.os.write(value);
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".writeBytes: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeBytes(byte[] value, int offset, int length)
      throws JMSException {
      setterCheck("writeBytes");
      if (value == null) throw new NullPointerException(ME + ".writeBytes");
      try {
         this.os.write(value, offset, length);
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".writeBytes: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeObject(Object value) throws JMSException {
      setterCheck("writeObject");
      if (value == null) throw new NullPointerException(ME + ".writeObject");
      try {
         ObjectOutputStream oo = new ObjectOutputStream(this.os);
         oo.writeObject(value);
      }
      catch (IOException ex) {
         throw new JMSException(ME + ".writeObject: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void reset() throws JMSException {
      if (this.writeOnly) {
         this.writeOnly = false;
         this.readOnly = true;
         try {
            this.content = this.baos.toByteArray();
            this.baos.close();
            this.os = null;
            this.baos = null;
            if (this.content != null) {
               this.readOnly = true; 
               this.writeOnly = false;
               this.is = new DataInputStream(new ByteArrayInputStream(this.content));
            }
            else {
               throw new JMSException(ME + ".reset: content was null", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
            }
         }
         catch (IOException ex) {
            throw new JMSException(ME + ".reset: " + ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
         }
      }
   }
}
