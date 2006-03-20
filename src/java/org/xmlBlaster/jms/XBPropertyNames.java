/*------------------------------------------------------------------------------
Name:      XBProviderSpecificProperties.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.jms;


/**
 * XBProviderSpecificProperties. Here are the definitions of the
 * keys of the properties used within xmlBlaster for JMS.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public interface XBPropertyNames {
   
   public final static String CONNECT_QOS = "JMS_xmlBlasterConnectQos";

   // these are keys used in the client properties,
   // most of them used in XBMessage and XBMessageConsumer
   public final static String JMS_MESSAGE_TYPE   = "JMSMessageType";
   public final static String JMS_TYPE           = "JMSType";
   public final static String JMS_TIMESTAMP      = "JMSTimestamp";
   public final static String JMS_REDELIVERED    = "JMSRedelivered";
   public final static String JMS_MESSAGE_ID     = "JMSMessageID";
   public final static String JMS_CORRELATION_ID = "JMSCorrelationID";
   // don't really remember how I was thinking here
   public final static String JMS_HEADER_PREFIX  = "jms/";
   
   public final static String JMS_REPLY_TO       = "JMSReplyTo";  
   public final static String JMS_DELIVERY_MODE  = "JMSDeliveryMode";
   public final static String JMS_EXPIRATION     = "JMSExpiration";
   public final static String JMS_PRIORITY       = "JMSPriority";
   
   
}
