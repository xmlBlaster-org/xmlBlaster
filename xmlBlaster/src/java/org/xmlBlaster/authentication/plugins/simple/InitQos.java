package org.xmlBlaster.authentication.plugins.simple;

import org.xmlBlaster.util.Log;
import java.io.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SaxHandlerBase;

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
public class InitQos extends SaxHandlerBase {

   private static String ME = "InitQos";

   // helper flags for SAX parsing
   private boolean inSecurityService = false;
   private boolean inUser = false;
   private boolean inPasswd = false;

   private String version = null;
   private String type = null;
   private String user = null;
   private String passwd = null;


   public InitQos(String xmlQoS_literal) throws XmlBlasterException {
      if (Log.DUMP) Log.dump(ME, "Creating securityPlugin-QoS(" + xmlQoS_literal + ")");
      init(xmlQoS_literal);
      if (Log.DUMP) Log.dump(ME, "Parsed securityPlugin-QoS to\n" + toXml());
   }

   public InitQos(String loginName, String password)
   {
      this.user = loginName;
      this.password = password;
      this.type = "simple";
      this.version = "1.0";
   }

   public String getVersion() {
      return version;
   }

   public String getType() {
      return type;
   }

   public String getName() {
      return user;
   }

   public String getPasswd() {
      return passwd;
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

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   public final String toXml()
   {
      StringBuffer sb = new StringBuffer();

      sb.append("<securityService type=\"" + getType() + "\" version=\"" + getVersion() + "\">\n");
      sb.append("   <user>" + user + "</user>\n");
      sb.append("   <passwd>" + passwd + "</passwd>\n");
      sb.append("</securityService>");

      return sb.toString();
   }


   /** For testing: java org.xmlBlaster.authentication.ClientQoS */
   public static void main(String[] args)
   {
      try {
         XmlBlasterProperty.init(args);
         String xml =
            "<securityService type=\"simple\" version=\"1.0\">\n" +
            "   <passwd>theUsersPwd</passwd>\n" +
            "   <user>aUser</user>\n" +
            "</securityService>";

         InitQos qos = new InitQos(xml);
         System.out.println(qos.toXml());
      }
      catch(Throwable e) {
         Log.error("TestFailed", e.toString());
      }
   }
}
