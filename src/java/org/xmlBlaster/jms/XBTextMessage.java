/*------------------------------------------------------------------------------
Name:      XBTextMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.TextMessage;

import org.xmlBlaster.util.def.Constants;

/**
 * XBTextMessage
 *
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * 
 */
public class XBTextMessage extends XBMessage implements TextMessage {

   public XBTextMessage(XBSession session, byte[] content) {
      super(session, content, XBMessage.TEXT);
   }
   
   public String getText() throws JMSException {
      if (this.content == null) return null;
      return Constants.toUtf8String(this.content);
   }

   public void setText(String text) throws JMSException {
      if (text == null) this.content = null;
      else this.content = Constants.toUtf8Bytes(text);
   }
}
