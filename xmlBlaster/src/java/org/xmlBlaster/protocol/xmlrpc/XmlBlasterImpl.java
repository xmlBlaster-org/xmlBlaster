/*------------------------------------------------------------------------------
Name:      XmlBlasterImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the xmlBlaster interface for xml-rpc.
Version:   $Id: XmlBlasterImpl.java,v 1.1 2000/08/30 00:21:58 laghi Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import java.util.Vector;
import org.jutils.log.Log;
import org.jutils.time.StopWatch;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.util.protocol.ProtoConverter;


/**
 * Implements the xmlBlaster server XML-RPC interface. Because the xml-rpc 
 * protocol does not recognize user-defined classes, these must be converted to
 * something which xml-rpc does understand. That's why following transformations
 * will take place:
 * <pre>
 * MessageUnit are converted to Vector 
 * MessageUnit[] are converted to Vector (of Vector)
 * String[] are converted to Vector (of String)
 * boolean are converted to int
 * </pre>
 * <p />
 * @author "Michele Laghi" <michele.laghi@attglobal.net>
 *
 * @see ProtoProtoConverter
 */
public class XmlBlasterImpl implements org.xmlBlaster.protocol.xmlrpc.I_XmlBlaster
{
   private final String ME = "XmlBlasterImpl";
   private org.xmlBlaster.protocol.I_XmlBlaster blasterNative;


   /**
    * Constructor.
    */
   public XmlBlasterImpl(org.xmlBlaster.protocol.I_XmlBlaster blasterNative) 
      throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering constructor ...");
      this.blasterNative = blasterNative;
   }


   /**
    * Subscribe to messages.
    */
   public String subscribe(String sessionId, String xmlKey_literal, String qos_literal) 
      throws XmlBlasterException
   {

      try {
         if (Log.CALLS) Log.calls(ME, "Entering subscribe() xmlKey=\n" 
                                  + xmlKey_literal + ") ...");
         StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

         String oid = blasterNative.subscribe(sessionId, xmlKey_literal, qos_literal);
         
         if (Log.TIME) Log.time(ME, "Elapsed time in subscribe()" + stop.nice());

         return oid;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); 
         // transform native exception to Corba exception
      }
   }


   /**
    * @see xmlBlaster.idl
    */
   public void unSubscribe(String sessionId, String xmlKey_literal, String qos_literal) 
      throws XmlBlasterException
   {
      try {
         Log.calls(ME, "Entering unSubscribe() xmlKey=\n" + xmlKey_literal + ") ...");
         StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

         blasterNative.unSubscribe(sessionId, xmlKey_literal, qos_literal);

         if (Log.TIME) Log.time(ME, "Elapsed time in unSubscribe()" + stop.nice());
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); 
         // transform native exception to Corba exception
      }
   }


   /**
    * @see xmlBlaster.idl
    */
   public String publish (String sessionId, String xmlKey_literal, Vector msgUnitWrap,
         String publishQoS_literal)
      throws XmlBlasterException
   {

      if (Log.CALLS) Log.calls(ME, "Entering publish() ...");

      try {

         MessageUnit msgUnit = ProtoConverter.vector2MessageUnit(msgUnitWrap);

         // convert the xml literal strings
         XmlKey xmlKey = new XmlKey(xmlKey_literal);
         PublishQoS publishQoS = new PublishQoS(publishQoS_literal);

         String retVal = blasterNative.publish(sessionId, xmlKey, msgUnit, publishQoS);
         // String retVal = blasterNative.publish(sessionId, msgUnit);
         return retVal;
      }

      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); 
         // transform native exception to Corba exception
      }
   }



   /**
    * @see xmlBlaster.idl
    */
   public Vector publishArr(String sessionId, Vector msgUnitArrWrap) 
      throws XmlBlasterException
   {

      if (Log.CALLS) Log.calls(ME, "Entering publish() ...");
      int arrayLength = msgUnitArrWrap.size();

      if (arrayLength < 1) {
         if (Log.TRACE) 
            Log.trace(ME, "Entering xmlBlaster.publish(), nothing to do, zero msgUnits sent");
         return new Vector(); // empty Vector return
      }
      
      try {
         MessageUnit[] msgUnitArr = ProtoConverter.vector2MessageUnitArray(msgUnitArrWrap);
         String[] strArr = blasterNative.publishArr(sessionId, msgUnitArr);
         return ProtoConverter.stringArray2Vector(strArr);
      }

      catch (ClassCastException e) {
         Log.error(ME+".publish", "not a valid MessageUnit: " + e.toString());
         throw new XmlBlasterException("Not a valid Message Unit", "Class Cast Exception");
      }
      
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); 
         // transform native exception to Corba exception
      }

   }



   /**
    * @see xmlBlaster.idl
    */
   public Vector erase(String sessionId, String xmlKey_literal, String qos_literal) 
      throws XmlBlasterException
   {
      try {
         if (Log.CALLS) Log.calls(ME, "Entering erase() xmlKey=\n" + xmlKey_literal + ") ...");

         String[] retArr = blasterNative.erase(sessionId, xmlKey_literal, qos_literal);

         return ProtoConverter.stringArray2Vector(retArr);
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); 
         // transform native exception to Corba exception
      }
   }



   /**
    * Synchronous access
    * @return content
    * @see xmlBlaster.idl
    */
   public Vector get(String sessionId, String xmlKey_literal, String qos_literal) 
      throws XmlBlasterException
   {

      try {

         if (Log.CALLS) Log.calls(ME, "Entering get() xmlKey=\n" + xmlKey_literal + ") ...");
         StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

         MessageUnit[] msgUnitArr = blasterNative.get(sessionId, xmlKey_literal, qos_literal);

         // convert the MessageUnit array to a Vector array
         Vector msgUnitArrWrap = ProtoConverter.messageUnitArray2Vector(msgUnitArr);

         if (Log.TIME) Log.time(ME, "Elapsed time in get()" + stop.nice());

         return msgUnitArrWrap;
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); 
         // transform native exception to Corba exception
      }
   }


   /**
    * Test the xml-rpc connection.
    * @return 1
    */
   public int ping() throws XmlBlasterException
   {
      return 1;
   }

   //   public String toXml() throws XmlBlasterException;

   public String toXml(String extraOffset) throws XmlBlasterException 
   {
      return blasterNative.toXml(extraOffset);
   }
   
   
}

