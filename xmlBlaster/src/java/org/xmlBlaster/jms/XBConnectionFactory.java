/*------------------------------------------------------------------------------
Name:      XBConnectionFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;

import javax.jms.JMSException;
import javax.jms.Connection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueConnection;
import javax.naming.Referenceable;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * XBConnectionFactory
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBConnectionFactory implements TopicConnectionFactory, Externalizable, Referenceable, QueueConnectionFactory {

   private final static String ME = "XBConnectionFactory";
   private final static long SER_VERSION = 1L;
   // private Global global;
   /** the arguments passed on construction: they should never be null */
   String[] args;

   public XBConnectionFactory(String[] args) {
      if (args == null) this.args = new String[0];
      else this.args = args;
   }

   public XBConnectionFactory() {
      this(null);
   }

   public static JMSException convert(XmlBlasterException ex, String additionalTxt) {
      if (additionalTxt == null) additionalTxt = "";
      String txt = additionalTxt + ex.getMessage();
      String embedded = ex.getEmbeddedMessage();
      if (embedded != null) txt += " " + embedded;
      return new JMSException(txt, ex.getErrorCodeStr()); 
   }

   public Connection createConnection() throws JMSException {
      try {
         return new XBConnection(this.args); 
      }
      catch (XmlBlasterException ex) {
         throw convert(ex, null);
      }
   }

   public TopicConnection createTopicConnection() throws JMSException {
      return (TopicConnection)createConnection();
   }

   public QueueConnection createQueueConnection() throws JMSException {
      return (QueueConnection)createConnection();
   }

   public Connection createConnection(String userName, String password)
      throws JMSException {
      try {
         return new XBConnection(this.args, userName, password); 
      }
      catch (XmlBlasterException ex) {
         throw convert(ex, null);
      }
   }

   public TopicConnection createTopicConnection(String userName, String password) 
      throws JMSException {
      return (TopicConnection)createConnection(userName, password);
   }

   public QueueConnection createQueueConnection(String userName, String password) 
      throws JMSException {
      return (QueueConnection)createConnection(userName, password);
   }

   public Reference getReference() {
      Reference ret = new Reference(this.getClass().getName(), XBObjectFactory.class.getName(), null);
      for (int i=0; i < this.args.length; i++) {
         ret.add(new StringRefAddr(this.args[i], null));         
      }
      return ret;
   }

   public void readExternal(ObjectInput oi) throws IOException, ClassNotFoundException {
      long version = oi.readLong();
      if (version <= SER_VERSION) {
         this.args = (String[])oi.readObject();
      }
      else throw new IOException(ME + ".writeExternal: current version '" + SER_VERSION + "' is older than serialized version '" + version + "'");
   }
   
   public void writeExternal(java.io.ObjectOutput oo) throws IOException {
      oo.writeLong(SER_VERSION);
      oo.writeObject(this.args);
   }
         
}
