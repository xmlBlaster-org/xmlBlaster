/*------------------------------------------------------------------------------
Name:      Address.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding address string and protocol string
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.address;

import org.xmlBlaster.util.Global;
import org.xml.sax.Attributes;


/**
 * Helper class holding address string, protocol string and client side connection properties.
 * <p />
 * <pre>
 * &lt;address type='XML-RPC' sessionId='4e56890ghdFzj0'
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

   /** The node id to which we want to connect */
   private String nodeId = null;

   /** TODO: Move this attribute to CbQueueProperty.java */
   private long maxMsg;

   /**
    */
   public Address(Global glob) {
      super(glob, "address");
      initialize();
   }

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    */
   public Address(Global glob, String type) {
      super(glob, "address");
      initialize();
      setType(type);
   }

   public void setMaxMsg(long maxMsg) {
      this.maxMsg = maxMsg;
   }

   //public long getMaxMsg() {
   //   return this.maxMsg;
   //}

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
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
      initialize();
      setType(type);
   }

   /**
    * Configure property settings. 
    * "-delay[heron] 20" has precedence over "-delay 10"
    * @see #Address(String, String)
    */
   private void initialize()
   {
      setPort(glob.getProperty().get("port", getPort()));
      setPort(glob.getProperty().get("client.port", getPort())); // this is stronger (do we need it?)

      setType(glob.getProperty().get("client.protocol", getType()));
      setCollectTime(glob.getProperty().get("burstMode.collectTime", DEFAULT_collectTime));
      setCollectTimeOneway(glob.getProperty().get("burstMode.collectTimeOneway", DEFAULT_collectTimeOneway));
      setPingInterval(glob.getProperty().get("pingInterval", getDefaultPingInterval()));
      setRetries(glob.getProperty().get("retries", getDefaultRetries()));
      setDelay(glob.getProperty().get("delay", getDefaultDelay()));
      setOneway(glob.getProperty().get("oneway", DEFAULT_oneway));
      setCompressType(glob.getProperty().get("compress.type", DEFAULT_compressType));
      setMinSize(glob.getProperty().get("compress.minSize", DEFAULT_minSize));
      setPtpAllowed(glob.getProperty().get("ptpAllowed", DEFAULT_ptpAllowed));
      setSessionId(glob.getProperty().get("sessionId", DEFAULT_sessionId));
      setDispatchPlugin(glob.getProperty().get("DispatchPlugin.defaultPlugin", DEFAULT_dispatchPlugin));
      if (nodeId != null) {
         setPort(glob.getProperty().get("port["+nodeId+"]", getPort()));
         setPort(glob.getProperty().get("client.port["+nodeId+"]", getPort())); // this is stronger (do we need it?)

         setType(glob.getProperty().get("client.protocol["+nodeId+"]", getType()));
         setCollectTime(glob.getProperty().get("burstMode.collectTime["+nodeId+"]", getCollectTime()));
         setCollectTimeOneway(glob.getProperty().get("burstMode.collectTimeOneway["+nodeId+"]", getCollectTimeOneway()));
         setPingInterval(glob.getProperty().get("pingInterval["+nodeId+"]", getPingInterval()));
         setRetries(glob.getProperty().get("retries["+nodeId+"]", getRetries()));
         setDelay(glob.getProperty().get("delay["+nodeId+"]", getDelay()));
         setOneway(glob.getProperty().get("oneway["+nodeId+"]", oneway()));
         setCompressType(glob.getProperty().get("compress.type["+nodeId+"]", getCompressType()));
         setMinSize(glob.getProperty().get("compress.minSize["+nodeId+"]", getMinSize()));
         setPtpAllowed(glob.getProperty().get("ptpAllowed["+nodeId+"]", isPtpAllowed()));
         setSessionId(glob.getProperty().get("sessionId["+nodeId+"]", getSessionId()));
         setDispatchPlugin(glob.getProperty().get("DispatchPlugin.defaultPlugin["+nodeId+"]", dispatchPlugin));
      }

      // TODO: This is handled in QueueProperty.java already ->
      /*
      long maxMsg = glob.getProperty().get("queue.maxMsg", CbQueueProperty.DEFAULT_maxMsgDefault);
      setMaxMsg(maxMsg);
      if (nodeId != null) {
         setMaxMsg(glob.getProperty().get("queue.maxMsg["+nodeId+"]", getMaxMsg()));
      }
      */
   }

   /** How often to retry if connection fails: defaults to -1 (retry forever) */
   public int getDefaultRetries() { return -1; }

   /** Delay between connection retries in milliseconds (5000 is a good value): defaults to 0, a value bigger 0 switches fails save mode on */
   public long getDefaultDelay() { return 0; }
   // /* Delay between connection retries in milliseconds: defaults to 5000 (5 sec), a value of 0 switches fails save mode off */
   // public long getDefaultDelay() { return 5 * 1000L; };

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

   /** @return The literal address as given by getAddress() */
   public String toString() {
      return getAddress();
   }

   /**
    * Get a usage string for the connection parameters
    */
   public final String usage()
   {
      String text = "";
      text += "Control fail save connection to xmlBlaster server:\n";
      // is in QueueProperty.java: text += "   -queue.maxMsg       The max. capacity of the client queue in number of messages [" + CbQueueProperty.DEFAULT_maxMsgDefault + "].\n";
    //text += "   -queue.onOverflow   Error handling when queue is full, 'block | deadMessage' [" + CbQueueProperty.DEFAULT_onOverflow + "].\n";
    //text += "   -queue.onFailure    Error handling when connection failed (after all retries etc.) [" + CbQueueProperty.DEFAULT_onFailure + "].\n";
      text += "   -burstMode.collectTimeOneway Number of milliseconds we shall collect oneway publish messages [" + Address.DEFAULT_collectTime + "].\n";
      text += "                       This allows performance tuning, try set it to 200.\n";
    //text += "   -oneway             Shall the publish() messages be send oneway (no application level ACK) [" + Address.DEFAULT_oneway + "]\n";
      text += "   -pingInterval       Pinging every given milliseconds [" + getDefaultPingInterval() + "]\n";
      text += "   -retries            How often to retry if connection fails (-1 is forever) [" + getDefaultRetries() + "]\n";
      text += "   -delay              Delay between connection retries in milliseconds [" + getDefaultDelay() + "]\n";
      text += "                       A delay value > 0 switches fails save mode on, 0 switches it off\n";
    //text += "   -DispatchPlugin.defaultPlugin  Specify your specific dispatcher plugin [" + CallbackAddress.DEFAULT_dispatchPlugin + "]\n";
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
            a.setAddress("127.0.0.1:7600");
            a.setCollectTime(12345L);
            a.setPingInterval(54321L);
            a.setRetries(17);
            a.setDelay(7890L);
            a.setOneway(true);
            a.setSessionId("0x4546hwi89");
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
}


