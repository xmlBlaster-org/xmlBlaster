/*------------------------------------------------------------------------------
Name:      ServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the CORBA xmlBlaster-server interface
Version:   $Id: ServerImpl.java,v 1.24 2003/03/31 14:22:00 ruff Exp $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.corba;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.ErrorCode;
import org.jutils.time.StopWatch;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.BlasterCallback;
import org.xmlBlaster.protocol.I_XmlBlaster;
import java.util.*;


/**
 * Implements the xmlBlaster server CORBA interface.
 * <p />
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 * @see org.xmlBlaster.engine.RequestBroker
 */
//public class ServerImpl extends ServerPOA {            // inheritance approach
public class ServerImpl implements ServerOperations {    // TIE approach

   private final String ME = "ServerImpl";
   private final Global glob;
   private final LogChannel log;
   private final org.omg.CORBA.ORB orb;
   private final I_XmlBlaster blaster;


   /**
    * Construct a persistently named object.
    */
   public ServerImpl(Global glob, org.omg.CORBA.ORB orb, I_XmlBlaster blaster) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = this.glob.getLog("corba");
      if (log.CALL) log.call(ME, "Entering constructor with ORB argument");
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
         if (log.CALL) log.call(ME, "Entering subscribe() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         if (log.DUMP) log.dump(ME, "subscribe()\n" + xmlKey_literal + "\n" + qos_literal);
         StopWatch stop=null; if (log.TIME) stop = new StopWatch();

         String oid = blaster.subscribe(getSecretSessionId(), xmlKey_literal, qos_literal);

         if (log.TIME) log.time(ME, "Elapsed time in subscribe()" + stop.nice());

         return oid;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw CorbaDriver.convert(e); // transform native exception to Corba exception
      }
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] unSubscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering unSubscribe() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         if (log.DUMP) log.dump(ME, "unSubscribe()\n" + xmlKey_literal + "\n" + qos_literal);
         StopWatch stop=null; if (log.TIME) stop = new StopWatch();

         String[] strArr = blaster.unSubscribe(getSecretSessionId(), xmlKey_literal, qos_literal);

         if (log.TIME) log.time(ME, "Elapsed time in unSubscribe()" + stop.nice());

         return strArr;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw CorbaDriver.convert(e); // transform native exception to Corba exception
      }
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String publish(org.xmlBlaster.protocol.corba.serverIdl.MessageUnit msgUnit) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering publish() ...");
         if (log.DUMP) log.dump(ME, "publish()\n" + msgUnit.xmlKey + "\n" + msgUnit.qos);

         String retVal = blaster.publish(getSecretSessionId(), CorbaDriver.convert(glob, msgUnit));

         return retVal;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw CorbaDriver.convert(e); // transform native exception to Corba exception
      }
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] publishArr(org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] msgUnitArr) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering publish(" + msgUnitArr.length + ") ...");
         if (log.DUMP) {
            for (int ii=0; ii<msgUnitArr.length; ii++)
               log.dump(ME, "publishArr()\n" + msgUnitArr[ii].xmlKey + "\n" + msgUnitArr[ii].qos);
         }

         String[] returnArr = new String[0];

         if (msgUnitArr.length < 1) {
            if (log.TRACE) log.trace(ME, "Entering xmlBlaster.publish(), nothing to do, zero msgUnits sent");
            return returnArr;
         }

         org.xmlBlaster.util.MsgUnitRaw[] internalUnitArr = CorbaDriver.convert(glob, msgUnitArr);   // convert Corba to internal ...

         String[] strArr = blaster.publishArr(getSecretSessionId(), internalUnitArr);

         return strArr;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw CorbaDriver.convert(e); // transform native exception to Corba exception
      }
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public void publishOneway(org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] msgUnitArr)
   {
      try {
         if (msgUnitArr == null || msgUnitArr.length < 1) {
            if (log.TRACE) log.trace(ME, "Entering xmlBlaster.publish(), nothing to do, zero msgUnits sent");
            return;
         }
         if (log.CALL) log.call(ME, "Entering publishOneway(" + msgUnitArr.length + ") ...");

         org.xmlBlaster.util.MsgUnitRaw[] internalUnitArr = CorbaDriver.convert(glob, msgUnitArr);   // convert Corba to internal ...
         blaster.publishOneway(getSecretSessionId(), internalUnitArr);
      }
      catch (Throwable e) {
         log.error(ME, "publishOneway() failed, exception is not sent to client: " + e.toString());
         e.printStackTrace();
      }
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] erase(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering erase() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         if (log.DUMP) log.dump(ME, "erase()\n" + xmlKey_literal + "\n" + qos_literal);

         String [] retArr = blaster.erase(getSecretSessionId(), xmlKey_literal, qos_literal);

         return retArr;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw CorbaDriver.convert(e); // transform native exception to Corba exception
      }
   }

   /**
    * Synchronous access
    * @return content
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] get(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering get() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         if (log.DUMP) log.dump(ME, "get()\n" + xmlKey_literal + "\n" + qos_literal);
         StopWatch stop=null; if (log.TIME) stop = new StopWatch();

         org.xmlBlaster.util.MsgUnitRaw[] msgUnitArr = blaster.get(getSecretSessionId(), xmlKey_literal, qos_literal);

         org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] corbaUnitArr = CorbaDriver.convert(msgUnitArr);  // convert internal to Corba ...

         if (log.TIME) log.time(ME, "Elapsed time in get()" + stop.nice());

         return corbaUnitArr;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw CorbaDriver.convert(e); // transform native exception to Corba exception
      }
   }


   /**
    * Extract the user session ID from POA.
    * <p />
    * This is a nice feature with CORBA-POA not available with RMI et al.
    */
   private String getSecretSessionId() throws XmlBlasterException
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
         sessionId = convert(active_oid);
      } catch (Exception e) {
         log.error(ME+".AccessCheckProblem", "Sorry, can't find out who you are, access denied");
         throw CorbaDriver.convert(new org.xmlBlaster.util.XmlBlasterException(glob,
                        ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED, ME,
                        "Sorry, can't find out who you are, access denied"));
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
      //result += new String(objectId).replace('\n', ' ');
      for (int i=0; i<objectId.length; i++) {
         int n1 = (objectId[i] & 0xff) / 16;
         int n2 = (objectId[i] & 0xff) % 16;
         char c1 = (char)(n1>9 ? ('A'+(n1-10)) : ('0'+n1));
         char c2 = (char)(n2>9 ? ('A'+(n2-10)) : ('0'+n2));
         result += ( c1 + (c2 + ""));
      }
      // if (log.TRACE) log.trace("ServerImpl.CONVERT", "Converted POA-AOM <" + objectId + "> to session ID <" + result + ">");
      return result;
   }

   /**
    * Ping to check if xmlBlaster is alive.
    * @param qos ""
    * @return ""
    */
   public String ping(String qos)
   {
      if (log.CALL) log.call(ME, "Entering ping() ...");
      return "";
   }
}

