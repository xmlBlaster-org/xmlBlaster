/*------------------------------------------------------------------------------
Name:      XBConnectionFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.j2ee.jms;

import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * XBConnectionFactory
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBConnectionFactory implements QueueConnectionFactory, TopicConnectionFactory {

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

   public QueueConnection createQueueConnection() throws JMSException {
      if (this.log.CALL) this.log.call(ME, "createQueueConnection");
      try {
         return new XBConnection(this.global); 
      }
      catch (XmlBlasterException ex) {
         throw convert(ex, null);
      }
   }

   /* (non-Javadoc)
    * @see javax.jms.QueueConnectionFactory#createQueueConnection(java.lang.String, java.lang.String)
    */
   public QueueConnection createQueueConnection(String userName, String password)
      throws JMSException {
      if (this.log.CALL) this.log.call(ME, "createQueueConnection");
      try {
         return new XBConnection(this.global, userName, password); 
      }
      catch (XmlBlasterException ex) {
         throw convert(ex, null);
      }
   }

   /* (non-Javadoc)
    * @see javax.jms.TopicConnectionFactory#createTopicConnection()
    */
   public TopicConnection createTopicConnection() throws JMSException {
      if (this.log.CALL) this.log.call(ME, "createTopicConnection");
      try {
         return new XBConnection(this.global); 
      }
      catch (XmlBlasterException ex) {
         throw convert(ex, null);
      }
   }

   /* (non-Javadoc)
    * @see javax.jms.TopicConnectionFactory#createTopicConnection(java.lang.String, java.lang.String)
    */
   public TopicConnection createTopicConnection(String userName, String password)
      throws JMSException {
      if (this.log.CALL) this.log.call(ME, "createTopicConnection");
      try {
         return new XBConnection(this.global, userName, password); 
      }
      catch (XmlBlasterException ex) {
         throw convert(ex, null);
      }
   }

}
