/*------------------------------------------------------------------------------
Name:      XmlRpcCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: XmlRpcCallbackImpl.java,v 1.10 2002/06/23 10:51:43 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.xmlrpc;


import org.xmlBlaster.client.protocol.I_CallbackExtended;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.UpdateKey;

import org.apache.xmlrpc.WebServer;


/**
 * The methods of this callback class are exposed to XML-RPC clients,
 * in this case to xmlBlaster when it wants to callback the client.
 * <p />
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public class XmlRpcCallbackImpl
{
   private final String ME = "XmlRpcCallbackImpl";
   private XmlRpcCallbackServer server = null;

   /**
    * Construct a persistently named object.
    * @param client    Your implementation of I_CallbackExtended, or null if you don't want any updates.
    */
   XmlRpcCallbackImpl(XmlRpcCallbackServer server) throws XmlBlasterException
   {
      this.server = server;
   }

   /**
    * The update method.
    * <p />
    * Gets invoked from xmlBlaster callback
    * @param sessionId A sessionId which we can decide if we trust it
    *                  This id is the one specified from the client which has setup the callback.
    */
   public String update(String cbSessionId, String updateKey, byte[] content,
                      String updateQos) throws XmlBlasterException
   {
      return server.update(cbSessionId, updateKey, content, updateQos);
   }

   /**
    * The 'oneway' update method. 
    * <p />
    * oneway is not natively supported by XmlRpc
    * <p />
    * Gets invoked from XmlRpcCallbackImpl.java (which was called by xmlBlaster)
    */
   public void updateOneway(String cbSessionId, String updateKey, byte[] content, String updateQos)
   {
      server.updateOneway(cbSessionId, updateKey, content, updateQos);
   }

   /**
    * Ping to check if the callback server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    */
   public String ping(String str)
   {
      return server.ping(str);
   }
} // class XmlRpcCallbackImpl

