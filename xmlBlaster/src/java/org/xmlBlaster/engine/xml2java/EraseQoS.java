/*------------------------------------------------------------------------------
Name:      EraseQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling QoS (quality of service), knows how to parse it with SAX
Version:   $Id: EraseQoS.java,v 1.8 2002/03/13 16:41:20 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xml2java;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xml.sax.Attributes;
import java.util.Vector;


/**
 * Handling of erase() quality of services.
 * <p />
 * QoS Informations sent from the client to the server via the erase() method<br />
 * They are needed to control the xmlBlaster behavior
 */
public class EraseQoS extends org.xmlBlaster.util.XmlQoSBase
{
   private static String ME = "EraseQoS";

   private boolean inNotify = false; // parsing inside <notify> ?
   /** Default is to notify subscribers when their topic is erased */
   private boolean notify = true;


   /**
    * Constructs the specialized quality of service object for a publish() call.
    */
   public EraseQoS(String xmlQoS_literal) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Creating EraseQoS(" + xmlQoS_literal + ")");
      init(xmlQoS_literal);
   }


   /**
    * @return true - notify subscribers, false - do nothing
    */
   public final boolean isNotify()
   {
      return notify;
   }


   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public void startElement(String uri, String localName, String name, Attributes attrs)
   {
      if (super.startElementBase(uri, localName, name, attrs) == true)
         return;

      if (Log.TRACE) Log.trace(ME, "Entering startElement for " + name);

      if (!inQos) return;

      if (name.equalsIgnoreCase("notify")) {
         if (!inQos)
            return;
         inNotify = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               Log.warn(ME, "Ignoring sent <notify> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (Log.TRACE) Log.trace(ME, "Found notify tag");
         }
         return;
      }
   }


   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name)
   {
      if (super.endElementBase(uri, localName, name) == true)
         return;

      if (Log.TRACE) Log.trace(ME, "Entering endElement for " + name);

      if(name.equalsIgnoreCase("notify")) {
         inNotify = false;
         String tmp = character.toString().trim();
         try {
            notify = new Boolean(tmp).booleanValue();
         } catch (NumberFormatException e) {
            Log.error(ME, "Wrong format of <notify>" + tmp + "</notify>, expected true or false.");
         }
         // if (Log.TRACE) Log.trace(ME, "Found notify = " + notify);
         character.setLength(0);
         return;
      }
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml()
   {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml(String extraOffset)
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<").append(ME).append(">");
      sb.append(offset).append("</").append(ME).append(">\n");

      return sb.toString();
   }


   /** For testing: java org.xmlBlaster.engine.xml2java.EraseQoS */
   public static void main(String[] args)
   {
      try {
         org.xmlBlaster.util.XmlBlasterProperty.init(args);
         String xml =
            "<qos>\n" +
            "   <notify>true</notify>\n" +
            "</qos>\n";

         EraseQoS qos = new EraseQoS(xml);
         System.out.println(qos.toXml());
      }
      catch(Throwable e) {
         Log.error("TestFailed", e.toString());
      }
   }
}
