/*------------------------------------------------------------------------------
Name:      XBProviderSpecificProperties.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.jms;

import java.util.HashSet;
import java.util.Set;

import org.xmlBlaster.util.def.Constants;

/**
 * XBProviderSpecificProperties. Here are the definitions of the
 * keys of the properties used within xmlBlaster for JMS.
 * 
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class XBPropertyNames {
   
   public final static String CONNECT_QOS = "JMS_xmlBlasterConnectQos";

   /**
    * This property sets the maximum size of each chunk. If a message has a content
    * which is bigger than this value, then the message is 'chunked', that is, it is
    * sent in several smaller messages. If this value is not set before publishing, the
    * size is unlimited. This is a specific feature of xmlBlaster.
    */
   public final static String MAX_CHUNK_SIZE = "JMS_maxChunkSize";
   
   /**
    * This is a feature specific to xmlBlaster. It allows to stream huge messages
    * (or real streams). If this is set to 'true' (defaults to false), then the action
    * of publishing will publish the first chunk (or submessage) but it will keep the
    * message alive, that is, it will continue publishing chunks even after having invoked
    * publish (or send). To finish publishing (i.e. to mark the end of the ongoing publishing)
    * you have to invoke clearBody() on the published message. If you set this flag and 
    * MAX_CHUNK_SIZE is not set, the application will choose an appropriate chunk size. 
    */
   public final static String OPEN_END_PUBLISH = "JMS_openEndPublish";
   
   // these are keys used in the client properties,
   // most of them used in XBMessage and XBMessageConsumer
   public final static String JMS_MESSAGE_TYPE   = "JMSMessageType";
   public final static String JMS_TYPE           = "JMSType";
   public final static String JMS_TIMESTAMP      = "JMSTimestamp";
   public final static String JMS_REDELIVERED    = "JMSRedelivered";
   public final static String JMS_MESSAGE_ID     = "JMSMessageID";
   public final static String JMS_CORRELATION_ID = "JMSCorrelationID";
   // don't really remember how I was thinking here
   // public final static String JMS_HEADER_PREFIX  = "jms/";
   
   public final static String JMS_REPLY_TO       = Constants.JMS_REPLY_TO;  
   public final static String JMS_DELIVERY_MODE  = "JMSDeliveryMode";
   public final static String JMS_EXPIRATION     = "JMSExpiration";
   public final static String JMS_PRIORITY       = "JMSPriority";

   public final static Set getStandardProps() {
      final Set ret = new HashSet();
      ret.add(CONNECT_QOS);
      ret.add(MAX_CHUNK_SIZE);
      ret.add(OPEN_END_PUBLISH);
      ret.add(JMS_MESSAGE_TYPE);
      ret.add(JMS_TYPE);
      ret.add(JMS_TIMESTAMP);
      ret.add(JMS_REDELIVERED);
      ret.add(JMS_MESSAGE_ID);
      ret.add(JMS_CORRELATION_ID);
      ret.add(JMS_REPLY_TO);
      ret.add(JMS_DELIVERY_MODE);
      ret.add(JMS_EXPIRATION);
      ret.add(JMS_PRIORITY);
      return ret;
   }
   
   
}
