/*------------------------------------------------------------------------------
Name:      Address.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding address string and protocol string
Version:   $Id: Address.java,v 1.2 2002/04/21 10:35:53 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xml.sax.Attributes;


/**
 * Helper class holding address string and protocol string.
 * <p />
 * <pre>
 * &lt;address type='XML-RPC' sessionId='4e56890ghdFzj0'
 *           pingInterval='60000' retries='5' delay='10000'
 *           oneway='false'>
 *    http://server:8080/cb
 *    &lt;compress type='gzip' minSize='1000'/>
 *    &lt;burstMode collectTime='400'/>
 * &lt;/address>
 * </pre>
 */
public class Address extends AddressBase
{
   private static final String ME = "Address";

   /**
    */
   public Address()
   {
      super("address");
      initialize();
   }

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    */
   public Address(String type)
   {
      super("address");
      initialize();
      setType(type);
   }

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    * @param address A address address for your client, suitable to the protocol
    */
   public Address(String type, String address)
   {
      super("address");
      initialize();
      setType(type);
      setAddress(address);
   }

   /**
    * Configure property settings. 
    */
   private void initialize()
   {
      collectTime = XmlBlasterProperty.get("collectTime", DEFAULT_collectTime);
      pingInterval = XmlBlasterProperty.get("pingInterval", DEFAULT_pingInterval);
      retries = XmlBlasterProperty.get("retries", DEFAULT_retries);
      delay = XmlBlasterProperty.get("delay", DEFAULT_delay);
      oneway = XmlBlasterProperty.get("oneway", DEFAULT_oneway);
      compressType = XmlBlasterProperty.get("compress.type", DEFAULT_compressType);
      minSize = XmlBlasterProperty.get("compress.minSize", DEFAULT_minSize);
      ptpAllowed = XmlBlasterProperty.get("ptpAllowed", DEFAULT_ptpAllowed);
      sessionId = XmlBlasterProperty.get("sessionId", DEFAULT_sessionId);
   }

   /** For testing: java org.xmlBlaster.engine.helper.Address */
   public static void main(String[] args)
   {
      try {
         Address a = new Address();
         a.setType("SOCKET");
         a.setAddress("127.0.0.1:7600");
         a.setCollectTime(12345L);
         a.setPingInterval(54321L);
         a.setRetries(17);
         a.setDelay(7890L);
         a.setOneway(true);
         a.setSessionId("0x4546hwi89");
         Log.info(ME, a.toXml());
      }
      catch(Throwable e) {
         e.printStackTrace();
         Log.error("TestFailed", e.toString());
      }
   }
}


