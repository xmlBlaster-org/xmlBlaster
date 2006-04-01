/*------------------------------------------------------------------------------
Name:      PluginConfigSaxFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.runlevel;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.util.def.ErrorCode;
import org.xml.sax.Attributes;


/**
 * This class parses an xml string to generate a PluginConfig object.
 * <p>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.runlevel.html">engine.runlevel requirement</a>
 * <pre>
 *  &lt;action do='LOAD'
 *             onStartupRunlevel='3'
 *             sequence='5'
 *             onFail='RESOURCE_CONFIGURATION_PLUGINFAILED'/>
 * </pre>
 */
public class PluginConfigSaxFactory extends SaxHandlerBase
{
   private String ME = "PluginConfigSaxFactory";
   private final Global glob;
   private static Logger log = Logger.getLogger(PluginConfigSaxFactory.class.getName());

   private PluginConfig pluginConfig;
   private boolean isPlugin = false; // to set when an 'action' tag has been found (to know when to throw an ex)
   private XmlBlasterException ex;

   private RunLevelActionSaxFactory actionFactory;
   private String attributeKey;
   private StringBuffer attributeValue;
   private boolean inAction = false;
   private boolean inAttribute = false;
   private boolean wrappedInCDATA = false; // for example: <attribute id='publishQos'><![CDATA[ bla ]]></attribute>
   private boolean embeddedCDATA = false;  // for example: <attribute id='publishQos'><qos><![CDATA[<expiration lifeTime='4000'/>]]></qos></attribute>
   private int subTagCounter;

   /**
    * Can be used as singleton. 
    */
   public PluginConfigSaxFactory(Global glob) {
      super(glob);
      setUseLexicalHandler(true); // to allow CDATA wrapped attributes 
      this.glob = glob;

      this.actionFactory = new RunLevelActionSaxFactory(this.glob);
   }

   /**
    * resets the factory (to be invoked before parsing)
    */
   public void reset() {
      this.ex = null; // reset the exeptions
      this.pluginConfig = new PluginConfig(glob);
      this.inAction = false;
      this.isPlugin = false;
      this.wrappedInCDATA = false;
   }

   /**
    * returns the parsed object
    */
   public PluginConfig getObject() {
      return this.pluginConfig;
   }

