/*------------------------------------------------------------------------------
Name:      CallbackSoapDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using SOAP interface.
Version:   $Id: CallbackSoapDriver.java,v 1.3 2002/08/26 11:04:26 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.soap;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.queue.MsgQueueEntry;
import org.xmlBlaster.client.protocol.soap.SoapConnection; // The SoapException to XmlBlasterException converter

import org.jafw.saw.*;
import org.jafw.saw.rpc.*;
import org.jafw.saw.util.*;
import org.jafw.saw.transport.*;
import org.jafw.saw.transport.http.*;
import java.io.IOException;
import java.net.URL;
import java.util.Vector;

/**
 * This object sends a MessageUnit back to a client using SOAP interface, in
 * the same JVM.
 * <p>
 * The I_CallbackDriver.update() method of the client will be invoked
 *
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 * @see org.xmlBlaster.protocol.soap.SoapDriver
 */
public class CallbackSoapDriver implements I_CallbackDriver
{
   private String ME = "CallbackSoapDriver";
   private Global glob = null;
   private LogChannel log;
   private CallbackAddress callbackAddress = null;
   private TransportConnection soapClient = null; // SOAP client to send method calls.
   /** See service.xml configuration */
   private final String service = "urn:I_XmlBlasterCallback";
      //String callbackSoapServerBindName = "urn://" + hostname + ":" + port + "/I_XmlBlasterCallback/" + loginName;


   /** Get a human readable name of this driver */
   public String getName() {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "SOAP"
    */
   public String getProtocolId() {
      return "SOAP";
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
    * @return "http://server.mars.universe:8686/"
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
    * @param  callbackAddress Contains the SOAP callback URL of the client
    */
   public void init(Global glob, CallbackAddress callbackAddress) throws XmlBlasterException {
      this.glob = glob;
      this.log = glob.getLog("soap");
      this.callbackAddress = callbackAddress;
      SAWHelper.initLogging();
      
      URL sawURL;
      try {
         //This will only work if you are using an HTTP server defined in 'conf/config.xml'
         //hostname    -    the hostname of the computer running the MathService example that we created
         //            probably localhost.
         //port      -   the port that the server is running on
         sawURL = new URL(getRawAddress()); // "http://develop:8686";
      } catch (Exception e) {
         log.error(ME, "Invalid URL '" + getRawAddress() + "', no callback possible");
         return;
      }

      try {
         soapClient = TransportConnectionManager.createTransportConnection(sawURL);
      } catch (SOAPException e) {
         log.error(ME, "FaultCode: " + e.getFaultCode() + " FaultString: " + e.getFaultString());
         throw new XmlBlasterException("SOAPException", e.toString());
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
   public final String[] sendUpdate(MsgQueueEntry[] msg) throws XmlBlasterException {
      if (msg == null || msg.length < 1) throw new XmlBlasterException(ME, "Illegal update argument");
 
      log.error(ME, "sendUpdate() not implemented");
      return new String[0];
      /*
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

            retVal[ii] = (String)soapClient.execute("update", args);

            if (log.TRACE) log.trace(ME, "Successfully sent message '" + msgUnit.getXmlKey()
                + "' update from sender '" + msg[0].getPublisherName() + "' to '" + callbackAddress.getSessionId() + "'");
         }
         return retVal;
      }
      catch (SoapException ex) {
         XmlBlasterException e = SoapConnection.extractXmlBlasterException(ex);
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
      */
   }

   /**
    * The oneway variant, without return value. 
    * @exception XmlBlasterException Is never from the client (oneway).
    */
   public void sendUpdateOneway(MsgQueueEntry[] msg) throws XmlBlasterException {
      if (msg == null || msg.length < 1) throw new XmlBlasterException(ME, "Illegal updateOneway argument");
 
      log.error(ME, "sendUpdateOneway() not implemented");
      /*
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

            soapClient.execute("updateOneway", args);

            if (log.TRACE) log.trace(ME, "Successfully sent message '" + msgUnit.getXmlKey()
                + "' update from sender '" + msg[0].getPublisherName() + "' to '" + callbackAddress.getSessionId() + "'");
         }
      }
      catch (SoapException ex) {
         XmlBlasterException e = SoapConnection.extractXmlBlasterException(ex);
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
      */
   }

   /**
    * Ping to check if callback server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    * @exception XmlBlasterException If client not reachable
    */
   public final String ping(String qos) throws XmlBlasterException {
      //Create a Call
      Call call = new Call();

      //Set the service to the name of the service we created
      call.setService(service);

      //Set the method we wish to invoke
      call.setMethodName("ping");
      
      //Create the parameters for the add method
      Parameter param1 = new Parameter("<qos/>", String.class, qos);
      
      //Set the parameter count and then set the parameters
      call.setParamCount(1);
      call.setParam(0, param1);
      

      //Invoke the call on the connection created above
      try {
         Parameter returnParam = soapClient.invoke(call);
         
         //Ensure we recieved a non null response, Note: if the call was invoking a 'void' method 
         //then the return will always be null, but there will be a SOAPException thrown if an error occurs
         if (returnParam == null) {
            log.error(ME, "I got a null response for ping(), something went wrong");
            throw new XmlBlasterException("CallbackPingFailed", "Soap callback ping failed, null was returned");
         } else {
            Class returnType = returnParam.getType();
            log.info(ME, "Return had class type of: " + returnType.getName());
            Object returnValue = returnParam.getValue();
            log.info(ME, "Return was: " + returnValue.toString());
            return returnValue.toString();
         }
      } catch (SOAPException se) {
         //If there was an error while invoking the call
         log.error(ME, "Ping failed, faultCode: " + se.getFaultCode() + " faultString: " + se.getFaultString());
         throw new XmlBlasterException("CallbackPingFailed", "Soap callback ping failed: " + se.toString());
      }
   }

   /**
    * This method shuts down the SOAP connection. 
    */
   public void shutdown() {
      //if (soapClient != null) soapClient.shutdown(); method is missing in Soap package !!!
      callbackAddress = null;
      if (this.soapClient != null) {
         // TODO:
         // ((SOAPHTTPConnection)this.soapClient).closeConnection();
         this.soapClient = null;
      }
      if (log.TRACE) log.trace(ME, "Shutdown done");
   }
}
