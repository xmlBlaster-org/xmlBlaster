/*------------------------------------------------------------------------------
Name:      PluginConfigSaxFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.runlevel;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.util.enum.ErrorCode;
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
   private final LogChannel log;

   private PluginConfig pluginConfig;
   private boolean isPlugin = false; // to set when an 'action' tag has been found (to know when to throw an ex)
   private XmlBlasterException ex;

   private RunLevelActionSaxFactory actionFactory;
   private String attributeKey;
   private StringBuffer attributeValue;
   private boolean inAction = false;
   private boolean wrappedInCDATA = false;

   /**
    * Can be used as singleton. 
    */
   public PluginConfigSaxFactory(Global glob) {
      super(glob);
      setUseLexicalHandler(true); // to allow CDATA wrapped attributes 
      this.glob = glob;
      this.log = glob.getLog("runlevel");
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
         if (this.log.TRACE) {
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
               if ("className".equalsIgnoreCase(key)) {
                  this.pluginConfig.setClassName(value);
                  continue;
               }
               if ("jar".equalsIgnoreCase(key)) {
                  this.pluginConfig.setJar(value);
                  continue;
               }
               this.log.warn(ME, "startElement: " + key + "='" + value + "' is unknown");
            }
         
         }
         return;
      }
      if ("attribute".equalsIgnoreCase(name)) {
         this.wrappedInCDATA = false;
         this.attributeKey = attrs.getValue("id");
         this.attributeValue = new StringBuffer();
         if (this.attributeKey == null || this.attributeKey.length() < 1)
            this.ex = new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".startElement",  "the attributes in the <plugin> tag must have an non-empty 'id' attribute");
         return;
      }
      this.log.warn(ME, "startElement: unknown tag '" + name + "'");
   }

   public void startCDATA() {
      if (this.log.CALL) this.log.call(ME, "startCDATA");
      this.wrappedInCDATA = true;
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
         if (this.attributeKey != null && this.attributeValue != null) {
            this.pluginConfig.addAttribute(this.attributeKey, this.attributeValue.toString());
            if (this.wrappedInCDATA) {
               this.pluginConfig.wrapAttributeInCDATA(this.attributeKey);
               this.wrappedInCDATA = false;
            }
         }
         this.attributeKey = null;
         this.attributeValue = null;
         return;
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

