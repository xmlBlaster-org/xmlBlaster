/*------------------------------------------------------------------------------
Name:      XBConnectionFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.Connection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueConnection;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * XBConnectionFactory
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBConnectionFactory implements TopicConnectionFactory, QueueConnectionFactory {

   private final static String ME = "XBConnectionFactory";
   private Global global;
   private LogChannel log;

   public XBConnectionFactory(Global global) {
      if (global == null) this.global = Global.instance();
      else this.global = global;
      this.log = this.global.getLog("jms");
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
      if (this.log.CALL) this.log.call(ME, "createConnection");
      try {
         return new XBConnection(this.global); 
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

   /* (non-Javadoc)
    * @see javax.jms.QueueConnectionFactory#createQueueConnection(java.lang.String, java.lang.String)
    */
   public Connection createConnection(String userName, String password)
      throws JMSException {
      if (this.log.CALL) this.log.call(ME, "createConnection");
      try {
         return new XBConnection(this.global, userName, password); 
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

}
