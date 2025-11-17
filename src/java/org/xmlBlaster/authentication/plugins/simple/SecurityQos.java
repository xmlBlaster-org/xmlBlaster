package org.xmlBlaster.authentication.plugins.simple;

import java.io.IOException;

import org.xml.sax.Attributes;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;

import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.util.ReplaceVariable;

/**
 * Parse the default security handling with loginName and password
 * from the login qos xml string:
 * <pre>
 *  &lt;securityService type="simple" version="1.0">
 *     &lt;user>aUser&lt;/user>
 *     &lt;passwd>theUsersPwd&lt;/passwd>
 *  &lt;/securityService>
 * </pre>
 */
public final class SecurityQos extends SaxHandlerBase implements I_SecurityQos
{
   private static String ME = "SecurityQos-simple";

   // helper flags for SAX parsing
   private transient boolean inSecurityService = false;
   private transient boolean inUser = false;
   private transient boolean inPasswd = false;

   private String type = "simple";
   private String version = "1.0";
   private String user = null;
   private String passwd = null;

   public SecurityQos(Global glob)
   {
      super(glob);
   }

   public SecurityQos(Global glob, String xmlQoS_literal) throws XmlBlasterException
   {
      super(glob);
      parse(xmlQoS_literal);
   }

   public void parse(String xmlQoS_literal) throws XmlBlasterException
   {
      // Strip CDATA tags that we are able to parse it:
      xmlQoS_literal = org.xmlBlaster.util.ReplaceVariable.replaceAll(xmlQoS_literal, "<![CDATA[", "");
      xmlQoS_literal = org.xmlBlaster.util.ReplaceVariable.replaceAll(xmlQoS_literal, "]]>", "");

      init(xmlQoS_literal);
   }

   public SecurityQos(Global glob, String loginName, String password)
   {
      super(glob);
      this.user = loginName;
      this.passwd = password;
   }

   public String getPluginVersion() {
      return version;
   }

   public String getPluginType() {
      return type;
   }

   public void setUserId(String userId)
   {
      this.user = userId;
   }

   public String getUserId()
   {
      return user;
   }
   
   public void setClientIp (String ip){
       
   }
   public String getClientIp(){
       return null;
   }


   /**
    * @param cred The password
    */
   public void setCredential(String cred)
   {
      this.passwd = cred;
   }

   /**
    * @return null (no password is delivered)
    */
   public String getCredential()
   {
      return null;
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
   }
   
   @Override
   public void parseJson(JsonNode node) throws XmlBlasterException {
       if (node == null || node.isNull()) {
           throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION,
                   "parseJson", "securityService JSON node is null");
       }

       try {
           JsonNode typeNode = node.get("type");
           JsonNode versionNode = node.get("version");

           if (typeNode != null && !typeNode.isNull()) {
               this.type = typeNode.asText().trim();
           } else {
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION,
                       "parseJson", "Missing 'type' attribute in securityService JSON");
           }

           if (versionNode != null && !versionNode.isNull()) {
               this.version = versionNode.asText().trim();
           } else {
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION,
                       "parseJson", "Missing 'version' attribute in securityService JSON");
           }

           JsonNode userNode = node.get("user");
           if (userNode != null && !userNode.isNull()) {
               this.user = userNode.asText().trim();
           }

           JsonNode passwdNode = node.get("passwd");
           if (passwdNode != null && !passwdNode.isNull()) {
               this.passwd = passwdNode.asText().trim();
           }

       } catch (Exception e) {
           // Wrap all exceptions in XmlBlasterException to match SAX error handling
           throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION,
                   "parseJson", "Error parsing securityService JSON: " + e.getMessage(), e);
       }
   }

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
      StringBuffer sb = new StringBuffer(160);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<securityService type=\"").append(getPluginType()).append("\" version=\"").append(getPluginVersion()).append("\">");
      sb.append(offset).append("   <![CDATA[");
      sb.append(offset).append("   <user>").append(user).append("</user>");
      sb.append(offset).append("   <passwd>").append(passwd).append("</passwd>");
      sb.append(offset).append("   ]]>");
      sb.append(offset).append("</securityService>");

      return sb.toString();
   }

   public void toJson(JsonGenerator gen) throws IOException {
      gen.writeStartObject();

      if (type != null && !type.isEmpty()) {
          gen.writeStringField("type", type);
      }
      if (version != null && !version.isEmpty()) {
          gen.writeStringField("version", version);
      }
      if (user != null && !user.isEmpty()) {
          gen.writeStringField("user", user);
      }
      if (passwd != null && !passwd.isEmpty()) {
          gen.writeStringField("passwd", passwd);
      }

      gen.writeEndObject();
   }
  


   /** For testing: java org.xmlBlaster.authentication.plugins.simple.SecurityQos */
   public static void main(String[] args)
   {
      try {
         Global glob = new Global(args);
         String xml =
            "<securityService type=\"simple\" version=\"1.0\">\n" +
            "   <![CDATA[\n" +
            "   <passwd>theUsersPwd</passwd>\n" +
            "   <user>aUser</user>\n" +
            "   ]]>\n" +
            "</securityService>";

         System.out.println("Original:\n" + xml);
         SecurityQos qos = new SecurityQos(glob, xml);
         System.out.println("Result:\n" + qos.toXml());
         qos.setUserId("AnotherUser");
         qos.setCredential("AnotherPassword");
         System.out.println(qos.toXml());
      }
      catch(Throwable e) {
         System.err.println("TestFailed: " + e.toString());
      }
   }

}
