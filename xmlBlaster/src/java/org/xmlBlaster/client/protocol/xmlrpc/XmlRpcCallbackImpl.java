/*------------------------------------------------------------------------------
Name:      XmlRpcCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: XmlRpcCallbackImpl.java,v 1.5 2002/01/22 17:21:28 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.xmlrpc;


import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.AbstractCallbackExtended;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.client.UpdateQoS;
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
    */
   public String update(String loginName, String updateKey, byte[] content,
                      String updateQoS) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering update() loginName=" + loginName);
      server.update(loginName, updateKey, content, updateQoS);
      return "<qos><state>OK</state></qos>";
   }
} // class XmlRpcCallbackImpl

