/*------------------------------------------------------------------------------
Name:      ConnectQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: ConnectQos.java,v 1.3 2001/09/05 13:11:18 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.ServerRef;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.client.QosWrapper;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xml.sax.Attributes;
import java.util.Vector;
import java.io.Serializable;


/**
 * This class encapsulates the qos of a login() or connect(). 
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>login</b> qos could look like this:<br />
 * <pre>
 *     &lt;qos>
 *        &lt;securityService type="simple" version="1.0">
 *          &lt;![CDATA[
 *          &lt;user>michele&lt;/user>
 *          &lt;passwd>secret&lt;/passwd>
 *          ]]>
 *        &lt;/securityService>
 *        &lt;session timeout='3600000' maxSessions='20'>
 *        &lt;/session>
 *        &lt;ptp>true&lt;/ptp>
 *        &lt;callback type='IOR'>
 *           IOR:10000010033200000099000010....
 *           &lt;burstMode collectTime='400' />
 *        &lt;/callback>
 *     &lt;/qos>
 * </pre>
 * NOTE: As a user of the Java client helper classes (client.protocol.XmlBlasterConnection)
 * you don't need to create the <pre>&lt;callback></pre> element.
 * This is generated automatically from the XmlBlasterConnection class when instantiating
 * the callback driver.
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see org.xmlBlaster.util.ConnectQos
 */
public class ConnectQos extends org.xmlBlaster.util.XmlQoSBase implements Serializable
{
   private String ME = "ConnectQos";

   /** PtP messages wanted?
    * <p />
    * <pre>
    * &lt;ptp>false&lt;ptp/> <!-- Don't send me any PtP messages (prevents spamming) -->
    * </pre>
    */
   protected boolean ptpAllowed = true;

   /** Default session span of life is one hour */
   protected long sessionTimeout = 3600L * 1000L; // One hour
   /** Maximum of six parallel logins for the same client */
   protected int maxSessions = 6;
   /** Passing own sessionId is not yet supported */
   protected String sessionId = null;

   protected transient PluginLoader pMgr;
   protected I_ClientPlugin plugin;
   protected I_SecurityQos securityQos;
   protected transient String tmpSecurityPluginType = null;
   protected transient String tmpSecurityPluginVersion = null;

   /**
    * The server reference, e.g. the CORBA IOR string or the XML-RPC url
    * This is returned from XmlBlaster connect() and not used for login
    */
   private transient boolean inServerRef = false;
   private transient ServerRef tmpServerRef = null;
   protected transient ServerRef[] serverRefArr = null;
   protected Vector serverRefVec = new Vector();  // <serverRef type="IOR">IOR:000122200...</serverRef>


   // helper flags for SAX parsing
   private transient boolean inBurstMode = false;
   private transient boolean inCompress = false;
   private transient boolean inPtpAllowed = false;
   private transient boolean inSecurityService = false;
   private transient boolean inSession = false;
   private transient boolean inSessionId = false;
   private transient boolean inCallback = false;
   
   private transient CallbackAddress tmpAddr = null;
   /** <callback type="IOR>IOR:000122200..."</callback> */
   protected transient CallbackAddress[] addressArr = null;
   /** Contains CallbackAddress objects. 
       <callback type="IOR>IOR:000122200..."</callback> */
   protected Vector addressVec = new Vector();

   /**
    * Default constructor for clients without asynchronous callbacks
    * and default security plugin (as specified in xmlBlaster.properties)
    */
   public ConnectQos()
   {
   }

   
   /**
    * Parses the given ASCII login QoS. 
    */
   public ConnectQos(String xmlQoS_literal) throws XmlBlasterException
   {
      if (Log.DUMP) Log.dump(ME, "Creating ConnectQos(" + xmlQoS_literal + ")");
      addressArr = null;
      init(xmlQoS_literal);
      if (Log.DUMP) Log.dump(ME, "Parsed ConnectQos to\n" + toXml());
   }


