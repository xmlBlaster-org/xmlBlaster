/*------------------------------------------------------------------------------
Name:      ServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the CORBA xmlBlaster-server interface
Version:   $Id: ServerImpl.java,v 1.7 2000/06/04 19:13:24 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.corba;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.StopWatch;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.engine.RequestBroker;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.xml2java.PublishQoS;
import org.xmlBlaster.engine.xml2java.SubscribeQoS;
import org.xmlBlaster.engine.xml2java.UnSubscribeQoS;
import org.xmlBlaster.engine.xml2java.EraseQoS;
import org.xmlBlaster.engine.xml2java.GetQoS;
import org.xmlBlaster.authentication.Authenticate;
import java.util.*;


/**
 * Implements the xmlBlaster server CORBA interface.
 * <p />
 * @see xmlBlaster.idl
 * @see org.xmlBlaster.engine.RequestBroker
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
   public ServerImpl(org.omg.CORBA.ORB orb, Authenticate authenticate, RequestBroker requestBroker) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering constructor with ORB argument");
      this.authenticate = authenticate;
      this.orb = orb;
      this.requestBroker = requestBroker;
   }


   /**
    * Access the authentication server
    */
   public Authenticate getAuthenticate()
   {
      return authenticate;
   }


   /**
    * Get a handle on the request broker singleton (the engine of xmlBlaster).
    * @return RequestBroker
    */
   public RequestBroker getRequestBroker()
   {
      return requestBroker;
   }


   /**
    * Subscribe to messages
    */
   public String subscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering subscribe() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
      if (Log.DUMP) Log.dump(ME, "-------START-subscribe()---------\n" + requestBroker.toXml());
      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

      ClientInfo clientInfo = authenticate.check(getSessionId());

      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      SubscribeQoS subscribeQoS = new SubscribeQoS(qos_literal);
      String oid = requestBroker.subscribe(clientInfo, xmlKey, subscribeQoS);

      if (Log.TIME) Log.time(ME, "Elapsed time in subscribe()" + stop.nice());
      if (Log.DUMP) Log.dump(ME, "-------END-subscribe()---------\n" + requestBroker.toXml());

      return oid;
   }


   /**
    * @see xmlBlaster.idl
    */
   public void unSubscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering unSubscribe() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
      if (Log.DUMP) Log.dump(ME, "-------START-unSubscribe()---------\n" + requestBroker.toXml());
      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

      ClientInfo clientInfo = authenticate.check(getSessionId());

      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      UnSubscribeQoS xmlQoS = new UnSubscribeQoS(qos_literal);
      requestBroker.unSubscribe(clientInfo, xmlKey, xmlQoS);

      if (Log.TIME) Log.time(ME, "Elapsed time in unSubscribe()" + stop.nice());
      if (Log.DUMP) Log.dump(ME, "-------END-unSubscribe()---------\n" + requestBroker.toXml());
   }


   /**
    * @see xmlBlaster.idl
    */
   public String publish(MessageUnit msgUnit, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering publish() ...");
      if (Log.DUMP) Log.dump(ME, "-------START-publish()---------\n" + requestBroker.toXml());

      ClientInfo clientInfo = authenticate.check(getSessionId());

      PublishQoS publishQoS = new PublishQoS(qos_literal);
      String retVal = requestBroker.publish(clientInfo, msgUnit, publishQoS);

      if (Log.DUMP) Log.dump(ME, "-------END-publish()---------\n" + requestBroker.toXml());

      return retVal;
   }


   /**
    * @see xmlBlaster.idl
    */
   public String[] publishArr(MessageUnit [] msgUnitArr, String [] qos_literal_Arr) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering publish() ...");
      if (Log.DUMP) Log.dump(ME, "-------START-publishArr()---------\n" + requestBroker.toXml());
      ClientInfo clientInfo = authenticate.check(getSessionId());

      String[] returnArr = new String[0];

      if (msgUnitArr.length < 1) {
         if (Log.TRACE) Log.trace(ME, "Entering xmlBlaster.publish(), nothing to do, zero msgUnits sent");
         return returnArr;
      }
      if (Log.CALLS) Log.trace(ME, "Entering xmlBlaster.publish() for " + msgUnitArr.length + " Messages");

      String[] strArr = requestBroker.publish(clientInfo, msgUnitArr, qos_literal_Arr);

      if (Log.DUMP) Log.dump(ME, "-------END-publishArr()---------\n" + requestBroker.toXml());
      return strArr;
   }


   /**
    * @see xmlBlaster.idl
    */
   public String[] erase(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering erase() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
      if (Log.DUMP) Log.dump(ME, "-------START-erase()---------\n" + requestBroker.toXml());
      ClientInfo clientInfo = authenticate.check(getSessionId());

      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      EraseQoS xmlQoS = new EraseQoS(qos_literal);
      if (Log.CALLS) Log.calls(ME, "Entering xmlBlaster.erase(" + xmlKey.getUniqueKey() + ")");

      String [] retArr = requestBroker.erase(clientInfo, xmlKey, xmlQoS);

      if (Log.DUMP) Log.dump(ME, "-------END-erase()---------\n" + requestBroker.toXml());

      return retArr;
   }


   /**
    * Synchronous access
    * @return content
    * @see xmlBlaster.idl
    */
   public MessageUnitContainer[] get(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering get() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
      if (Log.DUMP) Log.dump(ME, "-------START-get()---------\n" + requestBroker.toXml());
      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

      ClientInfo clientInfo = authenticate.check(getSessionId());

      XmlKey xmlKey = new XmlKey(xmlKey_literal);
      GetQoS xmlQoS = new GetQoS(qos_literal);
      MessageUnitContainer[] msgUnitContainerArr = requestBroker.get(clientInfo, xmlKey, xmlQoS);

      if (Log.TIME) Log.time(ME, "Elapsed time in get()" + stop.nice());
      if (Log.DUMP) Log.dump(ME, "-------END-get()---------\n" + requestBroker.toXml());

      return msgUnitContainerArr;
   }


   /**
    * Setting attributes for a client.
    * <p>
    *
    * @param clientName  The client which shall be administered
    * @param xmlAttr     the attributes of the client in xml syntax like group/role infos<br>
    *                    They are later queryable with XPath syntax<p>
    *     <pre>
    *        &lt;client name='tim'>
    *           &lt;group>
    *              Marketing
    *           &lt;/group>
    *           &lt;role>
    *              Managing director
    *           &lt;/role>
    *        &lt;/client>
    *     </pre>
    * @param qos         Quality of Service, flags for additional informations to control administration
    */
   public void setClientAttributes(String clientName, String xmlAttr_literal,
                            String qos_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering setClientAttributes(clientName=" + clientName/* + ", qos=" + qos_literal + ")"*/);

      if (clientName==null || xmlAttr_literal==null || qos_literal==null) {
         Log.error(ME+"InvalidArguments", "setClientAttributes failed: please use no null arguments for setClientAttributes()");
         throw new XmlBlasterException(ME+"InvalidArguments", "setClientAttributes failed: please use no null arguments for setClientAttributes()");
      }

      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

      ClientInfo clientInfoAdmin = authenticate.check(getSessionId());

      // !!! TODO
      Log.warning(ME, "Checking administrator privileges is not yet implemented, admin access for " + clientInfoAdmin.toString() + " accepted");

      requestBroker.setClientAttributes(clientName, xmlAttr_literal, qos_literal);

      if (Log.TIME) Log.time(ME, "Elapsed time in setClientAttributes()" + stop.nice());
   }


   /**
    * Extract the user session ID from POA
    */
   private String getSessionId() throws XmlBlasterException
   {
      byte[] active_oid;
      String sessionId;
      try {
         // who is it?
         // find out by asking the xmlBlasterPOA

         // org.omg.PortableServer.Current poa_current = xmlBlasterPOA.getORB().orb.getPOACurrent();
         org.omg.PortableServer.Current poa_current = org.omg.PortableServer.CurrentHelper.narrow(
                                                      orb.resolve_initial_references("POACurrent"));
         active_oid = poa_current.get_object_id();
         sessionId = ServerImpl.convert(active_oid);
      } catch (Exception e) {
         Log.error(ME+".AccessCheckProblem", "Sorry, can't find out who you are, access denied");
         throw new XmlBlasterException("AccessCheckProblem", "Sorry, can't find out who you are, access denied");
      }
      return sessionId;
   }


   /**
    * Converts an oid into a string as a hex dump with an unique identifier "IIOP:" in front. 
    * <p />
    * Copied from JacORB POAUtil.java
    * <br />
    * The identifier IIOP: allows to distinguish the session ID from other generated session ids
    */
   public static final String convert(byte[] objectId)
   {
      String result = "IIOP:";
      for (int i=0; i<objectId.length; i++) {
         int n1 = (objectId[i] & 0xff) / 16;
         int n2 = (objectId[i] & 0xff) % 16;
         char c1 = (char)(n1>9 ? ('A'+(n1-10)) : ('0'+n1));
         char c2 = (char)(n2>9 ? ('A'+(n2-10)) : ('0'+n2));
         // result = result + ( c1 + (c2 + " "));
         result += (c1 + c2);
      }
      // if (Log.TRACE) Log.trace("CONVERT", "Converted POA-AOM <" + objectId + "> to session ID <" + result + ">");
      return result;
   }


   /**
    * Ping to check if xmlBlaster is alive
    */
   public void ping()
   {
      // if (Log.CALLS) Log.calls(ME, "Entering ping() ...");
   }
}

