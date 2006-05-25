/*------------------------------------------------------------------------------
Name:      CallbackXmlRpcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.client.protocol.xmlrpc.XmlRpcConnection; // The XmlRpcException to XmlBlasterException converter

import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import java.io.IOException;
import java.util.Vector;

/**
 * This object sends a MsgUnitRaw back to a client using XMLRPC interface, in
 * the same JVM.
 * <p>
 * The I_CallbackDriver.update() method of the client will be invoked
 *
 * @author Michele Laghi (laghi@swissinfo.org)
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver
 */
public class CallbackXmlRpcDriver implements I_CallbackDriver
{
   private String ME = "CallbackXmlRpcDriver";
   private Global glob = null;
   private static Logger log = Logger.getLogger(CallbackXmlRpcDriver.class.getName());
   private CallbackAddress callbackAddress = null;
   private XmlRpcClient xmlRpcClient = null;

   /** Get a human readable name of this driver */
   public String getName() {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "XMLRPC"
    */
   public String getProtocolId() {
      return "XMLRPC";
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
      return callbackAddress.getRawAddress();
   }

   /**
    * Get callback reference here.
    * <p />
    * This method is enforced by interface I_CallbackDriver and is called by
    * xmlBlaster after instantiation of this class, telling us
    * the address to callback.
    * @param  callbackAddress Contains the stringified XMLRPC callback handle of
    *                      the client
    */
   public void init(Global glob, CallbackAddress callbackAddress) throws XmlBlasterException
   {
      this.glob = glob;

      this.callbackAddress = callbackAddress;
      try {
         xmlRpcClient = new XmlRpcClient(callbackAddress.getRawAddress());
         if (log.isLoggable(Level.FINE)) log.fine("Accessing client callback web server using given url=" + callbackAddress.getRawAddress());
      }
      catch (IOException ex1) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "init() failed", ex1);
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
   public final String[] sendUpdate(MsgUnitRaw[] msgArr) throws XmlBlasterException
   {
      if (msgArr == null || msgArr.length < 1) throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal update argument");
 
      // transform the msgUnits to Vectors
      try {
         String[] retVal = new String[msgArr.length];
         for (int ii=0; ii < msgArr.length; ii++) {
            Vector args = new Vector();
            MsgUnitRaw msgUnit = msgArr[ii];
            args.addElement(callbackAddress.getSecretSessionId());
            args.addElement(msgUnit.getKey());
            args.addElement(msgUnit.getContent());
            args.addElement(msgUnit.getQos());
          
            if (log.isLoggable(Level.FINE)) log.fine("Send an update to the client ...");

            retVal[ii] = (String)xmlRpcClient.execute("update", args);

            if (log.isLoggable(Level.FINE)) log.fine("Successfully sent message update to '" + callbackAddress.getSecretSessionId() + "'");
         }
         return retVal;
      }
      catch (XmlRpcException ex) {
         int start = ex.toString().indexOf("errorCode=");
         if (start == -1) {
            ex.printStackTrace();
            String str = "Sending message to " + ((callbackAddress!=null)?callbackAddress.getRawAddress():"?") + " failed, receivunexpected exception format from client";
            XmlBlasterException e2 = new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, str, ex);
            e2.isServerSide(false);
            e2.setLocation("client");
            throw e2;
         }
         XmlBlasterException e = XmlRpcConnection.extractXmlBlasterException(glob, ex);
         e.isServerSide(false);
         e.setLocation(this.getClass().getName());
         String str = "Sending message to " + ((callbackAddress!=null)?callbackAddress.getRawAddress():"?") + " failed in client: " + ex.toString();
         if (log.isLoggable(Level.FINE)) log.fine(str);
         // The remote client is only allowed to throw USER* errors!
         throw XmlBlasterException.tranformCallbackException(e);
      }
      catch (Throwable e) { // e.g. IOException
         String str = "Sending message to " + ((callbackAddress!=null)?callbackAddress.getRawAddress():"?") + " failed: " + e.toString();
         if (log.isLoggable(Level.FINE)) log.fine(str);
         e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "CallbackFailed", e);
      }
   }

   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgUnitRaw[] msgArr) throws XmlBlasterException
   {
      if (msgArr == null || msgArr.length < 1)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdateOneway() argument");

 
      // transform the msgUnits to Vectors
      try {
         for (int ii=0; ii < msgArr.length; ii++) {
            Vector args = new Vector();
            MsgUnitRaw msgUnit = msgArr[ii];
            args.addElement(callbackAddress.getSecretSessionId());
            args.addElement(msgUnit.getKey());
            args.addElement(msgUnit.getContent());
            args.addElement(msgUnit.getQos());
          
            if (log.isLoggable(Level.FINE)) log.fine("Send an updateOneway to the client ...");

            xmlRpcClient.execute("updateOneway", args);

            if (log.isLoggable(Level.FINE)) log.fine("Successfully sent message update to '" + callbackAddress.getSecretSessionId() + "'");
         }
      }
      catch (XmlRpcException ex) {
         XmlBlasterException e = XmlRpcConnection.extractXmlBlasterException(glob, ex);
         String str = "Sending oneway message to " + callbackAddress.getRawAddress() + " failed in client: " + ex.toString();
         if (log.isLoggable(Level.FINE)) log.fine(str);
         throw new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ERROR, ME, "CallbackFailed", e);
      }
      catch (Throwable e) {
         String str = "Sending oneway message to " + callbackAddress.getRawAddress() + " failed: " + e.toString();
         if (log.isLoggable(Level.FINE)) log.fine(str);
         //e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "CallbackFailed", e);
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
      }
      catch (XmlRpcException ex) {
         XmlBlasterException e = XmlRpcConnection.extractXmlBlasterException(glob, ex);
         throw new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ERROR, ME, "XmlRpc callback ping - got exception from client", e);
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "XmlRpc callback ping failed", e);
      }
   }

   public I_ProgressListener registerProgressListener(I_ProgressListener listener) {
      if (log.isLoggable(Level.FINE)) log.fine("Registering I_ProgressListener is not supported with this protocol plugin");
      return null;
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
      if (log.isLoggable(Level.FINE)) log.fine("Shutdown implementation is missing");
   }

   /**
    * @return true if the plugin is still alive, false otherwise
    */
   public boolean isAlive() {
      return true;
   }


}