   /**
    * Constructor for simple access with login name and password. 
    * @param mechanism may be null to use the default security plugin
    *                  as specified in xmlBlaster.properties
    * @param version may be null to use the default
    * @param loginName The unique userId
    * @param password  Your credentials, depends on the plugin type
    */
   public ConnectQos(String mechanism, String version, String loginName, String password) throws XmlBlasterException
   {
      securityQos = getPlugin(mechanism,version).getSecurityQos();
      securityQos.setUserId(loginName);
      securityQos.setCredential(password);
   }

   /**
    * For clients who whish to use the given security plugin. 
    * @param String The type of the plugin, e.g. "a2Blaster"
    * @param String The version of the plugin, e.g. "1.0"
    */
   public ConnectQos(String mechanism, String version) throws XmlBlasterException
   {
      getPlugin(mechanism, version);
   }


   /**
    * Allows to specify how you want to identify yourself. 
    * <p />
    * Usage to login to xmlBlaster:
    * <pre>
    *    import org.xmlBlaster.authentication.plugins.simple.SecurityQos;
    *    ...
    *    ConnectQos qos = new ConnectQos(new SecurityQos("joe", "secret"));
    *    xmlBlasterConnection.connect(qos);
    * </pre>
    */
   public ConnectQos(I_SecurityQos securityQos)
   {
      this.securityQos = securityQos;
   }


   /**
    * Accessing the Callback addresses of the client
    * this may be a CORBA-IOR or email or URL ...
    * <p />
    * @return An array of CallbackAddress objects, containing the address and the protocol type
    *         If no callback available, return an array of 0 length
    */
   public CallbackAddress[] getCallbackAddresses()
   {
      if (addressArr == null) {
         addressArr = new CallbackAddress[addressVec.size()];
         for (int ii=0; ii<addressArr.length; ii++)
            addressArr[ii] = (CallbackAddress)addressVec.elementAt(ii);
      }
      return addressArr;
   }


   /**
    * Allows to set or overwrite the parsed security plugin. 
    * <p />
    * &lt;securityService type='simple' version='1.0'>...&lt;/securityService>
    */
   public void setSecurityPluginData(String mechanism, String version, String loginName, String password) throws XmlBlasterException
   {
      org.xmlBlaster.client.PluginLoader loader = org.xmlBlaster.client.PluginLoader.getInstance();
      I_ClientPlugin plugin = loader.getClientPlugin(mechanism, version);
      securityQos = plugin.getSecurityQos();
      securityQos.setUserId(loginName);
      securityQos.setCredential(password);
   }


   /**
    * @param mechanism If null, the current plugin is used
    */
   protected I_ClientPlugin getPlugin(String mechanism, String version) throws XmlBlasterException
   {
      // Performance TODO: for 'null' mechanism every time a new plugin is incarnated
      if (plugin==null || !plugin.getType().equals(mechanism) || !plugin.getVersion().equals(version)) {
         if (pMgr==null) pMgr=PluginLoader.getInstance();
         try {
            if (mechanism != null)
               plugin=pMgr.getClientPlugin(mechanism, version);
            else
               plugin=pMgr.getCurrentClientPlugin();
         }
         catch (XmlBlasterException e) {
            Log.error(ME+".ConnectQos", "Security plugin initialization failed. Reason: "+e.toString());
            Log.error(ME+".ConnectQos", "No plugin. Trying to continue without the plugin.");
         }
      }

      return plugin;
   }


   /**
    * Return the SecurityPlugin specific information.
    * <p/>
    * @return String depending on plugin, e.g.
    * <pre>
    *  &lt;securityService type=\"gui\" version=\"3.0\">
    *     &lt;![CDATA[
    *        &lt;user>aUser&lt;/user>
    *        &lt;passwd>theUsersPwd&lt;/passwd>
    *     ]]>
    *  &lt;/securityService>
    * </pre>
    */
   public String getSecurityData() {
      return securityQos.toXml("   ");
   }


