package org.xmlBlaster.authentication.plugins.a2Blaster;

import org.xmlBlaster.util.Log;
import java.io.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SaxHandlerBase;

/**
 *
 *
 * @author  $Author: ruff $ ($Name:  $)
 * @version $Revision: 1.2 $ (State: $State) (Date: $Date: 2001/08/19 23:07:53 $)
 * Last Changes:
 *    ($Log: A2BlasterSecQoS.java,v $
 *    (Revision 1.2  2001/08/19 23:07:53  ruff
 *    (Merged the new security-plugin framework
 *    (
 *    (Revision 1.1.2.2  2001/08/19 12:21:02  kleinertz
 *    (*** empty log message ***
 *    (
 *    (Revision 1.1.2.1  2001/08/19 10:48:53  kleinertz
 *    (wkl: a2Blaster-plugin added
 *    ()
 */

public class A2BlasterSecQoS extends SaxHandlerBase {

   private static                    String ME = "A2BlasterSecQoS";

   // helper flags for SAX parsing
   private        boolean    inSecurityService = false;
   private        boolean               inUser = false;
   private        boolean             inPasswd = false;
   private        boolean inA2BlasterSessionId = false;

   private        String               version = null;
   private        String                  type = null;
   private        String                  user = null;
   private        String                passwd = null;
   private        String    a2BlasterSessionId = null;


   public A2BlasterSecQoS(String xmlQoS_literal) throws XmlBlasterException {
      if (Log.DUMP) Log.dump(ME, "Creating securityPlugin-QoS(" + xmlQoS_literal + ")");
      init(xmlQoS_literal);
      if (Log.DUMP) Log.dump(ME, "Parsed securityPlugin-QoS to\n" + toXml());
   }

   /**
    * Return the version of the requested security plugin (should be "1.0" in this case).
    * <p/>
    * @return String The security plugin version.
    */
   public String getVersion() {
      return version;
   }

   /**
    * Return the requested security plugin type (should be a2Blaster).
    * <p/>
    * @return String The security plugin type.
    */
   public String getType() {
      return type;
   }

   /**
    * return name of the subject.
    * <p/>
    * @return String Name or <code>null</code>
    */
   public String getName() {
      return user;
   }

   /**
    * return passwd of the subject.
    * <p/>
    * @return String Password or <code>null</code>
    */
   public String getPasswd() {
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
   public final String toXml()
   {
      StringBuffer sb = new StringBuffer();

      sb.append("<securityService type=\"" + getType() + "\" version=\"" + getVersion() + "\">\n");
      sb.append("   <user>" + user + "</user>\n");
      sb.append("   <passwd>" + passwd + "</passwd>\n");
      sb.append("   <sessionId>" + a2BlasterSessionId + "</sessionId>");
      sb.append("</securityService>");

      return sb.toString();
   }


   /** For testing: java org.xmlBlaster.authentication.A2BlasterQoS */
   public static void main(String[] args)
   {
      try {
         XmlBlasterProperty.init(args);
         String xml =
            "<securityService type=\"oldxmlBlaster-scheme\" version=\"1.0\">\n" +
            "   <passwd>root</passwd>\n" +
            "   <user>secrete</user>\n" +
            "</securityService>";

         A2BlasterSecQoS qos = new A2BlasterSecQoS(xml);
         System.out.println(qos.toXml());
      }
      catch(Throwable e) {
         Log.error("TestFailed", e.toString());
      }
   }
}
