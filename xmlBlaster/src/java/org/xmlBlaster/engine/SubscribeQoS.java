/*------------------------------------------------------------------------------
Name:      SubscribeQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling QoS (quality of service), knows how to parse it with SAX
Version:   $Id: SubscribeQoS.java,v 1.4 2000/01/31 12:00:30 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xml.sax.AttributeList;
import java.util.Vector;


/**
 * Handling of subscribe() quality of services.
 * <p />
 * QoS Informations sent from the client to the server via the subscribe() method<br />
 * They are needed to control the xmlBlaster
 */
public class SubscribeQoS extends org.xmlBlaster.util.XmlQoSBase
{
   private static String ME = "SubscribeQoS";

   // helper flags for SAX parsing

   // flags for QoS state
   private boolean noMeta = false;     // <NoMeta />    <!-- Don't send me the xmlKey meta data on updates -->
   private boolean noContent = false;  // <NoContent /> <!-- Don't send me the content data on updates (notify only) -->
   private boolean noLocal = false;    // <NoLocal />   <!-- Inhibit the delivery of messages to myself if i have published it -->


   /**
    * Constructs the specialized quality of service object for a publish() call.
    */
   public SubscribeQoS(String xmlQoS_literal) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Creating SubscribeQoS(" + xmlQoS_literal + ")");
      init(xmlQoS_literal);
   }


   /**
    * Does client wants to have the XmlKey meta tags on update?
    *
    * @return true if full XmlKey is sent
    *         false if only <key> tag with its attributes is sent
    */
   public final boolean sendMeta()
   {
      return !noMeta;
   }


   /**
    * Does client wish the content data on updates?
    *
    * @return true if clients wishes the content on message update
    *         false if client wishes empty content updates (NOTIFICATION style)
    */
   public final boolean sendContent()
   {
      return !noContent;
   }


   /**
    * Inhibit the delivery of messages to myself if i have published it (and am a subscriber as well)?
    * @return true/false
    */
   public final boolean sendLocal()
   {
      return !noLocal;
   }


   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public void startElement(String name, AttributeList attrs)
   {
      if (super.startElementBase(name, attrs) == true)
         return;

      if (Log.TRACE) Log.trace(ME, "Entering startElement for " + name);

      if (!inQos) return;

      if (name.equalsIgnoreCase("noMeta"))
         noMeta = true;
      if (name.equalsIgnoreCase("noContent"))
         noContent = true;
      if (name.equalsIgnoreCase("noLocal"))
         noLocal = true;
   }


   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String name)
   {
      super.endElement(name);

      if (Log.TRACE) Log.trace(ME, "Entering endElement for " + name);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final StringBuffer printOn() throws XmlBlasterException
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final StringBuffer printOn(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<" + ME + ">");
      if (noMeta)
         sb.append(offset + "   <noMeta />");
      if (noContent)
         sb.append(offset + "   <noContent />");
      if (noLocal)
         sb.append(offset + "   <noLocal />");
      sb.append(offset + "</" + ME + ">\n");

      return sb;
   }
}
