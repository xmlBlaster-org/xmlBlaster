/*------------------------------------------------------------------------------
Name:      PluginConfigSaxFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.runlevel;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.AttributeSaxFactory;
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
   private boolean inAction = false;

   private AttributeSaxFactory attributeFactory;
   /*
   private String attributeKey;
   private StringBuffer attributeValue;
   private boolean inAttribute = false;
   private boolean wrappedInCDATA = false; // for example: <attribute id='publishQos'><![CDATA[ bla ]]></attribute>
   private boolean embeddedCDATA = false;  // for example: <attribute id='publishQos'><qos><![CDATA[<expiration lifeTime='4000'/>]]></qos></attribute>
   private int subTagCounter;
   */
   
   /**
    * Can be used as singleton. 
    */
   public PluginConfigSaxFactory(Global glob) {
      super(glob);
      setUseLexicalHandler(true); // to allow CDATA wrapped attributes 
      this.glob = glob;

      this.actionFactory = new RunLevelActionSaxFactory(this.glob);
      this.attributeFactory = new AttributeSaxFactory(this.glob, null);
   }

   /**
    * resets the factory (to be invoked before parsing)
    */
   public void reset() {
      this.ex = null; // reset the exeptions
      this.pluginConfig = new PluginConfig(glob);
      this.attributeFactory.reset(this.pluginConfig);
      this.inAction = false;
      this.isPlugin = false;
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
                  this.pluginConfig.setCreateInternal(Boolean.valueOf(value).booleanValue());
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
      if ("attribute".equalsIgnoreCase(name) || this.attributeFactory.isInAttribute())
         this.attributeFactory.startElement(uri, localName, name, attrs);
   }

   public void startCDATA() {
      this.attributeFactory.startCDATA();
   }

   /**
    * The characters to be filled
    */
   public void characters(char[] ch, int start, int length) {
      this.attributeFactory.characters(ch, start, length);
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
      if ("attribute".equalsIgnoreCase(name) || this.attributeFactory.isInAttribute())
         this.attributeFactory.endElement(uri, localName, name);
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