   /**
    * Default constructor for transient PtP messages.
    * <p />
    * To make the message persistent, use the
    * @param callback The object containing the callback address.<br />
    *        To add more callbacks, us the addCallbackAddress() method.
    */
   public ConnectQos(CallbackAddress callback)
   {
      addCallbackAddress(callback);
   }


   /**
    * Allows to specify how you want to identify yourself. 
    * <p />
    * Usage to login to xmlBlaster:
    * <pre>
    *    import org.xmlBlaster.authentication.plugins.simple.SecurityQos;
    *    ...
    *    ConnectQos qos = new ConnectQos();
    *    qos.setCredential(new SecurityQos("joe", "secret"));
    *    xmlBlasterConnection.connect(qos);
    * </pre>
    */
   public void setSecurityQos(I_SecurityQos securityQos)
   {
      this.securityQos = securityQos;
   }


   /**
    * Allows to set session specific informations. 
    *
    * @param timeout The login session will be destroyed after given milliseconds
    * @param maxSessions The client wishes to establish this maximum of sessions in parallel
    */
   public void setSessionData(long timeout, int maxSessions)
   {
      this.sessionTimeout = timeout;
      this.maxSessions = maxSessions;
   }

   public void setSessionId(String id) {
      if(id==null || id.equals("")) id = null;
      sessionId = id;
   }

   public String getSessionId() {
      return sessionId;
   }

   /**
    * Accessing the ServerRef addresses of the xmlBlaster server
    * this may be a CORBA-IOR or email or URL ...
    * <p />
    * Only for results of connect() calls (used by clients)
    * @return An array of ServerRef objects, containing the address and the protocol type
    *         If no serverRef available, return an array of 0 length
    */
   public ServerRef[] getServerRefs()
   {
      if (serverRefArr == null) {
         serverRefArr = new ServerRef[serverRefVec.size()];
         for (int ii=0; ii<serverRefArr.length; ii++)
            serverRefArr[ii] = (ServerRef)serverRefVec.elementAt(ii);
      }
      return serverRefArr;
   }


   /**
    * Accessing the ServerRef address of the xmlBlaster server
    * this may be a CORBA-IOR or email or URL ...
    * <p />
    * Only for results of connect() calls (used by clients)
    * @return The first ServerRef object in the list, containing the address and the protocol type
    *         If no serverRef available we return null
    */
   public ServerRef getServerRef()
   {
      if (serverRefArr == null) {
         serverRefArr = new ServerRef[serverRefVec.size()];
         for (int ii=0; ii<serverRefArr.length; ii++)
            serverRefArr[ii] = (ServerRef)serverRefVec.elementAt(ii);
      }
      if (serverRefArr.length > 0)
         return serverRefArr[0];

      return null;
   }


   /**
    * Adds a server reference
    */
   public void setServerRef(ServerRef addr)
   {
      serverRefVec.addElement(addr);
      serverRefArr = null; // reset to be recalculated on demand
   }

   /**
    * @param Set if we accept point to point messages
    */
   public void setPtpAllowed(boolean ptpAllowed)
   {
      this.ptpAllowed = ptpAllowed;
   }


   /**
    * Allow to receive Point to Point messages (default). 
    * Every callback may suppress PtP as well.
    */
   public void allowPtP()
   {
      setPtpAllowed(true);
   }


   /**
    * I don't want to receive any PtP messages.
    */
   public void disallowPtP()
   {
      setPtpAllowed(false);
   }


   /**
    * Add a callback address where to send the message.
    * <p />
    * Note you can invoke this multiple times to allow multiple callbacks.
    * @param callback  An object containing the protocol (e.g. EMAIL) and the address (e.g. hugo@welfare.org)
    */
   public void addCallbackAddress(CallbackAddress callback)
   {
      if (addressVec == null)
         addressVec = new Vector();
      addressVec.addElement(callback);
      addressArr = null; // reset to be recalculated on demand
   }

