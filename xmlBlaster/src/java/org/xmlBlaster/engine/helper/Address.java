/*------------------------------------------------------------------------------
Name:      Address.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding address string and protocol string
Version:   $Id: Address.java,v 1.8 2002/05/16 15:35:22 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;

import org.xmlBlaster.util.Log;
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
   private int maxMsg;

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

   public void setMaxMsg(int maxMsg) {
      this.maxMsg = maxMsg;
   }

   public int getMaxMsg() {
      return this.maxMsg;
   }

    /*
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    * @param address A address address for your client, suitable to the protocol
   public Address(String type, String address) {
      super("address");
      initialize();
      setType(type);
      setAddress(address);
   }
      */

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
      if (nodeId != null) {
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
      }

      // TODO: This is handled in QueueProperty.java already -> 
      setMaxMsg(glob.getProperty().get("queue.maxMsg", CbQueueProperty.DEFAULT_maxMsgDefault));
      if (nodeId != null) {
         setMaxMsg(glob.getProperty().get("queue.maxMsg["+nodeId+"]", getMaxMsg()));
      }
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
         buf.append(" delay=").append(getDelay()).append(" retries=").append(getRetries()).append(" maxMsg=").append(getMaxMsg()).append(" pingInterval=").append(getPingInterval());
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
    //text += "   -queue.onOverflow   Error handling when queue is full, 'block | deadLetter' [" + CbQueueProperty.DEFAULT_onOverflow + "].\n";
    //text += "   -queue.onFailure    Error handling when connection failed (after all retries etc.) [" + CbQueueProperty.DEFAULT_onFailure + "].\n";
      text += "   -burstMode.collectTimeOneway Number of milliseconds we shall collect oneway publish messages [" + Address.DEFAULT_collectTime + "].\n";
      text += "                       This allows performance tuning, try set it to 200.\n";
    //text += "   -oneway             Shall the publish() messages be send oneway (no application level ACK) [" + Address.DEFAULT_oneway + "]\n";
      text += "   -pingInterval       Pinging every given milliseconds [" + getDefaultPingInterval() + "]\n";
      text += "   -retries            How often to retry if connection fails (-1 is forever) [" + getDefaultRetries() + "]\n";
      text += "   -delay              Delay between connection retries in milliseconds [" + getDefaultDelay() + "]\n";
      text += "                       A delay value > 0 switches fails save mode on, 0 switches it off\n";
    //text += "   -compress.type      With which format message be compressed on callback [" + Address.DEFAULT_compressType + "]\n";
    //text += "   -compress.minSize   Messages bigger this size in bytes are compressed [" + Address.DEFAULT_minSize + "]\n";
      return text;
   }

   /** For testing: java org.xmlBlaster.engine.helper.Address */
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
            Log.info(ME, a.toXml());
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
            Log.info(ME, a.toXml());
            //Log.info(ME, glob.getProperty().toXml());
         }
      }
      catch(Throwable e) {
         e.printStackTrace();
         Log.error("TestFailed", e.toString());
      }
   }
}


