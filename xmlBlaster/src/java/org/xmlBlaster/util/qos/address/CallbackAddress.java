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

   /**
    */
   public CallbackAddress(Global glob) {
      super(glob, "callback");
      this.instanceName = Constants.RELATING_CALLBACK;
      initialize();
   }

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XML-RPC"
    */
   public CallbackAddress(Global glob, String type) {
      super(glob, "callback");
      this.instanceName = Constants.RELATING_CALLBACK;
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
    *    <code>-/node/heron/dispatch/callback/retries 20</code>
    *   is precedence over
    *    <code>-dispatch/callback/retries 10</code>
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
   protected void initialize() {
      initHostname(glob.getCbHostname()); // don't use setHostname() as it would set isCardcodedHostname=true
      super.initialize();
   }

   /**
    * Shall this address be used for subject queue messages?
    * @return false if address is for session queue only
    */
   public boolean useForSubjectQueue() {
      return useForSubjectQueue.getValue();
   }

   /**
    * Shall this address be used for subject queue messages?
    * @param useForSubjectQueue false if address is for session queue only
    */
   public void useForSubjectQueue(boolean useForSubjectQueue) {
      this.useForSubjectQueue.setValue(useForSubjectQueue);
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

   public final String usage() {
      String text = "\nControl xmlBlaster server side callback (if we install a local callback server):\n";
      text += super.usage();
      text += "   -dispatch/" + this.instanceName + "/sessionId       The session ID which is passed to our callback server\n";
      text += "                                      update() method.\n";
      text += "   -dispatch/" + this.instanceName + "/burstMode/collectTime  Number of milliseconds xmlBlaster shall collect\n";
      text += "                                      callback messages [" + CallbackAddress.DEFAULT_collectTime + "].\n";
      text += "                                      The burst mode allows performance tuning, try set it to 200.\n";
      text += "   -dispatch/" + this.instanceName + "/oneway          Shall the update() messages be send oneway (no\n";
      text += "                                      application level ACK) [" + CallbackAddress.DEFAULT_oneway + "]\n";
      text += "   -dispatch/" + this.instanceName + "/pingInterval    Pinging every given milliseconds [" + getDefaultPingInterval() + "]\n";
      text += "   -dispatch/" + this.instanceName + "/retries         How often to retry if callback fails (-1 forever, 0 no retry, > 0\n";
      text += "                                      number of retries) [" + getDefaultRetries() + "]\n";
      text += "   -dispatch/" + this.instanceName + "/delay           Delay between callback retries in milliseconds [" + getDefaultDelay() + "]\n";
      text += "   -dispatch/" + this.instanceName + "/compress/type   With which format message be compressed on callback [" + CallbackAddress.DEFAULT_compressType + "]\n";
      text += "   -dispatch/" + this.instanceName + "/compress/minSize Messages bigger this size in bytes are compressed [" + CallbackAddress.DEFAULT_minSize + "]\n";
      text += "   -dispatch/" + this.instanceName + "/ptpAllowed      PtP messages wanted? false prevents spamming [" + CallbackAddress.DEFAULT_ptpAllowed + "]\n";
      text += "   -dispatch/" + this.instanceName + "/protocol        You can choose another protocol for the callback server\n";
      text += "                                      [defaults to -dispatch/clientSide/protocol]\n";
      //text += "   -dispatch/callback/DispatchPlugin/defaultPlugin  Specify your specific dispatcher plugin [" + CallbackAddress.DEFAULT_dispatchPlugin + "]\n";
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
            vec.addElement("-/node/" + nodeId + "/dispatch/callback/sessionId");
            vec.addElement("OK");
            vec.addElement("-dispatch/callback/sessionId");
            vec.addElement("ERROR");
            vec.addElement("-dispatch/callback/pingInterval");
            vec.addElement("8888");
            vec.addElement("-/node/" + nodeId + " /dispatch/callback/delay");
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