   public I_SecurityQos getSecurityQos() throws XmlBlasterException
   {
      return this.securityQos;
   }

   /**
    * Return the type of the referenced SecurityPlugin.
    * <p/>
    * @return String
    */
   public String getSecurityPluginType() throws XmlBlasterException
   {
      if (securityQos != null)
         return securityQos.getPluginType();
      return null;
   }

   /**
    * Return the version of the referenced SecurityPlugin.
    * <p/>
    * @return String
    */
   public String getSecurityPluginVersion() throws XmlBlasterException
   {
      if (securityQos != null)
         return securityQos.getPluginVersion();
      return null;
   }

   public String getUserId() throws XmlBlasterException
   {
      if (securityQos==null)
         return "NoLoginName";
      else
         return securityQos.getUserId();
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

      //if (Log.TRACE) Log.trace(ME, "Entering startElement for uri=" + uri + " localName=" + localName + " name=" + name);

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
            Log.error(ME, "Missing 'serverRef' attribute 'type' in login-qos");
            type = "IOR";
         }
         tmpServerRef = new ServerRef(type);
         if (tmp.length() > 0) {
            tmpServerRef.setAddress(tmp);
            character.setLength(0);
         }
         return;
      }

      if (inCallback && !inBurstMode && !inCompress && !inPtpAllowed) {
         String tmp = character.toString().trim(); // The address
         if (tmp.length() > 0) {
            tmpAddr.setAddress(tmp);
            //Log.info(ME, "Setting address '" + tmp + "'");
            character.setLength(0);
         }
      }

      if (name.equalsIgnoreCase("callback")) {
         inCallback = true;
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
            Log.error(ME, "Missing 'callback' attribute 'type' in login-qos");
            type = "IOR";
         }
         tmpAddr = new CallbackAddress(type);
         if (tmp.length() > 0) {
            tmpAddr.setAddress(tmp);
            character.setLength(0);
         }
         return;
      }

      if (name.equalsIgnoreCase("burstMode")) {
         inBurstMode = true;
         if (tmpAddr == null || !inCallback) {
            Log.error(ME, "<burstMode> tag is not in <callback> tag, element ignored.");
            character.setLength(0);
            return;
         }
         if (attrs != null) {
            int len = attrs.getLength();
            int ii=0;
            for (ii = 0; ii < len; ii++) {
               if (attrs.getQName(ii).equalsIgnoreCase("collectTime")) {
                  String tmp = attrs.getValue(ii).trim();
                  try {
                     tmpAddr.setCollectTime(new Long(tmp).longValue());
                  } catch (NumberFormatException e) {
                     Log.error(ME, "Wrong format of <burstMode collectTime='" + tmp + "'>, expected a long in milliseconds, burst mode is switched off.");
                  }
                  break;
               }
            }
            if (ii >= len)
               Log.error(ME, "Missing 'collectTime' attribute in login-qos <burstMode>");
         }
         else {
            Log.error(ME, "Missing 'collectTime' attribute in login-qos <burstMode>");
         }
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("compress")) {
         inCompress = true;
         if (tmpAddr == null || !inCallback) {
            Log.error(ME, "<compress> tag is not in <callback> tag, element ignored.");
            character.setLength(0);
            return;
         }
         if (attrs != null) {
            int len = attrs.getLength();
            int ii=0;
            for (ii = 0; ii < len; ii++) {
               if (attrs.getQName(ii).equalsIgnoreCase("type")) {
                  tmpAddr.setCompressType(attrs.getValue(ii).trim());
               }
               else if (attrs.getQName(ii).equalsIgnoreCase("minSize")) {
                  String tmp = attrs.getValue(ii).trim();
                  try {
                     tmpAddr.setMinSize(new Long(tmp).longValue());
                  } catch (NumberFormatException e) {
                     Log.error(ME, "Wrong format of <compress minSize='" + tmp + "'>, expected a long in bytes, compress is switched off.");
                  }
               }
            }
         }
         else {
            Log.error(ME, "Missing 'type' attribute in login-qos <compress>");
         }
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("ptp")) {
         inPtpAllowed = true;
         if (tmpAddr == null || !inCallback) {
            character.setLength(0); // Global PtP
            return;
         }
         character.setLength(0); // Callback specific PtP
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
         if (!existsTypeAttr) Log.error(ME, "Missing 'type' attribute in login-qos <securityService>");
         if (!existsVersionAttr) Log.error(ME, "Missing 'version' attribute in login-qos <securityService>");
         character.setLength(0);
         // Fall through and collect xml, will be parsed later by appropriate security plugin
      }

      if (name.equalsIgnoreCase("session")) {
         inSession = true;
         if (attrs != null) {
            int len = attrs.getLength();
            int ii=0;
            for (ii = 0; ii < len; ii++) {
               if (attrs.getQName(ii).equalsIgnoreCase("timeout"))
                  this.sessionTimeout = (new Long(attrs.getValue(ii).trim())).longValue();
               else if (attrs.getQName(ii).equalsIgnoreCase("maxSessions"))
                  this.maxSessions = (new Integer(attrs.getValue(ii).trim())).intValue();
               else
                  Log.warn(ME, "Ignoring unknown attribute '" + attrs.getQName(ii) + "' of <session> element");
            }
         }
         character.setLength(0);

         return;
      }

      if (name.equalsIgnoreCase("sessionId")) {
         if (!inSession)
            return;
         inSessionId = true;
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
   public void endElement(String uri, String localName, String name)
   {
      if (super.endElementBase(uri, localName, name) == true)
         return;

      //if (Log.TRACE) Log.trace(ME, "Entering endElement for " + name);

      if (name.equalsIgnoreCase("serverRef")) {
         inServerRef = false;
         String tmp = character.toString().trim(); // The address (if after inner tags)
         if (tmpServerRef != null) {
            if (tmp.length() > 0) tmpServerRef.setAddress(tmp);
            serverRefVec.addElement(tmpServerRef);
         }
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("callback")) {
         inCallback = false;
         String tmp = character.toString().trim(); // The address (if after inner tags)
         if (tmpAddr != null) {
            if (tmp.length() > 0) tmpAddr.setAddress(tmp);
            addressVec.addElement(tmpAddr);
         }
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("burstMode")) {
         inBurstMode = false;
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("compress")) {
         inCompress = false;
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("PtP")) {
         inPtpAllowed = false;
         String tmp = character.toString().trim();
         if (tmpAddr != null)
            tmpAddr.setPtpAllowed(new Boolean(tmp).booleanValue());
         else
            setPtpAllowed(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("securityService")) {
        inSecurityService = false;
        character.append("]]>\n");
        character.append("</").append(name).append(">");
        String tmp = character.toString().trim();
        // delegate the collected tags to our security plugin
        try {
           securityQos = getPlugin(tmpSecurityPluginType, tmpSecurityPluginVersion).getSecurityQos();
           /*
           "<securityService type=\""+tmpSecurityPluginType+"\" version=\""+tmpSecurityPluginVersion+"\">\n"+
               <user>user</user>
               <passwd>passwd</passwd>
            "</securityService>";
           */
           securityQos.parse(tmp);
         }
         catch(XmlBlasterException e) {
            Log.warn(ME, "Can't parse security string - " + e.toString() + "\n Check:\n" + tmp);
            throw new StopParseException(); // Enough error handling??
         }
      }

      if (name.equalsIgnoreCase("session")) {
         inSession = false;
      }

      if (name.equalsIgnoreCase("sessionId")) {
         if (inSession) {
            inSessionId = false;
            setSessionId(character.toString().trim());
         }
      }

      if (inSecurityService) {
         character.append("</"+name+">");
      }

   }


   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString()
   {
      return toXml();
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * The default is to include the security string
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
      if(plugin!=null && securityQos==null) {
         try {
            securityQos = getPlugin(null,null).getSecurityQos();
         } catch(XmlBlasterException e) {
            Log.warn(ME+".toXml", e.toString());
         }
      }

      StringBuffer sb = new StringBuffer(160);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append("<qos>\n");

      // <securityService ...
      if(securityQos!=null) sb.append(securityQos.toXml("   ")); // includes the qos of the ClientSecurityHelper

      sb.append(offset + "   <ptp>").append(ptpAllowed).append("</ptp>");

      sb.append(offset).append("   <session timeout='").append(sessionTimeout).append("' maxSessions='").append(maxSessions).append("'>");
      if(sessionId!=null) {
         sb.append(offset).append("      <sessionId>").append(sessionId).append("</sessionId>");
      }
      sb.append(offset).append("   </session>");

      for (int ii=0; ii<addressVec.size(); ii++) {
         CallbackAddress ad = (CallbackAddress)addressVec.elementAt(ii);
         sb.append(ad.toXml("   ")).append("\n");
      }

      for (int ii=0; ii<serverRefVec.size(); ii++) {
         ServerRef ref = (ServerRef)serverRefVec.elementAt(ii);
         sb.append(ref.toXml("   ")).append("\n");
      }

      sb.append("</qos>");

      return sb.toString();
   }

   /** For testing: java org.xmlBlaster.util.ConnectQos */
   public static void main(String[] args)
   {
      try {
         org.xmlBlaster.util.XmlBlasterProperty.init(args);
         ConnectQos qos = new ConnectQos(new CallbackAddress("IOR"));
         I_SecurityQos securityQos = new org.xmlBlaster.authentication.plugins.simple.SecurityQos("joe", "secret");
         qos.setSecurityQos(securityQos);
         System.out.println("Output from manually crafted QoS:\n" + qos.toXml());


         String xml =
            "<qos>\n" +
            "   <securityService type=\"simple\" version=\"1.0\">\n" +
            "      <![CDATA[\n" +
            "         <user>aUser</user>\n" +
            "         <passwd>theUsersPwd</passwd>\n" +
            "      ]]>\n" +
            "   </securityService>\n" +
            "   <ptp>true</ptp>\n" +
            "   <session timeout='3600000' maxSessions='20'>\n" +
            "      <sessionId>anId</sessionId>\n" +
            "   </session>\n" +
            "   <callback type='IOR'>\n" +
            "      <PtP>true</PtP>\n" +
            "      IOR:00011200070009990000....\n" +
            "      <compress type='gzip' minSize='1000' />\n" +
            "      <burstMode collectTime='400' />\n" +
            "   </callback>\n" +
            "   <callback type='EMAIL'>\n" +
            "      et@mars.universe\n" +
            "      <PtP>false</PtP>\n" +
            "   </callback>\n" +
            "   <callback type='XML-RPC'>\n" +
            "      <PtP>true</PtP>\n" +
            "      http:/www.mars.universe:8080/RPC2\n" +
            "   </callback>\n" +
            "   <offlineQueuing timeout='3600' />\n" +
            "   <serverRef type='IOR'>\n" +
            "      IOR:00011200070009990000....\n" +
            "   </serverRef>\n" +
            "   <serverRef type='EMAIL'>\n" +
            "      et@mars.universe\n" +
            "   </serverRef>\n" +
            "   <serverRef type='XML-RPC'>\n" +
            "      http:/www.mars.universe:8080/RPC2\n" +
            "   </serverRef>\n" +
            "</qos>\n";

         System.out.println("=====Original XML========\n");
         System.out.println(xml);
         qos = new ConnectQos(xml);
         System.out.println("=====Parsed and dumped===\n");
         System.out.println(qos.toXml());
         qos.setSecurityPluginData("simple", "1.0", "joe", "secret");
         System.out.println("=====Added security======\n");
         System.out.println(qos.toXml());
      }
      catch(Throwable e) {
         Log.error("TestFailed", e.toString());
      }
   }
}
