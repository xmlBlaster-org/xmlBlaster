/*------------------------------------------------------------------------------
Name:      CallbackAddress.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback address string and protocol string
Version:   $Id: CallbackAddress.java,v 1.9 2002/04/15 12:52:06 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xml.sax.Attributes;


/**
 * Helper class holding callback address string and protocol string.
 * <p />
 * <pre>
 * &lt;callback type='XML-RPC' sessionId='4e56890ghdFzj0'
 *           pingInterval='60000' retries='5' delay='10000'
 *           oneway='false' useForSubjectQueue='true'>
 *    http://server:8080/cb
 *    &lt;compress type='gzip' minSize='1000'/>
 *    &lt;burstMode collectTime='400'/>
 * &lt;/callback>
 * </pre>
 */
public class CallbackAddress extends AddressBase
{
   private static final String ME = "CallbackAddress";

   /**
    */
   public CallbackAddress()
   {
      super("callback");
      initialize();
   }

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    */
   public CallbackAddress(String type)
   {
      super("callback");
      initialize();
      setType(type);
   }

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    * @param address A callback address for your client, suitable to the protocol
    */
   public CallbackAddress(String type, String address)
   {
      super("callback");
      initialize();
      setType(type);
      setAddress(address);
   }

   /**
    * Configure property settings
    */
   private void initialize()
   {
      collectTime = XmlBlasterProperty.get("cb.collectTime", DEFAULT_collectTime);
      pingInterval = XmlBlasterProperty.get("cb.pingInterval", DEFAULT_pingInterval);
      retries = XmlBlasterProperty.get("cb.retries", DEFAULT_retries);
      delay = XmlBlasterProperty.get("cb.delay", DEFAULT_delay);
      useForSubjectQueue = XmlBlasterProperty.get("cb.useForSubjectQueue", DEFAULT_useForSubjectQueue);
      oneway = XmlBlasterProperty.get("cb.oneway", DEFAULT_oneway);
      compressType = XmlBlasterProperty.get("cb.compress.type", DEFAULT_compressType);
      minSize = XmlBlasterProperty.get("cb.compress.minSize", DEFAULT_minSize);
      ptpAllowed = XmlBlasterProperty.get("cb.ptpAllowed", DEFAULT_ptpAllowed);
      sessionId = XmlBlasterProperty.get("cb.sessionId", DEFAULT_sessionId);
   }

   /**
    * Shall this address be used for subject queue messages?
    * @return false if address is for session queue only
    */
   public boolean useForSubjectQueue()
   {
      return useForSubjectQueue;
   }

   /**
    * Shall this address be used for subject queue messages?
    * @param useForSubjectQueue false if address is for session queue only
    */
   public void useForSubjectQueue(boolean useForSubjectQueue)
   {
      this.useForSubjectQueue = useForSubjectQueue;
   }

   /**
    * @return true if we may send PtP messages
    */
   public boolean isPtpAllowed()
   {
      return this.ptpAllowed;
   }

   /**
    * @param Set if we accept point to point messages
    */
   public void setPtpAllowed(boolean ptpAllowed)
   {
      this.ptpAllowed = ptpAllowed;
   }
}


