/*------------------------------------------------------------------------------
Name:      CallbackXmlRpcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   This singleton sends messages to clients using XML-RPC interface.
Version:   $Id: CallbackXmlRpcDriver.java,v 1.4 2000/10/22 16:42:36 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;
import helma.xmlrpc.XmlRpcClient;
import helma.xmlrpc.XmlRpcException;
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
    * @exception e.id="CallbackFailed", should be caught and handled appropriate
    */
   public final void sendUpdate(ClientInfo clientInfo, MessageUnitWrapper msgUnitWrapper,
                                org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr)
      throws XmlBlasterException
   {
      // transform the msgUnits to Vectors
      int arraySize = msgUnitArr.length;
      try {
         for (int i=0; i < arraySize; i++) {
            Vector args = new Vector();
            args.addElement(clientInfo.getLoginName());
            args.addElement(msgUnitArr[i].getXmlKey());
            args.addElement(msgUnitArr[i].getContent());
            args.addElement(msgUnitArr[i].getQos());
            // send an update to the client

            xmlRpcClient.execute("update", args);
            Log.info(ME, "Received message update '" +
                            new String(msgUnitArr[i].content) + "' from sender '"
                            + clientInfo.toString() + "'");
         }
      }
      catch (XmlRpcException ex) {
         Log.error(ME + ".sendUpdate", "xml-rpc exception: " + ex.toString());
         throw new XmlBlasterException("CallbackFailed", "xml-rpc exception" + ex.toString());
      }
      catch (IOException ex1) {
         Log.error(ME + ".sendUpdate", "I/O exception: " + ex1.toString());
         throw new XmlBlasterException("CallbackFailed", "I/O exception: " + ex1.toString());
      }

   }


   /**
    * This method shuts down the driver.
    * <p />
    */
   public void shutdown()
   {
   }
}
