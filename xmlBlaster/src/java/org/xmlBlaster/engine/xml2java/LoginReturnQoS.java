/*------------------------------------------------------------------------------
Name:      LoginReturnQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling QoS (quality of service), knows how to parse it with SAX
Version:   $Id: LoginReturnQoS.java,v 1.2 2001/08/19 23:07:54 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xml2java;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.engine.helper.ServerRef;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import java.util.Vector;
import java.io.*;


/**
 * Handling of publish() quality of services.
 * <p />
 * QoS Informations sent from the client to the server via the publish() method<br />
 * They are needed to control the xmlBlaster
 * <p />
 * Example:
 * <pre>
 *   &lt;qos> &lt;!-- LoginReturnQoS -->
 *     &lt;sessionId>&lt;/sessionId>
 *     &lt;securityService type=\"simple\" version=\"1.0\">\n" +
 *        &lt;![CDATA[\n" +
 *           &lt;user>aUser&lt;/user>\n" +
 *           &lt;passwd>theUsersPwd&lt;/passwd>\n" +
 *        ]]>\n" +
 *     &lt;/securityService>\n" +
 *     &lt;serverRef type="IOR">IOR:001020000&lt;/serverRef>
 *  &lt;/qos>
 * </pre>
 */
public class LoginReturnQoS extends org.xmlBlaster.util.XmlQoSBase implements Serializable
{
   private static String ME = "LoginReturnQoS";

   // helper flags for SAX parsing

   /** Contains ServerRef objects */
   private boolean inServerRef = false;
   private ServerRef tmpAddr = null;
   transient private ServerRef[] addressArr = null;
   private Vector addressVec = new Vector();  // <serverRef type="IOR">IOR:000122200...</serverRef>
   private String securityPluginType = null;
   private String securityPluginVersion = null;
   private String securityPluginData = null;
   private String sessionId = null;

   private boolean inSecurityService = false;
   private boolean inSessionId = false;

   /**
    * Constructs the specialized quality of service object for a publish() call.
    */
   public LoginReturnQoS(String xmlQoS_literal) throws XmlBlasterException
   {
      if (Log.DUMP) Log.dump(ME, "Creating LoginReturnQoS(" + xmlQoS_literal + ")");
      addressArr = null;
      init(xmlQoS_literal);
      if (Log.DUMP) Log.dump(ME, "Parsed LoginReturnQoS to\n" + toXml());
   }


   /**
    * Constructs the specialized quality of service object for a publish() call.
    */
   public LoginReturnQoS(String sessionId, String clientSecurityCtxInfo) throws XmlBlasterException
   {
      if (Log.DUMP) Log.dump(ME, "Creating LoginReturnQoS(sessionId)");
      addressArr = null;
      this.sessionId = sessionId;
   }


   /**
    * Accessing the ServerRef addresses of the client
    * this may be a CORBA-IOR or email or URL ...
    * <p />
    * @return An array of ServerRef objects, containing the address and the protocol type
    *         If no serverRef available, return an array of 0 length
    */
   public ServerRef[] getServerRefs()
   {
      if (addressArr == null) {
         addressArr = new ServerRef[addressVec.size()];
         for (int ii=0; ii<addressArr.length; ii++)
            addressArr[ii] = (ServerRef)addressVec.elementAt(ii);
      }
      return addressArr;
   }


   /**
    * Accessing the ServerRef address of the xmlBlaster server
    * this may be a CORBA-IOR or email or URL ...
    * <p />
    * @return The first ServerRef object in the list, containing the address and the protocol type
    *         If no serverRef available we return null
    */
   public ServerRef getServerRef()
   {
      if (addressArr == null) {
         addressArr = new ServerRef[addressVec.size()];
         for (int ii=0; ii<addressArr.length; ii++)
            addressArr[ii] = (ServerRef)addressVec.elementAt(ii);
      }
      if (addressArr.length > 0)
         return addressArr[0];

      return null;
   }


   /**
    * Adds a server reference
    */
   public void setServerRef(ServerRef addr)
   {
      addressVec.addElement(addr);
      addressArr = null; // reset to be recalculated on demand
   }

   /**
    * Carry additional infos back from server login to client
    */
   public void setClientSecurityCtxInfo(String clientSecurityCtxInfo) {
      if  (clientSecurityCtxInfo != null) {
         Log.error(ME+".setClientSecurityCtxInfo", "Sending security data '" +  clientSecurityCtxInfo + "' back to client not yet implemented");
      }
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
         tmpAddr = new ServerRef(type);
         if (tmp.length() > 0) {
            tmpAddr.setAddress(tmp);
            character.setLength(0);
         }
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

      if (name.equalsIgnoreCase("sessionId")) {
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

      if (name.equalsIgnoreCase("serverRef")) {
         inServerRef = false;
         String tmp = character.toString().trim(); // The address (if after inner tags)
         if (tmpAddr != null) {
            if (tmp.length() > 0) tmpAddr.setAddress(tmp);
            addressVec.addElement(tmpAddr);
         }
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("securityService")) {
        inSecurityService = false;
        securityPluginData = "<securityService type=\""+getSecurityPluginType()+"\" version=\""+getSecurityPluginVersion()+"\">"+character.toString().trim()+"</securityService>";
      }

      if (name.equalsIgnoreCase("sessionId")) {
        inSessionId = false;
        setSessionId(character.toString().trim());
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
      ServerRef[] addr = getServerRefs();
      for (int ii=0; ii<addr.length; ii++)
         sb.append(addr[ii].toXml(extraOffset + "   "));
      if(sessionId!=null) {
         sb.append(offset +"   <sessionId>" + getSessionId() + "</sessionId>");
      }
      if(securityPluginType!=null) {
         sb.append(offset + "   <securityService type=\""
                          + getSecurityPluginType() + "\" version=\""
                          + getSecurityPluginVersion() + "\">");
         sb.append(offset + "      " + getSecurityPluginData());
         sb.append(offset + "   </securityService>");
      }
      sb.append(offset + "</qos>");

      return sb.toString();
   }


   /** For testing: java org.xmlBlaster.engine.xml2java.LoginReturnQoS */
   public static void main(String[] args)
   {
      try {
         XmlBlasterProperty.init(args);
         String xml =
            "<qos>\n" +
            "   <serverRef type='IOR'>\n" +
            "      IOR:00011200070009990000....\n" +
            "   </serverRef>\n" +
            "   <serverRef type='EMAIL'>\n" +
            "      et@mars.universe\n" +
            "   </serverRef>\n" +
            "   <serverRef type='XML-RPC'>\n" +
            "      http:/www.mars.universe:8080/RPC2\n" +
            "   </serverRef>\n" +
            "   <offlineQueuing timeout='3600' />\n" +
            "   <sessionId>anId</sessionId>" +
            "   <securityService type=\"simple\" version=\"1.0\">\n" +
            "      <![CDATA[\n" +
            "         <user>aUser</user>\n" +
            "         <passwd>theUsersPwd</passwd>\n" +
            "      ]]>\n" +
            "   </securityService>\n" +
            "</qos>\n";

         LoginReturnQoS qos = new LoginReturnQoS(xml);
         System.out.println(qos.toXml());
      }
      catch(Throwable e) {
         Log.error("TestFailed", e.toString());
      }
   }
}
