/*------------------------------------------------------------------------------
Name:      ServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Implementing the CORBA xmlBlaster-server interface
           $Revision $
           $Date: 1999/11/13 17:16:06 $
------------------------------------------------------------------------------*/
package org.xmlBlaster.serverIdl;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.clientIdl.BlasterCallback;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.engine.XmlKey;
import org.xmlBlaster.engine.XmlQoS;
import org.xmlBlaster.authentication.Authenticate;
import java.util.*;


/**
   Implements the xmlBlaster server CORBA Interface
*/
//public class ServerImpl extends ServerPOA {            // inheritance approach
public class ServerImpl implements ServerOperations {    // tie approach

   private final String ME = "ServerImpl";
   private org.omg.CORBA.ORB orb;
   private RequestBroker requestBroker;
   private Authenticate authenticate;


   /**
    * Construct a persistently named object.
    */
   public ServerImpl(org.omg.CORBA.ORB orb)
   {
      if (Log.CALLS) Log.trace(ME, "Entering constructor with ORB argument");
      this.orb = orb;
      this.requestBroker = RequestBroker.getInstance(this);
      this.authenticate = Authenticate.getInstance(this);
   }


   /**
    * Construct a transient object.
    */
   public ServerImpl()
   {
      super();
      if (Log.CALLS) Log.trace(ME, "Entering constructor without ORB argument");
      this.requestBroker = RequestBroker.getInstance(this);
      this.authenticate = Authenticate.getInstance(this);
   }


   /**
    * Authentication of a client
    * @param cb The Callback interface of the client
    * @return a unique sessionId for the client, to be used in following calls
    */
   public String login(String loginName, String passwd,
                       BlasterCallback cb,
                       String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.trace(ME, "Entering login(loginName=" + loginName + ", qos=" + qos_literal + ")");
      return authenticate.login(loginName, passwd, cb, qos_literal, orb.object_to_string(cb));
   }


   /**
    * Logout of a client
    */
   public void logout(String sessionId) throws XmlBlasterException
   {
      if (Log.CALLS) Log.trace(ME, "Entering logout(sessionId=" + sessionId);
      authenticate.logout(sessionId);
   }


   /**
    * @see xmlBlaster.idl
    */
   public void subscribe(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.trace(ME, "Entering subscribe(xmlKey=" + xmlKey_literal + ", qos=" + qos_literal + ")");
      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      XmlQoS xmlQoS = new XmlQoS(qos_literal);
      requestBroker.subscribe(authenticate.check(sessionId), xmlKey, xmlQoS);
   }


   /**
    * @see xmlBlaster.idl
    */
   public void unSubscribe(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.trace(ME, "Entering unSubscribe(xmlKey=" + xmlKey_literal + ", qos=" + qos_literal + ")");
      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      XmlQoS xmlQoS = new XmlQoS(qos_literal);
      requestBroker.unSubscribe(authenticate.check(sessionId), xmlKey, xmlQoS);
   }


   /**
    * @see xmlBlaster.idl
    */
   public int publish(String sessionId, MessageUnit [] messageUnitArr, String [] qos_literal_Arr) throws XmlBlasterException
   {
      if (messageUnitArr.length < 1) {
         if (Log.CALLS) Log.trace(ME, "Entering xmlBlaster.publish(), nothing to do, zero messageUnits sent");
         return 0;
      }
      if (Log.CALLS) Log.trace(ME, "Entering xmlBlaster.publish() for " + messageUnitArr.length + " Messages");
      return requestBroker.publish(messageUnitArr, qos_literal_Arr);
   }


   /**
    * @see xmlBlaster.idl
    */
   public int erase(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      XmlQoS xmlQoS = new XmlQoS(qos_literal);
      if (Log.CALLS) Log.trace(ME, "Entering xmlBlaster.erase(" + xmlKey.getUniqueKey() + ")");
      return requestBroker.erase(xmlKey, xmlQoS);
   }


   /**
    * Synchronous access
    * @return content
    * @see xmlBlaster.idl
    */
   public MessageUnit[] get(String sessionId, String xmlKey, String qos) throws XmlBlasterException
   {
      MessageUnit[] messageUnitArr = new MessageUnit[0];
       // IMPLEMENT: Operation
      return messageUnitArr;
   }


   /**
    * !!! This is the wrong place: But where shall i put it?
    */
   public org.xmlBlaster.clientIdl.BlasterCallback getBlasterCallback(String callbackIOR)
   {
      return org.xmlBlaster.clientIdl.BlasterCallbackHelper.narrow(orb.string_to_object(callbackIOR));
   }
}
