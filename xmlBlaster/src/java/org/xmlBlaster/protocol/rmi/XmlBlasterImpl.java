/*------------------------------------------------------------------------------
Name:      XmlBlasterImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the CORBA xmlBlaster-server interface
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.jutils.log.LogChannel;
import org.jutils.time.StopWatch;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;


/**
 * Implements the xmlBlaster server RMI interface.
 * <p />
 * @see org.xmlBlaster.engine.RequestBroker
 */
public class XmlBlasterImpl extends UnicastRemoteObject implements org.xmlBlaster.protocol.rmi.I_XmlBlaster
{
   private final String ME = "RMI.XmlBlasterImpl";
   private final LogChannel log;
   private org.xmlBlaster.protocol.I_XmlBlaster blasterNative;


   /**
    */
   public XmlBlasterImpl(Global glob, org.xmlBlaster.protocol.I_XmlBlaster blasterNative) throws RemoteException, XmlBlasterException
   {
      this.log = glob.getLog("rmi");
      if (log.CALL) log.call(ME, "Entering constructor ...");
      this.blasterNative = blasterNative;
   }


   /**
    * Subscribe to messages
    */
   public String subscribe(String sessionId, String xmlKey_literal, String qos_literal) throws RemoteException, XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering subscribe() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
      StopWatch stop=null; if (log.TIME) stop = new StopWatch();

      String oid = blasterNative.subscribe(sessionId, xmlKey_literal, qos_literal);

      if (log.TIME) log.time(ME, "Elapsed time in subscribe()" + stop.nice());

      return oid;
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] unSubscribe(String sessionId, String xmlKey_literal, String qos_literal) throws RemoteException, XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering unSubscribe() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
      StopWatch stop=null; if (log.TIME) stop = new StopWatch();

      String[] retArr = blasterNative.unSubscribe(sessionId, xmlKey_literal, qos_literal);

      if (log.TIME) log.time(ME, "Elapsed time in unSubscribe()" + stop.nice());

      return retArr;
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String publish(String sessionId, MsgUnitRaw msgUnit) throws RemoteException, XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering publish() ...");

      String retVal = blasterNative.publish(sessionId, msgUnit);

      return retVal;
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String[] publishArr(String sessionId, MsgUnitRaw[] msgUnitArr) throws RemoteException, XmlBlasterException
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
   public void publishOneway(String sessionId, MsgUnitRaw[] msgUnitArr) throws RemoteException
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
      if (log.CALL) log.call(ME, "Entering erase() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");

      String [] retArr = blasterNative.erase(sessionId, xmlKey_literal, qos_literal);

      return retArr;
   }


   /**
    * Synchronous access
    * @return content
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public MsgUnitRaw[] get(String sessionId, String xmlKey_literal, String qos_literal) throws RemoteException, XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering get() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
      StopWatch stop=null; if (log.TIME) stop = new StopWatch();

      MsgUnitRaw[] msgUnitArr = blasterNative.get(sessionId, xmlKey_literal, qos_literal);

      if (log.TIME) log.time(ME, "Elapsed time in get()" + stop.nice());

      return msgUnitArr;
   }

   /**
     * Ping to check if xmlBlaster is alive. 
     * @see org.xmlBlaster.protocol.I_XmlBlaster#ping(String)
     */
   public String ping(String qos) throws RemoteException
   {
      return blasterNative.ping(qos);
   }
}

