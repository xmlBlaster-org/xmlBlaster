/*------------------------------------------------------------------------------
Name:      XBConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;

import org.jutils.log.LogChannel;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_Callback;

/**
 * XBConnection
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBConnection implements QueueConnection, TopicConnection, I_Callback {

   private final static String ME = "XBConnection";
   private Global global;
   private LogChannel log;
   private String user, password;
   private ConnectQos connectQos;
   private ConnectReturnQos connectReturnQos;
   private I_XmlBlasterAccess access;
   private ExceptionListener exceptionListener;

   
   I_XmlBlasterAccess getAccess() {
      return this.access;
   }
   
   Global getGlobal() {
      return this.global;
   }
   
   XBConnection(Global global, String user, String password) throws XmlBlasterException {
      this.user = user;
      this.password = password;
      this.global = global;
      this.log = this.global.getLog("jms");
      // clone to make sure to have an own instance (or should the 'global' member be a clone ?)
      this.access = this.global.getClone(null).getXmlBlasterAccess();
      if (this.user == null && this.password == null) 
         this.connectQos = new ConnectQos(this.global);
      else this.connectQos = new ConnectQos(this.global, this.user, this.password);
   }
   
   XBConnection(Global global) throws XmlBlasterException {
      this(global, null, null);
   }

   
   /* (non-Javadoc)
    * @see javax.jms.QueueConnection#createConnectionConsumer(javax.jms.Queue, java.lang.String, javax.jms.ServerSessionPool, int)
    */
   public ConnectionConsumer createConnectionConsumer(
      Queue arg0,
      String arg1,
      ServerSessionPool arg2,
      int arg3)
      throws JMSException {
      // TODO Auto-generated method stub
      throw new JMSException(ME + " 'createConnectionConsumer' not implemented yet");
   }

   /* (non-Javadoc)
    * @see javax.jms.QueueConnection#createQueueSession(boolean, int)
    */
   public QueueSession createQueueSession(boolean arg0, int arg1)
      throws JMSException {
      // TODO Auto-generated method stub
      throw new JMSException(ME + " 'createQueueSession' not implemented yet");
   }

   /* (non-Javadoc)
    * @see javax.jms.TopicConnection#createConnectionConsumer(javax.jms.Topic, java.lang.String, javax.jms.ServerSessionPool, int)
    */
   public ConnectionConsumer createConnectionConsumer(
      Topic arg0,
      String arg1,
      ServerSessionPool arg2,
      int arg3)
      throws JMSException {
      // TODO Auto-generated method stub
      throw new JMSException(ME + " 'createConnectionConsumer' not implemented yet");
   }

   /* (non-Javadoc)
    * @see javax.jms.TopicConnection#createDurableConnectionConsumer(javax.jms.Topic, java.lang.String, java.lang.String, javax.jms.ServerSessionPool, int)
    */
   public ConnectionConsumer createDurableConnectionConsumer(
      Topic arg0,
      String arg1,
      String arg2,
      ServerSessionPool arg3,
      int arg4)
      throws JMSException {
      // TODO Auto-generated method stub
      throw new JMSException(ME + " 'createDurableConnectionConsumer' not implemented yet");
   }

   public TopicSession createTopicSession(boolean transacted, int ackMode)
      throws JMSException {
      if (transacted) 
         throw new JMSException(ME + " 'createTopicSession' in transacted mode not implemented yet");
      return new XBTopicSession(this, ackMode);      
      
   }

   /* (non-Javadoc)
    * @see javax.jms.Connection#close()
    */
   public void close() throws JMSException {
      stop();
   }

   public String getClientID() throws JMSException {
      return this.connectQos.getUserId();
   }

   /* (non-Javadoc)
    * @see javax.jms.Connection#getExceptionListener()
    */
   public ExceptionListener getExceptionListener() throws JMSException {
      return this.exceptionListener;
   }

   /* (non-Javadoc)
    * @see javax.jms.Connection#getMetaData()
    */
   public ConnectionMetaData getMetaData() throws JMSException {
      // TODO Auto-generated method stub
      throw new JMSException(ME + " 'getMetaData' not implemented yet");
   }

   /* (non-Javadoc)
    * @see javax.jms.Connection#setClientID(java.lang.String)
    */
   public void setClientID(String loginName) throws JMSException {
      try {
         this.connectQos.setUserId(loginName);
      }
      catch (XmlBlasterException ex) {
         JMSException jmsEx = new JMSException(ex.getMessage(), ex.getErrorCodeStr());
         if (this.exceptionListener != null) {
            this.exceptionListener.onException(jmsEx);
         }
         else throw jmsEx;
      }
   }

   /* (non-Javadoc)
    * @see javax.jms.Connection#setExceptionListener(javax.jms.ExceptionListener)
    */
   public void setExceptionListener(ExceptionListener exeptionListener) throws JMSException {
      this.exceptionListener = exeptionListener;
   }

   /* (non-Javadoc)
    * @see javax.jms.Connection#start()
    */
   public void start() throws JMSException {
      try {
         this.connectReturnQos = this.access.connect(this.connectQos, this);
      }
      catch (XmlBlasterException ex) {
         JMSException jmsEx = new JMSException(ex.getMessage(), ex.getErrorCodeStr());
         if (this.exceptionListener != null) {
            this.exceptionListener.onException(jmsEx);
         }
         else throw jmsEx;
      }
   }

   /* (non-Javadoc)
    * @see javax.jms.Connection#stop()
    */
   public void stop() throws JMSException {
         DisconnectQos disconnectQos = new DisconnectQos(this.global);
         this.access.disconnect(disconnectQos);
      }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      // this should actually never be invoked since the subscribers have own I_Callback 
      this.log.warn(ME, "unassociated update invoked for message '" + updateKey.getOid() + "'");
      return "OK";
   }


}
