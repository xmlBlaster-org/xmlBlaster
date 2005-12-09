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
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.storage.ClientQueueProperty;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.address.ServerRef;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.util.SessionName;

import org.xml.sax.Attributes;

/**
 * This class encapsulates the qos of a login() or connect(). 
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>connect QoS</b> could look like this:<br />
 * <pre>
 *&lt;qos>
 *   &lt;securityService type="htpasswd" version="1.0">
 *     &lt;![CDATA[
 *     &lt;user>joe&lt;/user>
 *     &lt;passwd>secret&lt;/passwd>
 *     ]]>
 *   &lt;/securityService>
 *
 *   &lt;session name='/node/heron/client/joe' timeout='3600000'
                 maxSessions='10' clearSessions='false'
                 reconnectSameClientOnly='false'/>
 *
 *   &lt;ptp>true&lt;/ptp>  <!-- Allow receiving PtP messages (no SPAM protection) -->
 *
 *   &lt;duplicateUpdates>true&lt;/duplicateUpdates>
 *
 *   &lt;!-- The client side queue (is ignored on server side): -->
 *   &lt;queue relating='client' type='CACHE' version='1.0' maxEntries='1000' maxBytes='4000' onOverflow='exception'>
 *      &lt;address type='IOR'>
 *         IOR:10000010033200000099000010....
 *      &lt;/address>
 *   &lt;queue>
 *
 *   &lt;!-- Configures the server side callback queue: -->
 *   &lt;queue relating='callback' type='CACHE' version='1.0' maxEntries='1000' maxBytes='4000' onOverflow='deadMessage'>
 *      &lt;callback type='IOR' sessionId='4e56890ghdFzj0'>
 *         IOR:10000010033200000099000010....
 *         &lt;burstMode collectTime='400' />
 *      &lt;/callback>
 *   &lt;/queue>
 *&lt;/qos>
 * </pre>
 * NOTE: As a user of the Java client helper classes (client.I_XmlBlasterAccess)
 * you don't need to create the <pre>&lt;callback></pre> element.
 * This is generated automatically from I_XmlBlasterAccess when instantiating
 * the callback driver.
 *
 * <p />
 *
 * A typical <b>connect return QoS</b> could look like this (this is the acknowledge returned by
 * the server to the client on successful connect):<br />
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
 *               clearSessions='false' sessionId='sessionId:192.168.1.4-null-1042823803521-2074317763-3'/>
 *
 *   &lt;reconnected>false&lt;/reconnected>  &lt;!-- Has the client reconnected to an existing session? -->
 *
 *   &lt;!-- The server side callback queue: -->
 *   &lt;queue relating='callback' type='CACHE' version='1.0' maxEntries='1000' maxBytes='4000' onOverflow='deadMessage'>
 *      &lt;callback type='XMLRPC' bootstrapHostname='192.168.1.4' sessionId='4e56890ghdFzj0'>
 *         http://192.168.1.4:8081/
 *         &lt;burstMode collectTime='400' />
 *      &lt;/callback>
 *   &lt;queue>
 *&lt;/qos>
 * </pre>
 * @see org.xmlBlaster.test.classtest.ConnectQosTest
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.connect.html">connect interface</a>
 */
public final class ConnectQosSaxFactory extends org.xmlBlaster.util.XmlQoSBase implements I_ConnectQosFactory
{
   private String ME = "ConnectQosSaxFactory";
   private final Global glob;
   private final LogChannel log;

   private ConnectQosData connectQosData;

   // helper flags for SAX parsing
   //private boolean inServerRef;
   private boolean inQueue;
   private boolean inSecurityService;
   //private boolean inSession;
   private boolean inCallback;
   private boolean inAddress;
   
