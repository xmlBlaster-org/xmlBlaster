/*------------------------------------------------------------------------------
Name:      XmlParserBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: XmlParserBase.java 12936 2004-11-24 20:15:11Z ruff $
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwriter;

import java.util.HashSet;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.util.qos.ClientProperty;


/**
 * In good old C days this would have been named a 'flag' (with bit wise setting)<br />
 * But this allows to specify QoS (quality of service) in XML syntax.
 * <p />
 * With XML there are no problems to extend the services of the xmlBlaster in unlimited ways.<br />
 * The xml string is parsed with a SAX parser, since no persistent DOM tree is needed
 * and SAX is much faster.
 * <p />
 * You may use this as a base class for your specialized QoS.<br />
 * The &lt;qos> tag is parsed here, and you provide the parsing of the inner tags.
 */
public class XmlParserBase extends SaxHandlerBase {
   
   private String ME = "XmlParserBase";
   protected boolean inRootTag = false;     // parsing inside <qos> ? </qos>
   protected ClientProperty clientProperty;
   protected Set allowedTagNames;
   protected String qosTag;
   protected int inClientProperty;
   

   public XmlParserBase(Global glob, String qosTag) {
      super(glob);
      setUseLexicalHandler(true);
      if (qosTag != null)
         this.qosTag = qosTag.trim();
      this.allowedTagNames = new HashSet();
   }

   
   protected static void addTagToString(StringBuffer buf,  String tagName, Attributes attrs) {
      buf.append("<").append(tagName);
      if (attrs == null || attrs.getLength() < 1)
         return;
      for (int i=0; i < attrs.getLength(); i++) {
         buf.append(" ").append(attrs.getQName(i)).append("='").append(attrs.getValue(i)).append("'");
      }
      buf.append(">");
   }
   
   public void addAllowedTag(String key) {
      this.allowedTagNames.add(key);
   }
   
   /**
    * To avoid SAX parsing (which costs many CPU cycles)
    * check the QoS string here if it contains anything useful.
    * @param qos The literal ASCII xml string
    */
   protected boolean isEmpty(String qos) {
      if (qos == null)
         return true;

      qos = qos.trim();

      if (qos.length() < 11) // minimum: "<qos/>" or "<qos></qos>"
         return true;

      String middle = qos.substring(5, qos.length()-6);
      if (middle.trim().length() < 1)
         return true;

      return false;
   }


   /**
    * Start element callback, does handling of tag &lt;qos>.
    * <p />
    * You may include this into your derived startElement() method like this:<br />
    * <pre>
    *  if (super.startElementBase(name, attrs) == true)
    *     return;
    * </pre>
    * @return true if the tag is parsed here, the derived class doesn't need to look at this tag anymore
    *         false this tag is not handled by this Base class
    */
   protected final boolean startElementBase(String uri, String localName, String name, Attributes attrs) {
      if (this.inClientProperty > 0) {
         if (this.allowedTagNames.contains(name))
            this.inClientProperty++;
         addTagToString(this.character, name, attrs);
         return true;
      }
      if (name.equalsIgnoreCase(this.qosTag)) {
         this.inRootTag = true;
         return true;
      }
      if (!this.inRootTag) {
         org.xmlBlaster.util.Global.instance().getLog("core").warn(ME, "Ignoring unknown element '" + name + "'.");
         character.setLength(0);
         Thread.dumpStack();
         return true;
      }
      if (this.allowedTagNames.contains(name)) {
         this.clientProperty = new ClientProperty(attrs.getValue("name"), attrs.getValue("type"), attrs.getValue("encoding"));
         this.inClientProperty++;
         character.setLength(0);
         return true;
      }
      return false;
   }


   /**
    * Start element.
    * <p />
    * Default implementation, knows how to parse &lt;qos> but knows nothing about the tags inside of qos
    */
   public void startElement(String uri, String localName, String name, Attributes attrs) throws org.xml.sax.SAXException
   {
      startElementBase(uri, localName, name, attrs);
   }


   /**
    * End element callback, does handling of tag &lt;qos>.
    * <p />
    * You may include this into your derived endElement() method like this:<br />
    * <pre>
    *  if (super.endElementBase(name) == true)
    *     return;
    * </pre>
    * @return true if the tag is parsed here, the derived class doesn't need to look at this tag anymore
    *         false this tag is not handled by this Base class
    */
   protected final boolean endElementBase(String uri, String localName, String name) {
      if( name.equalsIgnoreCase(this.qosTag) ) {
         this.inRootTag = false;
         character.setLength(0);
         return true;
      }

      if (this.allowedTagNames.contains(name)) {
         String tmp = character.toString(); // .trim();
         this.inClientProperty--;
         if (this.inClientProperty < 1) {
            if (this.clientProperty != null) {
               if (this.clientProperty.isStringType() && !this.clientProperty.isBase64())
                  this.clientProperty.setValue(tmp);
               else
                  this.clientProperty.setValueRaw(tmp);
            }
            return true;
         }
      }

      if (this.inClientProperty > 0) {
         this.character.append("</").append(name).append(">");
         return true;
      }

      return false;
   }


   /** End element.
    * <p />
    * Default implementation, knows how to parse &lt;qos> but knows nothing about the tags inside of qos
    */
   public void endElement(String uri, String localName, String name) throws org.xml.sax.SAXException
   {
      endElementBase(uri, localName, name);
   }
   
   public void startCDATA() {
      if (this.inClientProperty > 0)
         this.character.append("<![CDATA[");
   }

   public void endCDATA() {
      if (this.inClientProperty > 0)
         this.character.append("]]>");
   }
   
   
   /**
    * If value contains XML harmful characters it needs to be
    * wrapped by CDATA or encoded to Base64. 
    * @param value The string to verify
    * @return 0 No protection necessary
    *         1 Protection with CDATA is needed
    *         2 Protection with Base64 is needed
    */
   public static int protectionNeeded(String value) {
      if (value == null) return 0;
      if (value.indexOf("]]>") >= 0)
         return 2;
      for (int i=0; i<value.length(); i++) {
         int c = value.charAt(i);
         if (c == '<' || c == '&')
                 return 1;
      }
      return 0;
   }
   
   
}
