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
 * @version $Revision: 1.2 $ (State: $State) (Date: $Date: 2001/08/30 17:14:49 $)
 * Last Changes:
 *    ($Log: ClientInfo.java,v $
 *    (Revision 1.2  2001/08/30 17:14:49  ruff
 *    (Renamed security stuff
 *    (
 *    (Revision 1.1.2.1  2001/08/22 11:18:42  ruff
 *    (changed naming schema
 *    (
 *    (Revision 1.2  2001/08/19 23:07:53  ruff
 *    (Merged the new security-plugin framework
 *    (
 *    (Revision 1.1.2.1  2001/08/19 10:48:53  kleinertz
 *    (wkl: a2Blaster-plugin added
 *    ()
 */

public class ClientInfo extends SaxHandlerBase {

   private static                    String ME = "ClientInfo";

   // helper flags for SAX parsing
   private        boolean    inUserInfo = false;
   private        boolean       inLogin = false;

   private        String          login = null;


   public ClientInfo(String xml_literal) throws XmlBlasterException {
      init(xml_literal);
   }

   public String getName() {
      return login;
   }

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public void startElement(String uri, String localName, String name, Attributes attrs)
   {
      if (name.equalsIgnoreCase("userinfo")) {
         inUserInfo = true;
         character.setLength(0);

         return;
      }

      if (name.equalsIgnoreCase("login")) {
         inLogin = true;
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
      if (name.equalsIgnoreCase("userinfo")) {
         inUserInfo = false;
         character.setLength(0);

         return;
      }

      if (name.equalsIgnoreCase("login")) {
         inLogin = false;
         login = character.toString().trim();
         character.setLength(0);

         return;
      }
   }

}
