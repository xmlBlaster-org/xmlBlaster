/*------------------------------------------------------------------------------
Name:      XmlRpcCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.xmlrpc;


import java.util.logging.Logger;

import org.xmlBlaster.util.XmlBlasterException;
/**
 * The methods of this callback class are exposed to XMLRPC clients,
 * in this case to xmlBlaster when it wants to callback the client.
 * <p />
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class XmlRpcCallbackImpl {
   private final String ME = "XmlRpcCallbackImpl";
   private XmlRpcCallbackServer server = null;

   private final static Logger log = Logger.getLogger(XmlRpcCallbackImpl.class.getName());
   /**
    * Construct a persistently named object.
    * @param client    Your implementation of I_CallbackExtended, or null if you don't want any updates.
    */
   XmlRpcCallbackImpl(XmlRpcCallbackServer server) throws XmlBlasterException {
      this.server = server;
   }

   XmlRpcCallbackImpl() {
      
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
   public String updateOneway(String cbSessionId, String updateKey, byte[] content, String updateQos)
   {
      server.updateOneway(cbSessionId, updateKey, content, updateQos);
      return ""; // fake to make new 3.0 xmlrpc happy
   }

   /**
    * Ping to check if the callback server is alive. 
    * @see org.xmlBlaster.protocol.I_CallbackDriver#ping(String)
    */
   public String ping(String str)
   {
      return server.ping(str);
   }

   
   
} // class XmlRpcCallbackImpl

