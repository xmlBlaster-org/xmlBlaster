/*------------------------------------------------------------------------------
Name:      CallbackAddress.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback address string and protocol string
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.address;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.Constants;
import org.xml.sax.Attributes;


/**
 * Helper class holding callback address string and protocol string.
 * <p />
 * <pre>
 * &lt;callback type='XML-RPC' sessionId='4e56890ghdFzj0'
 *           pingInterval='60000' retries='5' delay='10000'
 *           oneway='false' useForSubjectQueue='true'
 *           dispatchPlugin='Priority,1.0'>
 *    http://server:8080/cb
 *    &lt;compress type='gzip' minSize='1000'/>
 *    &lt;burstMode collectTime='400'/>
 * &lt;/callback>
 * </pre>
 */
public class CallbackAddress extends AddressBase
{
   private static final String ME = "CallbackAddress";
   private String nodeId = null;

   /**
    */
   public CallbackAddress(Global glob) {
      super(glob, "callback");
      initialize();
   }

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    */
   public CallbackAddress(Global glob, String type) {
      super(glob, "callback");
      initialize();
      setType(type);
   }

   /** How often to retry if connection fails: defaults to 0 retries, on failure we give up */
   public int getDefaultRetries() { return 0; }

   /** Delay between connection retries in milliseconds: defaults to one minute */
   public long getDefaultDelay() { return Constants.MINUTE_IN_MILLIS; };

   /** Ping interval: pinging every given milliseconds, defaults to one minute */
   public long getDefaultPingInterval() { return Constants.MINUTE_IN_MILLIS; }

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    * @param nodeId  A unique string (typically the cluster node id we connect to).<br />
    *   This is used for extended env-variable support, e.g. for a given
    *    <code>nodeId="heron"</ code>
    *   the command line argument (or xmlBlaster.property entry)
    *    <code>-cb.retries[heron] 20</code>
    *   is precedence over
    *    <code>-cb.retries 10</code>
    */
   public CallbackAddress(Global glob, String type, String nodeId) {
      super(glob, "callback");
      this.nodeId = nodeId;
      initialize();
      setType(type);
   }
   
   /**
    * Configure property settings
    */
   private void initialize() {
      initHostname(glob.getCbHostname()); // don't use setHostname() as it would set isCardcodedHostname=true

      // Choose same protocol for callback as for sending (as a default)
      String protocolType = glob.getProperty().get("client.protocol", getType());
      setType(glob.getProperty().get("cb.protocol", protocolType));

      setPort(glob.getProperty().get("cb.port", getPort()));
      setCollectTime(glob.getProperty().get("cb.burstMode.collectTime", DEFAULT_collectTime)); // sync update()
      setCollectTimeOneway(glob.getProperty().get("cb.burstMode.collectTimeOneway", DEFAULT_collectTimeOneway)); // oneway update()
      setPingInterval(glob.getProperty().get("cb.pingInterval", getDefaultPingInterval()));
      setRetries(glob.getProperty().get("cb.retries", getDefaultRetries()));
      setDelay(glob.getProperty().get("cb.delay", getDefaultDelay()));
      useForSubjectQueue(glob.getProperty().get("cb.useForSubjectQueue", DEFAULT_useForSubjectQueue));
      setOneway(glob.getProperty().get("cb.oneway", DEFAULT_oneway));
      setCompressType(glob.getProperty().get("cb.compress.type", DEFAULT_compressType));
      setMinSize(glob.getProperty().get("cb.compress.minSize", DEFAULT_minSize));
      setPtpAllowed(glob.getProperty().get("cb.ptpAllowed", DEFAULT_ptpAllowed));
      setSecretSessionId(glob.getProperty().get("cb.sessionId", DEFAULT_sessionId));
      setDispatchPlugin(glob.getProperty().get("cb.DispatchPlugin.defaultPlugin", DEFAULT_dispatchPlugin));
      if (nodeId != null) {
         // Choose same protocol for callback as for sending (as a default)
         String protocolTypeNode = glob.getProperty().get("client.protocol["+nodeId+"]", getType());
         setType(glob.getProperty().get("cb.protocol["+nodeId+"]", protocolTypeNode));

         setPort(glob.getProperty().get("cb.port["+nodeId+"]", getPort()));
         setCollectTime(glob.getProperty().get("cb.burstMode.collectTime["+nodeId+"]", collectTime));
         setCollectTimeOneway(glob.getProperty().get("cb.burstMode.collectTimeOneway["+nodeId+"]", collectTimeOneway));
         setPingInterval(glob.getProperty().get("cb.pingInterval["+nodeId+"]", pingInterval));
         setRetries(glob.getProperty().get("cb.retries["+nodeId+"]", retries));
         setDelay(glob.getProperty().get("cb.delay["+nodeId+"]", delay));
         useForSubjectQueue(glob.getProperty().get("cb.useForSubjectQueue["+nodeId+"]", useForSubjectQueue));
         setOneway(glob.getProperty().get("cb.oneway["+nodeId+"]", oneway));
         setCompressType(glob.getProperty().get("cb.compress.type["+nodeId+"]", compressType));
         setMinSize(glob.getProperty().get("cb.compress.minSize["+nodeId+"]", minSize));
         setPtpAllowed(glob.getProperty().get("cb.ptpAllowed["+nodeId+"]", ptpAllowed));
         setSecretSessionId(glob.getProperty().get("cb.sessionId["+nodeId+"]", sessionId));
         setDispatchPlugin(glob.getProperty().get("cb.DispatchPlugin.defaultPlugin["+nodeId+"]", dispatchPlugin));
      }
   }

