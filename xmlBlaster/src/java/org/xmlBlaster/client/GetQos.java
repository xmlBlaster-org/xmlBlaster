/*------------------------------------------------------------------------------
Name:      GetQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xml.sax.Attributes;


/**
 * Handling one QoS (quality of service), knows how to parse it with SAX
 * @deprecated  Use org.xmlBlaster.client.qos.GetReturnQos
 */
public class GetQos extends org.xmlBlaster.util.XmlQoSBase
{
   private String ME = "GetQos";
   private LogChannel log;

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
      super(Global.instance());
      this.log = Global.instance().getLog("core");
      if (log.CALL) log.call(ME, "Creating GetQos(" + xmlQoS_literal + ")");
      //if (log.CALL) log.call(ME, "Creating GetQos()");
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

      //if (log.TRACE) log.trace(ME, "Entering startElement for " + name);

      if (name.equalsIgnoreCase("state")) {
         if (!inQos)
            return;
         inState = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getQName(i).equalsIgnoreCase("id") ) {
                  state = attrs.getValue(i).trim();
                  break;
               }
            }
            // if (log.TRACE) log.trace(ME, "Found state tag");
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
               log.warn(ME, "Ignoring sent <sender> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (log.TRACE) log.trace(ME, "Found sender tag");
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

      // if (log.TRACE) log.trace(ME, "Entering endElement for " + name);

      if(name.equalsIgnoreCase("state")) {
         inState = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("sender")) {
         inSender = false;
         sender = character.toString().trim();
         // if (log.TRACE) log.trace(ME, "Found message sender login name = " + sender);
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

      sb.append(offset + "<qos> <!-- GetQos -->");
      if (state != null) {
         sb.append(offset).append("   <state id='").append(state).append("'/>");
      }
      if (sender != null) {
         sb.append(offset + "   <sender>");
         sb.append(offset + "      " + sender);
         sb.append(offset + "   </sender>");
      }
      sb.append(offset + "</qos>\n");

      return sb.toString();
   }


   public final String toString()
   {
      return toXml(null);
   }


   /**
    *  For testing invoke: jaco org.xmlBlaster.client.GetQos
    */
   public static void main( String[] args ) throws XmlBlasterException
   {
      String xml = "<qos>\n" +
                   "   <state id='OK'/>\n" +
                   "   <sender>\n" +
                   "      Joe\n" +
                   "   </sender>\n" +
                   "</qos>";

      GetQos up = new GetQos(xml);
      System.out.println("\n" + up.toXml());

      up = new GetQos(up.toXml());
   }
}
