/*------------------------------------------------------------------------------
Name:      PluginHolderSaxFactory.java
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
import org.xmlBlaster.util.FileLocator;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.net.URL;


/**
 * This class parses an xml string to generate a PluginHolder object.
 * <p>
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.runlevel.html">engine.runlevel requirement</a>
 * <pre>
 *  &lt;action do='LOAD'
 *             onStartupRunlevel='3'
 *             sequence='5'
 *             onFail='RESOURCE_CONFIGURATION_PLUGINFAILED'/>
 * </pre>
 */
public class PluginHolderSaxFactory extends SaxHandlerBase
{
   private String ME = "PluginHolderSaxFactory";
   private final Global glob;
   private static Logger log = Logger.getLogger(PluginHolderSaxFactory.class.getName());

   private PluginHolder pluginHolder;
   private XmlBlasterException ex;

   private PluginConfigSaxFactory pluginFactory;
   private boolean inPlugin = false; // to set when a '<plugin>' tag has been found (to know when to throw an ex)
   private boolean isXmlBlaster = false;
   private String currentNode;

   /**
    * Can be used as singleton. 
    */
   public PluginHolderSaxFactory(Global glob) {
      super(glob);
      setUseLexicalHandler(true); // to allow CDATA wrapped attributes 
      this.glob = glob;

      this.pluginFactory = new PluginConfigSaxFactory(this.glob);
   }

   /**
    * resets the factory (to be invoked before parsing)
    */
   public void reset() {
      this.ex = null; // reset the exeptions
      this.pluginHolder = new PluginHolder(glob);
      this.inPlugin = false;
      this.currentNode = null;
      this.isXmlBlaster = false;
   }

   /**
    * returns the parsed object
    */
   public PluginHolder getObject() {
      return this.pluginHolder;
   }

   /**
    * Parses the given xmlBlasterPlugins.xml returns a PluginHolderData holding the data. 
    * @param the XML based ASCII string
    */
   public synchronized PluginHolder readObject(String xmlTxt) throws XmlBlasterException {
      if (xmlTxt == null || xmlTxt.trim().length() < 1)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".readObject", "the <xmlBlaster> element is empty");
      reset();
      try {
         this.init(xmlTxt);      // use SAX parser to parse it (is slow)
      }
      catch (Throwable thr) {
         if (log.isLoggable(Level.FINE)) {
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".readObject", "exception occured when parsing the <xmlBlaster> tag. In fact it was '" + xmlTxt + "'", thr);
         }
         else {
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".readObject", "exception occured when parsing the <xmlBlaster> tag. In fact it was '" + xmlTxt + "'");
         }
      }

      if (this.ex != null) throw ex;

      if (!this.isXmlBlaster)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".readObject", "the string '" + xmlTxt + "' does not contain the <xmlBlaster> tag");
      return this.pluginHolder;
   }


   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public final void startElement(String uri, String localName, String name, Attributes attrs) {
      if (this.ex != null ) return;

      if ("plugin".equalsIgnoreCase(name)) {
         this.inPlugin = true;
         this.pluginFactory.reset();
      }
      if (this.inPlugin) {
         this.pluginFactory.startElement(uri, localName, name, attrs);
         return;
      }

      if ("node".equalsIgnoreCase(name)) {
         String id = null;
         if (attrs != null) id = attrs.getValue("id");
         if (id == null || id.length() < 1)
            this.ex = new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".startElement", "in the <node> tag the 'id' attribute is mandatory:found none");
         this.currentNode = id;
         return;
      }

      if ("xmlBlaster".equalsIgnoreCase(name)) {
         this.isXmlBlaster = true;
         return;
      }

      log.warning("startElement: unknown tag '" + name + "'");
   }

   /**
    * The characters to be filled
    */
   public void characters(char[] ch, int start, int length) {
      if (this.inPlugin) this.pluginFactory.characters(ch, start, length);
   }


   public void startCDATA() {
      if (this.inPlugin) this.pluginFactory.startCDATA();
   }


   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name) {
      if (this.ex != null ) return;
      if (this.inPlugin) {
         this.pluginFactory.endElement(uri, localName, name);
         if ("plugin".equalsIgnoreCase(name)) {
            if (this.currentNode != null) {
               this.pluginHolder.addPluginConfig(this.currentNode, this.pluginFactory.getObject());
            }
            else this.pluginHolder.addDefaultPluginConfig(this.pluginFactory.getObject());
            this.inPlugin = false;
         }
         return;
      }
      if ("node".equalsIgnoreCase(name)) {
         this.currentNode = null;
         return;
      }   
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String writeObject(PluginHolder pluginConfig, String extraOffset) {
      return pluginConfig.toXml(extraOffset);
   }

   /**
    * A human readable name of this factory
    * @return "PluginHolderSaxFactory"
    */
   public String getName() {
      return "PluginHolderSaxFactory";
   }


   /**
    * Reads the configuration file <code>xmlBlasterPlugins.xml</code>. It first searches the file according to the 
    * xmlBlaster search strategy specified in the engine.runlevel requirement.
    * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.runlevel.html">engine.runlevel requirement</a>
    */
   public PluginHolder readConfigFile() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("readConfigFile");
      FileLocator fileLocator = new FileLocator(this.glob);
      URL url = fileLocator.findFileInXmlBlasterSearchPath("pluginsFile", "xmlBlasterPlugins.xml");

      // null pointer check here ....
      if (url == null) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".readConfigFile",
         "the file 'xmlBlasterPlugins.xml' has not been found in the search path nor in the property 'pluginsFile'");
      }

      if (log.isLoggable(Level.FINE)) log.fine("readConfigFile: the file is '" + url.getFile() + "'");
      try {
         InputStream fis = url.openStream();
         InputSource inSource = new InputSource(fis);
         reset();
         init(url.toString(), inSource);
         PluginHolder ret = getObject();
         PluginConfig[] arr = ret.getAllPluginConfig(this.glob.getNodeId().getId());
         for (int i=0; i<arr.length; i++)
            arr[i].registerMBean();
         if (log.isLoggable(Level.FINEST)) log.finest(".readConfigFile. The content: \n" + ret.toXml());
         return ret;
      }
      catch(java.io.IOException ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".readConfigFile", "the file '" + url.getFile() + "' has not been found", ex);
      }
   }

   public static void main(String[] args) {
      Global glob = Global.instance();
      glob.init(args);

      try {
         PluginHolderSaxFactory factory = new PluginHolderSaxFactory(glob);
         PluginHolder holder = factory.readConfigFile();
         log.info(holder.toXml());
      }
      catch (XmlBlasterException ex) {
         log.severe(ex.getMessage());
      }
   }

}

