/*------------------------------------------------------------------------------
Name:      Address.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding address string and protocol string
Version:   $Id: Address.java,v 1.3 2002/05/02 12:35:43 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.helper;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xml.sax.Attributes;


/**
 * Helper class holding address string and protocol string.
 * <p />
 * <pre>
 * &lt;address type='XML-RPC' sessionId='4e56890ghdFzj0'
 *           pingInterval='60000' retries='5' delay='10000'
 *           oneway='false'>
 *    http://server:8080/cb
 *    &lt;compress type='gzip' minSize='1000'/>
 *    &lt;burstMode collectTime='400'/>
 * &lt;/address>
 * </pre>
 */
public class Address extends AddressBase
{
   private static final String ME = "Address";
   private String nodeId = null;

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
      collectTime = glob.getProperty().get("collectTime", DEFAULT_collectTime);
      pingInterval = glob.getProperty().get("pingInterval", DEFAULT_pingInterval);
      retries = glob.getProperty().get("retries", DEFAULT_retries);
      delay = glob.getProperty().get("delay", DEFAULT_delay);
      oneway = glob.getProperty().get("oneway", DEFAULT_oneway);
      compressType = glob.getProperty().get("compress.type", DEFAULT_compressType);
      minSize = glob.getProperty().get("compress.minSize", DEFAULT_minSize);
      ptpAllowed = glob.getProperty().get("ptpAllowed", DEFAULT_ptpAllowed);
      sessionId = glob.getProperty().get("sessionId", DEFAULT_sessionId);
      if (nodeId != null) {
         collectTime = glob.getProperty().get("collectTime["+nodeId+"]", collectTime);
         pingInterval = glob.getProperty().get("pingInterval["+nodeId+"]", pingInterval);
         retries = glob.getProperty().get("retries["+nodeId+"]", retries);
         delay = glob.getProperty().get("delay["+nodeId+"]", delay);
         oneway = glob.getProperty().get("oneway["+nodeId+"]", oneway);
         compressType = glob.getProperty().get("compress.type["+nodeId+"]", compressType);
         minSize = glob.getProperty().get("compress.minSize["+nodeId+"]", minSize);
         ptpAllowed = glob.getProperty().get("ptpAllowed["+nodeId+"]", ptpAllowed);
         sessionId = glob.getProperty().get("sessionId["+nodeId+"]", sessionId);
      }
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


