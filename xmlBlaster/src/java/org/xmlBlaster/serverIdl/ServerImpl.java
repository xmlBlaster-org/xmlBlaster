/*------------------------------------------------------------------------------
Name:      ServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Implementing the CORBA xmlBlaster-server interface
           $Revision $
           $Date: 1999/11/15 09:35:48 $
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
 * Implements the xmlBlaster server CORBA Interface
 * @see xmlBlaster.idl
 */
public class ServerImpl extends ServerPOA {            // inheritance approach
//public class ServerImpl implements ServerOperations {    // TIE approach

   private final String ME = "ServerImpl";
   private org.omg.CORBA.ORB orb;
   private RequestBroker requestBroker;
   private Authenticate authenticate;

   /**
    * Construct a persistently named object.
    */
   public ServerImpl(org.omg.CORBA.ORB orb, Authenticate authenticate)
   {
      if (Log.CALLS) Log.trace(ME, "Entering constructor with ORB argument");
      this.orb = orb;
      this.requestBroker = RequestBroker.getInstance(this);
      this.authenticate = authenticate;
   }


   /**
    * Subscribe to messages
    */
   public void subscribe(String sessionId, String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.trace(ME, "Entering subscribe(xmlKey=" + xmlKey_literal + ", qos=" + qos_literal + ")");

      if (Log.HACK_POA) {
         try {
            org.omg.PortableServer.Current poa_current = org.omg.PortableServer.CurrentHelper.narrow(
                                                         orb.resolve_initial_references("POACurrent"));
            byte[] active_oid = poa_current.get_object_id();
            Log.warning(ME, "subscribe for poa oid: " + active_oid);
            org.omg.PortableServer.POA xmlBlasterPOA = poa_current.get_POA();
            org.omg.PortableServer.Servant servant = xmlBlasterPOA.id_to_servant(active_oid);
            org.omg.CORBA.Object servantObj = xmlBlasterPOA.id_to_reference(active_oid);
            String IOR = orb.object_to_string(servantObj);
            Log.warning(ME, "subscribe for IOR: " + IOR);


            //org.omg.PortableServer.Servant servant = poa_current.getServant();
            //byte[] oid = xmlBlasterPOA.servant_to_id(servant);
            //Log.warning(ME, "subscribe for servant oid: " + oid);

            // NOT TIE:
            /* is wrong:
            byte[] this_oid = xmlBlasterPOA.reference_to_id(_this());
            Log.warning(ME, "subscribe for _this() oid: " + this_oid);
            */

            Log.warning(ME, "subscribe for servant _object_id() oid: " + _object_id()); // == poa_current.get_object_id()

         } catch (Exception e) {
            Log.error(ME, "subscribe for oid");
         }
      }


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
      authenticate.check(sessionId);

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
      authenticate.check(sessionId);

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

}

