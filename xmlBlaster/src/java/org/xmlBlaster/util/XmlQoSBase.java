/*------------------------------------------------------------------------------
Name:      XmlQoSBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one QoS (quality of service), knows how to parse it with SAX
Version:   $Id: XmlQoSBase.java,v 1.19 2003/12/07 11:57:14 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.io.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;
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
public class XmlQoSBase extends SaxHandlerBase
{
   private String ME = "XmlQoSBase";
   protected boolean inQos = false;     // parsing inside <qos> ? </qos>
   protected ClientProperty clientProperty;


   /**
    * Constructs an un initialized QoS (quality of service) object.
    * You need to call the init() method to parse the XML string.
    */
   public XmlQoSBase()
   {
   }

   public XmlQoSBase(Global glob)
   {
      super(glob);
   }

   /**
    * To avoid SAX parsing (which costs many CPU cycles)
    * check the QoS string here if it contains anything useful.
    * @param qos The literal ASCII xml string
    */
   protected boolean isEmpty(String qos)
   {
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
   protected final boolean startElementBase(String uri, String localName, String name, Attributes attrs)
   {
      if (name.equalsIgnoreCase("qos")) {
         inQos = true;
         return true;
      }
      if (!inQos) {
         org.xmlBlaster.util.Global.instance().getLog("core").warn(ME, "Ignoring unknown element '" + name + "'.");
         character.setLength(0);
         Thread.currentThread().dumpStack();
         return true;
      }
      if (name.equalsIgnoreCase("clientProperty")) {
         this.clientProperty = new ClientProperty(this.glob, attrs.getValue("name"), attrs.getValue("type"), attrs.getValue("encoding"));
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
   public void startElement(String uri, String localName, String name, Attributes attrs)
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
      if( name.equalsIgnoreCase("qos") ) {
         inQos = false;
         character.setLength(0);
         return true;
      }

      if (name.equalsIgnoreCase("clientProperty")) {
         String tmp = character.toString().trim();
         if (this.clientProperty != null) {
            this.clientProperty.setValueRaw(tmp);
         }
         return true;
      }

      return false;
   }


   /** End element.
    * <p />
    * Default implementation, knows how to parse &lt;qos> but knows nothing about the tags inside of qos
    */
   public void endElement(String uri, String localName, String name)
   {
      endElementBase(uri, localName, name);
   }
}
