/*------------------------------------------------------------------------------
Name:      EraseQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling QoS (quality of service), knows how to parse it with SAX
Version:   $Id: EraseQoS.java,v 1.1 1999/12/09 00:11:05 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xml.sax.AttributeList;
import java.util.Vector;


/**
 * Handling of erase() quality of services.
 * <p />
 * QoS Informations sent from the client to the server via the erase() Method<br />
 * The are needed to control the xmlBlaster behavior
 */
public class EraseQoS extends org.xmlBlaster.util.XmlQoSBase
{
   private static String ME = "EraseQoS";


   /**
    * Constructs the specialized quality of service object for a publish() call.
    */
   public EraseQoS(String xmlQoS_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Creating EraseQoS(" + xmlQoS_literal + ")");
      init(xmlQoS_literal);
   }


   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public void startElement(String name, AttributeList attrs)
   {
      super.startElement(name, attrs);

      if (Log.TRACE) Log.trace(ME, "Entering startElement for " + name);

      if (!inQos) return;
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
      sb.append(offset + "</" + ME + ">\n");

      return sb;
   }
}