   /**
    * Parses the given xml Qos and returns a PluginConfigData holding the data. 
    * Parsing of update() and publish() QoS is supported here.
    * @param the XML based ASCII string
    */
   public synchronized PluginConfig readObject(String xmlTxt) throws XmlBlasterException {
      if (xmlTxt == null || xmlTxt.trim().length() < 1)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".readObject", "the <plugin> element is empty");
      reset();
      try {
         this.init(xmlTxt);      // use SAX parser to parse it (is slow)
      }
      catch (Throwable thr) {
         if (log.isLoggable(Level.FINE)) {
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".readObject", "exception occured when parsing the <plugin> tag. In fact it was '" + xmlTxt + "'", thr);
         }
         else {
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".readObject", "exception occured when parsing the <plugin> tag. In fact it was '" + xmlTxt + "'");
         }
      }

      if (this.ex != null) throw ex;

      if (!this.isPlugin) 
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".readObject", "the string '" + xmlTxt + "' does not contain the <plugin> tag");
      return this.pluginConfig;
   }


   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public final void startElement(String uri, String localName, String name, Attributes attrs) {
      if (this.ex != null ) return;

      if ("action".equalsIgnoreCase(name)) {
         this.inAction = true;
         this.actionFactory.reset();
      }
      if (this.inAction) {
         this.actionFactory.startElement(uri, localName, name, attrs);
         return;
      }

      if ("plugin".equalsIgnoreCase(name)) {
         this.isPlugin = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               String key = attrs.getQName(i);
               String value = attrs.getValue(i).trim();

               if ("id".equalsIgnoreCase(key)) {
                  this.pluginConfig.setId(value);
                  continue;
               }
               if ("create".equalsIgnoreCase(key)) {
                  this.pluginConfig.setCreate(Boolean.valueOf(value).booleanValue());
                  continue;
               }
               if ("className".equalsIgnoreCase(key)) {
                  this.pluginConfig.setClassName(value);
                  continue;
               }
               if ("jar".equalsIgnoreCase(key)) {
                  this.pluginConfig.setJar(value);
                  continue;
               }
               log.warning("startElement: " + key + "='" + value + "' is unknown");
            }
         
         }
         return;
      }
      if ("attribute".equalsIgnoreCase(name)) {
         this.inAttribute = true;
         this.wrappedInCDATA = false;
         this.attributeKey = attrs.getValue("id");
         this.attributeValue = new StringBuffer(1024);
         if (this.attributeKey == null || this.attributeKey.length() < 1)
            this.ex = new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".startElement",  "the attributes in the <plugin> tag must have an non-empty 'id' attribute");
         return;
      }

      if (this.inAttribute) {
         this.subTagCounter++;
         this.attributeValue.append("<").append(name);
         for (int i=0; i<attrs.getLength(); i++) {
            String qName = attrs.getQName(i);
            String val = attrs.getValue(i);
            this.attributeValue.append(" ").append(qName).append("='").append(val).append("'");
         }
         this.attributeValue.append(">");
      }
      else {
         log.warning("startElement: unknown tag '" + name + "'");
      }
   }

   public void startCDATA() {
      if (log.isLoggable(Level.FINER)) this.log.finer("startCDATA");
      this.wrappedInCDATA = true;
      if (this.subTagCounter > 0) {
         this.attributeValue.append("<![CDATA[");
         this.embeddedCDATA = true;
      }
   }

   /**
    * The characters to be filled
    */
   public void characters(char[] ch, int start, int length) {
      if (this.attributeValue != null)
         this.attributeValue.append(ch, start, length);
   }


   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name) {
      if (this.ex != null ) return;
      if (this.inAction) {
         this.actionFactory.endElement(uri, localName, name);
         if ("action".equalsIgnoreCase(name)) {
            this.pluginConfig.addAction(this.actionFactory.getObject());
            this.inAction = false;
         }
         return;
      }
      if ("attribute".equalsIgnoreCase(name)) {
         this.inAttribute = false;
         this.subTagCounter = 0;
         if (this.attributeKey != null && this.attributeValue != null) {
            String val = this.attributeValue.toString();

            /*if (val.startsWith("<![CDATA[")) {*/  //if (this.wrappedInCDATA) {
            if (false) { // currently bypassed since we don't want to strip &lt;![CDATA[ 
               // Strip CDATA if ampersand '&lt;![CDATA[' was used instead of '<![CDATA[':
               int pos = val.indexOf("<![CDATA[");
               if (pos >= 0) {
                  val = val.substring(pos+9);
               }
               pos = val.lastIndexOf("]]>");
               if (pos >= 0) {
                  val = val.substring(0, pos);
               }
            }
            this.pluginConfig.addAttribute(this.attributeKey, val);
            if (this.wrappedInCDATA) {
               this.pluginConfig.wrapAttributeInCDATA(this.attributeKey);
               this.wrappedInCDATA = false;
            }
         }
         this.attributeKey = null;
         this.attributeValue = null;
         return;
      }   
      if (this.inAttribute) {
         if (this.embeddedCDATA) {
            this.attributeValue.append("]]>");
            this.embeddedCDATA = false;
         }
         this.attributeValue.append("</"+name+">");
         this.subTagCounter--;
      }
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String writeObject(PluginConfig pluginConfig, String extraOffset) {
      return pluginConfig.toXml(extraOffset);
   }

   /**
    * A human readable name of this factory
    * @return "PluginConfigSaxFactory"
    */
   public String getName() {
      return "PluginConfigSaxFactory";
   }
}
