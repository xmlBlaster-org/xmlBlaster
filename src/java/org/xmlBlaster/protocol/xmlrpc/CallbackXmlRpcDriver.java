/*------------------------------------------------------------------------------
Name:      CallbackXmlRpcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import java.io.IOException;
import java.net.ConnectException;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.xmlBlaster.client.protocol.xmlrpc.XmlRpcConnection;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.protocol.xmlrpc.XmlRpcClientFactory;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.I_ProgressListener;

/**
 * This object sends a MsgUnitRaw back to a client using XMLRPC interface, in
 * the same JVM.
 * <p>
 * The I_CallbackDriver.update() method of the client will be invoked
 *
 * @author Michele Laghi (michele@laghi.eu)
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver
 */
public class CallbackXmlRpcDriver implements I_CallbackDriver {
   private String ME = "CallbackXmlRpcDriver";
   private Global glob = null;
   private static Logger log = Logger.getLogger(CallbackXmlRpcDriver.class.getName());
   private CallbackAddress callbackAddress = null;
   private XmlRpcClient xmlRpcClient = null;

   private CallbackXmlRpcDriverSingleChannel singleChannelDriver;
   private boolean contentAsString;
   private boolean initialized = false;
   
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
      if (log.isLoggable(Level.FINE))
         log.fine("init invoked");
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
   public void init(Global global, CallbackAddress cbAddress) throws XmlBlasterException {
      this.glob = global;
      // workaround to pass this callback driver object to the other side (the AuthenticateImpl)
      glob.putInWeakRegistry(Thread.currentThread(), this);
      this.callbackAddress = cbAddress;
      if (callbackAddress != null)
         contentAsString = callbackAddress.getEnv("contentAsString", false).getValue();
      
      org.xmlBlaster.engine.ServerScope 
         engineGlob = (org.xmlBlaster.engine.ServerScope)glob.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope);
      if (engineGlob == null)
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "could not retreive the ServerNodeScope. Am I really on the server side ?");
   }


   public void postInit(String sessionId, XmlBlasterImpl xblImpl, boolean singleChannel, boolean useCDATA) throws XmlBlasterException {
      try {
         if (singleChannel) {
            singleChannelDriver = new CallbackXmlRpcDriverSingleChannel();
            singleChannelDriver.init(glob, callbackAddress);
            singleChannelDriver.register(sessionId, xblImpl);
         }
         else {
            try {
               if (callbackAddress == null)
                  throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "postInit() failed, callbackAddress is not known");
               xmlRpcClient = XmlRpcClientFactory.getXmlRpcClient(glob, null, callbackAddress, useCDATA);
               if (log.isLoggable(Level.FINE)) 
                  log.fine("Accessing client callback web server using given url=" + callbackAddress.getRawAddress());
            }
            catch (IOException ex1) {
               throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "init() failed", ex1);
            }
         }
      }
      finally {
         initialized = true;
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
   public final String[] sendUpdate(MsgUnitRaw[] msgArr) throws XmlBlasterException {

      // wait until initialization process has been fully completed since we don't know yet how the
      // client will have it: singleChannel or not
      long count = 0L;
      while (!initialized) {
         try {
            Thread.sleep(50L);
            count++;
            if (count > 1200L) {
            	// try maximum 1 minutes
            	if (log.isLoggable(Level.FINE)) {
            		log.fine("The callback is not initialized yet (from now on assuming multiChannel");
            		initialized = true;
            	}
                break;
            }
         }
         catch (Exception ex) {
         }
      }
      
      
      if (singleChannelDriver != null)
         return singleChannelDriver.sendUpdate(msgArr);
      
      if (msgArr == null || msgArr.length < 1) throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal update argument");

      if (xmlRpcClient == null) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "The xmlRpcClient on the server side is null");
      }
      // transform the msgUnits to Vectors
      try {
         String[] retVal = new String[msgArr.length];
         for (int ii=0; ii < msgArr.length; ii++) {
            Vector<Object> args = new Vector<Object>();
            MsgUnitRaw msgUnit = msgArr[ii];
            args.addElement(callbackAddress.getSecretSessionId());
            args.addElement(msgUnit.getKey());
            if (contentAsString)
               args.addElement(msgUnit.getContentStr());
            else
               args.addElement(msgUnit.getContent());
            args.addElement(msgUnit.getQos());

            if (log.isLoggable(Level.FINE)) log.fine("Send an update to the client ...");

            String tmp = (String)xmlRpcClient.execute("$default.update", args);

            final String XMLSCRIPT_UPDATE_TAG_START = "<update type='R'>";
            final String XMLSCRIPT_UPDATE_TAG_END = "</update>";
            int pos = tmp.indexOf(XMLSCRIPT_UPDATE_TAG_START);
            if (pos > -1) {
               tmp = tmp.substring(pos + XMLSCRIPT_UPDATE_TAG_START.length());
               pos = tmp.indexOf(XMLSCRIPT_UPDATE_TAG_END);
               if (pos > -1)
                  tmp = tmp.substring(0, pos);
               tmp = tmp.trim();
            }
            retVal[ii] = tmp;
            if (log.isLoggable(Level.FINE)) 
               log.fine("Successfully sent message update to '" + callbackAddress.getSecretSessionId() + "'");
         }
         return retVal;
      }
      catch (XmlRpcException ex) {
         int start = ex.toString().indexOf("errorCode=");
         
         // org.apache.xmlrpc.XmlRpcException: java.lang.Exception: RPC handler object not found for "update": no default handler registered.
         boolean isServerSide = ex.toString().indexOf("no default handler registered") != -1;
         
         // if (ex.linkedException != null && ex.linkedException instanceof ConnectException)
         if (isJavaNetEx(ex.linkedException))
            isServerSide = true;
         
         if (isServerSide) {
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, ex.toString());
         }
         
         if (start == -1) {
            ex.printStackTrace();
            String str = "Sending message to " + ((callbackAddress!=null)?callbackAddress.getRawAddress():"?") + " failed, received unexpected exception format from client";
            XmlBlasterException e2 = new XmlBlasterException(glob, ErrorCode.USER_UPDATE_HOLDBACK, ME, str, ex);
            e2.isServerSide(false);
            e2.setLocation("client");
            throw e2;
         }
         XmlBlasterException e = XmlRpcConnection.extractXmlBlasterException(glob, ex, ErrorCode.USER_UPDATE_ERROR, ME+".sendUpdate");
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

   private boolean isJavaNetEx(Throwable ex) {
	   boolean ret = false;
	   if (ex != null) {
		   ret  = ex.getClass().getName().startsWith("java.net.");
	   }
	   return ret;
   }

   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      if (singleChannelDriver != null) {
         singleChannelDriver.sendUpdateOneway(msgArr);
         return;
      }
      if (msgArr == null || msgArr.length < 1)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdateOneway() argument");

 
      // transform the msgUnits to Vectors
      try {
         for (int ii=0; ii < msgArr.length; ii++) {
            Vector<Object> args = new Vector<Object>();
            MsgUnitRaw msgUnit = msgArr[ii];
            args.addElement(callbackAddress.getSecretSessionId());
            args.addElement(msgUnit.getKey());
            if (contentAsString)
               args.addElement(msgUnit.getContentStr());
            else
               args.addElement(msgUnit.getContent());
            args.addElement(msgUnit.getQos());
          
            if (log.isLoggable(Level.FINE)) log.fine("Send an updateOneway to the client ...");

            xmlRpcClient.execute("$default.updateOneway", args);

            if (log.isLoggable(Level.FINE)) log.fine("Successfully sent message update to '" + callbackAddress.getSecretSessionId() + "'");
         }
      }
      catch (XmlRpcException ex) { // oneway: the client never sends an XmlBlasterException
         String str = "Sending oneway message to " + callbackAddress.getRawAddress() + " failed in client: " + ex.toString();
         if (log.isLoggable(Level.FINE)) log.fine(str);
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, ex.toString());
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
   public final String ping(String qos) throws XmlBlasterException {
      if (singleChannelDriver != null)
         return singleChannelDriver.ping(qos);
      
      if (qos != null && qos.indexOf(Constants.INFO_INITIAL) != -1) {
         if (log.isLoggable(Level.FINE)) log.fine("XmlRpc callback ping is suppressed as doing it before connect() and the callback is not functional");
         return Constants.RET_OK;
      }

      if (xmlRpcClient == null) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "Callback client is null");
      }
      try {
         Vector args = new Vector();
         args.addElement(qos);
         String ret = (String)xmlRpcClient.execute("$default.ping", args);
         
         final String XMLSCRIPT_PING_TAG_START = "<ping type='R'>";
         final String XMLSCRIPT_PING_TAG_END = "</ping>";
         int pos = ret.indexOf(XMLSCRIPT_PING_TAG_START);
         if (pos > -1) {
            ret = ret.substring(pos + XMLSCRIPT_PING_TAG_START.length());
            pos = ret.indexOf(XMLSCRIPT_PING_TAG_END);
            if (pos > -1)
               ret = ret.substring(0, pos);
            ret = ret.trim();
         }

         return ret;
      }
      catch (XmlRpcException ex) {
         String str = "Sending ping to " + callbackAddress.getRawAddress() + " failed in client: " + ex.toString();
         if (log.isLoggable(Level.FINEST)) log.finest(str);
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, ex.toString());
      }
      catch (Throwable e) {
         if (!(e instanceof IOException)) // IOException: callback connection refused if client is away, is normal behavior
            e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "XmlRpc callback ping failed", e);
      }
   }

   public I_ProgressListener registerProgressListener(I_ProgressListener listener) {
      if (log.isLoggable(Level.FINE)) 
         log.fine("Registering I_ProgressListener is not supported with this protocol plugin");
      return null;
   }

   /**
    * This method shuts down the driver.
    * <p />
    */
   public void shutdown() {
      //if (xmlRpcClient != null) xmlRpcClient.shutdown(); method is missing in XmlRpc package !!!
      initialized = false;
      if (singleChannelDriver != null) {
         singleChannelDriver.shutdown();
         singleChannelDriver = null;
      }
      //callbackAddress = null; If the callback driver keeps reconnect polling we need the address
      xmlRpcClient = null;
      if (log.isLoggable(Level.FINE)) log.fine("Shutdown implementation is missing");
   }

   /**
    * @return true if the plugin is still alive, false otherwise
    */
   public boolean isAlive() {
      return true;
   }

   public CallbackXmlRpcDriverSingleChannel getSingleChannelDriver() {
      return singleChannelDriver;
   }

   

}
