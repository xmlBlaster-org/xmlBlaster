/*------------------------------------------------------------------------------
Name:      XBBytesMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import javax.jms.BytesMessage;
import javax.jms.JMSException;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.MsgQosData;

/**
 * XBBytesMessage
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBBytesMessage extends XBMessage implements BytesMessage {
   private final static String ME = "XBBytesMessage";
   private ObjectInputStream is;
   private ObjectOutputStream os;
   
   /**
    * If the content is empty it will be considered a message for a producer, i.e. a message to
    * be filled and sent. If the content is not null, then it is assumed to be a message for a
    * consumer
    * @param global
    * @param key
    * @param content
    * @param qos
    */
   XBBytesMessage(Global global, MsgKeyData key, byte[] content, MsgQosData qos) throws JMSException {
      super(global, key, content, qos, XBMessage.BYTES);
      try {
         if (this.content != null) 
            is = new ObjectInputStream(new ByteArrayInputStream(this.content));
         else os = new ObjectOutputStream(new ByteArrayOutputStream());   
      }
      catch (IOException ex) {
         throw new JMSException(ME, "constructor: " + ex.getMessage());
      }
   }


   private void getterCheck(String methodName) throws JMSException {
      if (is == null) {
         throw new JMSException(ME, methodName + " this is a producer message (only setters allowed)");
      }
   }

   private void setterCheck(String methodName) throws JMSException {
      if (os == null) {
         throw new JMSException(ME, methodName + " this is a consumer message (only getters allowed)");
      }
   }


   public long getBodyLength() throws JMSException {
      return this.content.length;
   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#readBoolean()
    */
   public boolean readBoolean() throws JMSException {
      getterCheck("readBoolean");
      try {
         return this.is.readBoolean();
      }
      catch (IOException ex) {
         throw new JMSException(ME, "readBoolean: " + ex.getMessage());
      }
   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#readByte()
    */
   public byte readByte() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#readUnsignedByte()
    */
   public int readUnsignedByte() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#readShort()
    */
   public short readShort() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#readUnsignedShort()
    */
   public int readUnsignedShort() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#readChar()
    */
   public char readChar() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#readInt()
    */
   public int readInt() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#readLong()
    */
   public long readLong() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#readFloat()
    */
   public float readFloat() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#readDouble()
    */
   public double readDouble() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#readUTF()
    */
   public String readUTF() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#readBytes(byte[])
    */
   public int readBytes(byte[] arg0) throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#readBytes(byte[], int)
    */
   public int readBytes(byte[] arg0, int arg1) throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#writeBoolean(boolean)
    */
   public void writeBoolean(boolean arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#writeByte(byte)
    */
   public void writeByte(byte arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#writeShort(short)
    */
   public void writeShort(short arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#writeChar(char)
    */
   public void writeChar(char arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#writeInt(int)
    */
   public void writeInt(int arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#writeLong(long)
    */
   public void writeLong(long arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#writeFloat(float)
    */
   public void writeFloat(float arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#writeDouble(double)
    */
   public void writeDouble(double arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#writeUTF(java.lang.String)
    */
   public void writeUTF(String arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#writeBytes(byte[])
    */
   public void writeBytes(byte[] arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#writeBytes(byte[], int, int)
    */
   public void writeBytes(byte[] arg0, int arg1, int arg2)
      throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#writeObject(java.lang.Object)
    */
   public void writeObject(Object arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.BytesMessage#reset()
    */
   public void reset() throws JMSException {
      // TODO Auto-generated method stub

   }

   public static void main(String[] args) {
   }
}
