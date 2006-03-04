/*------------------------------------------------------------------------------
Name:      Sql92Filter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Support check of message content with regular expressions.
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime.sql92;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.lexical.Sql92Selector;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.mime.I_AccessFilter;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.engine.Global;

/**
 * This sql92 plugin allows to filter the client properties of the qos expressions of the
 * kind specified in SQL92 for the 'WHERE' selections. It is also conform to the 
 * specification of JMS Message Selectors (version 1.1).  
 * <p />
 * Please register this plugin in xmlBlaster.properties:
 * <pre>
 * MimeAccessPlugin[Sql92Filter][1.0]=org.xmlBlaster.engine.mime.sql92.Sql92Filter
 * </pre>
 * Plugins must implement the I_Plugin interface to be loadable by the PluginManager
 * and must implement the I_AccessFilter interface to be usable as a filter.
 * <p />
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @see <a href="http://java.sun.com/products/jms/docs.html" target="others">The JMS specification</a>
 */
public class Sql92Filter implements I_Plugin, I_AccessFilter
{
   private final String ME = "Sql92Filter";
   private Global glob;
   private static Logger log = Logger.getLogger(Sql92Filter.class.getName());

   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(Global glob) {
      this.glob = glob;

      log.info("Filter is initialized, we check all mime types");
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
   }

   /**
    * Return plugin type for Plugin loader
    * @return "Sql92Filter"
    */
   public String getType() {
      return "Sql92Filter";
   }

   /**
    * Return plugin version for Plugin loader
    * @return "1.0"
    */
   public String getVersion() {
      return "1.0";
   }

   /**
    * Get a human readable name of this filter implementation
    * @return "Sql92Filter"
    */
   public String getName() {
      return "Sql92Filter";
   }

   /**
    * Get the content MIME type for which this plugin applies
    * @return "*" This plugin handles all mime types
    */
   public String[] getMimeTypes() {
      String[] mimeTypes = { "*" };
      return mimeTypes;
   }

   /**
    * Get the content MIME version number for which this plugin applies
    * @return "1.0" (this is the default version number)
    */
   public String[] getMimeExtended() {
      String[] mimeExtended = { Constants.DEFAULT_CONTENT_MIME_EXTENDED }; // "1.0"
      return mimeExtended;
   }

   /**
    * Check if the filter rule matches for this message. 
    * @param publisher The subject object describing the publisher
    * @param receiver The subject object describing the receiver
    * @param msgUnit The message to check
    * @param query   The Query instance holding the regular expression from your filter.<br />
    * @return true   The filter regex expression matches the message content.
    * @exception see I_AccessFilter#match()
    */
   public boolean match(SessionInfo receiver, MsgUnit msgUnit, Query query) throws XmlBlasterException {
      if (msgUnit == null) {
         Thread.dumpStack();
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal argument in regex match() call");
      }

      Sql92Selector selector;
      if (query.getPreparedQuery() == null) {
         selector = new Sql92Selector(this.glob);
         query.setPreparedQuery(selector); // for better performance we remember the regex expression
      }
      else
         selector = (Sql92Selector)query.getPreparedQuery();
      return selector.select(query.getQuery(), msgUnit.getQosData().getClientProperties());
   }

   public void shutdown() {
   }

}

