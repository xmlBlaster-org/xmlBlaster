/*------------------------------------------------------------------------------
Name:      CbInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Version:   $Id: CbInfo.java,v 1.12 2002/04/24 06:51:08 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.callback;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.queue.MsgQueueEntry;
import java.util.Hashtable;
import java.util.StringTokenizer;


/**
 * Holding all necessary infos to establish callback
 * connections and invoke their update().
 */
public class CbInfo
{
   public final String ME = "CbInfo";
   private final Global glob;
   private I_CallbackDriver[] callbackDrivers = null;
   private CallbackAddress[] cbArr = null;
   /** Map holding the Class of all protocol I_CallbackDriver.java implementations, e.g. CallbackCorbaDriver */
   private static Hashtable protocols = null;

   /*
   public CbInfo()
   {
      callbackDrivers = new I_CallbackDriver[0];
      cbArr = new CallbackAddress[0];
   }
   */

   public CbInfo(Global glob, CallbackAddress[] cbArr) throws XmlBlasterException
   {
      this.glob = glob;
      initialize(cbArr);
   }

   public void initialize(CallbackAddress[] cbArr_) throws XmlBlasterException
   {
      this.cbArr = cbArr_;
      loadDrivers();
      if (cbArr == null || cbArr.length==0) {
         callbackDrivers = new I_CallbackDriver[0];
         cbArr = new CallbackAddress[0];
      }
      else {
         callbackDrivers = new I_CallbackDriver[cbArr.length];
         for (int ii=0; ii<cbArr.length; ii++) {
 
            Object obj = protocols.get(cbArr[ii].getType());
            if (obj == null) {
               Log.error(ME+".UnknownCallbackProtocol", "Sorry, callback type='" + cbArr[ii].getType() + "' is not supported, try setting it in xmlBlaster.properties");
               throw new XmlBlasterException("UnknownCallbackProtocol", "Sorry, callback type='" + cbArr[ii].getType() + "' is not supported");
            }

            // Check if a native callback driver is passed in the glob Hashtable (e.g. for "SOCKET" or "native"), take this instance
            I_CallbackDriver cbDriverNative = glob.getNativeCallbackDriver(cbArr[ii].getType() + cbArr[ii].getAddress());
            if (cbDriverNative != null)  { // && obj.toString().indexOf("org.xmlBlaster.protocol.socket.CallbackSocketDriver") >= 0) {
               callbackDrivers[ii] = cbDriverNative;
               callbackDrivers[ii].init(cbArr[ii]);
               if (Log.TRACE) Log.trace(ME, "Created native callback driver for protocol '" + cbArr[ii].getType() + "'");
               continue;
            }

            if (obj instanceof String) { // e.g. SOCKET:native
               Log.error(ME, "Ignoring protocol driver " + cbArr[ii].getType());
               continue;
            }
         
            // else we need to load (instantiate) the protocol driver ...

            Class cl = (Class)obj;

            try {
               callbackDrivers[ii] = (I_CallbackDriver)cl.newInstance();
               callbackDrivers[ii].init(cbArr[ii]);
               if (Log.TRACE) Log.trace(ME, "Created callback driver for protocol '" + cbArr[ii].getType() + "'");
            }
            catch (IllegalAccessException e) {
               Log.error(ME, "The driver class '" + cbArr[ii].getType() + "' is not accessible\n -> check the driver name and/or the CLASSPATH to the driver");
            }
            catch (SecurityException e) {
               Log.error(ME, "No right to access the driver class or initializer '" + cbArr[ii].getType() + "'");
            }
            catch (Throwable e) {
               Log.error(ME, "The driver class or initializer '" + cbArr[ii].getType() + "' is invalid\n -> check the driver name and/or the CLASSPATH to the driver file: " + e.toString());
            }
         }
      }
   }

   /*
   // Access all known callback drivers
   public I_CallbackDriver[] getCallbackDrivers()
   {
      return callbackDrivers;
   }
   */


   /** @return Number of established callback drivers */
   public int getSize()
   {
      return callbackDrivers.length;
   }


