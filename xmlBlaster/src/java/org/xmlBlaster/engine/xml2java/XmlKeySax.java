/*------------------------------------------------------------------------------
Name:      XmlKeySax.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Parsing XmlKey with SAX 2 parser
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xml2java;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.AccessFilterQos;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import java.util.Vector;

/**
 * SAX 2 parsing the &lt;filter> tags in an XmlKey. 
 * <p />
 * Example:
 * <pre>
 *  &lt;key queryType='XPATH'>
 *     /xmlBlaster/key/RUGBY
 *     &lt;filter type='ContentLength' version='1.0'>
 *       800
 *     &lt;/filter>
 *  &lt;key>
 * </pre>
 */
public class XmlKeySax extends SaxHandlerBase
{
   private final String ME = "XmlKeySax";
   private final Global glob;

   private final XmlKey xmlKey;
   private int inKey = 0;
   private transient boolean inFilter = false;

   private AccessFilterQos tmpFilter = null;

   /**
    * Create an XmlKey SAX parser. 
    * @param xmlKey The XmlKey to fill with data
    */
   public XmlKeySax(Global glob, XmlKey xmlKey) {
      this.glob = glob;
      this.xmlKey = xmlKey;
      if (glob.getLog().CALL) glob.getLog().call(ME, "Starting SAX parser");
   }

   /**
    * Called for SAX key start tag
    * @return true if ok, false on error
    */
   public final void startElement(String uri, String localName, String name, Attributes attrs) {
      //glob.getLog().info(ME, "startElement: name=" + name + " character='" + character.toString() + "'");
      
      if (name.equalsIgnoreCase("key")) {
         inKey++;
         if (inKey > 1) return; // ignore nested key tags
         if (attrs != null) {
            /* Currently we only want the filter tag!
            String tmp = attrs.getValue("oid");
            if (tmp != null) xmlKey.setKeyOid(tmp.trim());
            tmp = attrs.getValue("queryType");
            if (tmp != null) xmlKey.setQueryType(tmp.trim());
            tmp = attrs.getValue("contentMime");
            if (tmp != null) xmlKey.setContentMime(tmp.trim());
            tmp = attrs.getValue("contentMimeExtended");
            if (tmp != null) xmlKey.setContentMimeExtended(tmp.trim());
            tmp = attrs.getValue("domain");
            if (tmp != null) xmlKey.setDomain(tmp.trim());
            */
         }
         character.setLength(0);
         return;
      }

      /* Currently we only want the filter tag!
      if (inKey == 1) {
         String tmp = character.toString().trim(); // The XPath query string
         if (tmp.length() > 0) {
            xmlKey.setQueryString(tmp);
         }
      }
      */

      if (inKey == 1 && name.equalsIgnoreCase("filter")) {
         inFilter = true;
         tmpFilter = new AccessFilterQos(glob);
         boolean ok = tmpFilter.startElement(uri, localName, name, character, attrs);
         if (ok) {
            xmlKey.addFilter(tmpFilter);
         }
         else
            tmpFilter = null;
         return;
      }

      if (inKey > 0) {
         // Collect everything to pass it later to XmlKey for DOM parsing:
         character.append("<").append(name);
         if (attrs != null) {
            int len = attrs.getLength();
            for (int ii=0; ii<len; ii++) {
                character.append(" ").append(attrs.getQName(ii)).append("='").append(attrs.getValue(ii)).append("'");
            }
         }
         character.append(">");
         return;
      }

      return;
   }

   /**
    * Handle SAX parsed end element
    */
   public final void endElement(String uri, String localName, String name) {
      //glob.getLog().info(ME, "endElement: name=" + name + " character='" + character.toString() + "'");
      if (name.equalsIgnoreCase("key")) {
         inKey--;
         if (inKey > 0) return; // ignore nested key tags
         /* Currently we only want the filter tag!
         String tmp = character.toString().trim(); // The xpath query (if after inner tags)
         if (tmp.length() > 0)
            xmlKey.setQueryString(tmp);
         */
         character.setLength(0);
      }

      if (inKey == 1 && name.equalsIgnoreCase("filter")) {
         inFilter = false;
         if (tmpFilter != null)
            tmpFilter.endElement(uri, localName, name, character);
         return;
      }

      if (inKey > 0)
         character.append("</"+name+">");
   }

   /** For testing: java org.xmlBlaster.engine.xml2java.XmlKeySax */
   public static void main(String[] args)
   {
      try {
         Global glob = new Global(args);
         String xml =
            "  <key queryType='XPATH'>\n" +
            "     //STOCK\n" +
            "     <filter type='ContentLength'>\n" +
            "       8000\n" +
            "     </filter>\n" +
            "     <filter type='ContainsChecker' version='7.1' xy='true'>\n" +
            "       bug\n" +
            "     </filter>\n" +
            "  </key>";

         {
            System.out.println("\nTEST1 ...");
            XmlKey xmlKey = new XmlKey(glob, xml);
            //System.out.println(xmlKey.toXml());
            AccessFilterQos[] qosArr = xmlKey.getFilterQos();
            for (int ii=0; ii<qosArr.length; ii++) {
               System.out.println(qosArr[ii].toXml());
            }
         }
      }
      catch(Throwable e) {
         Log.error("TestFailed", e.toString());
      }
   }
}
