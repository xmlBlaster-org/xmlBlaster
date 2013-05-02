/*------------------------------------------------------------------------------
Name:      CallbackAddress.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback address string and protocol string
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos.address;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;


/**
 * Helper class holding callback address string and protocol string.
 * <p />
 * <pre>
 * &lt;callback type='XMLRPC' sessionId='4e56890ghdFzj0'
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
   /**
    */
   public CallbackAddress(Global glob) {
      super(glob, Constants.RELATING_CALLBACK); // "callback"
      this.instanceName = Constants.RELATING_CALLBACK;
      initialize();
   }

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XMLRPC"
    */
   public CallbackAddress(Global glob, String type) {
      super(glob, Constants.RELATING_CALLBACK); // "callback"
      this.instanceName = Constants.RELATING_CALLBACK;
      setType(type);
      initialize();
   }

   /** How often to retry if connection fails: defaults to 0 retries, on failure we give up */
   public int getDefaultRetries() { return 0; }

   /** Delay between connection retries in milliseconds: defaults to one minute */
   public long getDefaultDelay() { return Constants.MINUTE_IN_MILLIS; };

   /** Ping interval: pinging every given milliseconds, defaults to one minute */
   public long getDefaultPingInterval() { return Constants.MINUTE_IN_MILLIS; }

   /**
    * @param type    The protocol type, e.g. "IOR", "EMAIL", "XMLRPC"
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
      setType(type);
      initialize();
   }
   
   /**
    * Configure property settings
    */
   protected void initialize() {
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

   /** @return The literal address as given by getRawAddress() */
   public String toString() {
      return getRawAddress();
   }

   /** Client side usage used by XmlBlasterAccess */
   public final String usage() {
      String text = "\nControl xmlBlaster server side callback (if we install a local callback server):\n";
      text += super.usage();
      text += "   -dispatch/" + this.instanceName + "/sessionId\n";
      text += "                       The session ID which is passed to our callback server's update() method.\n";
      text += "   -dispatch/" + this.instanceName + "/burstMode/collectTime\n";
      text += "                       Number of milliseconds xmlBlaster shall collect\n";
      text += "                       callback messages [" + CallbackAddress.DEFAULT_collectTime + "].\n";
      text += "                       The burst mode allows performance tuning, try set it to 200.\n";
      text += "   -dispatch/" + this.instanceName + "/burstMode/maxEntries\n";
      text += "                       The maximum bulk size of a callback invocation [" + DEFAULT_burstModeMaxEntries + "]\n";
      text += "                       -1 takes all entries of highest priority available in the\n";
      text += "                       callback RAM queue possibly limited by maxBytes\n";
      text += "   -dispatch/" + this.instanceName + "/burstMode/maxBytes\n";
      text += "                       The maximum bulk size of a callback invocation [" + DEFAULT_burstModeMaxBytes + "]\n";
      text += "                       -1L takes all entries of highest priority available in the\n";
      text += "                       callback RAM queue possibly limited by maxEntries\n";
      text += "   -dispatch/" + this.instanceName + "/oneway\n";
      text += "                       Shall the update() messages be send oneway (no\n";
      text += "                       application level ACK) [" + CallbackAddress.DEFAULT_oneway + "]\n";
      text += "   -dispatch/" + this.instanceName + "/dispatcherActive\n";
      text += "                       If false inhibit delivery of callback messages [" + DEFAULT_dispatcherActive + "]\n";
      /*
      text += "   -dispatch/" + this.instanceName + "/compress/type\n";
      text += "                                      With which format message be compressed on callback [" + CallbackAddress.DEFAULT_compressType + "]\n";
      text += "   -dispatch/" + this.instanceName + "/compress/minSize\n";
      text += "                                      Messages bigger this size in bytes are compressed [" + CallbackAddress.DEFAULT_minSize + "]\n";
      */
      text += "   -dispatch/" + this.instanceName + "/ptpAllowed\n";
      text += "                       PtP messages wanted? false prevents spamming [" + CallbackAddress.DEFAULT_ptpAllowed + "]\n";
      
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

   public void setFromPersistenceRecovery(boolean fromPersistenceRecovery) {
      this.fromPersistenceRecovery = fromPersistenceRecovery;
   }
}


