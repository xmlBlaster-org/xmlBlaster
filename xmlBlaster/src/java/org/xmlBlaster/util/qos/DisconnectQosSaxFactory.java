/*------------------------------------------------------------------------------
Name:      DisconnectQosSaxFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Parsing disconnect QoS
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import java.util.HashMap;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

import org.xml.sax.Attributes;

/**
 * This class encapsulates the qos of a logout() or disconnect(). 
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>disconnect QoS</b> could look like this (showing default settings):<br />
 * <pre>
 *  &lt;qos>
 *    &lt;deleteSubjectQueue>true&lt;/deleteSubjectQueue>
 *    &lt;clearSessions>false&lt;/clearSessions>
 *  &lt;/qos>
 * </pre>
 * @see org.xmlBlaster.test.classtest.DisconnectQosTest
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.disconnect.html">disconnect interface</a>
 */
public final class DisconnectQosSaxFactory extends org.xmlBlaster.util.XmlQoSBase implements I_DisconnectQosFactory
{
   private String ME = "DisconnectQosSaxFactory";
   private final Global glob;
   private final LogChannel log;

   private DisconnectQosData disconnectQosData;
   private String clientPropertyKey;
   
   /**
    */
   public DisconnectQosSaxFactory(Global glob) {
      super(glob);
      this.glob = glob;
      this.log = glob.getLog("core");
   }
   
   /**
    * Parses the given xml Qos and returns a DisconnectQosData holding the data. 
    * Parsing of disconnect() QoS is supported here.
    * @param the XML based ASCII string
    */
   public synchronized DisconnectQosData readObject(String xmlQos) throws XmlBlasterException {
      if (xmlQos == null) {
         xmlQos = "<qos/>";
      }

      this.disconnectQosData = new DisconnectQosData(glob, this, xmlQos);

      if (!isEmpty(xmlQos)) // if possible avoid expensive SAX parsing
         init(xmlQos);      // use SAX parser to parse it (is slow)

      return this.disconnectQosData;
   }

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public void startElement(String uri, String localName, String name, Attributes attrs) {
      if (super.startElementBase(uri, localName, name, attrs) == true)
         return;

      if (name.equalsIgnoreCase("deleteSubjectQueue")) {
         this.disconnectQosData.deleteSubjectQueue(true);
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("clearSessions")) {
         this.disconnectQosData.clearSessions(true);
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("clientProperty")) {
         this.clientPropertyKey = attrs.getValue("name");
         character.setLength(0);
         return;
      }

   }


   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name) {
      if (super.endElementBase(uri, localName, name) == true)
         return;

      if (name.equalsIgnoreCase("deleteSubjectQueue")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            this.disconnectQosData.deleteSubjectQueue(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("clearSessions")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            this.disconnectQosData.clearSessions(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("clientProperty")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0 || this.clientPropertyKey != null)
            this.disconnectQosData.setClientProperty(this.clientPropertyKey, tmp);
         this.clientPropertyKey = null;   
         return;
      }

   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the DisconnectQos as a XML ASCII string
    */
   public String writeObject(DisconnectQosData disconnectQosData, String extraOffset) {
      return toXml(disconnectQosData, extraOffset);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public static final String toXml(DisconnectQosData data, String extraOffset) {
      StringBuffer sb = new StringBuffer(512);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<qos>");
      if (data.deleteSubjectQueueProp().isModified()) {
         if (data.deleteSubjectQueue())
            sb.append(offset).append(" <deleteSubjectQueue/>");
         else
            sb.append(offset).append(" <deleteSubjectQueue>").append(data.deleteSubjectQueue()).append("</deleteSubjectQueue>");
      }
      if (data.clearSessionsProp().isModified()) {
         if (data.clearSessions())
            sb.append(offset).append(" <clearSessions/>");
         else
            sb.append(offset).append(" <clearSessions>").append(data.clearSessions()).append("</clearSessions>");
      }

      HashMap map = data.getClientProperties();
      if (map != null && map.size() > 0) {
         Object[] keys = map.keySet().toArray();
         for (int i=0; i < keys.length; i++) {
            sb.append(offset).append(" <clientProperty name='").append((String)keys[i]).append("'>").append(map.get(keys[i])).append("</clientProperty>");
         }
      }
      
      sb.append(offset).append("</qos>");

      return sb.toString();
   }

   /**
    * A human readable name of this factory
    * @return "DisconnectQosSaxFactory"
    */
   public String getName() {
      return "DisconnectQosSaxFactory";
   }
}
