/*------------------------------------------------------------------------------
Name:      ServerImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the CORBA xmlBlaster-server interface
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.corba;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.I_XmlBlaster;


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
   private static Logger log = Logger.getLogger(ServerImpl.class.getName());
   private final org.omg.CORBA.ORB orb;
   private final I_XmlBlaster blaster;
   private final AddressServer addressServer;


   /**
    * Construct a persistently named object.
    */
   public ServerImpl(Global glob, org.omg.CORBA.ORB orb, AddressServer addressServer, I_XmlBlaster blaster) throws XmlBlasterException
   {
      this.glob = glob;

      if (log.isLoggable(Level.FINER)) log.finer("Entering constructor with ORB argument");
      this.orb = orb;
      this.blaster = blaster;
      this.addressServer = addressServer;
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
         if (log.isLoggable(Level.FINER)) log.finer("Entering subscribe() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         if (log.isLoggable(Level.FINEST)) log.finest("subscribe()\n" + xmlKey_literal + "\n" + qos_literal);

         String oid = blaster.subscribe(this.addressServer, getSecretSessionId(), xmlKey_literal, qos_literal);

         // if (log.isLoggable(Level.FINEST)) log.finest("Elapsed time in subscribe()" + stop.nice());

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
         if (log.isLoggable(Level.FINER)) log.finer("Entering unSubscribe() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         if (log.isLoggable(Level.FINEST)) log.finest("unSubscribe()\n" + xmlKey_literal + "\n" + qos_literal);

         String[] strArr = blaster.unSubscribe(this.addressServer, getSecretSessionId(), xmlKey_literal, qos_literal);

         // if (log.isLoggable(Level.FINEST)) log.finest("Elapsed time in unSubscribe()" + stop.nice());

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
         if (log.isLoggable(Level.FINER)) log.finer("Entering publish() ...");
         if (log.isLoggable(Level.FINEST)) log.finest("publish()\n" + msgUnit.xmlKey + "\n" + msgUnit.qos);

         String retVal = blaster.publish(this.addressServer, getSecretSessionId(), CorbaDriver.convert(glob, msgUnit));

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
         if (log.isLoggable(Level.FINER)) log.finer("Entering publish(" + msgUnitArr.length + ") ...");
         if (log.isLoggable(Level.FINEST)) {
            for (int ii=0; ii<msgUnitArr.length; ii++)
               log.finest("publishArr()\n" + msgUnitArr[ii].xmlKey + "\n" + msgUnitArr[ii].qos);
         }

         String[] returnArr = new String[0];

         if (msgUnitArr.length < 1) {
            if (log.isLoggable(Level.FINE)) log.fine("Entering xmlBlaster.publish(), nothing to do, zero msgUnits sent");
            return returnArr;
         }

         org.xmlBlaster.util.MsgUnitRaw[] internalUnitArr = CorbaDriver.convert(glob, msgUnitArr);   // convert Corba to internal ...

         String[] strArr = blaster.publishArr(this.addressServer, getSecretSessionId(), internalUnitArr);

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
            if (log.isLoggable(Level.FINE)) log.fine("Entering xmlBlaster.publish(), nothing to do, zero msgUnits sent");
            return;
         }
         if (log.isLoggable(Level.FINER)) log.finer("Entering publishOneway(" + msgUnitArr.length + ") ...");

         org.xmlBlaster.util.MsgUnitRaw[] internalUnitArr = CorbaDriver.convert(glob, msgUnitArr);   // convert Corba to internal ...
         blaster.publishOneway(this.addressServer, getSecretSessionId(), internalUnitArr);
      }
      catch (Throwable e) {
         log.severe("publishOneway() failed, exception is not sent to client: " + e.toString());
         e.printStackTrace();
      }
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] erase(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      try {
         if (log.isLoggable(Level.FINER)) log.finer("Entering erase() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         if (log.isLoggable(Level.FINEST)) log.finest("erase()\n" + xmlKey_literal + "\n" + qos_literal);

         String [] retArr = blaster.erase(this.addressServer, getSecretSessionId(), xmlKey_literal, qos_literal);

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
         if (log.isLoggable(Level.FINER)) log.finer("Entering get() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         if (log.isLoggable(Level.FINEST)) log.finest("get()\n" + xmlKey_literal + "\n" + qos_literal);

         org.xmlBlaster.util.MsgUnitRaw[] msgUnitArr = blaster.get(this.addressServer, getSecretSessionId(), xmlKey_literal, qos_literal);

         org.xmlBlaster.protocol.corba.serverIdl.MessageUnit[] corbaUnitArr = CorbaDriver.convert(msgUnitArr);  // convert internal to Corba ...

         // if (log.isLoggable(Level.FINEST)) log.finest("Elapsed time in get()" + stop.nice());

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
         log.severe("Sorry, can't find out who you are, access denied");
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
      // if (log.isLoggable(Level.FINE)) log.trace("ServerImpl.CONVERT", "Converted POA-AOM <" + objectId + "> to session ID <" + result + ">");
      return result;
   }

   /**
    * Ping to check if xmlBlaster is alive.
    * @see org.xmlBlaster.protocol.I_XmlBlaster#ping(String)
    */
   public String ping(String qos)
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering ping("+qos+") ...");
      return blaster.ping(this.addressServer, qos);
   }
}

