/*------------------------------------------------------------------------------
Name:      XmlBlasterImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the xmlBlaster interface for xml-rpc.
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import java.util.Vector;
import org.jutils.log.LogChannel;
import org.jutils.time.StopWatch;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.util.protocol.ProtoConverter;


/**
 * Implements the xmlBlaster server XMLRPC interface. Because the xml-rpc
 * protocol does not recognize user-defined classes, these must be converted to
 * something which xml-rpc does understand. That's why following transformations
 * will take place:
 * <pre>
 * MsgUnitRaw are converted to Vector
 * MsgUnitRaw[] are converted to Vector (of Vector)
 * String[] are converted to Vector (of String)
 * boolean are converted to int
 * void return is not allowed so we return an empty string instead
 * </pre>
 * <p />
 * @author "Michele Laghi" (laghi@swissinfo.org)
 */
public class XmlBlasterImpl
{
   private final String ME = "XmlRpc.XmlBlasterImpl";
   private LogChannel log;
   private org.xmlBlaster.protocol.I_XmlBlaster blasterNative;


   /**
    * Constructor.
    */
   public XmlBlasterImpl(Global glob, org.xmlBlaster.protocol.I_XmlBlaster blasterNative)
      throws XmlBlasterException
   {
      log = glob.getLog("xmlrpc");
      if (log.CALL) log.call(ME, "Entering constructor ...");
      this.blasterNative = blasterNative;
   }


   /**
    * Subscribe to messages.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">The interface.subscribe requirement</a>
    */
   public String subscribe(String sessionId, String xmlKey_literal, String qos_literal)
      throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering subscribe() xmlKey=\n"
                                 + xmlKey_literal + ") ...");
      StopWatch stop=null; if (log.TIME) stop = new StopWatch();

      String oid = blasterNative.subscribe(sessionId, xmlKey_literal, qos_literal);

      if (log.TIME) log.time(ME, "Elapsed time in subscribe()" + stop.nice());

      return oid;
   }


   /**
    * void return is not allowed so we return an empty string instead
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html">The interface.unSubscribe requirement</a>
    */
   public Vector unSubscribe(String sessionId, String xmlKey_literal, String qos_literal)
      throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering unSubscribe() xmlKey=\n" + xmlKey_literal + ") ...");
      StopWatch stop=null; if (log.TIME) stop = new StopWatch();

      String[] retArr = blasterNative.unSubscribe(sessionId, xmlKey_literal, qos_literal);

      if (log.TIME) log.time(ME, "Elapsed time in unSubscribe()" + stop.nice());

      return ProtoConverter.stringArray2Vector(retArr);
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public String publish (String sessionId, String xmlKey_literal, byte[] content,
         String publishQos_literal)
      throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering publish() ...");
      MsgUnitRaw msgUnit = new MsgUnitRaw(xmlKey_literal, content, publishQos_literal);
      return blasterNative.publish(sessionId, msgUnit);
   }


   /**
    * This variant allows to publish simple string based messages
    * (the content is a string).
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public String publish (String sessionId, String xmlKey_literal, String content,
                          String publishQos_literal)
      throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering publish() ....");

      MsgUnitRaw msgUnit = new MsgUnitRaw(xmlKey_literal, content.getBytes(), publishQos_literal);

//      // convert the xml literal strings
//      PublishQos publishQos = new PublishQos(publishQos_literal);

      String retVal = blasterNative.publish(sessionId, msgUnit);
      return retVal;
   }


   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public String publish (String sessionId, Vector msgUnitWrap)
      throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering publish() ...");

      MsgUnitRaw msgUnit = ProtoConverter.vector2MsgUnitRaw(msgUnitWrap);

      //PublishQos publishQos = new PublishQos(msgUnit.getQos());

      // String retVal = blasterNative.publish(sessionId, xmlKey, msgUnit, publishQos);
      String retVal = blasterNative.publish(sessionId, msgUnit);
      return retVal;
   }




   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public Vector publishArr(String sessionId, Vector msgUnitArrWrap)
      throws XmlBlasterException
   {

      if (log.CALL) log.call(ME, "Entering publish() ...");
      int arrayLength = msgUnitArrWrap.size();

      if (arrayLength < 1) {
         if (log.TRACE)
            log.trace(ME, "Entering xmlBlaster.publish(), nothing to do, zero msgUnits sent");
         return new Vector(); // empty Vector return
      }

      try {
         MsgUnitRaw[] msgUnitArr = ProtoConverter.vector2MsgUnitRawArray(msgUnitArrWrap);
         String[] strArr = blasterNative.publishArr(sessionId, msgUnitArr);
         return ProtoConverter.stringArray2Vector(strArr);
      }
      catch (ClassCastException e) {
         log.error(ME+".publish", "not a valid MsgUnitRaw: " + e.toString());
         throw new XmlBlasterException("Not a valid Message Unit", "Class Cast Exception");
      }
   }

   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public void publishOneway(String sessionId, Vector msgUnitArrWrap)
   {
      if (log.CALL) log.call(ME, "Entering publishOneway() ...");
      int arrayLength = msgUnitArrWrap.size();

      if (arrayLength < 1) {
         if (log.TRACE) log.trace(ME, "Entering xmlBlaster.publishOneway(), nothing to do, zero msgUnits sent");
         return;
      }

      try {
         MsgUnitRaw[] msgUnitArr = ProtoConverter.vector2MsgUnitRawArray(msgUnitArrWrap);
         blasterNative.publishOneway(sessionId, msgUnitArr);
      }
      catch (Throwable e) {
         log.error(ME, "Caught exception which can't be delivered to client because of 'oneway' mode: " + e.toString());
      }
   }

   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.erase.html">The interface.erase requirement</a>
    */
   public Vector erase(String sessionId, String xmlKey_literal, String qos_literal)
      throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering erase() xmlKey=\n" + xmlKey_literal + ") ...");

      String[] retArr = blasterNative.erase(sessionId, xmlKey_literal, qos_literal);

      return ProtoConverter.stringArray2Vector(retArr);
   }



   /**
    * Synchronous access
    * @return content
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html">The interface.get requirement</a>
    */
   public Vector get(String sessionId, String xmlKey_literal, String qos_literal)
      throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering get() xmlKey=\n" + xmlKey_literal + ") ...");
      StopWatch stop=null; if (log.TIME) stop = new StopWatch();

      MsgUnitRaw[] msgUnitArr = blasterNative.get(sessionId, xmlKey_literal, qos_literal);

      // convert the MsgUnitRaw array to a Vector array
      Vector msgUnitArrWrap = ProtoConverter.messageUnitArray2Vector(msgUnitArr);

      if (log.TIME) log.time(ME, "Elapsed time in get()" + stop.nice());

      return msgUnitArrWrap;
   }


   /**
    * Test the xml-rpc connection.
    * @return ""
    */
   public String ping(String qos)
   {
      return "";
   }

   //   public String toXml() throws XmlBlasterException;

   public String toXml(String extraOffset) throws XmlBlasterException
   {
      return blasterNative.toXml(extraOffset);
   }
}

