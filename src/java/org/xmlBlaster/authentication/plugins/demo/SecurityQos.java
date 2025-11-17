package org.xmlBlaster.authentication.plugins.demo;

import java.io.IOException;

import org.xml.sax.Attributes;
import org.xmlBlaster.util.Base64;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;

import org.xmlBlaster.authentication.plugins.I_SecurityQos;

/**
 * Helper class for Java clients.
 * <p />
 * This class only generates a login() or init() qos xml string
 * typically of the form:
 * <p />
 * <pre>
 *    &lt;securityService type='gui' version='1.0'>
 *       &lt;![CDATA[
 *          &lt;user>KingKong&lt;/user>
 *          &lt;passwd>secret&lt;/passwd>
 *       ]]>
 *    &lt;/securityService>
 * </pre>
 */
public class SecurityQos extends SaxHandlerBase implements I_SecurityQos
{
   private String type = "gui";
   private String version = "1.0";
   private String user = "";
   private String passwd = "";

   public SecurityQos(Global glob)
   {
      super(glob);
   }

   public SecurityQos(Global glob, String loginName, String password)
   {
      super(glob);
      this.user = loginName;
      this.passwd = password;
   }

   public void parse(String xmlQos_literal) throws XmlBlasterException
   {
      // Strip CDATA tags that we are able to parse it:
      xmlQos_literal = org.xmlBlaster.util.ReplaceVariable.replaceAll(xmlQos_literal, "<![CDATA[", "");
      xmlQos_literal = org.xmlBlaster.util.ReplaceVariable.replaceAll(xmlQos_literal, "]]>", "");

      init(xmlQos_literal);
   }

   public void setUserId(String userId)
   {
      this.user = userId;
   }

   public String getUserId()
   {
      return user;
   }

   public String getPluginType()
   {
      return type;
   }

   public String getPluginVersion()
   {
      return version;
   }

   public void setCredential(String cred)
   {
      this.passwd = cred;
   }

   public String getCredential()
   {
      return null;
   }

   public void setClientIp (String ip){
   }

   public String getClientIp(){
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
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("passwd")) {
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
         user = character.toString().trim();
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("passwd")) {
         passwd = character.toString().trim();
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("securityService")) {
         character.setLength(0);

         return;
      }
   }
   
   @Override
   public void parseJson(JsonNode node) throws XmlBlasterException {
      if (node == null || node.isNull()) {
         throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY,"securityService JSON node is null");
     }

     // --- Mirror SAX behaviour for <securityService type="..." version="..."> ---
     JsonNode typeNode = node.get("type");
     JsonNode versionNode = node.get("version");

     if (typeNode != null && !typeNode.isNull()) {
         this.type = typeNode.asText().trim();
     } else {
        throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY,"Missing 'type' attribute in login-qos securityService");
     }

     if (versionNode != null && !versionNode.isNull()) {
         this.version = versionNode.asText().trim();
     } else {
        throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY,"Missing 'version' attribute in login-qos <securityService>");
     }

     JsonNode userNode = node.get("user");
     if (userNode != null && !userNode.isNull()) {
         this.user = userNode.asText().trim();
     }

     JsonNode passwdNode = node.get("passwd");
     if (passwdNode != null && !passwdNode.isNull()) {
         this.passwd = passwdNode.asText().trim();
     }
 }

   public String toXml(String extraOffset)
   {
      StringBuffer sb = new StringBuffer(200);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      if(passwd==null) passwd="";
      if(user==null) user="";

      sb.append(offset).append("<securityService type=\"").append(type).append("\" version=\"").append(version).append("\">");
      // The XmlRpc driver does not like it.
      sb.append(offset).append("   <![CDATA[");
      sb.append(offset).append("      <user>").append(user).append("</user>");
      sb.append(offset).append("      <passwd>").append(passwd).append("</passwd>");
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
      if (passwd == null)
         passwd = "";
      if (user == null)
         user = "";

      gen.writeStringField("user", user);

      gen.writeStringField("passwd", passwd);

      gen.writeEndObject();
   }


}
