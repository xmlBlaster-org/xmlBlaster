/*------------------------------------------------------------------------------
Name:      ConnectQosSaxFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Parsing connect QoS
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.storage.QueueProperty;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.address.ServerRef;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.StopParseException;
import org.xmlBlaster.util.SessionName;

import org.xml.sax.Attributes;

import java.util.Vector;
import java.util.ArrayList;
import java.io.Serializable;


/**
 * This class encapsulates the qos of a login() or connect(). 
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>login</b> qos could look like this:<br />
 * <pre>
 *&lt;qos>
 *   &lt;securityService type="htpasswd" version="1.0">
 *     &lt;![CDATA[
 *     &lt;user>joe&lt;/user>
 *     &lt;passwd>secret&lt;/passwd>
 *     ]]>
 *   &lt;/securityService>
 *
 *   &lt;session name='/node/heron/client/joe/-9' timeout='3600000' maxSessions='10' clearSessions='false'
 *               sessionId='4e56890ghdFzj0'/>
 *
 *   &lt;ptp>true&lt;/ptp>  <!-- Allow receiving PtP messages (no SPAM protection) -->
 *
 *   &lt;!-- The client side queue: -->
 *   &lt;queue relating='client' type='CACHE' version='1.0' maxMsg='1000' maxBytes='4000' onOverflow='exception'>
 *      &lt;address type='IOR' sessionId='4e56890ghdFzj0'>
 *         IOR:10000010033200000099000010....
 *      &lt;/address>
 *   &lt;queue>
 *
 *   &lt;!-- The server side callback queue: -->
 *   &lt;queue relating='callback' type='CACHE' version='1.0' maxMsg='1000' maxBytes='4000' onOverflow='deadMessage'>
 *      &lt;callback type='IOR' sessionId='4e56890ghdFzj0'>
 *         IOR:10000010033200000099000010....
 *         &lt;burstMode collectTime='400' />
 *      &lt;/callback>
 *   &lt;queue>
 *&lt;/qos>
 * </pre>
 * NOTE: As a user of the Java client helper classes (client.protocol.XmlBlasterConnection)
 * you don't need to create the <pre>&lt;callback></pre> element.
 * This is generated automatically from the XmlBlasterConnection class when instantiating
 * the callback driver.
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see org.xmlBlaster.test.classtest.ConnectQosTest
 */
public final class ConnectQosSaxFactory extends org.xmlBlaster.util.XmlQoSBase implements I_ConnectQosFactory
{
   private String ME = "ConnectQosSaxFactory";
   private final Global glob;
   private final LogChannel log;

   private ConnectQosData connectQosData;

   // helper flags for SAX parsing
   private boolean inServerRef;
   private boolean inQueue;
   private boolean inSecurityService;
   private boolean inSession;
   private boolean inCallback;
   private boolean inAddress;
   
   /** Helper for SAX parsing */
   private ServerRef tmpServerRef;
   private CbQueueProperty tmpCbProp;
   private CallbackAddress tmpCbAddr;
   private QueueProperty tmpProp;
   private Address tmpAddr;
   protected String tmpSecurityPluginType;
   protected String tmpSecurityPluginVersion;

   /**
    */
   public ConnectQosSaxFactory(Global glob) {
      super(glob);
      this.glob = glob;
      this.log = glob.getLog("core");
   }
   