   /** Send the messages back to the client, using all available drivers */
   public void sendUpdate(MsgQueueEntry[] msg, int redeliver) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "msg.length=" + msg.length + " callbackDrivers.length=" + callbackDrivers.length); 
      if (cbArr.length == 0) { // assert
         Log.error(ME, "cbArr.length == 0, msg.length=" + msg.length + " messages are lost");
         throw new XmlBlasterException(ME, "cbArr.length == 0, msg.length=" + msg.length + " messages are lost");
      }

      // First we export the message (call the interceptor) ...
      for (int i=0; i<msg.length; i++) {
         I_Session sessionSecCtx = msg[i].getSessionInfo().getSecuritySession();
         if (sessionSecCtx==null) {
            Log.error(ME+".accessDenied", "No session security context!");
            throw new XmlBlasterException(ME+".accessDenied", "No session security context!");
         }
         //cbArr[0] REDUCE UPDATE QOS!!! TODO
         msg[i].setMessageUnit(sessionSecCtx.exportMessage(msg[i].getMessageUnit(i, msg.length, redeliver)));
         if (Log.DUMP) Log.dump(ME, "CallbackQos=" + msg[i].getMessageUnit().getQos());
      }

      if (callbackDrivers.length < 1)
         Log.error(ME, "Sending of callback failed, no callback driver available");

      for (int ii=0; ii<callbackDrivers.length; ii++) {
         if (cbArr[ii].oneway()) {
            //Log.info(ME, "Sending oneway message ...");
            callbackDrivers[ii].sendUpdateOneway(msg);
         }
         else {
            callbackDrivers[ii].sendUpdate(msg);
         }
      }
   }


   /**
    * Load the callback drivers from xmlBlaster.properties.
    * <p />
    * Accessing the CallbackDriver for this client, supporting the
    * desired protocol (CORBA, EMAIL, HTTP, RMI).
    * <p />
    * Default is support for IOR, XML-RPC, RMI and the JDBC service (ODBC bridge)
    */
   private final void loadDrivers()
   {
      if (protocols != null)
         return;

      protocols = new Hashtable();
      String defaultDrivers = // See Main.java for "Protocol.Drivers" default settings
               "IOR:org.xmlBlaster.protocol.corba.CallbackCorbaDriver," +
               "SOCKET:org.xmlBlaster.protocol.socket.CallbackSocketDriver," +
               "RMI:org.xmlBlaster.protocol.rmi.CallbackRmiDriver," +
               "XML-RPC:org.xmlBlaster.protocol.xmlrpc.CallbackXmlRpcDriver," +
               "JDBC:org.xmlBlaster.protocol.jdbc.CallbackJdbcDriver";

      String drivers = XmlBlasterProperty.get("Protocol.CallbackDrivers", defaultDrivers);
      StringTokenizer st = new StringTokenizer(drivers, ",");
      int numDrivers = st.countTokens();
      for (int ii=0; ii<numDrivers; ii++) {
         String token = st.nextToken().trim();
         int index = token.indexOf(":");
         if (index < 0) {
            Log.error(ME, "Wrong syntax in xmlBlaster.properties Protocol.CallbackDrivers, driver ignored: " + token);
            continue;
         }
         String protocol = token.substring(0, index).trim();
         String driverId = token.substring(index+1).trim();

         if (driverId.equalsIgnoreCase("NATIVE")) { // We can mark in xmlBlaster.properties e.g. SOCKET:native
            protocols.put(protocol, "");
            continue;
         }

         // Load the protocol driver ...
         try {
            if (Log.TRACE) Log.trace(ME, "Trying Class.forName('" + driverId + "') ...");
            Class cl = java.lang.Class.forName(driverId);
            protocols.put(protocol, cl);
            // Log.info(ME, "Found callback driver class '" + driverId + "' for protocol '" + protocol + "'");
         }
         catch (SecurityException e) {
            Log.error(ME, "No right to access the driver class or initializer '" + driverId + "'");
         }
         catch (Throwable e) {
            Log.error(ME, "The driver class or initializer '" + driverId + "' is invalid\n -> check the driver name in xmlBlaster.properties and/or the CLASSPATH to the driver file: " + e.toString());
         }
      }
   }


   /**
    * Stop all callback drivers of this client.
    */
   public void shutdown()
   {
      for (int ii=0; ii<callbackDrivers.length; ii++) {
         callbackDrivers[ii].shutdown();
      }
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of SessionInfo as a XML ASCII string
    */
   public final String toXml(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer(256);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<CbInfo>");
      if (callbackDrivers.length < 1)
         sb.append(offset).append("   <noCallbackDriver />");
      else {
         for (int ii=0; ii<callbackDrivers.length; ii++) {
            sb.append(offset).append("   <" + callbackDrivers[ii].getName() + " />");
         }
      }
      sb.append(offset).append("</CbInfo>");

      return sb.toString();
   }
}

