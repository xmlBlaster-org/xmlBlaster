package org.xmlBlaster.authentication.plugins.simple;

import org.xml.sax.Attributes;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
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
