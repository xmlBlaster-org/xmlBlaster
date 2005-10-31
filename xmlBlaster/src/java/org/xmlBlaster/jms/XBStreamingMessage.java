/*------------------------------------------------------------------------------
Name:      XBStreamingMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Enumeration;
import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.def.Constants;

/**
 * XBStreamingMessage. This is an xmlBlaster specific implementation to
 * allow real streaming.
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBStreamingMessage extends XBTextMessage {
   
   private InputStream in;
   private int maxBufSize = 1000000;
   
   public XBStreamingMessage(XBSession session, InputStream in) {
      super(session, null);
      if (in != null)
         setInputStream(in);
   }
   
   public InputStream getInputStream() {
      this.type = XBMessage.STREAMING;
      return this.in;
   }

   public void setInputStream(InputStream in) {
      this.type = XBMessage.STREAMING;
      this.in = in;
   }
   
   
   void send(Session session, MessageProducer producer, Destination dest) throws JMSException {
      String streamId = (new org.xmlBlaster.util.Global()).getId() + "-" + (new Timestamp()).getTimestamp();
      setStringProperty(Constants.STREAM_ID, streamId);
      int bufSize = 0;
      if (propertyExists(XBConnectionMetaData.JMSX_MAX_CHUNK_SIZE))
         bufSize = getIntProperty(XBConnectionMetaData.JMSX_MAX_CHUNK_SIZE);
      if (bufSize > this.maxBufSize || bufSize == 0)
         bufSize = this.maxBufSize;
      byte[] buf = null; // new byte[bufSize];
      long count = 0;
      try {
         while (true) {
            int available = this.in.available();
            if (available >= bufSize)
               available = bufSize;
            else
               available++; // we increment by one to see if it is an eof
            
            buf = new byte[available];
            int length = this.in.read(buf);
            if (length != 0) {
               // must pass null since otherwise you get a non-writeable ex.
               BytesMessage chunk = this.session.createBytesMessage();
               
               chunk.writeBytes(buf);
               if (length < available)
                  chunk.setBooleanProperty(Constants.CHUNK_EOF, true);
               chunk.setLongProperty(Constants.CHUNK_SEQ_NUM, count);
               chunk.writeBytes(buf, 0, length);
               producer.send(dest, chunk);
               count++;
               if (length < available)
                  return;
            }
         }            
      }
      catch (Exception ex) {
         if (count > 0) {
            BytesMessage chunk = session.createBytesMessage();
            chunk.setBooleanProperty(Constants.CHUNK_EOF, true);
            chunk.setLongProperty(Constants.CHUNK_SEQ_NUM, count);
            Enumeration eNum = chunk.getPropertyNames();
            while (eNum.hasMoreElements()) {
               String key = (String)eNum.nextElement();
               chunk.setObjectProperty(key, getObjectProperty(key));
            }
            chunk.setStringProperty(Constants.CHUNK_EXCEPTION, ex.getMessage());
            producer.send(dest, chunk);
         }
         if (ex instanceof JMSException)
            throw (JMSException)ex;
         throw new JMSException(ex.getMessage());
      }
   }
}
