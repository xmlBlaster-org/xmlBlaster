/*------------------------------------------------------------------------------
Name:      ServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the CORBA xmlBlaster-server interface
Version:   $Id: ServerImpl.java,v 1.10 2000/06/18 15:22:00 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.corba;

import org.jutils.log.Log;
import org.jutils.time.StopWatch;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.protocol.I_XmlBlaster;
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
   private I_XmlBlaster blaster;


   /**
    * Construct a persistently named object.
    */
   public ServerImpl(org.omg.CORBA.ORB orb, I_XmlBlaster blaster) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering constructor with ORB argument");
      this.orb = orb;
      this.blaster = blaster;
   }


   /**
    * Get a handle on the request broker singleton (the engine of xmlBlaster).
    * @return I_XmlBlaster
    */
   public I_XmlBlaster getXmlBlaster()
   {
      return blaster;
   }


   /**
    * Subscribe to messages
    */
   public String subscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      try {
         if (Log.CALLS) Log.calls(ME, "Entering subscribe() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         if (Log.DUMP) Log.dump(ME, "-------START-subscribe()---------\n" + blaster.toXml());
         StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

         String oid = blaster.subscribe(getSessionId(), xmlKey_literal, qos_literal);

         if (Log.TIME) Log.time(ME, "Elapsed time in subscribe()" + stop.nice());
         if (Log.DUMP) Log.dump(ME, "-------END-subscribe()---------\n" + blaster.toXml());

         return oid;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }


   /**
    * @see xmlBlaster.idl
    */
   public void unSubscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      try {
         if (Log.CALLS) Log.calls(ME, "Entering unSubscribe() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         if (Log.DUMP) Log.dump(ME, "-------START-unSubscribe()---------\n" + blaster.toXml());
         StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

         blaster.unSubscribe(getSessionId(), xmlKey_literal, qos_literal);

         if (Log.TIME) Log.time(ME, "Elapsed time in unSubscribe()" + stop.nice());
         if (Log.DUMP) Log.dump(ME, "-------END-unSubscribe()---------\n" + blaster.toXml());
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }


   /**
    * @see xmlBlaster.idl
    */
   public String publish(MessageUnit msgUnit, String qos_literal) throws XmlBlasterException
   {
      try {
         if (Log.CALLS) Log.calls(ME, "Entering publish() ...");
         if (Log.DUMP) Log.dump(ME, "-------START-publish()---------\n" + blaster.toXml());

         String retVal = blaster.publish(getSessionId(), msgUnit, qos_literal);

         if (Log.DUMP) Log.dump(ME, "-------END-publish()---------\n" + blaster.toXml());

         return retVal;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }


   /**
    * @see xmlBlaster.idl
    */
   public String[] publishArr(MessageUnit [] msgUnitArr, String [] qos_literal_Arr) throws XmlBlasterException
   {
      try {
         if (Log.CALLS) Log.calls(ME, "Entering publish() ...");
         if (Log.DUMP) Log.dump(ME, "-------START-publishArr()---------\n" + blaster.toXml());

         String[] returnArr = new String[0];

         if (msgUnitArr.length < 1) {
            if (Log.TRACE) Log.trace(ME, "Entering xmlBlaster.publish(), nothing to do, zero msgUnits sent");
            return returnArr;
         }
         if (Log.CALLS) Log.trace(ME, "Entering xmlBlaster.publish() for " + msgUnitArr.length + " Messages");

         String[] strArr = blaster.publishArr(getSessionId(), msgUnitArr, qos_literal_Arr);

         if (Log.DUMP) Log.dump(ME, "-------END-publishArr()---------\n" + blaster.toXml());
         return strArr;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }


   /**
    * @see xmlBlaster.idl
    */
   public String[] erase(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      try {
         if (Log.CALLS) Log.calls(ME, "Entering erase() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         if (Log.DUMP) Log.dump(ME, "-------START-erase()---------\n" + blaster.toXml());

         String [] retArr = blaster.erase(getSessionId(), xmlKey_literal, qos_literal);

         if (Log.DUMP) Log.dump(ME, "-------END-erase()---------\n" + blaster.toXml());

         return retArr;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }


   /**
    * Synchronous access
    * @return content
    * @see xmlBlaster.idl
    */
   public MessageUnitContainer[] get(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      try {
         if (Log.CALLS) Log.calls(ME, "Entering get() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         if (Log.DUMP) Log.dump(ME, "-------START-get()---------\n" + blaster.toXml());
         StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

         MessageUnitContainer[] msgUnitContainerArr = blaster.get(getSessionId(), xmlKey_literal, qos_literal);

         if (Log.TIME) Log.time(ME, "Elapsed time in get()" + stop.nice());
         if (Log.DUMP) Log.dump(ME, "-------END-get()---------\n" + blaster.toXml());

         return msgUnitContainerArr;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
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

      // !!! TODO
      Log.warning(ME, "Checking administrator privileges is not yet implemented");
      throw new XmlBlasterException("NotImplemented", "Checking administrator privileges is not yet implemented");

      // !? blaster.setClientAttributes(clientName, xmlAttr_literal, qos_literal);

      //if (Log.TIME) Log.time(ME, "Elapsed time in setClientAttributes()" + stop.nice());
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

