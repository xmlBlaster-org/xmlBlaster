/*------------------------------------------------------------------------------
Name:      XBTopic.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.j2ee.jms;

import javax.jms.JMSException;
import javax.jms.Topic;

/**
 * XBTopic
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBTopic implements Topic {

   private String topicName;
   
   public XBTopic(String topicName) {
      this.topicName = topicName;
   }

   public String getTopicName() throws JMSException {
      return this.topicName;
   }

   public String toString() {
      return this.topicName;
   }

}
