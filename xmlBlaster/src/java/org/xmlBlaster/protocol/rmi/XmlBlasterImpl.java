/*------------------------------------------------------------------------------
Name:      XmlBlasterImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the CORBA xmlBlaster-server interface
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.engine.qos.AddressServer;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;


/**
 * Implements the xmlBlaster server RMI interface.
 * <p />
 * @see org.xmlBlaster.engine.RequestBroker
 */
public class XmlBlasterImpl extends UnicastRemoteObject implements org.xmlBlaster.protocol.rmi.I_XmlBlaster
{
   private static final long serialVersionUID = 1L;
   private static Logger log = Logger.getLogger(XmlBlasterImpl.class.getName());
   private org.xmlBlaster.protocol.I_XmlBlaster blasterNative;
   private final AddressServer addressServer;


   /**
    */
   public XmlBlasterImpl(Global glob, AddressServer addressServer,
                         org.xmlBlaster.protocol.I_XmlBlaster blasterNative) throws RemoteException, XmlBlasterException
   {

      if (log.isLoggable(Level.FINER)) log.finer("Entering constructor ...");
      this.blasterNative = blasterNative;
      this.addressServer = addressServer;
   }


   /**
    * Subscribe to messages
    */
   public String subscribe(String sessionId, String xmlKey_literal, String qos_literal) throws RemoteException, XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering subscribe() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");

      String oid = blasterNative.subscribe(this.addressServer, sessionId, xmlKey_literal, qos_literal);

      return oid;
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] unSubscribe(String sessionId, String xmlKey_literal, String qos_literal) throws RemoteException, XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering unSubscribe() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");

      String[] retArr = blasterNative.unSubscribe(this.addressServer, sessionId, xmlKey_literal, qos_literal);

      return retArr;
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String publish(String sessionId, MsgUnitRaw msgUnit) throws RemoteException, XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering publish() ...");

      String retVal = blasterNative.publish(this.addressServer, sessionId, msgUnit);

      return retVal;
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] publishArr(String sessionId, MsgUnitRaw[] msgUnitArr) throws RemoteException, XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.fine("Entering xmlBlaster.publish() for " + msgUnitArr.length + " Messages");
      if (msgUnitArr.length < 1) {
         if (log.isLoggable(Level.FINE)) log.fine("Entering xmlBlaster.publishArr(), nothing to do, zero msgUnits sent");
         return new String[0];
      }
      return blasterNative.publishArr(this.addressServer, sessionId, msgUnitArr);
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public void publishOneway(String sessionId, MsgUnitRaw[] msgUnitArr) throws RemoteException
   {
      if (log.isLoggable(Level.FINER)) log.fine("Entering xmlBlaster.publishOneway() for " + msgUnitArr.length + " Messages");

      if (msgUnitArr.length < 1) {
         if (log.isLoggable(Level.FINE)) log.fine("Entering xmlBlaster.publishOneway(), nothing to do, zero msgUnits sent");
         return;
      }

      blasterNative.publishOneway(this.addressServer, sessionId, msgUnitArr);
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] erase(String sessionId, String xmlKey_literal, String qos_literal) throws RemoteException, XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering erase() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");

      String [] retArr = blasterNative.erase(this.addressServer, sessionId, xmlKey_literal, qos_literal);

      return retArr;
   }


   /**
    * Synchronous access
    * @return content
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public MsgUnitRaw[] get(String sessionId, String xmlKey_literal, String qos_literal) throws RemoteException, XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering get() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");

      MsgUnitRaw[] msgUnitArr = blasterNative.get(this.addressServer, sessionId, xmlKey_literal, qos_literal);

      return msgUnitArr;
   }

   /**
     * Ping to check if xmlBlaster is alive. 
     * @see org.xmlBlaster.protocol.I_XmlBlaster#ping(String)
     */
   public String ping(String qos) throws RemoteException
   {
      return blasterNative.ping(this.addressServer, qos);
   }
}

