/*------------------------------------------------------------------------------
 Name:      XBBytesMessage.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MessageEOFException;
import javax.jms.MessageNotReadableException;
import javax.jms.MessageNotWriteableException;

import org.xmlBlaster.util.def.ErrorCode;

/**
 * XBBytesMessage
 * 
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * 
 */
public class XBBytesMessage extends XBMessage implements BytesMessage {
   private final static String ME = "XBBytesMessage";

   private DataInputStream dataIs;

   private DataOutputStream dataOs;

   private ByteArrayOutputStream baos;

   private ByteArrayInputStream bais;

   private boolean writeOnly;

   /**
    * true if the stream has reached its end. This is needed to return -1 when readBytes
    * is invoked on a passed stream.
    */
   private boolean streamEnded;
   
   public XBBytesMessage(XBSession session, byte[] content) throws JMSException {
      this(session, content, XBMessage.BYTES);
   }
   
   /**
    * If the content is empty it will be considered a message for a producer,
    * i.e. a message to be filled and sent. If the content is not null, then it
    * is assumed to be a message for a consumer
    * 
    * @param global
    * @param key
    * @param content
    * @param qos
    */
   XBBytesMessage(XBSession session, byte[] content, int type) throws JMSException {
      super(session, content, type);
      makeWriteable();
   }

   private void makeWriteable() throws JMSException {
      this.writeOnly = true;
      this.streamEnded = false;
      this.baos = new ByteArrayOutputStream(this.content == null ? 10000
            : this.content.length + 10000);
      try {
         this.dataOs = new DataOutputStream(baos);
         // this.objOs = new ObjectOutputStream(baos);
         if (this.content != null && content.length > 0)
            baos.write(content);
         this.dataIs = null;
         this.bais = null;
      } 
      catch (IOException ex) {
         throw new XBException(ex, "when creating a message");
      }
   }
   
   
   private void getterCheck(String methodName)
         throws MessageNotReadableException {
      if (this.writeOnly) {
         throw new MessageNotReadableException(ME
               + " writeonly message: not allowed to read on operation '"
               + methodName + "'");
      }
   }

   private void setterCheck(String methodName)
         throws MessageNotWriteableException {
      if (this.readOnly) {
         throw new MessageNotWriteableException("could not invoke '"
               + methodName + "' since the message is in readonly mode");
      }
   }

   public long getBodyLength() throws JMSException {
      getterCheck("getBodyLength");
      if (this.content == null) {
         if (this.dataOs != null)
            return this.dataOs.size();
         else
            return 0L;
      }
      return this.content.length;
   }

   private final void resetDataStream() throws JMSException {
      try {
         this.dataIs.reset();
      }
      catch (IOException ex) {
         throw new MessageEOFException(ME + ".resetDataStream: " + ex.getMessage(),
               ErrorCode.RESOURCE_UNAVAILABLE.getErrorCode());
      }
      
   }
   
