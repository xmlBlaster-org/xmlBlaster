/*------------------------------------------------------------------------------
Name:      XBTextMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import javax.jms.JMSException;
import javax.jms.ObjectMessage;

import org.xmlBlaster.util.def.ErrorCode;

/**
 * XBTextMessage
 *
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * 
 */
public class XBObjectMessage extends XBMessage implements ObjectMessage {

   XBObjectMessage(XBSession session, byte[] content) {
      super(session, content, XBMessage.OBJECT);
   }
   
   public Serializable getObject() throws JMSException {
      Serializable ret;
      try {
         ByteArrayInputStream bais = new ByteArrayInputStream(this.content);
         ObjectInputStream ois = new ObjectInputStream(bais);
         ret = (Serializable)ois.readObject();
         ois.close();
         return ret;
      }
      catch (IOException ex) {
         throw new JMSException (ex.getMessage(), ErrorCode.RESOURCE_FILEIO.getErrorCode());
      }
      catch (ClassNotFoundException ex) {
         throw new JMSException (ex.getMessage(), ErrorCode.INTERNAL_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public void setObject(Serializable object) throws JMSException {
      try {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ObjectOutputStream oos = new ObjectOutputStream(baos);
         oos.writeObject(object);
         this.content = baos.toByteArray();
      }
      catch (IOException ex) {
         throw new JMSException (ex.getMessage(), ErrorCode.RESOURCE_FILEIO.getErrorCode());
      }
   }

}