   /**
    * Shall this address be used for subject queue messages?
    * @return false if address is for session queue only
    */
   public boolean useForSubjectQueue() {
      return useForSubjectQueue;
   }

   /**
    * Shall this address be used for subject queue messages?
    * @param useForSubjectQueue false if address is for session queue only
    */
   public void useForSubjectQueue(boolean useForSubjectQueue) {
      this.useForSubjectQueue = useForSubjectQueue;
   }

   /**
    * The identifier sent to the callback client, the client can decide if he trusts this invocation
    * @see AddressBase#setSecretSessionId(String)
    */
   public void setSecretCbSessionId(String cbSessionId) {
      setSecretSessionId(cbSessionId);
   }

   /** @return The literal address as given by getAddress() */
   public String toString() {
      return getAddress();
   }

   /**
    * Get a usage string for the server side supported callback connection parameters
    */
   public String usage()
   {
      String text = "\n";
      text += "Control xmlBlaster server side callback (if we install a local callback server):\n";
      text += "   -cb.sessionId       The session ID which is passed to our callback server update() method.\n";
      text += "   -cb.burstMode.collectTime Number of milliseconds xmlBlaster shall collect callback messages [" + CallbackAddress.DEFAULT_collectTime + "].\n";
      text += "                         The burst mode allows performance tuning, try set it to 200.\n";
      text += "   -cb.oneway          Shall the update() messages be send oneway (no application level ACK) [" + CallbackAddress.DEFAULT_oneway + "]\n";
      text += "   -cb.pingInterval    Pinging every given milliseconds [" + getDefaultPingInterval() + "]\n";
      text += "   -cb.retries         How often to retry if callback fails (-1 forever, 0 no retry, > 0 number of retries) [" + getDefaultRetries() + "]\n";
      text += "   -cb.delay           Delay between callback retries in milliseconds [" + getDefaultDelay() + "]\n";
      text += "   -cb.compress.type   With which format message be compressed on callback [" + CallbackAddress.DEFAULT_compressType + "]\n";
      text += "   -cb.compress.minSize Messages bigger this size in bytes are compressed [" + CallbackAddress.DEFAULT_minSize + "]\n";
      text += "   -cb.ptpAllowed      PtP messages wanted? false prevents spamming [" + CallbackAddress.DEFAULT_ptpAllowed + "]\n";
      text += "   -cb.protocol        You can choose another protocol for the callback server [defaults to -client.protocol]\n";
      //text += "   -cb.DispatchPlugin.defaultPlugin  Specify your specific dispatcher plugin [" + CallbackAddress.DEFAULT_dispatchPlugin + "]\n";
      return text;
   }

   /** For testing: java org.xmlBlaster.util.qos.address.CallbackAddress */
   public static void main(String[] argsDefault) {
      try {
         {
            Global glob = new Global(argsDefault);
            CallbackAddress a = new CallbackAddress(glob);
            a.setType("SOCKET");
            a.setAddress("127.0.0.1:7600");
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
            vec.addElement("-cb.sessionId["+nodeId+"]");
            vec.addElement("OK");
            vec.addElement("-cb.sessionId");
            vec.addElement("ERROR");
            vec.addElement("-cb.pingInterval");
            vec.addElement("8888");
            vec.addElement("-cb.delay["+nodeId+"]");
            vec.addElement("8888");
            String[] args = (String[])vec.toArray(new String[0]);

            Global glob = new Global(args);
            CallbackAddress a = new CallbackAddress(glob, "RMI", nodeId);
            System.out.println(a.toXml());
            //System.out.println(glob.getProperty().toXml());
         }
      }
      catch(Throwable e) {
         e.printStackTrace();
         System.out.println("TestFailed"+e.toString());
      }
   }
}