   /** Helper for SAX parsing */
   private ServerRef tmpServerRef;
   private CbQueueProperty tmpCbProp;
   private CallbackAddress tmpCbAddr;
   private ClientQueueProperty tmpProp;
   private Address tmpAddr;
   protected String tmpSecurityPluginType;
   protected String tmpSecurityPluginVersion;
   //private boolean inIsPersistent = false;

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
    * This call is thread safe if not intermixed with <tt>getConnectQosData()</tt> calls.
    * @param the XML based ASCII string
    */
   public synchronized ConnectQosData readObject(String xmlQos) throws XmlBlasterException {
      if (xmlQos == null) {
         xmlQos = "<qos/>";
      }

      this.inQueue = false;
      this.inSecurityService = false;
      //this.inSession = false;
      this.inCallback = false;
      this.inAddress = false;
      this.tmpServerRef = null;
      this.tmpCbProp = null;
      this.tmpCbAddr = null;
      this.tmpProp = null;
      this.tmpAddr = null;
      this.tmpSecurityPluginType = null;
      this.tmpSecurityPluginVersion = null;
      //this.inIsPersistent = false;
      
      this.connectQosData = new ConnectQosData(glob, this, xmlQos, null);

      if (!isEmpty(xmlQos)) // if possible avoid expensive SAX parsing
         init(xmlQos);      // use SAX parser to parse it (is slow)

      return this.connectQosData;
   }

