package org.xmlBlaster.authentication.plugins.a2Blaster;

import org.xmlBlaster.util.Log;
import java.io.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.jutils.text.StringHelper;

/**
 * @author  $Author: ruff $ ($Name:  $)
 * @version $Revision: 1.2 $ (State: $State) (Date: $Date: 2001/12/20 22:04:41 $)
 */
public class SecurityQos extends SaxHandlerBase implements I_SecurityQos
{

   private static String ME = "SecurityQos";

   // helper flags for SAX parsing
   private        boolean    inSecurityService = false;
   private        boolean               inUser = false;
   private        boolean             inPasswd = false;
   private        boolean inA2BlasterSessionId = false;

   private        String                  type = "a2Blaster";
   private        String               version = "1.0";
   private        String                  user = null;
   private        String                passwd = null;
   private        String    a2BlasterSessionId = null;


   public SecurityQos(String xmlQos_literal) throws XmlBlasterException {
      parse(xmlQos_literal);
   }

   public void parse(String xmlQos_literal) throws XmlBlasterException
   {
      // Strip CDATA tags that we are able to parse it:
      xmlQos_literal = StringHelper.replaceAll(xmlQos_literal, "<![CDATA[", "");
      xmlQos_literal = StringHelper.replaceAll(xmlQos_literal, "]]>", "");

      if (Log.DUMP) Log.dump(ME, "Creating securityPlugin-QoS(" + xmlQos_literal + ")");
      init(xmlQos_literal);
      if (Log.DUMP) Log.dump(ME, "Parsed securityPlugin-QoS to\n" + toXml());
   }

   public SecurityQos(String loginName, String password)
   {
      this.user = loginName;
      this.passwd = password;
   }

   /**
    * Return the version of the requested security plugin (should be "1.0" in this case).
    * <p/>
    * @return String The security plugin version.
    */
   public String getPluginVersion() {
      return version;
   }

   /**
    * Return the requested security plugin type (should be a2Blaster).
    * <p/>
    * @return String The security plugin type.
    */
   public String getPluginType() {
      return type;
   }

   /**
    * return name of the subject.
    * <p/>
    * @return String Name or <code>null</code>
    */
   public String getUserId() {
      return user;
   }

   public void setUserId(String userId)
   {
      this.user = userId;
   }

   public void setCredential(String cred)
   {
      this.passwd = cred;
   }

   /**
    * return passwd of the subject.
    * <p/>
    * @return String Password or <code>null</code>
    */
   public String getCredential() {
      return passwd;
   }

   public String getA2BlasterSessionId() {
      return a2BlasterSessionId;
   }

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public void startElement(String uri, String localName, String name, Attributes attrs)
   {
      if (name.equalsIgnoreCase("securityService")) {
         inSecurityService = true;
         if (attrs != null) {
            int len = attrs.getLength();
            int ii=0;
            for (ii = 0; ii < len; ii++) {
               if (attrs.getQName(ii).equalsIgnoreCase("type")) {
                  type = attrs.getValue(ii).trim();
               }
               else if (attrs.getQName(ii).equalsIgnoreCase("version")) {
                  version = attrs.getValue(ii).trim();
               }
            }
         }
         character.setLength(0);

         return;
      }

      if (name.equalsIgnoreCase("user")) {
         inUser = true;
         character.setLength(0);

         return;
      }

      if (name.equalsIgnoreCase("passwd")) {
         inPasswd = true;
         character.setLength(0);

         return;
      }

      if (name.equalsIgnoreCase("sessionId")) {
         inA2BlasterSessionId = true;
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
      if (name.equalsIgnoreCase("user")) {
         inUser = false;
         user = character.toString().trim();
         character.setLength(0);

         return;
      }

      if (name.equalsIgnoreCase("passwd")) {
         inPasswd = false;
         passwd = character.toString().trim();
         character.setLength(0);

         return;
      }

      if (name.equalsIgnoreCase("securityService")) {
         inSecurityService = false;
         character.setLength(0);

         return;
      }

      if (name.equalsIgnoreCase("sessionId")) {
         inA2BlasterSessionId = false;
         a2BlasterSessionId = character.toString().trim();
         character.setLength(0);

         return;
      }
   }

   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml(String extraOffset)
   {
      StringBuffer sb = new StringBuffer(200);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<securityService type=\"").append(getPluginType()).append("\" version=\"").append(getPluginVersion()).append("\">\n");
      sb.append(offset).append("   <![CDATA[");
      sb.append(offset).append("   <user>").append(user).append("</user>\n");
      sb.append(offset).append("   <passwd>").append(passwd).append("</passwd>\n");
      sb.append(offset).append("   ]]>");
      sb.append(offset).append("   <sessionId>").append(a2BlasterSessionId).append("</sessionId>");
      sb.append(offset).append("</securityService>");

      return sb.toString();
   }


   /** For testing: java org.xmlBlaster.authentication.plugins.a2Blaster.SecurityQos */
   public static void main(String[] args)
   {
      try {
         XmlBlasterProperty.init(args);
         String xml =
            "<securityService type=\"a2Blaster\" version=\"1.0\">\n" +
            "   <![CDATA[\n" +
            "   <user>root</user>\n" +
            "   <passwd>secret</passwd>\n" +
            "   ]]>\n" +
            "</securityService>";

         SecurityQos qos = new SecurityQos(xml);
         System.out.println(qos.toXml());
      }
      catch(Throwable e) {
         Log.error("TestFailed", e.toString());
      }
   }
}
