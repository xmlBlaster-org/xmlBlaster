/*------------------------------------------------------------------------------
Name:      CallbackXmlRpcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using XML-RPC interface.
Version:   $Id: CallbackXmlRpcDriver.java,v 1.12 2002/03/13 16:41:31 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.queue.MsgQueueEntry;
import org.xmlBlaster.util.XmlBlasterException;
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
 * @author michele.laghi@attglobal.net
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 * @see org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver
 */
public class CallbackXmlRpcDriver implements I_CallbackDriver
{
   private String ME = "CallbackXmlRpcDriver";
   private CallbackAddress callbackAddress = null;
   private XmlRpcClient xmlRpcClient = null;

   /** Get a human readable name of this driver */
   public String getName()
   {
      return ME;
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
   public void init(CallbackAddress callbackAddress) throws XmlBlasterException
   {
      this.callbackAddress = callbackAddress;
      try {
         xmlRpcClient = new XmlRpcClient(callbackAddress.getAddress());
         Log.info(ME, "Accessing client callback web server using given url=" + callbackAddress.getAddress());
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
    *     &lt;state>       &lt;!-- Client processing state -->
    *        OK            &lt;!-- OK | ERROR -->
    *     &lt;/state>
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
          
            if (Log.TRACE) Log.trace(ME, "Send an update to the client ...");

            retVal[ii] = (String)xmlRpcClient.execute("update", args);

            if (Log.TRACE) Log.trace(ME, "Successfully sent message '" + msgUnit.getXmlKey()
                + "' update from sender '" + msg[0].getPublisherName() + "' to '" + callbackAddress.getSessionId() + "'");
         }
         return retVal;
      }
      catch (XmlRpcException ex) {
         XmlBlasterException e = XmlRpcConnection.extractXmlBlasterException(ex);
         String str = "Sending message to " + callbackAddress.getAddress() + " failed in client: " + ex.toString();
         if (Log.TRACE) Log.trace(ME + ".sendUpdate", str);
         throw new XmlBlasterException("CallbackFailed", str);
      }
      catch (Throwable e) {
         String str = "Sending message to " + callbackAddress.getAddress() + " failed: " + e.toString();
         if (Log.TRACE) Log.trace(ME + ".sendUpdate", str);
         e.printStackTrace();
         throw new XmlBlasterException("CallbackFailed", str);
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
      if (Log.TRACE) Log.trace(ME, "Shutdown implementation is missing");
   }
}
