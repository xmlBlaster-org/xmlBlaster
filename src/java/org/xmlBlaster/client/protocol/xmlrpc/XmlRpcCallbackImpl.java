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
   public final static String INHIBITED_CALLBACK_EXCEPTION = "INHIBITED_CALLBACK_EXCEPTION";
   
   private XmlRpcCallbackServer server = null;

   private final static Logger log = Logger.getLogger(XmlRpcCallbackImpl.class.getName());
   private boolean inhibitCbExceptions;
   
   /**
    * Construct a persistently named object.
    * @param client    Your implementation of I_CallbackExtended, or null if you don't want any updates.
    */
   XmlRpcCallbackImpl(XmlRpcCallbackServer server, boolean inhibitCbExceptions) throws XmlBlasterException {
      this.server = server;
      this.inhibitCbExceptions = inhibitCbExceptions;
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
                      String updateQos) throws XmlBlasterException {
      if (inhibitCbExceptions) {
         try {
            return server.update(cbSessionId, updateKey, content, updateQos);
         }
         catch (Throwable ex) {
            log.severe("Exception " + ex.getMessage() + " occured and its reason will hidden for the server since 'inhibitException' was set to true");
            ex.printStackTrace();
            return INHIBITED_CALLBACK_EXCEPTION;
         }
      }
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
    * The update method.
    * <p />
    * Gets invoked from xmlBlaster callback
    * @param sessionId A sessionId which we can decide if we trust it
    *                  This id is the one specified from the client which has setup the callback.
    */
   public String update(String cbSessionId, String updateKey, String contentAsString,
                      String updateQos) throws XmlBlasterException {
      return update(cbSessionId, updateKey, contentAsString.getBytes(), updateQos);
   }

   /**
    * The 'oneway' update method. 
    * <p />
    * oneway is not natively supported by XmlRpc
    * <p />
    * Gets invoked from XmlRpcCallbackImpl.java (which was called by xmlBlaster)
    */
   public String updateOneway(String cbSessionId, String updateKey, String contentAsString, String updateQos)
   {
      return updateOneway(cbSessionId, updateKey, contentAsString.getBytes(), updateQos);
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

