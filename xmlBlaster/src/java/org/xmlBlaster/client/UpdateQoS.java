/*------------------------------------------------------------------------------
Name:      UpdateQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: UpdateQoS.java,v 1.1 1999/12/09 00:11:05 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xml.sax.AttributeList;


/**
 * QoS (quality of service) informations sent from server to client<br />
 * via the update() method from the BlasterCallback interface.
 * <p />
 * You may use this if you are a Java client to parse the QoS argument.
 */
public class UpdateQoS extends org.xmlBlaster.util.XmlQoSBase
{
   private String ME = "UpdateQoS";

   // helper flags for SAX parsing
   private boolean inSender = false; // parsing inside <sender> ?

   private String sender = null;     // the sender (publisher) of this message (unique loginName)


   /**
    * Constructs the specialized quality of service object for a update() call.
    */
   public UpdateQoS(String xmlQoS_literal) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Creating UpdateQoS(" + xmlQoS_literal + ")");
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

      //if (Log.TRACE) Log.trace(ME, "Entering startElement for " + name);

      if (name.equalsIgnoreCase("sender")) {
         if (!inQos)
            return;
         inSender = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               Log.warning(ME, "Ignoring sent <sender> attribute " + attrs.getName(i) + "=" + attrs.getValue(i).trim());
            }
            if (Log.TRACE) Log.trace(ME, "Found sender tag");
         }
         return;
      }
   }


   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String name)
   {
      super.endElement(name);

      // if (Log.TRACE) Log.trace(ME, "Entering endElement for " + name);

      if(name.equalsIgnoreCase("sender")) {
         inSender = false;
         sender = character.toString().trim();
         if (Log.TRACE) Log.trace(ME, "Found message sender login name = " + sender);
         character = new StringBuffer();
         return;
      }
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
      if (sender != null) {
         sb.append(offset + "   <sender>" + ME + ">");
         sb.append(offset + "      " + sender);
         sb.append(offset + "   </sender>" + ME + ">");
      }
      sb.append(offset + "</" + ME + ">\n");

      return sb;
   }
}
