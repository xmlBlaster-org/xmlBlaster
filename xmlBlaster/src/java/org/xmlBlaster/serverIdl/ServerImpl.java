/*------------------------------------------------------------------------------
Name:      ServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the CORBA xmlBlaster-server interface
Version:   $Id: ServerImpl.java,v 1.21 1999/11/23 15:31:45 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.serverIdl;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.StopWatch;
import org.xmlBlaster.clientIdl.BlasterCallback;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.engine.ClientInfo;
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
   public ServerImpl(org.omg.CORBA.ORB orb, Authenticate authenticate) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering constructor with ORB argument");
      this.authenticate = authenticate;
      this.orb = orb;
      this.requestBroker = RequestBroker.getInstance(authenticate);
   }


   /**
    * Access the authentication server
    */
   public Authenticate getAuthenticate()
   {
      return authenticate;
   }


   /**
    * Subscribe to messages
    */
   public void subscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering subscribe(xmlKey=" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
      if (Log.DUMP) Log.dump(ME, "-------START-subscribe()---------\n" + requestBroker.printOn().toString());
      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

      ClientInfo clientInfo = authenticate.check();

      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      XmlQoS xmlQoS = new XmlQoS(qos_literal);
      requestBroker.subscribe(clientInfo, xmlKey, xmlQoS);

      if (Log.TIME) Log.time(ME, "Elapsed time in subscribe()" + stop.nice());
      if (Log.DUMP) Log.dump(ME, "-------END-subscribe()---------\n" + requestBroker.printOn().toString());
   }


   /**
    * @see xmlBlaster.idl
    */
   public void unSubscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering unSubscribe(xmlKey=" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
      if (Log.DUMP) Log.dump(ME, "-------START-unSubscribe()---------\n" + requestBroker.printOn().toString());
      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

      ClientInfo clientInfo = authenticate.check();

      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      XmlQoS xmlQoS = new XmlQoS(qos_literal);
      requestBroker.unSubscribe(clientInfo, xmlKey, xmlQoS);

      if (Log.TIME) Log.time(ME, "Elapsed time in unSubscribe()" + stop.nice());
      if (Log.DUMP) Log.dump(ME, "-------END-unSubscribe()---------\n" + requestBroker.printOn().toString());
   }


   /**
    * @see xmlBlaster.idl
    */
   public String publish(MessageUnit messageUnit, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering publish() ...");
      if (Log.DUMP) Log.dump(ME, "-------START-publish()---------\n" + requestBroker.printOn().toString());

      ClientInfo clientInfo = authenticate.check();

      String retVal = requestBroker.publish(messageUnit, qos_literal);

      if (Log.DUMP) Log.dump(ME, "-------END-publish()---------\n" + requestBroker.printOn().toString());

      return retVal;
   }


   /**
    * @see xmlBlaster.idl
    */
   public String[] publishArr(MessageUnit [] messageUnitArr, String [] qos_literal_Arr) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering publish() ...");
      if (Log.DUMP) Log.dump(ME, "-------START-publishArr()---------\n" + requestBroker.printOn().toString());
      ClientInfo clientInfo = authenticate.check();

      String[] returnArr = new String[0];

      if (messageUnitArr.length < 1) {
         if (Log.TRACE) Log.trace(ME, "Entering xmlBlaster.publish(), nothing to do, zero messageUnits sent");
         return returnArr;
      }
      if (Log.CALLS) Log.trace(ME, "Entering xmlBlaster.publish() for " + messageUnitArr.length + " Messages");

      String[] strArr = requestBroker.publish(messageUnitArr, qos_literal_Arr);

      if (Log.DUMP) Log.dump(ME, "-------END-publishArr()---------\n" + requestBroker.printOn().toString());
      return strArr;
   }


   /**
    * @see xmlBlaster.idl
    */
   public String[] erase(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering erase(xmlKey=" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
      if (Log.DUMP) Log.dump(ME, "-------START-erase()---------\n" + requestBroker.printOn().toString());
      ClientInfo clientInfo = authenticate.check();

      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      XmlQoS xmlQoS = new XmlQoS(qos_literal);
      if (Log.CALLS) Log.calls(ME, "Entering xmlBlaster.erase(" + xmlKey.getUniqueKey() + ")");

      String [] retArr = requestBroker.erase(clientInfo, xmlKey, xmlQoS);

      if (Log.DUMP) Log.dump(ME, "-------END-erase()---------\n" + requestBroker.printOn().toString());

      return retArr;
   }


   /**
    * Synchronous access
    * @return content
    * @see xmlBlaster.idl
    */
   public MessageUnit[] get(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering unSubscribe(xmlKey=" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
      if (Log.DUMP) Log.dump(ME, "-------START-get()---------\n" + requestBroker.printOn().toString());
      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

      ClientInfo clientInfo = authenticate.check();

      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      XmlQoS xmlQoS = new XmlQoS(qos_literal);
      MessageUnit[] messageUnitArr = requestBroker.get(clientInfo, xmlKey, xmlQoS);

      if (Log.TIME) Log.time(ME, "Elapsed time in get()" + stop.nice());
      if (Log.DUMP) Log.dump(ME, "-------END-get()---------\n" + requestBroker.printOn().toString());

      return messageUnitArr;
   }

}

