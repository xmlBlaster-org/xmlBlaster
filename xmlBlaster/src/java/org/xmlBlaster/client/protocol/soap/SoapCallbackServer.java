/*------------------------------------------------------------------------------
Name:      SoapCallbackServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.soap;


import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.key.UpdateKey;

import org.jafw.saw.*;
import org.jafw.saw.rpc.*;
import org.jafw.saw.util.*;
import org.jafw.saw.transport.*;
import org.jafw.saw.server.*;
import java.io.IOException;

/**
 * Example for a Soap callback implementation.
 * <p />
 * You can use this default callback handling with your clients,
 * but if you need other handling of callbacks, take a copy
 * of this Callback implementation and add your own code.
 * <p />
 * The xmlBlaster callback client call the update() from SoapCallbackImpl
 * which delegates it to this update() method.
 * <p />
 * <pre>
 *     -dispatch/callback/protocol/soap/port
 *                       Specify a port number where soap callback webserver listens.
 *                       Default is port 8689, the port 0 switches this feature off.
 *     -dispatch/callback/protocol/soap/hostname
 *                       Specify a hostname where xmlrp callback server runs.
 *                       Default is the localhost.
 * </pre>
 * If the callback server can't be established because of the port is not free,
 * this driver loops and tries with a port number one higher until it finds a free port
 * to listen for callbacks.<br />
 * The correct port is automatically transferred in the login - QoS - so that xmlBlaster
 * can find the callback server.
 * <p />
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class SoapCallbackServer implements I_CallbackServer
{
   private String ME = "SoapCallbackServer";
   private Global glob = null;
   private LogChannel log = null;
   private I_CallbackExtended client;
   private String loginName;
   /** The name for the SOAP registry */
   private String callbackServerUrl = null;
   /** XmlBlaster SOAP callback web server listen port is 8689 */
   public static final int DEFAULT_CALLBACK_PORT = 8689; // org.xmlBlaster.protocol.soap.SoapDriver.DEFAULT_CALLBACK_PORT;
   private ServerManager manager = null;

   /** You must call initialize after constructing me */
   public SoapCallbackServer() {}

   /**
    * Construct a persistently named object.
    * @param client    Your implementation of I_CallbackExtended, or null if you don't want any updates.
    */
   public void initialize(Global glob, String name, I_CallbackExtended client) throws XmlBlasterException
   {
      this.ME = "SoapCallbackServer-" + name;
      this.glob = glob;
      this.log = glob.getLog("soap");
      this.client = client;
      this.loginName = name;
      createCallbackServer();
      log.info(ME, "Success, created SOAP callback server for " + loginName);
   }


   /**
    * Building a Callback server, using the tie approach.
    *
    * @param the BlasterCallback server
    * @exception XmlBlasterException if the BlasterCallback server can't be created
    *            id="CallbackCreationError"
    */
   public void createCallbackServer() throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "createCallbackServer() ...");

      // TODO: Use ConnectQos to allow hardcoded hostname/socket

      // Use the xmlBlaster-server soapRegistry as a fallback:
      int callbackPort = glob.getProperty().get("soap.port",
                                                DEFAULT_CALLBACK_PORT); // default xmlBlaster SOAP publishing port is 8689
      // Use the given callback port if specified :
      callbackPort = glob.getProperty().get("soap.portCB", callbackPort);

      String hostname = glob.getCbHostname();
      java.net.InetAddress inetAddr = null;
      try {
         inetAddr = java.net.InetAddress.getByName(hostname);
      } catch(java.net.UnknownHostException e) {
         if (log.TRACE) log.trace("InitSoapFailed", "The host [" + hostname + "] for the callback server is invalid, try '-soap.hostnameCB=<ip>': " + e.toString());
         throw new XmlBlasterException("InitSoapFailed", "The host [" + hostname + "] for the callback server is invalid, try '-soap.hostnameCB=<ip>': " + e.toString());
      }

      log.error(ME, "not implemented");
