/*------------------------------------------------------------------------------
Name:      AddressServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding address string and protocol string
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.address.AddressBase;

/**
 * Helper class holding address string, protocol string and client side connection properties.
 * <p />
 * <pre>
 * &lt;address type='XMLRPC' sessionId='4e56890ghdFzj0'
 *           pingInterval='60000' retries='5' delay='10000'
 *           oneway='false'>
 *    http://server:8080/cb
 *    &lt;compress type='gzip' minSize='1000'/>
 *    &lt;burstMode collectTime='400'/> <!-- for publishOneway() calls -->
 * &lt;/address>
 * </pre>
 */
public class AddressServer extends AddressBase
{
   private static final String ME = "AddressServer";

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XMLRPC"
    * @param nodeId  A unique string (typically the cluster node id we connect to).<br />
    *   This is used for extended env-variable support, e.g. for a given
    *    <code>nodeId="heron"</ code>
    *   the command line argument (or xmlBlaster.property entry)
    *    <code>-retries[heron] 20</code>
    *   is precedence over
    *    <code>-retries 10</code>
    */
   public AddressServer(Global glob, String type, String nodeId) {
      super(glob, "address");
      this.nodeId = nodeId;
      this.instanceName = null;
      setType(type);
      initialize();
   }

   /** DUMMY */
   public int getDefaultRetries() { return -1; }

   /** DUMMY */
   public long getDefaultDelay() { return -1; };

   /** DUMMY */
   public long getDefaultPingInterval() { return -1; }

   /** @return The literal address as given by getAddressServer() */
   public String toString() {
      return getRawAddress();
   }
}


