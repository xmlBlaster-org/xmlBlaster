/*------------------------------------------------------------------------------
Name:      XmlBlasterImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the CORBA xmlBlaster-server interface
Version:   $Id: XmlBlasterImpl.java,v 1.9 2002/09/13 23:18:13 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.jutils.log.LogChannel;
import org.jutils.time.StopWatch;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;


/**
 * Implements the xmlBlaster server RMI interface.
 * <p />
 * @see org.xmlBlaster.engine.RequestBroker
 */
public class XmlBlasterImpl extends UnicastRemoteObject implements org.xmlBlaster.protocol.rmi.I_XmlBlaster
{
   private final String ME = "XmlBlasterImpl";
   private final LogChannel log;
   private org.xmlBlaster.protocol.I_XmlBlaster blasterNative;


   /**
    */
   public XmlBlasterImpl(org.xmlBlaster.protocol.I_XmlBlaster blasterNative) throws RemoteException, XmlBlasterException
   {
      this.log = Global.instance().getLog("rmi");
      if (log.CALL) log.call(ME, "Entering constructor ...");
      this.blasterNative = blasterNative;
   }


   /**
    * Subscribe to messages
    */
   public String subscribe(String sessionId, String xmlKey_literal, String qos_literal) throws RemoteException, XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering subscribe() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         StopWatch stop=null; if (log.TIME) stop = new StopWatch();

         String oid = blasterNative.subscribe(sessionId, xmlKey_literal, qos_literal);

         if (log.TIME) log.time(ME, "Elapsed time in subscribe()" + stop.nice());

         return oid;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public void unSubscribe(String sessionId, String xmlKey_literal, String qos_literal) throws RemoteException, XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering unSubscribe() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         StopWatch stop=null; if (log.TIME) stop = new StopWatch();

         blasterNative.unSubscribe(sessionId, xmlKey_literal, qos_literal);

         if (log.TIME) log.time(ME, "Elapsed time in unSubscribe()" + stop.nice());
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String publish(String sessionId, MessageUnit msgUnit) throws RemoteException, XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering publish() ...");

         String retVal = blasterNative.publish(sessionId, msgUnit);


         return retVal;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] publishArr(String sessionId, MessageUnit[] msgUnitArr) throws RemoteException, XmlBlasterException
   {
      if (log.CALL) log.trace(ME, "Entering xmlBlaster.publish() for " + msgUnitArr.length + " Messages");
      if (msgUnitArr.length < 1) {
         if (log.TRACE) log.trace(ME, "Entering xmlBlaster.publishArr(), nothing to do, zero msgUnits sent");
         return new String[0];
      }
      return blasterNative.publishArr(sessionId, msgUnitArr);
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public void publishOneway(String sessionId, MessageUnit[] msgUnitArr) throws RemoteException
   {
      if (log.CALL) log.trace(ME, "Entering xmlBlaster.publishOneway() for " + msgUnitArr.length + " Messages");

      if (msgUnitArr.length < 1) {
         if (log.TRACE) log.trace(ME, "Entering xmlBlaster.publishOneway(), nothing to do, zero msgUnits sent");
         return;
      }

      blasterNative.publishOneway(sessionId, msgUnitArr);
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] erase(String sessionId, String xmlKey_literal, String qos_literal) throws RemoteException, XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering erase() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");

         String [] retArr = blasterNative.erase(sessionId, xmlKey_literal, qos_literal);


         return retArr;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }


   /**
    * Synchronous access
    * @return content
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public MessageUnit[] get(String sessionId, String xmlKey_literal, String qos_literal) throws RemoteException, XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering get() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         StopWatch stop=null; if (log.TIME) stop = new StopWatch();

         MessageUnit[] msgUnitArr = blasterNative.get(sessionId, xmlKey_literal, qos_literal);

         if (log.TIME) log.time(ME, "Elapsed time in get()" + stop.nice());

         return msgUnitArr;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }

   /**
     * Ping to check if xmlBlaster is alive. 
     * This ping checks the availability on the application level.
     * @param qos Currently an empty string ""
     * @return    Currently an empty string ""
     */
   public String ping(String qos) throws RemoteException
   {
      return "";
   }
}

