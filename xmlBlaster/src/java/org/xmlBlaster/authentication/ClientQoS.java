/*-----------------------------------------------------------------------------
Name:      ClientQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling login QoS (quality of service), knows how to parse with SAX
Version:   $Id: ClientQoS.java,v 1.15 2001/08/31 15:24:19 ruff Exp $
Author:    ruff@swand.lake.de
-----------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xml.sax.Attributes;
import java.util.Vector;
import java.io.Serializable;


/**
 * Handling of login() quality of services.
 * <p />
 * QoS Informations sent from the client to the server via the login() method<br />
 * They inform the server about client preferences and wishes
 * <p />
 * This class corresponds to the Java client helper class LoginQosWrapper.
 * @see org.xmlBlaster.client.LoginQosWrapper
 */
public class ClientQoS extends org.xmlBlaster.util.XmlQoSBase implements Serializable
{
   private static String ME = "ClientQoS";

   // helper flags for SAX parsing

   /** Contains CallbackAddress objects */
   private boolean inCallback = false;
   private CallbackAddress tmpAddr = null;
   transient private CallbackAddress[] addressArr = null;
   private Vector addressVec = new Vector();  // <callback type="IOR">IOR:000122200...</callback>
   private String securityPluginType = null;
   private String securityPluginVersion = null;
   private String securityPluginData = null;
   private String sessionId = null;
   private long sessionTimeout = 3600L * 1000L; // One hour
   private int maxSessions = 6;

   private boolean inBurstMode = false;
   private boolean inCompress = false;
   private boolean inPtpAllowed = false;
   private boolean inSecurityService = false;
   private boolean inSession = false;
   private boolean inSessionId = false;

   /**
    * Constructs the specialized quality of service object for a publish() call.
    */
   public ClientQoS(String xmlQoS_literal) throws XmlBlasterException
   {
      if (Log.DUMP) Log.dump(ME, "Creating ClientQoS(" + xmlQoS_literal + ")");
      addressArr = null;
      init(xmlQoS_literal);
      if (Log.DUMP) Log.dump(ME, "Parsed ClientQoS to\n" + toXml());
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


   public void setCallbackAddress(CallbackAddress addr)
   {
      addressVec.addElement(addr);
      addressArr = null; // reset to be recalculated on demand
   }


   /**
    * Set SecurityPlugin specific information.
    * <p/>
    * @param String The type of the used plugin
    * @param String The version of the used plugin
    * @param String The content.
    */
   public void setSecurityPlugin(String type, String version, String content) {
      this.securityPluginType=type;
      this.securityPluginVersion=version;
      this.securityPluginData=content;
   }


   /**
    * Return the type of the referenced SecurityPlugin.
    * <p/>
    * @return String
    */
   public String getSecurityPluginType() {
      return securityPluginType;
   }


   /**
    * Return the version of the referenced SecurityPlugin.
    * <p/>
    * @return String
    */
   public String getSecurityPluginVersion() {
      return securityPluginVersion;
   }


   /**
    * Return the SecurityPlugin specific information.
    * <p/>
    * @return String
    */
   public String getSecurityPluginData() {
      return securityPluginData;
   }

   public void setSessionId(String id) {
      if(id.equals("")) id = null;
      sessionId = id;
   }

   public String getSessionId() {
      return sessionId;
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

      if (name.equalsIgnoreCase("PtP")) {
         inPtpAllowed = true;
         if (tmpAddr == null || !inCallback) {
            Log.error(ME, "<PtP> tag is not in <callback> tag, element ignored.");
            character.setLength(0);
            return;
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
                  securityPluginType = attrs.getValue(ii).trim();
               }
               else if (attrs.getQName(ii).equalsIgnoreCase("version")) {
                  existsVersionAttr = true;
                  securityPluginVersion = attrs.getValue(ii).trim();
               }
            }
         }
         if (!existsTypeAttr) Log.error(ME, "Missing 'type' attribute in login-qos <securityService>");
         if (!existsVersionAttr) Log.error(ME, "Missing 'version' attribute in login-qos <securityService>");
         character.setLength(0);

         return;
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
         if (tmpAddr != null) {
            String tmp = character.toString().trim();
            tmpAddr.setPtpAllowed(new Boolean(tmp).booleanValue());
         }
         else
            Log.error(ME, "Internal problem, ignoring <PtP> element");
            character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("securityService")) {
        inSecurityService = false;
        securityPluginData = "<securityService type=\""+getSecurityPluginType()+"\" version=\""+getSecurityPluginVersion()+"\">"+character.toString().trim()+"</securityService>";
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

   }

   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml()
   {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   public final String toXml(String extraOffset)
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<qos>");
      CallbackAddress[] addr = getCallbackAddresses();
      for (int ii=0; ii<addr.length; ii++)
         sb.append(addr[ii].toXml(extraOffset + "   "));
      if(sessionId!=null) {
         sb.append(offset +"   <sessionId>" + getSessionId() + "</sessionId>");
      }
      if(securityPluginType!=null) {
         sb.append(offset + getSecurityPluginData());
      }
      sb.append(offset + "</qos>");

      return sb.toString();
   }


   /** For testing: java org.xmlBlaster.authentication.ClientQoS */
   public static void main(String[] args)
   {
      try {
         XmlBlasterProperty.init(args);
         String xml =
            "<qos>\n" +
            "   <session timeout='3600000' maxSessions='20'>" +
            "      <sessionId>anId</sessionId>" +
            "   </session>" +
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
            "   <securityService type=\"simple\" version=\"1.0\">\n" +
            "      <![CDATA[\n" +
            "         <user>aUser</user>\n" +
            "         <passwd>theUsersPwd</passwd>\n" +
            "      ]]>\n" +
            "   </securityService>\n" +
            "</qos>\n";

         ClientQoS qos = new ClientQoS(xml);
         System.out.println(qos.toXml());
      }
      catch(Throwable e) {
         Log.error("TestFailed", e.toString());
      }
   }
}
