/*------------------------------------------------------------------------------
Name:      CbInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Version:   $Id: CbInfo.java,v 1.2 2001/01/30 14:12:19 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.callback;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.util.XmlBlasterProperty;
import java.util.Hashtable;
import java.util.StringTokenizer;


/**
 * Holding all necessary infos to establish a callback
 * connection and invoke the update().
 */
public class CbInfo
{
   public final String ME = "CbInfo";
   private I_CallbackDriver[] callbackDrivers = null;
   /** Map holding the Class of all protocol I_CallbackDriver.java implementations, e.g. CallbackCorbaDriver */
   private static Hashtable protocols = null;

   public CbInfo()
   {
      callbackDrivers = new I_CallbackDriver[0];
   }

   public CbInfo(CallbackAddress[] cbArr) throws XmlBlasterException
   {
      initialize(cbArr);
   }

   public void initialize(CallbackAddress[] cbArr) throws XmlBlasterException
   {
      loadDrivers();
      if (cbArr == null) {
         callbackDrivers = new I_CallbackDriver[0];
      }
      else {
         callbackDrivers = new I_CallbackDriver[cbArr.length];
         for (int ii=0; ii<cbArr.length; ii++) {
            // Load the protocol driver ...
            Class cl = (Class)protocols.get(cbArr[ii].getType());
            if (cl == null) {
               Log.error(ME+".UnknownCallbackProtocol", "Sorry, callback type='" + cbArr[ii].getType() + "' is not supported");
               throw new XmlBlasterException("UnknownCallbackProtocol", "Sorry, callback type='" + cbArr[ii].getType() + "' is not supported");
            }


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
   public void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper, MessageUnit[] arr) throws XmlBlasterException
   {
      for (int ii=0; ii<callbackDrivers.length; ii++) {
         callbackDrivers[ii].sendUpdate(clientInfo, msgUnitWrapper, arr);
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
      String defaultDrivers =
               "IOR:org.xmlBlaster.protocol.corba.CallbackCorbaDriver," +
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
    * @return internal state of ClientInfo as a XML ASCII string
    */
   public final String toXml(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<CbInfo>");
      if (callbackDrivers.length < 1)
         sb.append(offset + "   <noCallbackDriver />");
      else {
         for (int ii=0; ii<callbackDrivers.length; ii++) {
            sb.append(offset + "   <" + callbackDrivers[ii].getName() + " />");
         }
      }
      sb.append(offset + "</CbInfo>\n");

      return sb.toString();
   }
}

