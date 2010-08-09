/*------------------------------------------------------------------------------
 Name:      MsgInfoParserFactory.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.util.xbformat;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.xmlBlaster.client.script.XmlScriptInterpreter;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.I_PluginConfig;

/**
 * Creates a parser instance to serialize xmlBlaster messages. For example the
 * SOCKET xbf (xmlBlaster format) or the XmlScripting format.
 * 
 * @author xmlBlaster@marcelruff.info
 * @see <a
 *      href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The
 *      protocol.socket requirement</a>
 */
public class MsgInfoParserFactory {
   private static Logger log = Logger.getLogger(MsgInfoParserFactory.class.getName());
   private static MsgInfoParserFactory instance;
   private Map pluginNames;

   public static MsgInfoParserFactory instance() {
      if (instance == null) {
         synchronized (MsgInfoParserFactory.class) {
            if (instance == null) {
               instance = new MsgInfoParserFactory();
            }
         }
         instance.init();  // outside of synchronized as it calls recursively instance()
      }
      return instance;
   }

   private MsgInfoParserFactory() {
      instance = this;
      this.pluginNames = new HashMap();
   }

   private void init() {
      // TODO: We force register() of plugins here:
      // This needs to be changed to be dynamically
	  int count = 0;
      try {
         Class.forName("org.xmlBlaster.util.xbformat.XbfParser");
         new XbfParser();
         count++;
       } catch (Throwable e) { // ClassNotFoundException NoClassDefFoundError
         //e.printStackTrace();
         log.fine("No org.xmlBlaster.util.xbformat.XbfParser in CLASSPATH found");
      }
      try {
         Class.forName("org.xmlBlaster.util.xbformat.XmlScriptParser");
         new XmlScriptParser();
         count++;
      } catch (Throwable e) { // ClassNotFoundException NoClassDefFoundError
         //e.printStackTrace();
         log.fine("No org.xmlBlaster.util.xbformat.XmlScriptParser in CLASSPATH found");
      }
      if (count == 0)
    	  log.severe("No I_MsgInfoParser implementation found in CLASSPATH (expected XbfParser or XmlScriptParser)");
   }
   
   public synchronized void register(String key, String className) {
      this.pluginNames.put(key, className);
   }

   /**
    * Access the parser class name. 
    * @param fileName For example "xmlBlasterMessage.xbfz"
    * @param mimeType For example "application/xmlBlaster-xbfz"
    * @return For example "org.xmlBlaster.util.xbformat.XbfParser"
    * or null if none found
    */
   public String guessParserName(String fileName, String mimeType) {
      if (mimeType != null) {
         // The email layer may append a "; ..." to our original mimeType
         // mimeType="application/xmlBlaster-xbfz; name=xmlBlasterMessage.xbfz"
         // mimeType="text/plain; name=xmlBlasterMessage.xml; charset=UTF-8"
         int index = mimeType.indexOf(";");
         if (index > 0) {
            mimeType = mimeType.substring(0, index);
         }
         String className = (String)this.pluginNames.get(mimeType);
         if (className != null) return className;
      }
      // fileName = "xmlBlasterMessage.xml"      
      if (fileName != null) {
         int index = fileName.lastIndexOf(".");
         if (index > 0) {
            String extension = fileName.substring(index);
            String className = (String)this.pluginNames.get(extension);
            if (className != null) return className;
         }
      }
      return null;
   }

   /**
    * Check if the given mime can be parsed.  
    * @param fileName For example "xmlBlasterMessage.xbfz"
    * @param mimeType For example "application/xmlBlaster-xbfz"
    * @return true if we can parse this type
    */
   public boolean parserExists(String fileName, String mimeType) {
      return guessParserName(fileName, mimeType) != null;
   }

   /**
    * Create a new parser instance. 
    * The init() method is called already
    * @param glob
    * @param progressListener
    * @param className For example "org.xmlBlaster.util.xbformat.XbfParser"
    *           or "org.xmlBlaster.util.xbformat.XmlScriptParser"
    *           Can be null
    * @param pluginConfig TODO
    * @return Defaults to XbfParser()
    */
   public I_MsgInfoParser getMsgInfoParser(Global glob,
         I_ProgressListener progressListener,
         String className, I_PluginConfig pluginConfig)
         throws XmlBlasterException {
      I_MsgInfoParser msgInfoParser = null;
      if (className == null)
         msgInfoParser = new XbfParser();
      else {
         Class clazz;
         try {
            clazz = java.lang.Class.forName(className);
            msgInfoParser = (I_MsgInfoParser) clazz.newInstance();
         } catch (Exception e) {
            throw new XmlBlasterException(glob,
                  ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED,
                  "MsgInfoParserFactory", "The parser plugin '" + className
                        + "' is not found or has no default constructor", e);
         }
      }
      msgInfoParser.init(glob, progressListener, pluginConfig);
      return msgInfoParser;
   }
}