   public boolean readBoolean() throws JMSException {
      getterCheck("readBoolean");
      try {
         this.dataIs.mark(10);
         return this.dataIs.readBoolean();
      }
      catch (EOFException ex) {
         throw new MessageEOFException(ME + ".readBoolean: " + ex.getMessage(),
               ErrorCode.USER_MESSAGE_INVALID.getErrorCode());
      }
      catch (IOException ex) {
         resetDataStream();
         throw new JMSException(ME + ".readBoolean: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public byte readByte() throws JMSException {
      getterCheck("readByte");
      try {
         this.dataIs.mark(10);
         return this.dataIs.readByte();
      }
      catch (EOFException ex) {
         throw new MessageEOFException(ME + ".readByte: " + ex.getMessage(),
               ErrorCode.USER_MESSAGE_INVALID.getErrorCode());
      }
      catch (IOException ex) {
         resetDataStream();
         throw new JMSException(ME + ".readByte: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public int readUnsignedByte() throws JMSException {
      getterCheck("readUnsignedByte");
      try {
         this.dataIs.mark(10);
         return this.dataIs.readUnsignedByte();
      }
      catch (EOFException ex) {
         throw new MessageEOFException(ME + ".readUnsignedByte: " + ex.getMessage(),
               ErrorCode.USER_MESSAGE_INVALID.getErrorCode());
      }
      catch (IOException ex) {
         resetDataStream();
         throw new JMSException(ME + ".readUnsignedByte: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public short readShort() throws JMSException {
      getterCheck("readShort");
      try {
         this.dataIs.mark(10);
         return this.dataIs.readShort();
      } 
      catch (EOFException ex) {
         throw new MessageEOFException(ME + ".readShort: " + ex.getMessage(),
               ErrorCode.USER_MESSAGE_INVALID.getErrorCode());
      }
      catch (IOException ex) {
         resetDataStream();
         throw new JMSException(ME + ".readShort: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public int readUnsignedShort() throws JMSException {
      getterCheck("readUnsignedShort");
      try {
         this.dataIs.mark(10);
         return this.dataIs.readUnsignedShort();
      }
      catch (EOFException ex) {
         throw new MessageEOFException(ME + ".readUnsignedShort: " + ex.getMessage(),
               ErrorCode.USER_MESSAGE_INVALID.getErrorCode());
      }
      catch (IOException ex) {
         resetDataStream();
         throw new JMSException(ME + ".readUnsignedShort: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public char readChar() throws JMSException {
      getterCheck("readChar");
      try {
         this.dataIs.mark(10);
         return this.dataIs.readChar();
      }
      catch (EOFException ex) {
         throw new MessageEOFException(ME + ".readChar: " + ex.getMessage(),
               ErrorCode.USER_MESSAGE_INVALID.getErrorCode());
      }
      catch (IOException ex) {
         resetDataStream();
         throw new JMSException(ME + ".readChar: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public int readInt() throws JMSException {
      getterCheck("readInt");
      try {
         this.dataIs.mark(10);
         return this.dataIs.readInt();
      }
      catch (EOFException ex) {
         throw new MessageEOFException(ME + ".readInt: " + ex.getMessage(),
               ErrorCode.USER_MESSAGE_INVALID.getErrorCode());
      }
      catch (IOException ex) {
         resetDataStream();
         throw new JMSException(ME + ".readInt: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public long readLong() throws JMSException {
      getterCheck("readLong");
      try {
         this.dataIs.mark(10);
         return this.dataIs.readLong();
      }
      catch (EOFException ex) {
         throw new MessageEOFException(ME + ".readLong: " + ex.getMessage(),
               ErrorCode.USER_MESSAGE_INVALID.getErrorCode());
      }
      catch (IOException ex) {
         resetDataStream();
         throw new JMSException(ME + ".readLong: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public float readFloat() throws JMSException {
      getterCheck("readFloat");
      try {
         this.dataIs.mark(10);
         return this.dataIs.readFloat();
      }
      catch (EOFException ex) {
         throw new MessageEOFException(ME + ".readFloat: " + ex.getMessage(),
               ErrorCode.USER_MESSAGE_INVALID.getErrorCode());
      }
      catch (IOException ex) {
         resetDataStream();
         throw new JMSException(ME + ".readFloat: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public double readDouble() throws JMSException {
      getterCheck("readDouble");
      try {
         this.dataIs.mark(10);
         return this.dataIs.readDouble();
      }
      catch (EOFException ex) {
         throw new MessageEOFException(ME + ".readDouble: " + ex.getMessage(),
               ErrorCode.USER_MESSAGE_INVALID.getErrorCode());
      }
      catch (IOException ex) {
         resetDataStream();
         throw new JMSException(ME + ".readDouble: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public String readUTF() throws JMSException {
      getterCheck("readUTF");
      try {
         this.dataIs.mark(this.content == null ? 10 : this.content.length);
         return this.dataIs.readUTF();
      }
      catch (EOFException ex) {
         throw new MessageEOFException(ME + ".readUTF: " + ex.getMessage(),
               ErrorCode.USER_MESSAGE_INVALID.getErrorCode());
      }
      catch (IOException ex) {
         resetDataStream();
         throw new JMSException(ME + ".readUTF: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public String readString() throws JMSException {
      getterCheck("readString");
      return readUTF();
   }

   public int readBytes(byte[] value) throws JMSException {
      return readBytes(value, value.length);
   }

   public Object readObject() throws JMSException {
      getterCheck("readObject");
      try {
         this.dataIs.mark(this.content == null ? 10 : this.content.length);
         /*
          * We can not use ObjectStream as a decorator around ByteArrayOutputStream since
          * it puts 4 bytes in the stream, so we wrap the object inside a pair of 
          * <int, byte[]> and serialize that
          */
         int size = this.dataIs.readInt();
         byte[] valAsBytes = new byte[size];
         this.dataIs.read(valAsBytes);
         ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(valAsBytes));
         return ois.readObject();
      }
      catch (EOFException ex) {
         throw new MessageEOFException(ME + ".readObject: " + ex.getMessage(),
               ErrorCode.USER_MESSAGE_INVALID.getErrorCode());
      }
      catch (ClassNotFoundException ex) {
         throw new XBException(ex, "readObject: the class was not found");
      }
      catch (IOException ex) {
         resetDataStream();
         throw new XBException(ex, "readObject: an IOException occured");
      }
   }   

   public int readBytes(byte[] value, int length) throws JMSException {
      getterCheck("readBytes");
      if (length < 0 || length > value.length)
         throw new IndexOutOfBoundsException(ME + ".readBytes: length='"
               + length + "' array length='" + value.length + "'");
      if (this.streamEnded)
         return -1;
      try {
         this.dataIs.mark(value.length);
         int size = 0;
         int offset = 0;
         int sum = 0;
         // while ((size = this.dataIs.available()) > 0) {
         while ((size = this.bais.available()) > 0) {
            if (offset + size > length)
               size = length - offset;
            if (size < 1)
               break;
            sum += this.dataIs.read(value, offset, size);
         }
         if (sum < length)
            this.streamEnded = true;
         return sum;
      }
      catch (EOFException ex) {
         throw new MessageEOFException(ME + ".readDouble: " + ex.getMessage(),
               ErrorCode.USER_MESSAGE_INVALID.getErrorCode());
      }
      catch (IOException ex) {
         resetDataStream();
         throw new JMSException(ME + ".readBytes: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeBoolean(boolean value) throws JMSException {
      setterCheck("writeBoolean");
      try {
         this.dataOs.writeBoolean(value);
      } catch (IOException ex) {
         throw new JMSException(ME + ".writeBoolean: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeByte(byte value) throws JMSException {
      setterCheck("writeByte");
      try {
         this.dataOs.writeByte(value);
      } catch (IOException ex) {
         throw new JMSException(ME + ".writeByte: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeShort(short value) throws JMSException {
      setterCheck("writeShort");
      try {
         this.dataOs.writeShort(value);
      } catch (IOException ex) {
         throw new JMSException(ME + ".writeShort: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeChar(char value) throws JMSException {
      setterCheck("writeChar");
      try {
         this.dataOs.writeChar(value);
      } catch (IOException ex) {
         throw new JMSException(ME + ".writeChar: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeInt(int value) throws JMSException {
      setterCheck("writeInt");
      try {
         this.dataOs.writeInt(value);
      } catch (IOException ex) {
         throw new JMSException(ME + ".writeInt: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeLong(long value) throws JMSException {
      setterCheck("writeLong");
      try {
         this.dataOs.writeLong(value);
      } catch (IOException ex) {
         throw new JMSException(ME + ".writeLong: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeFloat(float value) throws JMSException {
      setterCheck("writeFloat");
      try {
         this.dataOs.writeFloat(value);
      } catch (IOException ex) {
         throw new JMSException(ME + ".writeFloat: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeDouble(double value) throws JMSException {
      setterCheck("writeDouble");
      try {
         this.dataOs.writeDouble(value);
      } catch (IOException ex) {
         throw new JMSException(ME + ".writeDouble: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeUTF(String value) throws JMSException {
      setterCheck("writeUTF");
      if (value == null)
         throw new NullPointerException(ME + ".writeUTF");
      try {
         this.dataOs.writeUTF(value);
      } catch (IOException ex) {
         throw new JMSException(ME + ".writeUTF: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeString(String value) throws JMSException {
      setterCheck("writeString");
      if (value == null)
         throw new NullPointerException(ME + ".writeString");
      try {
         this.dataOs.writeUTF(value);
      } catch (IOException ex) {
         throw new JMSException(ME + ".writeString: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void writeBytes(byte[] value) throws JMSException {
      writeBytes(value, 0, value.length);
   }

   public void writeBytes(byte[] value, int offset, int length)
         throws JMSException {
      setterCheck("writeBytes");
      if (value == null)
         throw new NullPointerException(ME + ".writeBytes");
      try {
         this.dataOs.write(value, offset, length);
      } 
      catch (IOException ex) {
         throw new JMSException(ME + ".writeBytes: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   
   public void writeObject(Object value) throws JMSException {
      setterCheck("writeObject");
      if (value == null)
         throw new NullPointerException(ME + ".writeObject");
      try {
         /*
          * We can not use ObjectStream as a decorator around ByteArrayOutputStream since
          * it puts 4 bytes in the stream, so we wrap the object inside a pair of 
          * <int, byte[]> and serialize that
          */
         ByteArrayOutputStream tmp = new ByteArrayOutputStream();
         ObjectOutputStream oos = new ObjectOutputStream(tmp);
         oos.writeObject(value);
         byte[] valAsBytes = tmp.toByteArray();
         this.dataOs.writeInt(valAsBytes.length);
         this.dataOs.write(valAsBytes);
      } 
      catch (IOException ex) {
         throw new JMSException(ME + ".writeObject: " + ex.getMessage(),
               ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public synchronized void reset() throws JMSException {
      if (this.writeOnly) {
         this.content = this.baos.toByteArray();
      }
      this.bais = new ByteArrayInputStream(this.content);
      this.dataIs = new DataInputStream(this.bais);
      if (!this.dataIs.markSupported())
         throw new XBException(ErrorCode.INTERNAL_NOTIMPLEMENTED.getDescription(), "Mark is not supported for the stream. Can not recover in case of an IOException");
      
      this.readOnly = true;
      this.writeOnly = false;
      this.baos = null;
      this.dataOs = null;
   }

   public void clearBody() throws JMSException {
      super.clearBody();
      makeWriteable();
   }
}
