/*------------------------------------------------------------------------------
Name:      ServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Implementing the CORBA xmlBlaster-server interface
           $Revision $
           $Date: 1999/11/11 16:15:00 $
------------------------------------------------------------------------------*/
package org.xmlBlaster.serverIdl;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.engine.XmlKey;
import org.xmlBlaster.engine.XmlQoS;
import java.util.*;


/**
   Implements the xmlBlaster server CORBA Interface
*/
//public class ServerImpl extends ServerPOA {            // inheritance approach
public class ServerImpl implements ServerOperations {    // tie approach

   private final String ME = "ServerImpl";
   private org.omg.CORBA.ORB orb;
   private RequestBroker requestBroker;


   /**
    * Construct a persistently named object.
    */
   public ServerImpl(org.omg.CORBA.ORB orb)
   {
      if (Log.CALLS) Log.trace(ME, "Entering constructor with ORB argument");
      this.orb = orb;
      this.requestBroker = RequestBroker.getInstance(this);
   }


   /**
    * Construct a transient object.
    */
   public ServerImpl()
   {
      super();
      if (Log.CALLS) Log.trace(ME, "Entering constructor without ORB argument");
      this.requestBroker = RequestBroker.getInstance(this);
   }


   /**
    * @see xmlBlaster.idl
    */
   public void subscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.trace(ME, "Entering subscribe(xmlKey=" + xmlKey_literal + ", qos=" + qos_literal + ")");
      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      XmlQoS xmlQoS = new XmlQoS(qos_literal);
      requestBroker.subscribe(xmlKey, xmlQoS);
   }


   /**
    * @see xmlBlaster.idl
    */
   public void unSubscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.trace(ME, "Entering unSubscribe(xmlKey=" + xmlKey_literal + ", qos=" + qos_literal + ")");
      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      XmlQoS xmlQoS = new XmlQoS(qos_literal);
      requestBroker.unSubscribe(xmlKey, xmlQoS);
   }


   /**
    * @see xmlBlaster.idl
    */
   public int publish(String xmlKey_literal, byte[] content) throws XmlBlasterException
   {
      if (Log.CALLS) Log.trace(ME, "Entering set(xmlKey=" + xmlKey_literal + ")");
      return setQoS(xmlKey_literal, content, (String)null);
   }


   /**
    * @see xmlBlaster.idl
    */
   public int setQoS(String xmlKey_literal, byte[] content, String qos_literal) throws XmlBlasterException
   {
      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      XmlQoS xmlQoS = new XmlQoS(qos_literal);
      if (Log.CALLS) Log.trace(ME, "Entering xmlBlaster.setQos(" + xmlKey.getUniqueKey() + ")");
      return requestBroker.set(xmlKey, content, xmlQoS);
   }


   /**
    * @see xmlBlaster.idl
    */
   public int erase(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      XmlQoS xmlQoS = new XmlQoS(qos_literal);
      if (Log.CALLS) Log.trace(ME, "Entering xmlBlaster.erase(" + xmlKey.getUniqueKey() + ")");
      return requestBroker.erase(xmlKey, xmlQoS);
   }


   /**
    * !!! This is the wrong place: But where shall i put it?
    */
   public org.xmlBlaster.clientIdl.BlasterCallback getBlasterCallback(String callbackIOR)
   {
      return org.xmlBlaster.clientIdl.BlasterCallbackHelper.narrow(orb.string_to_object(callbackIOR));
   }
}
