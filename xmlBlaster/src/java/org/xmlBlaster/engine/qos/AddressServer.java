/*------------------------------------------------------------------------------
Name:      AddressServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding address string and protocol string
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.address.AddressBase;
import java.util.Properties;

/**
 * Helper class holding address string, protocol string of server side protocol plugins. 
 * <p />
 * <pre>
 * &lt;address type='XMLRPC'>
 *    http://server:8080/
 * &lt;/address>
 * </pre>
 */
public class AddressServer extends AddressBase
{
   private static final String ME = "AddressServer";

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XMLRPC"
    * @param nodeId  A unique string (typically the cluster node id we connect to).
    * @param pluginInfoParameters Attributes from xmlBlasterPlugins.xml
    */
   public AddressServer(Global glob, String type, String nodeId, Properties pluginInfoParameters) {
      super(glob, "address");
      this.nodeId = nodeId;
      this.instanceName = null;
      if (pluginInfoParameters != null)
         setPluginInfoParameters(pluginInfoParameters);
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


