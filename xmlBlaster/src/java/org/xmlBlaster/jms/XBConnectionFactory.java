/*------------------------------------------------------------------------------
Name:      XBConnectionFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;

import javax.jms.ConnectionMetaData;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Connection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueConnection;
import javax.naming.Referenceable;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.ConnectQosSaxFactory;

/**
 * XBConnectionFactory
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBConnectionFactory implements TopicConnectionFactory, Externalizable, Referenceable, QueueConnectionFactory {

   private final static String ME = "XBConnectionFactory";
   private final static long SER_VERSION = 1L;
   private Global global;
   private ConnectionMetaData metaData = new XBConnectionMetaData();
   private ExceptionListener exceptionListener;
   
   /** the arguments passed on construction: they should never be null */
   String[] args;
   ConnectQos connectQos;
   boolean forQueues;
   
   /**
    * Creates a factory for creating connections
    * @param connectQosLitteral the string qos to use for creation of sessions
    *        it can be null. If it is set, then the arguments passed
    *        are overwritten when it comes to the creation of the connectQos. 
    * @param args the arguments to pass to construct the global object.
    */
   public XBConnectionFactory(String connectQosLitteral, String[] args, boolean forQueues) 
      throws XmlBlasterException {
      if (args == null) this.args = new String[0];
      else this.args = args;
      this.global = new Global(this.args);
      this.connectQos = parseConnectQos(connectQosLitteral);
      this.forQueues = forQueues;
   }

   private ConnectQos parseConnectQos(String qosLitteral) throws XmlBlasterException {
      if (qosLitteral == null || qosLitteral.length() < 6) 
         return new ConnectQos(this.global);
      return new ConnectQos(this.global, (new ConnectQosSaxFactory(global)).readObject(qosLitteral));      
   }
   
   public XBConnectionFactory() throws XmlBlasterException {
      this(null, null, false);
   }

   public ConnectQos getConnectQos() {
      return this.connectQos;
   }
   
   /**
    * Takes a clone of the connectQos and fills it with the security data
    * if necessary
    * @param user
    * @param password
    * @return
    * @throws XmlBlasterException
    */
   private final ConnectQos getConnectQos(String user, String password) throws XmlBlasterException {
      
      // ConnectQos connQos = new ConnectQos(this.global, (ConnectQosData)this.connectQos.getData().clone());
      ConnectQosSaxFactory factory = new ConnectQosSaxFactory(this.global);
      ConnectQos connQos = new ConnectQos(this.global, factory.readObject(this.connectQos.toXml()));
      
      if (user != null) {
         connQos.setUserId(user);
         connQos.getSecurityQos().setUserId(user);
      }
      if (password != null) connQos.getSecurityQos().setCredential(password);
      return connQos;
   }
   
   public Connection createConnection() throws JMSException {
      try {
         return new XBConnection(getConnectQos(null, null), this.metaData, false); 
      }
      catch (XmlBlasterException ex) {
         throw new XBException(ex, ME + ".createConnection");
      }
   }

   public TopicConnection createTopicConnection() throws JMSException {
      if (this.forQueues)
         throw new IllegalStateException(ME + ".createTopicConnection", "You can not create TopicConnection objects from a QueueConnectionFactory");
      try {
         return new XBConnection(getConnectQos(null, null), this.metaData, false); 
      }
      catch (XmlBlasterException ex) {
         throw new XBException(ex, ME + ".createTopicConnection");
      }
   }

   public QueueConnection createQueueConnection() throws JMSException {
      if (!this.forQueues)
         throw new IllegalStateException(ME + ".createQueueConnection", "You can not create TopicConnection objects from a TopicConnectionFactory");
      try {
         return new XBConnection(getConnectQos(null, null), this.metaData, true); 
      }
      catch (XmlBlasterException ex) {
         throw new XBException(ex, ME + ".createQueueConnection");
      }
   }

   public Connection createConnection(String userName, String password)
      throws JMSException {
      try {
         return new XBConnection(getConnectQos(userName, password), this.metaData, false); 
      }
      catch (XmlBlasterException ex) {
         throw new XBException(ex, ME + ".createConnection");
      }
   }

   public TopicConnection createTopicConnection(String userName, String password) 
      throws JMSException {
      if (this.forQueues)
         throw new IllegalStateException(ME + ".createTopicConnection", "You can not create TopicConnection objects from a QueueConnectionFactory");
      try {
         return new XBConnection(getConnectQos(userName, password), this.metaData, false); 
      }
      catch (XmlBlasterException ex) {
         throw new XBException(ex, ME + ".createTopicConnection");
      }
   }

   public QueueConnection createQueueConnection(String userName, String password) 
      throws JMSException {
      if (!this.forQueues)
         throw new IllegalStateException(ME + ".createQueueConnection", "You can not create TopicConnection objects from a TopicConnectionFactory");
      try {
         return new XBConnection(getConnectQos(userName, password), this.metaData, true); 
      }
      catch (XmlBlasterException ex) {
         throw new XBException(ex, ME + ".createQueueConnection");
      }
   }

   public Reference getReference() {
      Reference ret = new Reference(this.getClass().getName(), XBObjectFactory.class.getName(), null);
      ret.add(new StringRefAddr("" + this.forQueues, null));         
      ret.add(new StringRefAddr(this.connectQos.toXml(), null));         
      for (int i=0; i < this.args.length; i++) {
         ret.add(new StringRefAddr(this.args[i], null));         
      }
      return ret;
   }

   public void readExternal(ObjectInput oi) throws IOException, ClassNotFoundException {
      long version = oi.readLong();
      if (version <= SER_VERSION) {
         this.forQueues = oi.readBoolean();
         String qosLitteral = (String)oi.readObject();
         this.args = (String[])oi.readObject();
         this.global = new Global(this.args);
         try {
            this.connectQos = parseConnectQos(qosLitteral);
         }
         catch (XmlBlasterException ex) {
            ex.printStackTrace();
            throw new IOException(ME + ".readExternal exception occured: " + ex.getMessage());
         }
      }
      else throw new IOException(ME + ".writeExternal: current version '" + SER_VERSION + "' is older than serialized version '" + version + "'");
   }
   
   public void writeExternal(java.io.ObjectOutput oo) throws IOException {
      oo.writeLong(SER_VERSION);
      oo.writeBoolean(this.forQueues);
      oo.writeObject(this.connectQos.toXml());
      oo.writeObject(this.args);
   }
         
}