   /**
    * Parses the given xml Qos and returns a ConnectQosData holding the data. 
    * Parsing of connect() and connect-return QoS is supported here.
    * @param the XML based ASCII string
    */
   public synchronized ConnectQosData readObject(String xmlQos) throws XmlBlasterException {
      if (xmlQos == null) {
         xmlQos = "<qos/>";
      }

      this.connectQosData = new ConnectQosData(glob, this, xmlQos, null);

      if (!isEmpty(xmlQos)) // if possible avoid expensive SAX parsing
         init(xmlQos);      // use SAX parser to parse it (is slow)

      return this.connectQosData;
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

      //if (log.TRACE) log.trace(ME, "Entering startElement for uri=" + uri + " localName=" + localName + " name=" + name);

      if (name.equalsIgnoreCase("serverRef")) {
         inServerRef = true;
         String tmp = character.toString().trim(); // The address (if before inner tags)
         String type = null;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
                if( attrs.getQName(i).equalsIgnoreCase("type") ) {
                  type = attrs.getValue(i).trim();
                  break;
                }
            }
         }
         if (type == null) {
            log.error(ME, "Missing 'serverRef' attribute 'type' in login-qos");
            type = "IOR";
         }
         tmpServerRef = new ServerRef(type);
         if (tmp.length() > 0) {
            tmpServerRef.setAddress(tmp);
            character.setLength(0);
         }
         return;
      }

      if (inCallback) {
         tmpCbAddr.startElement(uri, localName, name, character, attrs);
         return;
      }

      if (name.equalsIgnoreCase("callback")) {
         inCallback = true;
         if (!inQueue) {
            tmpCbProp = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, null); // Use default queue properties for this callback address
            this.connectQosData.setSessionCbQueueProperty(tmpCbProp);
         }
         tmpCbAddr = new CallbackAddress(glob);
         tmpCbAddr.startElement(uri, localName, name, character, attrs);
         tmpCbProp.setCallbackAddress(tmpCbAddr);
         return;
      }

      if (name.equalsIgnoreCase("address")) {
         inAddress = true;
         if (!inQueue) {
            tmpProp = new QueueProperty(glob, null); // Use default queue properties for this connection address
            this.connectQosData.addClientQueueProperty(tmpProp);
         }
         tmpAddr = new Address(glob);
         tmpAddr.startElement(uri, localName, name, character, attrs);
         tmpProp.setAddress(tmpAddr);
         return;
      }

      if (name.equalsIgnoreCase("queue")) {
         inQueue = true;
         if (inCallback) {
            log.error(ME, "<queue> tag is not allowed inside <callback> tag, element ignored.");
            character.setLength(0);
            return;
         }
         if (inAddress) {
            log.error(ME, "<queue> tag is not allowed inside <address> tag, element ignored.");
            character.setLength(0);
            return;
         }
         String related = attrs.getValue("relating");
         if (Constants.RELATING_CLIENT.equalsIgnoreCase(related)) {
            tmpProp = new QueueProperty(glob, null);
            tmpProp.startElement(uri, localName, name, attrs);
            this.connectQosData.addClientQueueProperty(tmpProp);
         }
         else if (Constants.RELATING_CALLBACK.equalsIgnoreCase(related)) {
            tmpCbProp = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, null);
            tmpCbProp.startElement(uri, localName, name, attrs);
            this.connectQosData.setSessionCbQueueProperty(tmpCbProp);
         }
         else if (Constants.RELATING_SUBJECT.equalsIgnoreCase(related)) {
            tmpCbProp = new CbQueueProperty(glob, Constants.RELATING_SUBJECT, null);
            tmpCbProp.startElement(uri, localName, name, attrs);
            this.connectQosData.setSubjectQueueProperty(tmpCbProp);
         }
         else {
            log.warn(ME, "The given relating='" + related + "' is not supported, configuration for '" + related + "' is ignored");
         }
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("securityService")) {
         inSecurityService = true;
         boolean existsTypeAttr = false;
         boolean existsVersionAttr = false;
         if (attrs != null) {
            int len = attrs.getLength();
            int ii=0;
            for (ii = 0; ii < len; ii++) {
               if (attrs.getQName(ii).equalsIgnoreCase("type")) {
                  existsTypeAttr = true;
                  tmpSecurityPluginType = attrs.getValue(ii).trim();
               }
               else if (attrs.getQName(ii).equalsIgnoreCase("version")) {
                  existsVersionAttr = true;
                  tmpSecurityPluginVersion = attrs.getValue(ii).trim();
               }
            }
         }
         if (!existsTypeAttr) log.error(ME, "Missing 'type' attribute in login-qos <securityService>");
         if (!existsVersionAttr) log.error(ME, "Missing 'version' attribute in login-qos <securityService>");
         character.setLength(0);
         // Fall through and collect xml, will be parsed later by appropriate security plugin
      }

      if (name.equalsIgnoreCase("session")) {
         inSession = true;
         if (attrs != null) {
            int len = attrs.getLength();
            int ii=0;
            SessionQos sessionQos = this.connectQosData.getSessionQos();
            for (ii = 0; ii < len; ii++) {
               if (attrs.getQName(ii).equalsIgnoreCase("name")) {
                  sessionQos.setSessionName(new SessionName(glob, attrs.getValue(ii).trim()));
               }
               else if (attrs.getQName(ii).equalsIgnoreCase("timeout"))
                  sessionQos.setSessionTimeout((new Long(attrs.getValue(ii).trim())).longValue());
               else if (attrs.getQName(ii).equalsIgnoreCase("maxSessions"))
                  sessionQos.setMaxSessions((new Integer(attrs.getValue(ii).trim())).intValue());
               else if (attrs.getQName(ii).equalsIgnoreCase("clearSessions"))
                  sessionQos.clearSessions((new Boolean(attrs.getValue(ii).trim())).booleanValue());
               else if (attrs.getQName(ii).equalsIgnoreCase("sessionId"))
                  sessionQos.setSessionId(attrs.getValue(ii));
               else
                  log.warn(ME, "Ignoring unknown attribute '" + attrs.getQName(ii) + "' of <session> element");
            }
         }
         character.setLength(0);

         return;
      }

      if (name.equalsIgnoreCase("ptp")) {
         this.connectQosData.setPtpAllowed(true);
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("isClusterNode")) {
         this.connectQosData.setIsClusterNode(true);
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("duplicateUpdates")) {
         this.connectQosData.setDuplicateUpdates(true);
         character.setLength(0);
         return;
      }

      if (inSecurityService) {
         //Collect everything in character buffer
         character.append("<").append(name);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               character.append(" ").append(attrs.getQName(i)).append("=\"").append(attrs.getValue(i)).append("\"");
            }
         }
         character.append(">");
         if (name.equalsIgnoreCase("securityService"))
            character.append("<![CDATA[");
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

      //if (log.TRACE) log.trace(ME, "Entering endElement for " + name);

      if (name.equalsIgnoreCase("serverRef")) {
         inServerRef = false;
         String tmp = character.toString().trim(); // The address (if after inner tags)
         if (tmpServerRef != null) {
            if (tmp.length() > 0) tmpServerRef.setAddress(tmp);
            this.connectQosData.addServerRef(tmpServerRef);
         }
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("queue")) {
         inQueue = false;
         character.setLength(0);
         return;
      }

      if (inCallback) {
         if (name.equalsIgnoreCase("callback")) inCallback = false;
         tmpCbAddr.endElement(uri, localName, name, character);
         return;
      }

      if (inAddress) {
         if (name.equalsIgnoreCase("address")) inAddress = false;
         tmpAddr.endElement(uri, localName, name, character);
         return;
      }

      if (name.equalsIgnoreCase("ptp")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            this.connectQosData.setPtpAllowed(new Boolean(tmp).booleanValue());
         return;
      }

      if (name.equalsIgnoreCase("isClusterNode")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            this.connectQosData.setIsClusterNode(new Boolean(tmp).booleanValue());
         return;
      }

      if (name.equalsIgnoreCase("duplicateUpdates")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            this.connectQosData.setDuplicateUpdates(new Boolean(tmp).booleanValue());
         return;
      }

      if (name.equalsIgnoreCase("securityService")) {
        inSecurityService = false;
        character.append("]]>\n");
        character.append("</").append(name).append(">");
        String tmp = character.toString().trim();
        // delegate the collected tags to our security plugin
        try {
           I_SecurityQos securityQos = this.connectQosData.getSecurityPlugin(tmpSecurityPluginType, tmpSecurityPluginVersion).getSecurityQos();
           /*
           "<securityService type=\""+tmpSecurityPluginType+"\" version=\""+tmpSecurityPluginVersion+"\">\n"+
               <user>user</user>
               <passwd>passwd</passwd>
            "</securityService>";
           */
           securityQos.parse(tmp);
           this.connectQosData.setSecurityQos(securityQos);
         }
         catch(XmlBlasterException e) {
            log.warn(ME, "Can't parse security string - " + e.toString() + "\n Check:\n" + tmp);
            throw new StopParseException(); // Enough error handling??
         }
      }

      if (name.equalsIgnoreCase("session")) {
         inSession = false;
      }

      if (inSecurityService) {
         character.append("</"+name+">");
      }
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the ConnectQos as a XML ASCII string
    */
   public String writeObject(ConnectQosData connectQosData, String extraOffset) {
      return toXml(connectQosData, extraOffset);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public static final String toXml(ConnectQosData data, String extraOffset) {
      StringBuffer sb = new StringBuffer(2000);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<qos>");

      if (data.getSecurityQos() != null)  // <securityService ...
         sb.append(data.getSecurityQos().toXml(extraOffset+Constants.INDENT)); // includes the qos of the ClientSecurityHelper

      if (data.isPtpAllowedProp().isModified()) {
         if (data.isPtpAllowed())
            sb.append(offset).append(" <ptp/>");
         else
            sb.append(offset).append(" <ptp>false</ptp>");
      }
   
      if (data.isClusterNodeProp().isModified()) {
         if (data.isClusterNode())
            sb.append(offset).append(" <isClusterNode/>");
         else
            sb.append(offset).append(" <isClusterNode>false</isClusterNode>");
      }

      if (data.duplicateUpdatesProp().isModified()) {
         if (data.duplicateUpdates())
            sb.append(offset).append(" <duplicateUpdates/>");
         else
            sb.append(offset).append(" <duplicateUpdates>false</duplicateUpdates>");
      }

      sb.append(data.getSessionQos().toXml(extraOffset+Constants.INDENT));

      {
         QueueProperty[] arr = data.getQueuePropertyArr();
         for (int ii=0; arr!=null && ii<arr.length; ii++) {
            sb.append(arr[ii].toXml(extraOffset+Constants.INDENT));
         }
      }

      if (data.hasSubjectQueueProperty()) {
         sb.append(data.getSubjectQueueProperty().toXml(extraOffset+Constants.INDENT));
      }
      
      if (data.hasSessionCbQueueProperty()) {
         sb.append(data.getSessionCbQueueProperty().toXml(extraOffset+Constants.INDENT));
      }

      {
         ServerRef[] arr = data.getServerRefs();
         for (int ii=0; arr!=null && ii<arr.length; ii++) {
            sb.append(arr[ii].toXml(extraOffset+Constants.INDENT));
         }
      }

      sb.append(offset).append("</qos>");

      return sb.toString();
   }

   /**
    * A human readable name of this factory
    * @return "ConnectQosSaxFactory"
    */
   public String getName() {
      return "ConnectQosSaxFactory";
   }
}
