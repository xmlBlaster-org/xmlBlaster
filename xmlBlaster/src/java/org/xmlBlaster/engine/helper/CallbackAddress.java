/*------------------------------------------------------------------------------
Name:      CallbackAddress.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding callback address string and protocol string
Version:   $Id: CallbackAddress.java,v 1.11 2002/05/02 12:35:43 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xml.sax.Attributes;


/**
 * Helper class holding callback address string and protocol string.
 * <p />
 * <pre>
 * &lt;callback type='XML-RPC' sessionId='4e56890ghdFzj0'
 *           pingInterval='60000' retries='5' delay='10000'
 *           oneway='false' useForSubjectQueue='true'>
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
      collectTime = glob.getProperty().get("cb.burstMode.collectTime", DEFAULT_collectTime);
      pingInterval = glob.getProperty().get("cb.pingInterval", DEFAULT_pingInterval);
      retries = glob.getProperty().get("cb.retries", DEFAULT_retries);
      delay = glob.getProperty().get("cb.delay", DEFAULT_delay);
      useForSubjectQueue = glob.getProperty().get("cb.useForSubjectQueue", DEFAULT_useForSubjectQueue);
      oneway = glob.getProperty().get("cb.oneway", DEFAULT_oneway);
      compressType = glob.getProperty().get("cb.compress.type", DEFAULT_compressType);
      minSize = glob.getProperty().get("cb.compress.minSize", DEFAULT_minSize);
      ptpAllowed = glob.getProperty().get("cb.ptpAllowed", DEFAULT_ptpAllowed);
      sessionId = glob.getProperty().get("cb.sessionId", DEFAULT_sessionId);
      if (nodeId != null) {
         collectTime = glob.getProperty().get("cb.burstMode.collectTime["+nodeId+"]", collectTime);
         pingInterval = glob.getProperty().get("cb.pingInterval["+nodeId+"]", pingInterval);
         retries = glob.getProperty().get("cb.retries["+nodeId+"]", retries);
         delay = glob.getProperty().get("cb.delay["+nodeId+"]", delay);
         useForSubjectQueue = glob.getProperty().get("cb.useForSubjectQueue["+nodeId+"]", useForSubjectQueue);
         oneway = glob.getProperty().get("cb.oneway["+nodeId+"]", oneway);
         compressType = glob.getProperty().get("cb.compress.type["+nodeId+"]", compressType);
         minSize = glob.getProperty().get("cb.compress.minSize["+nodeId+"]", minSize);
         ptpAllowed = glob.getProperty().get("cb.ptpAllowed["+nodeId+"]", ptpAllowed);
         sessionId = glob.getProperty().get("cb.sessionId["+nodeId+"]", sessionId);
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
    * @return true if we may send PtP messages
    */
   public boolean isPtpAllowed() {
      return this.ptpAllowed;
   }

   /**
    * @param Set if we accept point to point messages
    */
   public void setPtpAllowed(boolean ptpAllowed) {
      this.ptpAllowed = ptpAllowed;
   }

   /** For testing: java org.xmlBlaster.engine.helper.CallbackAddress */
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
            a.setSessionId("0x4546hwi89");
            Log.info(ME, a.toXml());
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


