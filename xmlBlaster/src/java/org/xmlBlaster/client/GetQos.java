/*------------------------------------------------------------------------------
Name:      GetQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: GetQos.java,v 1.1 2001/03/27 19:49:32 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xml.sax.Attributes;


/**
 * QoS (quality of service) informations sent from server to client<br />
 * in the returned messages of a get() method call. 
 * <p />
 * If you are a Java client you may use this class to parse the QoS argument.
 * <p />
 * Example:
 * <pre>
 *   &lt;qos> &lt;!-- GetQos -->
 *     &lt;state>
 *        OK
 *     &lt;/state>
 *     &lt;sender>
 *        Tim
 *     &lt;/sender>
 *  &lt;/qos>
 * </pre>
 */
public class GetQos extends org.xmlBlaster.util.XmlQoSBase
{
   private String ME = "GetQos";

   /** helper flag for SAX parsing: parsing inside <state> ? */
   private boolean inState = false;
   /** the state of the message */
   private String state = null;
   /** helper flag for SAX parsing: parsing inside <sender> ? */
   private boolean inSender = false;
   /** the sender (publisher) of this message (unique loginName) */
   private String sender = null;


   /**
    * Constructs the specialized quality of service object for the returned
    * message of a get() call.
    */
   public GetQos(String xmlQoS_literal) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Creating GetQos(" + xmlQoS_literal + ")");
      //if (Log.CALL) Log.call(ME, "Creating GetQos()");
      init(xmlQoS_literal);
   }


   /**
    * Access sender name.
    * @return loginName of sender
    */
   public String getSender()
   {
      return sender;
   }


   /**
    * Access state of message.
    * @return OK (Other values are not yet supported)
    */
   public String getState()
   {
      return state;
   }


   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public void startElement(String uri, String localName, String name, Attributes attrs)
   {
      super.startElement(uri, localName, name, attrs);

      //if (Log.TRACE) Log.trace(ME, "Entering startElement for " + name);

      if (name.equalsIgnoreCase("state")) {
         if (!inQos)
            return;
         inState = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               Log.warn(ME, "Ignoring sent <state> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (Log.TRACE) Log.trace(ME, "Found state tag");
         }
         return;
      }

      if (name.equalsIgnoreCase("sender")) {
         if (!inQos)
            return;
         inSender = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               Log.warn(ME, "Ignoring sent <sender> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (Log.TRACE) Log.trace(ME, "Found sender tag");
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
      super.endElement(uri, localName, name);

      // if (Log.TRACE) Log.trace(ME, "Entering endElement for " + name);

      if(name.equalsIgnoreCase("state")) {
         inState = false;
         state = character.toString().trim();
         // if (Log.TRACE) Log.trace(ME, "Found message state = " + state);
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("sender")) {
         inSender = false;
         sender = character.toString().trim();
         // if (Log.TRACE) Log.trace(ME, "Found message sender login name = " + sender);
         character.setLength(0);
         return;
      }
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final StringBuffer printOn()
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final StringBuffer printOn(String extraOffset)
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<qos> <!-- GetQos -->");
      if (state != null) {
         sb.append(offset + "   <state>");
         sb.append(offset + "      " + state);
         sb.append(offset + "   </state>");
      }
      if (sender != null) {
         sb.append(offset + "   <sender>");
         sb.append(offset + "      " + sender);
         sb.append(offset + "   </sender>");
      }
      sb.append(offset + "</qos>\n");

      return sb;
   }


   public final String toString()
   {
      return printOn(null).toString();
   }


   /**
    *  For testing invoke: jaco org.xmlBlaster.client.GetQos
    */
   public static void main( String[] args ) throws XmlBlasterException
   {
      String xml = "<qos>\n" +
                   "   <state>\n" +
                   "      OK\n" +
                   "   </state>\n" +
                   "   <sender>\n" +
                   "      Joe\n" +
                   "   </sender>\n" +
                   "</qos>";

      GetQos up = new GetQos(xml);
      Log.info("Test", "\n" + up.toXml());

      up = new GetQos(up.toXml());
      Log.exit("Test", "\n" + up.toXml());
   }
}
