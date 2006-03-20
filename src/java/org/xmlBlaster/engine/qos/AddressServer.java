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
   private Object remoteAddress;

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XMLRPC"
    * @param nodeId  A unique string (typically the cluster node id we connect to).
    * @param pluginInfoParameters Attributes from xmlBlasterPlugins.xml
    */
   public AddressServer(Global glob, String type, String nodeId, Properties pluginInfoParameters) {
      super(glob, "address");
      this.nodeId = nodeId;
      this.instanceName = null;
      setType(type);
      if (pluginInfoParameters != null)
         setPluginInfoParameters(pluginInfoParameters);
      else
         initialize();
   }

   /**
    * Some protocol plugins may add this information. 
    * For example the SOCKET plugin can deliver the remote clients
    * IP and port here.
    * @return For example getRemoteAddress().toString() = "socket://192.168.1.2:64794"
    *         Can be null depending on the protocol plugin.<br />
    *         For example the SOCKET protocol returns an "org.xmlBlaster.protocol.socket.SocketUrl" instance
    */
   public Object getRemoteAddress() {
      return this.remoteAddress;
   }

   public void setRemoteAddress(Object remoteAddress) {
      this.remoteAddress = remoteAddress;
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


