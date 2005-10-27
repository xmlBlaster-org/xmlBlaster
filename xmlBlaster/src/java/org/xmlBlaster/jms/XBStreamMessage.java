/*------------------------------------------------------------------------------
Name:      XBStreamMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.StreamMessage;

/**
 * XBStreamMessage
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBStreamMessage extends XBMessage implements StreamMessage {

   XBStreamMessage(XBSession session, byte[] content) {
      super(session, content, XBMessage.TEXT);
   }
   

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#readBoolean()
    */
   public boolean readBoolean() throws JMSException {
      // TODO Auto-generated method stub
      return false;
   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#readByte()
    */
   public byte readByte() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#readBytes(byte[])
    */
   public int readBytes(byte[] arg0) throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#readChar()
    */
   public char readChar() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#readDouble()
    */
   public double readDouble() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#readFloat()
    */
   public float readFloat() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#readInt()
    */
   public int readInt() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#readLong()
    */
   public long readLong() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#readObject()
    */
   public Object readObject() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#readShort()
    */
   public short readShort() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#readString()
    */
   public String readString() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#reset()
    */
   public void reset() throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#writeBoolean(boolean)
    */
   public void writeBoolean(boolean arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#writeByte(byte)
    */
   public void writeByte(byte arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#writeBytes(byte[])
    */
   public void writeBytes(byte[] arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#writeBytes(byte[], int, int)
    */
   public void writeBytes(byte[] arg0, int arg1, int arg2)
      throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#writeChar(char)
    */
   public void writeChar(char arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#writeDouble(double)
    */
   public void writeDouble(double arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#writeFloat(float)
    */
   public void writeFloat(float arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#writeInt(int)
    */
   public void writeInt(int arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#writeLong(long)
    */
   public void writeLong(long arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#writeObject(java.lang.Object)
    */
   public void writeObject(Object arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#writeShort(short)
    */
   public void writeShort(short arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.StreamMessage#writeString(java.lang.String)
    */
   public void writeString(String arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

}
