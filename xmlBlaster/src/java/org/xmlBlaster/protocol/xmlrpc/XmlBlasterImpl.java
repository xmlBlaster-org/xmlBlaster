/*------------------------------------------------------------------------------
Name:      XmlBlasterImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the xmlBlaster interface for xml-rpc.
Version:   $Id: XmlBlasterImpl.java,v 1.9 2001/02/14 10:55:21 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import java.util.Vector;
import org.xmlBlaster.util.Log;
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
public class XmlBlasterImpl
{
   private final String ME = "XmlRpc.XmlBlasterImpl";
   private org.xmlBlaster.protocol.I_XmlBlaster blasterNative;


   /**
    * Constructor.
    */
   public XmlBlasterImpl(org.xmlBlaster.protocol.I_XmlBlaster blasterNative)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering constructor ...");
      this.blasterNative = blasterNative;
   }


   /**
    * Subscribe to messages.
    */
   public String subscribe(String sessionId, String xmlKey_literal, String qos_literal)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering subscribe() xmlKey=\n"
                                 + xmlKey_literal + ") ...");
      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

      String oid = blasterNative.subscribe(sessionId, xmlKey_literal, qos_literal);

      if (Log.TIME) Log.time(ME, "Elapsed time in subscribe()" + stop.nice());

      return oid;
   }


   /**
    * @see xmlBlaster.idl
    */
   public void unSubscribe(String sessionId, String xmlKey_literal, String qos_literal)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering unSubscribe() xmlKey=\n" + xmlKey_literal + ") ...");
      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

      blasterNative.unSubscribe(sessionId, xmlKey_literal, qos_literal);

      if (Log.TIME) Log.time(ME, "Elapsed time in unSubscribe()" + stop.nice());
   }


   /**
    * @see xmlBlaster.idl
    */
   public String publish (String sessionId, String xmlKey_literal, byte[] content,
         String publishQoS_literal)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering publish() ...");
      MessageUnit msgUnit = new MessageUnit(xmlKey_literal, content, publishQoS_literal);
      return blasterNative.publish(sessionId, msgUnit);
   }


   /**
    * This variant allows to publish simple string based messages
    * (the content is a string).
    * @see xmlBlaster.idl
    */
   public String publish (String sessionId, String xmlKey_literal, String content,
         String publishQoS_literal)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering publish() ....");

      MessageUnit msgUnit = new MessageUnit(xmlKey_literal, content.getBytes(), publishQoS_literal);

      // convert the xml literal strings
      XmlKey xmlKey = new XmlKey(xmlKey_literal, true);
      PublishQoS publishQoS = new PublishQoS(publishQoS_literal);

      String retVal = blasterNative.publish(sessionId, xmlKey, msgUnit, publishQoS);
      return retVal;
   }


   /**
    * @see xmlBlaster.idl
    */
   public String publish (String sessionId, Vector msgUnitWrap)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering publish() ...");

      MessageUnit msgUnit = ProtoConverter.vector2MessageUnit(msgUnitWrap);

      // convert the xml literal strings
      XmlKey xmlKey = new XmlKey(msgUnit.getXmlKey(), true);
      PublishQoS publishQoS = new PublishQoS(msgUnit.getQos());

      String retVal = blasterNative.publish(sessionId, xmlKey, msgUnit, publishQoS);
      // String retVal = blasterNative.publish(sessionId, msgUnit);
      return retVal;
   }




   /**
    * @see xmlBlaster.idl
    */
   public Vector publishArr(String sessionId, Vector msgUnitArrWrap)
      throws XmlBlasterException
   {

      if (Log.CALL) Log.call(ME, "Entering publish() ...");
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
   }



   /**
    * @see xmlBlaster.idl
    */
   public Vector erase(String sessionId, String xmlKey_literal, String qos_literal)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering erase() xmlKey=\n" + xmlKey_literal + ") ...");

      String[] retArr = blasterNative.erase(sessionId, xmlKey_literal, qos_literal);

      return ProtoConverter.stringArray2Vector(retArr);
   }



   /**
    * Synchronous access
    * @return content
    * @see xmlBlaster.idl
    */
   public Vector get(String sessionId, String xmlKey_literal, String qos_literal)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering get() xmlKey=\n" + xmlKey_literal + ") ...");
      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();

      MessageUnit[] msgUnitArr = blasterNative.get(sessionId, xmlKey_literal, qos_literal);

      // convert the MessageUnit array to a Vector array
      Vector msgUnitArrWrap = ProtoConverter.messageUnitArray2Vector(msgUnitArr);

      if (Log.TIME) Log.time(ME, "Elapsed time in get()" + stop.nice());

      return msgUnitArrWrap;
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

