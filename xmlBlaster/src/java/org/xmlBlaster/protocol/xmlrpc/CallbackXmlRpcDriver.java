/*------------------------------------------------------------------------------
Name:      CallbackXmlRpcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using XML-RPC interface.
Version:   $Id: CallbackXmlRpcDriver.java,v 1.20 2002/09/18 16:30:04 laghi Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.queue.MsgQueueEntry;
import org.xmlBlaster.client.protocol.xmlrpc.XmlRpcConnection; // The XmlRpcException to XmlBlasterException converter

import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import java.io.IOException;
import java.util.Vector;

/**
 * This object sends a MessageUnit back to a client using XML-RPC interface, in
 * the same JVM.
 * <p>
 * The I_CallbackDriver.update() method of the client will be invoked
 *
 * @author Michele Laghi (laghi@swissinfo.org)
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 * @see org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver
 */
public class CallbackXmlRpcDriver implements I_CallbackDriver
{
   private String ME = "CallbackXmlRpcDriver";
   private Global glob = null;
   private LogChannel log;
   private CallbackAddress callbackAddress = null;
   private XmlRpcClient xmlRpcClient = null;

   /** Get a human readable name of this driver */
   public String getName() {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "XML-RPC"
    */
   public String getProtocolId() {
      return "XML-RPC";
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getProtocolId();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return "1.0";
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
   }

   /**
    * Get the address how to access this driver. 
    * @return "http://server.mars.universe:8080/"
    */
   public String getRawAddress() {
      return callbackAddress.getAddress();
   }

   /**
    * Get callback reference here.
    * <p />
    * This method is enforced by interface I_CallbackDriver and is called by
    * xmlBlaster after instantiation of this class, telling us
    * the address to callback.
    * @param  callbackAddress Contains the stringified XML-RPC callback handle of
    *                      the client
    */
   public void init(Global glob, CallbackAddress callbackAddress) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = glob.getLog("xmlrpc");
      this.callbackAddress = callbackAddress;
      try {
         xmlRpcClient = new XmlRpcClient(callbackAddress.getAddress());
         if (log.TRACE) log.trace(ME, "Accessing client callback web server using given url=" + callbackAddress.getAddress());
      }
      catch (IOException ex1) {
         throw new XmlBlasterException(ME, ex1.toString());
      }
   }

   /**
    * This sends the update to the client.
    * <p />
    * This method is enforced by interface I_CallbackDriver and is called by
    * @return Clients should return a qos as follows.
    *         An empty qos string "" is valid as well and
    *         interpreted as OK
    * <pre>
    *  &lt;qos>
    *     &lt;state id='OK'/>
    *  &lt;/qos>
    * </pre>
    * @exception e.id="CallbackFailed", should be caught and handled appropriate
    */
   public final String[] sendUpdate(MsgQueueEntry[] msg) throws XmlBlasterException
   {
      if (msg == null || msg.length < 1) throw new XmlBlasterException(ME, "Illegal update argument");
 
      // transform the msgUnits to Vectors
      try {
         String[] retVal = new String[msg.length];
         for (int ii=0; ii < msg.length; ii++) {
            Vector args = new Vector();
            MessageUnit msgUnit = msg[ii].getMessageUnit();
            args.addElement(callbackAddress.getSessionId());
            args.addElement(msgUnit.getXmlKey());
            args.addElement(msgUnit.getContent());
            args.addElement(msgUnit.getQos());
          
            if (log.TRACE) log.trace(ME, "Send an update to the client ...");

            retVal[ii] = (String)xmlRpcClient.execute("update", args);

            if (log.TRACE) log.trace(ME, "Successfully sent message '" + msgUnit.getXmlKey()
                + "' update from sender '" + msg[0].getPublisherName() + "' to '" + callbackAddress.getSessionId() + "'");
         }
         return retVal;
      }
      catch (XmlRpcException ex) {
         XmlBlasterException e = XmlRpcConnection.extractXmlBlasterException(ex);
         String str = "Sending message to " + callbackAddress.getAddress() + " failed in client: " + ex.toString();
         if (log.TRACE) log.trace(ME + ".sendUpdate", str);
         throw new XmlBlasterException("CallbackFailed", str);
      }
      catch (Throwable e) {
         String str = "Sending message to " + callbackAddress.getAddress() + " failed: " + e.toString();
         if (log.TRACE) log.trace(ME + ".sendUpdate", str);
         e.printStackTrace();
         throw new XmlBlasterException("CallbackFailed", str);
      }
   }

   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgQueueEntry[] msg) throws XmlBlasterException
   {
      if (msg == null || msg.length < 1) throw new XmlBlasterException(ME, "Illegal updateOneway argument");
 
      // transform the msgUnits to Vectors
      try {
         for (int ii=0; ii < msg.length; ii++) {
            Vector args = new Vector();
            MessageUnit msgUnit = msg[ii].getMessageUnit();
            args.addElement(callbackAddress.getSessionId());
            args.addElement(msgUnit.getXmlKey());
            args.addElement(msgUnit.getContent());
            args.addElement(msgUnit.getQos());
          
            if (log.TRACE) log.trace(ME, "Send an updateOneway to the client ...");

            xmlRpcClient.execute("updateOneway", args);

            if (log.TRACE) log.trace(ME, "Successfully sent message '" + msgUnit.getXmlKey()
                + "' update from sender '" + msg[0].getPublisherName() + "' to '" + callbackAddress.getSessionId() + "'");
         }
      }
      catch (XmlRpcException ex) {
         XmlBlasterException e = XmlRpcConnection.extractXmlBlasterException(ex);
         String str = "Sending oneway message to " + callbackAddress.getAddress() + " failed in client: " + ex.toString();
         if (log.TRACE) log.trace(ME + ".sendUpdateOneway", str);
         throw new XmlBlasterException("CallbackFailed", str);
      }
      catch (Throwable e) {
         String str = "Sending oneway message to " + callbackAddress.getAddress() + " failed: " + e.toString();
         if (log.TRACE) log.trace(ME + ".sendUpdateOneway", str);
         e.printStackTrace();
         throw new XmlBlasterException("CallbackFailed", str);
      }
   }

   /**
    * Ping to check if callback server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    * @exception XmlBlasterException If client not reachable
    */
   public final String ping(String qos) throws XmlBlasterException
   {
      try {
         Vector args = new Vector();
         args.addElement("");
         return (String)xmlRpcClient.execute("ping", args);
      } catch (Throwable e) {
         throw new XmlBlasterException("CallbackPingFailed", "XmlRpc callback ping failed: " + e.toString());
      }
   }

   /**
    * This method shuts down the driver.
    * <p />
    */
   public void shutdown()
   {
      //if (xmlRpcClient != null) xmlRpcClient.shutdown(); method is missing in XmlRpc package !!!
      callbackAddress = null;
      xmlRpcClient = null;
      if (log.TRACE) log.trace(ME, "Shutdown implementation is missing");
   }
}
