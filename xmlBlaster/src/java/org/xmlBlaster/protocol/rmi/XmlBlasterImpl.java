/*------------------------------------------------------------------------------
Name:      XmlBlasterImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the CORBA xmlBlaster-server interface
Version:   $Id: XmlBlasterImpl.java,v 1.4 2000/06/25 18:32:43 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.rmi;

import org.jutils.log.Log;
import org.jutils.time.StopWatch;
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
   private org.xmlBlaster.protocol.I_XmlBlaster blasterNative;


   /**
    */
   public XmlBlasterImpl(org.xmlBlaster.protocol.I_XmlBlaster blasterNative) throws RemoteException, XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering constructor ...");
      this.blasterNative = blasterNative;
   }


   /**
    * Subscribe to messages
    */
   public String subscribe(String sessionId, String xmlKey_literal, String qos_literal) throws RemoteException, XmlBlasterException
   {
      try {
         if (Log.CALLS) Log.calls(ME, "Entering subscribe() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

         String oid = blasterNative.subscribe(sessionId, xmlKey_literal, qos_literal);

         if (Log.TIME) Log.time(ME, "Elapsed time in subscribe()" + stop.nice());

         return oid;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }


   /**
    * @see xmlBlaster.idl
    */
   public void unSubscribe(String sessionId, String xmlKey_literal, String qos_literal) throws RemoteException, XmlBlasterException
   {
      try {
         if (Log.CALLS) Log.calls(ME, "Entering unSubscribe() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

         blasterNative.unSubscribe(sessionId, xmlKey_literal, qos_literal);

         if (Log.TIME) Log.time(ME, "Elapsed time in unSubscribe()" + stop.nice());
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }


   /**
    * @see xmlBlaster.idl
    */
   public String publish(String sessionId, MessageUnit msgUnit) throws RemoteException, XmlBlasterException
   {
      try {
         if (Log.CALLS) Log.calls(ME, "Entering publish() ...");

         String retVal = blasterNative.publish(sessionId, msgUnit);


         return retVal;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }


   /**
    * @see xmlBlaster.idl
    */
   public String[] publishArr(String sessionId, MessageUnit[] msgUnitArr) throws RemoteException, XmlBlasterException
   {
      try {
         if (Log.CALLS) Log.calls(ME, "Entering publish() ...");

         String[] returnArr = new String[0];

         if (msgUnitArr.length < 1) {
            if (Log.TRACE) Log.trace(ME, "Entering xmlBlaster.publish(), nothing to do, zero msgUnits sent");
            return returnArr;
         }
         if (Log.CALLS) Log.trace(ME, "Entering xmlBlaster.publish() for " + msgUnitArr.length + " Messages");

         String[] strArr = blasterNative.publishArr(sessionId, msgUnitArr);

         return strArr;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }


   /**
    * @see xmlBlaster.idl
    */
   public String[] erase(String sessionId, String xmlKey_literal, String qos_literal) throws RemoteException, XmlBlasterException
   {
      try {
         if (Log.CALLS) Log.calls(ME, "Entering erase() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");

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
    * @see xmlBlaster.idl
    */
   public MessageUnit[] get(String sessionId, String xmlKey_literal, String qos_literal) throws RemoteException, XmlBlasterException
   {
      try {
         if (Log.CALLS) Log.calls(ME, "Entering get() xmlKey=\n" + xmlKey_literal/* + ", qos=" + qos_literal*/ + ") ...");
         StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

         MessageUnit[] msgUnitArr = blasterNative.get(sessionId, xmlKey_literal, qos_literal);

         if (Log.TIME) Log.time(ME, "Elapsed time in get()" + stop.nice());

         return msgUnitArr;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }
   }


   /**
    * Test RMI connection.
    * @return true
    */
   public boolean ping() throws RemoteException, XmlBlasterException
   {
      return true;
   }
}

