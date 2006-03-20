/*------------------------------------------------------------------------------
Name:      XBMapMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.util.Enumeration;

import javax.jms.JMSException;
import javax.jms.MapMessage;

/**
 * XBMapMessage
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBMapMessage extends XBMessage implements MapMessage {

   XBMapMessage(XBSession session, byte[] content) {
      super(session, content, XBMessage.MAP);
   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#getBoolean(java.lang.String)
    */
   public boolean getBoolean(String arg0) throws JMSException {
      // TODO Auto-generated method stub
      return false;
   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#getByte(java.lang.String)
    */
   public byte getByte(String arg0) throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#getShort(java.lang.String)
    */
   public short getShort(String arg0) throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#getChar(java.lang.String)
    */
   public char getChar(String arg0) throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#getInt(java.lang.String)
    */
   public int getInt(String arg0) throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#getLong(java.lang.String)
    */
   public long getLong(String arg0) throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#getFloat(java.lang.String)
    */
   public float getFloat(String arg0) throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#getDouble(java.lang.String)
    */
   public double getDouble(String arg0) throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#getString(java.lang.String)
    */
   public String getString(String arg0) throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#getBytes(java.lang.String)
    */
   public byte[] getBytes(String arg0) throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#getObject(java.lang.String)
    */
   public Object getObject(String arg0) throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#getMapNames()
    */
   public Enumeration getMapNames() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#setBoolean(java.lang.String, boolean)
    */
   public void setBoolean(String arg0, boolean arg1) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#setByte(java.lang.String, byte)
    */
   public void setByte(String arg0, byte arg1) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#setShort(java.lang.String, short)
    */
   public void setShort(String arg0, short arg1) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#setChar(java.lang.String, char)
    */
   public void setChar(String arg0, char arg1) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#setInt(java.lang.String, int)
    */
   public void setInt(String arg0, int arg1) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#setLong(java.lang.String, long)
    */
   public void setLong(String arg0, long arg1) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#setFloat(java.lang.String, float)
    */
   public void setFloat(String arg0, float arg1) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#setDouble(java.lang.String, double)
    */
   public void setDouble(String arg0, double arg1) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#setString(java.lang.String, java.lang.String)
    */
   public void setString(String arg0, String arg1) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#setBytes(java.lang.String, byte[])
    */
   public void setBytes(String arg0, byte[] arg1) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#setBytes(java.lang.String, byte[], int, int)
    */
   public void setBytes(String arg0, byte[] arg1, int arg2, int arg3)
      throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#setObject(java.lang.String, java.lang.Object)
    */
   public void setObject(String arg0, Object arg1) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.MapMessage#itemExists(java.lang.String)
    */
   public boolean itemExists(String arg0) throws JMSException {
      // TODO Auto-generated method stub
      return false;
   }

   public static void main(String[] args) {
   }
}
