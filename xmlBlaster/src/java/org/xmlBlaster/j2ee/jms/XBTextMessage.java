/*------------------------------------------------------------------------------
Name:      XBTextMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.j2ee.jms;

import javax.jms.JMSException;
import javax.jms.TextMessage;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.MsgQosData;

/**
 * XBTextMessage
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBTextMessage extends XBMessage implements TextMessage {

   private final static String ME = "XBTextMessage";

   XBTextMessage(Global global, MsgKeyData key, byte[] content, MsgQosData qos) {
      super(global, key, content, qos, XBMessage.TEXT);
   }
   
   public String getText() throws JMSException {
      return new String(this.content);
   }

   public void setText(String text) throws JMSException {
      this.content = text.getBytes();
   }
}
