/*------------------------------------------------------------------------------
Name:      XBTextMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

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

   public XBTextMessage(Global global, MsgKeyData key, byte[] content, MsgQosData qos) {
      super(global, key, content, qos, XBMessage.TEXT);
   }
   
   public String getText() throws JMSException {
      if (this.content == null) return null;
      return new String(this.content);
   }

   public void setText(String text) throws JMSException {
      if (text == null) this.content = null;
      else this.content = text.getBytes();
   }
}
