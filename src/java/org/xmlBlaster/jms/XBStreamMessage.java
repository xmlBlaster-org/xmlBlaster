/*------------------------------------------------------------------------------
Name:      XBStreamMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.StreamMessage;

/**
 * XBStreamMessage
 *
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * 
 */
public class XBStreamMessage extends XBBytesMessage implements StreamMessage {

   public XBStreamMessage(XBSession session, byte[] content) throws JMSException {
      super(session, content, XBMessage.STREAM);
   }
}
