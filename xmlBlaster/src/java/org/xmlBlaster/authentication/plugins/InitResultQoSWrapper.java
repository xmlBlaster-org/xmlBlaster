package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.client.PluginManager;
import org.xmlBlaster.util.Log;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
//import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * This class wraps the result of <code>org.xmlBlaster.authentication.authenticate.init(...)</code>.
 * (Only used by the CORBA driver!)
 */

public class InitResultQoSWrapper extends SaxHandlerBase {
   public static final String ME = "InitResultQoSWrapper";

   private boolean      inSecurityService = false;
   private boolean            inSessionId = false;
   private boolean            inServerRef = false;

   private String               sessionId = null;
   private String           xmlBlasterIOR = null;

   private PluginManager       secPlgnMgr = null;
   private I_SecurityClientHelper secPlgn = null;
   private String                    type = null;
   private String                 version = null;

   public InitResultQoSWrapper(String xmlQos_literal) throws XmlBlasterException
   {
      Log.trace(ME+"."+ME+"()", "-------START--------\n");
      init(xmlQos_literal);
      Log.trace(ME+"."+ME+"()", "-------END----------\n");
   }

   /**
    * Return the assigned sessionId
    * <p/>
    * @return String SessionId
    */
   public String getSessionId()
   {
      return sessionId;
   }

   /**
    * Return the IOR of the xmlBlaster
    * </p>
    * @return String IOR
    */
   public String getXmlBlasterIOR()
   {
      return xmlBlasterIOR;
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

      if (name.equalsIgnoreCase("sessionId")) {
         inSessionId = true;
         character.setLength(0);

         return;
      }

      if (name.equalsIgnoreCase("serverRef")) {
         inServerRef = true;
         character.setLength(0);

         return;
      }

   }

   public void endElement(String uri, String localName, String name)
   {
      if (name.equalsIgnoreCase("sessionId")) {
         inSessionId = false;
         sessionId = character.toString().trim();
         character.setLength(0);

         return;
      }

      if (name.equalsIgnoreCase("serverRef")) {
         inServerRef = false;
         xmlBlasterIOR = character.toString().trim();
         character.setLength(0);

         return;
      }

      if (name.equalsIgnoreCase("securityService")) {
         inSecurityService = false;
         secPlgnMgr = PluginManager.getInstance();
         try {
            secPlgn = secPlgnMgr.getClientPlugin(type, version);
         }
         catch (Exception e) {
            Log.error(ME+"."+ME, "Security plugin initialization failed. Reason: "+e.toString());
         }

         if (secPlgn!=null) secPlgn.setSessionData(character.toString().trim());
         character.setLength(0);

         return;
      }
   }
}