   /**
    * This is NOT thread safe so you need a new factory for each parse. 
    * Use this variant if you have a bigger xml markup and want to delegate
    * startElement(), endElement() calls to this.
    * PRECONDITION: Call setConnectQosData() first!
    */
   public ConnectQosData getConnectQosData() {
      return this.connectQosData;
   }
   /** If a delegate call startElement() directly */
   public void setConnectQosData(ConnectQosData data) {
      this.connectQosData = data;
   }

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public void startElement(String uri, String localName, String name, Attributes attrs) {
      startElement(uri, localName, name, this.character, attrs);
   }
   /**
    * Start element from SAX parsing, call as delegate delivers the character
    */
   public void startElement(String uri, String localName, String name, StringBuffer character, Attributes attrs) {
      if (super.startElementBase(uri, localName, name, attrs) == true)
         return;

      //if (log.TRACE) log.trace(ME, "Entering startElement for uri=" + uri + " localName=" + localName + " name=" + name);

      if (name.equalsIgnoreCase("serverRef")) {
         //this.inServerRef = true;
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
            type = AddressBase.DEFAULT_type;// Since 1.0.7 "SOCKET", before "IOR";
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
         boolean accepted=true;
         if (!inQueue) {
            tmpProp = new ClientQueueProperty(glob, null); // Use default queue properties for this connection address
            accepted = this.connectQosData.addClientQueueProperty(tmpProp);
         }
         tmpAddr = new Address(glob);
         tmpAddr.startElement(uri, localName, name, character, attrs);
         if (accepted) {
            tmpProp.setAddress(tmpAddr);
         }
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
            tmpProp = new ClientQueueProperty(glob, null);
            tmpProp.startElement(uri, localName, name, attrs);
            /*boolean accepted = */this.connectQosData.addClientQueueProperty(tmpProp);
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
         //this.inSession = true;
         if (attrs != null) {
            int len = attrs.getLength();
            int ii=0;
            SessionQos sessionQos = this.connectQosData.getSessionQos();
            for (ii = 0; ii < len; ii++) {
               if (attrs.getQName(ii).equalsIgnoreCase("name")) {
                  if (glob.isServerSide()) { // Force the server node ID on connect
                     sessionQos.setSessionName(new SessionName(glob, glob.getNodeId(), attrs.getValue(ii).trim()));
                  }
                  else {
                     sessionQos.setSessionName(new SessionName(glob, attrs.getValue(ii).trim()));
                  }
               }
               else if (attrs.getQName(ii).equalsIgnoreCase("timeout"))
                  sessionQos.setSessionTimeout((new Long(attrs.getValue(ii).trim())).longValue());
               else if (attrs.getQName(ii).equalsIgnoreCase("maxSessions"))
                  sessionQos.setMaxSessions((new Integer(attrs.getValue(ii).trim())).intValue());
               else if (attrs.getQName(ii).equalsIgnoreCase("clearSessions"))
                  sessionQos.clearSessions((new Boolean(attrs.getValue(ii).trim())).booleanValue());
               else if (attrs.getQName(ii).equalsIgnoreCase("reconnectSameClientOnly"))
                  sessionQos.setReconnectSameClientOnly((new Boolean(attrs.getValue(ii).trim())).booleanValue());
               else if (attrs.getQName(ii).equalsIgnoreCase("sessionId"))
                  sessionQos.setSecretSessionId(attrs.getValue(ii));
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

      if (name.equalsIgnoreCase("clusterNode")) {
         this.connectQosData.setClusterNode(true);
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("refreshSession")) {
         this.connectQosData.setRefreshSession(true);
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("duplicateUpdates")) {
         this.connectQosData.setDuplicateUpdates(true);
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("reconnected")) {
         this.connectQosData.setReconnected(true);
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("persistent")) {
         if (!inQos)
            return;
         //this.inIsPersistent = true;
         character.setLength(0);
         connectQosData.setPersistent(true);
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
   public void endElement(String uri, String localName, String name) throws org.xml.sax.SAXException {
      endElement(uri, localName, name, this.character);
   }

   public void endElement(String uri, String localName, String name, StringBuffer character) throws org.xml.sax.SAXException {
      if (super.endElementBase(uri, localName, name) == true) {
         // "clientProperty" and "attribute" must match super.clientPropertyTagNames.contains(name))
         if (name.equalsIgnoreCase(ClientProperty.CLIENTPROPERTY_TAG)) {
            this.connectQosData.addClientProperty(this.clientProperty);
         }
         if (name.equalsIgnoreCase(ClientProperty.ATTRIBUTE_TAG)) {
            if (this.inAddress)
               this.tmpAddr.addClientProperty(this.clientProperty);
            else if (this.inCallback)
               this.tmpCbAddr.addClientProperty(this.clientProperty);
         }
         return;
      }

      //if (log.TRACE) log.trace(ME, "Entering endElement for " + name);

      if (name.equalsIgnoreCase("serverRef")) {
         //this.inServerRef = false;
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
         character.setLength(0);
         return;
      }

      if (inAddress) {
         if (name.equalsIgnoreCase("address")) inAddress = false;
         tmpAddr.endElement(uri, localName, name, character);
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("ptp")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            this.connectQosData.setPtpAllowed(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("clusterNode")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            this.connectQosData.setClusterNode(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("refreshSession")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            this.connectQosData.setRefreshSession(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("duplicateUpdates")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            this.connectQosData.setDuplicateUpdates(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("reconnected")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            this.connectQosData.setReconnected(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("instanceId")) {
         String tmp = character.toString().trim();
         this.connectQosData.setInstanceId(tmp);
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("securityService")) {
        inSecurityService = false;
        character.append("]]>\n");
        character.append("</").append(name).append(">");
        String tmp = character.toString().trim();
        character.setLength(0);
        // delegate the collected tags to our security plugin
        try {
           I_SecurityQos securityQos = this.connectQosData.getClientPlugin(tmpSecurityPluginType, tmpSecurityPluginVersion).createSecurityQos();
           /*
           "<securityService type=\""+tmpSecurityPluginType+"\" version=\""+tmpSecurityPluginVersion+"\">\n"+
               <user>user</user>
               <passwd>passwd</passwd>
            "</securityService>";
           */
           securityQos.parse(tmp);
           this.connectQosData.setSecurityQos(securityQos);
           return;
         }
         catch(XmlBlasterException e) {
            log.warn(ME, "Can't parse security string - " + e.toString() + "\n Check:\n" + tmp);
            throw new org.xml.sax.SAXException("Can't parse security string - " + e.toString() + "\n Check:\n" + tmp, e);
            //throw new StopParseException();
         }
      }

      if(name.equalsIgnoreCase("persistent")) {
         //this.inIsPersistent = false;
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            connectQosData.setPersistent(new Boolean(tmp).booleanValue());
         // if (log.TRACE) log.trace(ME, "Found persistent = " + msgQosData.isPersistent());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("session")) {
         //this.inSession = false;
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

   public String writeObject(ConnectQosData connectQosData, String extraOffset, int flag) {
      return toXml(connectQosData, extraOffset, flag);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public static final String toXml(ConnectQosData data, String extraOffset) {
      return toXml("qos", data, extraOffset, Constants.TOXML_FLAG_DEFAULT);
   }

   /**
    * @param rootTag Usually "qos" to form "&lt;qos>", but could be "connectQos". 
    */
   public static final String toXml(ConnectQosData data, String extraOffset, int flag) {
      return toXml("qos", data, extraOffset, flag);
   }

   /**
    * @param flag For example Constants.TOXML_FLAG_NOSECURITY
    */
   public static final String toXml(String rootTag, ConnectQosData data, String extraOffset, int flag) {
      StringBuffer sb = new StringBuffer(2000);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<").append(rootTag).append(">");

      if (data.getSecurityQos() != null) { // <securityService ...
         if ((flag & Constants.TOXML_FLAG_NOSECURITY) != Constants.TOXML_FLAG_NOSECURITY) {
            sb.append(data.getSecurityQos().toXml(extraOffset+Constants.INDENT)); // includes the qos of the ClientSecurityHelper
         }
      }

      if (data.isPtpAllowedProp().isModified()) {
         if (data.isPtpAllowed())
            sb.append(offset).append(" <ptp/>");
         else
            sb.append(offset).append(" <ptp>false</ptp>");
      }
   
      if (data.getClusterNodeProp().isModified()) {
         if (data.isClusterNode())
            sb.append(offset).append(" <clusterNode/>");
         else
            sb.append(offset).append(" <clusterNode>false</clusterNode>");
      }

      if (data.getRefreshSessionProp().isModified()) {
         if (data.getRefreshSession())
            sb.append(offset).append(" <refreshSession/>");
         else
            sb.append(offset).append(" <refreshSession>false</refreshSession>");
      }

      if (data.duplicateUpdatesProp().isModified()) {
         if (data.duplicateUpdates())
            sb.append(offset).append(" <duplicateUpdates/>");
         else
            sb.append(offset).append(" <duplicateUpdates>false</duplicateUpdates>");
      }

      if (data.getReconnectedProp().isModified()) {
         if (data.isReconnected())
            sb.append(offset).append(" <reconnected/>");
         else
            sb.append(offset).append(" <reconnected>false</reconnected>");
      }

      sb.append(offset).append(" <instanceId>").append(data.getInstanceId()).append("</instanceId>");

      if (data.getPersistentProp().isModified()) {
         if (data.isPersistent())
            sb.append(offset).append(" <persistent/>");
         else
            sb.append(offset).append(" <persistent>false</persistent>");
      }

      sb.append(data.getSessionQos().toXml(extraOffset+Constants.INDENT));

      {
         ClientQueueProperty[] arr = data.getClientQueuePropertyArr();
         for (int ii=0; arr!=null && ii<arr.length; ii++) {
            sb.append(arr[ii].toXml(extraOffset+Constants.INDENT));
         }
      }

      /*
       NOTE: We can't optimize it away, as we need to check the Global env and args:
       */
      //if (data.hasSubjectQueueProperty()) {
         sb.append(data.getSubjectQueueProperty().toXml(extraOffset+Constants.INDENT));
      //}
      
      //if (data.hasSessionCbQueueProperty()) {
         sb.append(data.getSessionCbQueueProperty().toXml(extraOffset+Constants.INDENT));
      //}

      {
         ServerRef[] arr = data.getServerRefs();
         for (int ii=0; arr!=null && ii<arr.length; ii++) {
            sb.append(arr[ii].toXml(extraOffset+Constants.INDENT));
         }
      }

      sb.append(data.writePropertiesXml(extraOffset+Constants.INDENT));
      sb.append(offset).append("</").append(rootTag).append(">");

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
