/*------------------------------------------------------------------------------
Name:      XBTemporaryTopic.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.TemporaryTopic;

/**
 * XBTemporaryTopic
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBTemporaryTopic extends XBTopic implements TemporaryTopic {

   XBTemporaryTopic() {
      super();
   }
   

   /* (non-Javadoc)
    * @see javax.jms.TemporaryTopic#delete()
    */
   public void delete() throws JMSException {
      // TODO Auto-generated method stub
   }
}