/*
      try {
         if (callbackPort > 0) {
            // Start an 'soap webserver' if desired
            int numTries = 20; // start looking for a free port, begin with default port -soap.portCB=<port>
            for (int ii=0; ii<numTries; ii++) {
               try {
                  manager = new WebServer(callbackPort, inetAddr);
                  break;
               } catch(java.net.BindException e) {
                  log.warn(ME, "Port " + callbackPort + " for XMLRPC callback server is in use already, trying with port " +  (callbackPort+1) + ": " + e.toString());
                  callbackPort++;
               } catch(java.io.IOException e) {
                  if (e.getMessage().indexOf("Cannot assign requested address") != -1) {
                     if (log.TRACE) log.warn(ME, "Host " + hostname + " for XMLRPC callback server is invalid: " + e.toString());
                     throw new XmlBlasterException(ME, "Local host IP '" + hostname + "' for XMLRPC callback server is invalid: " + e.toString());
                  }
                  else {  // e.getMessage() = "Address already in use"
                     log.warn(ME, "Port " + callbackPort + " for XMLRPC callback server is in use already, trying with port " +  (callbackPort+1) + ": " + e.toString());
                  }
                  callbackPort++;
               }
               if (ii == (numTries-1)) {
                  log.error(ME, "Can't find free port " + callbackPort + " for XMLRPC callback server, please use -soap.portCB=<port> to specify a free one.");
               }
            }
            manager.addHandler("$default", new SoapCallbackImpl(this)); // register update() method
            callbackServerUrl = "http://" + hostname + ":" + callbackPort + "/";
            this.ME = "SoapCallbackServer-" + callbackServerUrl;
            //log.info(ME, "Created Soap callback http server");
         }
         else
            log.info(ME, "Soap callback http server not created, because of -soap.portCB is 0");
      } catch (XmlBlasterException e) {
         throw e;
      } catch (Exception e) {
         e.printStackTrace();
         throw new XmlBlasterException("InitSoapFailed", "Could not initialize SOAP callback server host=" + hostname + " port=" + callbackPort + ": " + e.toString());
      }
*/
   }

   /**
    * Returns the 'well known' protocol type. 
    * @return "SOAP"
    */
   public String getCbProtocol()
   {
      return "SOAP";
   }
   
   /**
    * Returns the current callback address. 
    * @return Something like "http://myserver.com/soap"
    */
   public String getCbAddress() throws XmlBlasterException
   {
      return callbackServerUrl;
   }
   
   /**
    * Shutdown the callback server.
    * @return true if everything went fine.
    */
   public void shutdown()
   {
      if (manager != null) {
         try {
            manager.killServers();        
         }
         catch (IOException e) {
            String text = "Stopping SOAP callback server failed: " + e.toString();
            log.warn(ME, text);
         }
         manager = null;
         log.info(ME, "SOAP callback driver stopped.");
      }
      else
         log.info(ME, "SOAP calback server shutdown, nothing to do.");
   }

   /**
    * The update method.
    * <p />
    * Gets invoked from SoapCallbackImpl.java (which was called by xmlBlaster)
    */
   public String update(String cbSessionId, String updateKey, byte[] content,
                       String updateQos) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering update(): sessionId: " + cbSessionId);
      return client.update(cbSessionId, updateKey, content, updateQos);
   }

   /**
    * The 'oneway' update method. 
    * <p />
    * oneway is not natively supported by Soap
    * <p />
    * Gets invoked from SoapCallbackImpl.java (which was called by xmlBlaster)
    */
   public void updateOneway(String cbSessionId, String updateKey, byte[] content,
                       String updateQos)
   {
      if (log.CALL) log.call(ME, "Entering updateOneway(): sessionId: " + cbSessionId);
      try {
         client.updateOneway(cbSessionId, updateKey, content, updateQos);
      }
      catch (Throwable e) {
         log.error(ME, "Caught exception which can't be delivered to xmlBlaster because of 'oneway' mode: " + e.toString());
      }
   }

   /**
    * Ping to check if the callback server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    */
   public String ping(String str)
   {
      return "";
   }
} // class SoapCallbackServer

