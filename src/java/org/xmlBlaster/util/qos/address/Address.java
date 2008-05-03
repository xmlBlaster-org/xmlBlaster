/*------------------------------------------------------------------------------
Name:      Address.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding address string and protocol string
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.address;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;

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
public class Address extends AddressBase
{
   private static final String ME = "Address";

   private transient CallbackAddress callbackAddress;
   
   /**
    */
   public Address(Global glob) {
      super(glob, "address");
      this.instanceName = Constants.RELATING_CLIENT;
      initialize();
   }

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XMLRPC"
    */
   public Address(Global glob, String type) {
      super(glob, "address");
      this.instanceName = Constants.RELATING_CLIENT;
      if (type != null) {
         setType(type);
      }
      initialize();
   }

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
   public Address(Global glob, String type, String nodeId) {
      super(glob, "address");
      this.nodeId = nodeId;
      this.instanceName = Constants.RELATING_CLIENT;
      if (type != null) {
         setType(type);
      }
      initialize();
   }


   /** How often to retry if connection fails: defaults to -1 (retry forever), 0 switches failsafe mode off */
   public int getDefaultRetries() { return -1; }

   /* Delay between connection retries in milliseconds: defaults to 5000 (5 sec) */
   public long getDefaultDelay() { return 5 * 1000L; };

   /** Ping interval: pinging every given milliseconds, defaults to 10 seconds */
   public long getDefaultPingInterval() { return 10 * 1000L; }

   /** For logging only */
   public String getSettings() {
      StringBuffer buf = new StringBuffer(126);
      buf.append(super.getSettings());
      if (getDelay() > 0)
         buf.append(" delay=").append(getDelay()).append(" retries=").append(getRetries()).append(" pingInterval=").append(getPingInterval());
      return buf.toString();
   }

   /** @return The literal address as given by getRawAddress() */
   public String toString() {
      return getRawAddress();
   }

   /** Client side usage used by XmlBlasterAccess */
   public final String usage() {
      String text = "\nControl failsafe connection to xmlBlaster server:\n";
      text += super.usage();
    //text += "   -queue.onOverflow   Error handling when queue is full, 'block | deadMessage' [" + CbQueueProperty.DEFAULT_onOverflow + "].\n";
    //text += "   -queue.onFailure    Error handling when connection failed (after all retries etc.) [" + CbQueueProperty.DEFAULT_onFailure + "].\n";
      text += "   -dispatch/" + this.instanceName + "/burstMode/collectTime\n";
      text += "                       Number of milliseconds we shall collect\n";
      text += "                       publish messages [" + Address.DEFAULT_collectTime + "].\n";
      text += "                       This allows performance tuning, try set it to 200.\n";
    //text += "   -DispatchPlugin/defaultPlugin  Specify your specific dispatcher plugin [" + CallbackAddress.DEFAULT_dispatchPlugin + "]\n";
    //text += "   -compress.type      With which format message be compressed on callback [" + Address.DEFAULT_compressType + "]\n";
    //text += "   -compress.minSize   Messages bigger this size in bytes are compressed [" + Address.DEFAULT_minSize + "]\n";
      return text;
   }

   /** For testing: java org.xmlBlaster.util.qos.address.Address */
   public static void main(String[] argsDefault)
   {
      try {
         {
            Global glob = new Global(argsDefault);
            Address a = new Address(glob);
            a.setType("SOCKET");
            a.setBootstrapHostname("oioihost");
            a.setRawAddress("9999");
            a.setRawAddress("127.0.0.1:7600");
            a.setCollectTime(12345L);
            a.setPingInterval(54321L);
            a.setRetries(17);
            a.setDelay(7890L);
            a.setOneway(true);
            a.setSecretSessionId("0x4546hwi89");
            System.out.println(a.toXml());
         }
         {
            String nodeId = "heron";
            
            java.util.Vector vec = new java.util.Vector();
            vec.addElement("-sessionId");
            vec.addElement("ERROR");
            vec.addElement("-sessionId["+nodeId+"]");
            vec.addElement("OK");
            vec.addElement("-pingInterval");
            vec.addElement("8888");
            vec.addElement("-delay["+nodeId+"]");
            vec.addElement("8888");
            String[] args = (String[])vec.toArray(new String[0]);

            Global glob = new Global(args);
            Address a = new Address(glob, "RMI", nodeId);
            System.out.println(a.toXml());
         }
      }
      catch(Throwable e) {
         e.printStackTrace();
         System.err.println("TestFailed: " + e.toString());
      }
   }

   public CallbackAddress getCallbackAddress() {
      return callbackAddress;
   }

   public void setCallbackAddress(CallbackAddress callbackAddress) {
      this.callbackAddress = callbackAddress;
   }
}


