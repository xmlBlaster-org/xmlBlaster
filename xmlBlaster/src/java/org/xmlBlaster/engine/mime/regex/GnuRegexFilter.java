/*------------------------------------------------------------------------------
Name:      GnuRegexFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Support check of message content with regular expressions.
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime.regex;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.mime.I_AccessFilter;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.engine.Global;

import gnu.regexp.RE;

/**
 * This regex plugin allows to filter message contents with regular expressions. 
 * <p />
 * Message contents which don't match the regular expression are not send via update()
 * or updateOneway() to the subscriber. The same filter may be used
 * for the synchronous get() access and for clusters to map messages to master nodes.
 * <p />
 * Please register this plugin in xmlBlaster.properties:
 * <pre>
 * MimeAccessPlugin[GnuRegexFilter][1.0]=org.xmlBlaster.engine.mime.regex.GnuRegexFilter
 * </pre>
 * Plugins must implement the I_Plugin interface to be loadable by the PluginManager
 * and must implement the I_AccessFilter interface to be usable as a filter.
 * <p />
 * <p />
 * NOTE: Since JDK 1.4 we have a java.util.regex package, but regrettably this can't be
 * used with JDK 1.2 or JDK 1.3. If you want to use Suns package just copy this class
 * and code it with suns implementation in our match() method:
 * <pre>
 *   import java.util.regex.Pattern;
 *   import java.util.regex.Matcher;
 *   ...
 *
 *   query.setPreparedQuery(Pattern.compile(query.getQuery()));
 *   ...
 *   Matcher m = preparedQuery.matcher(msgUnit.getContentStr());
 *   return  m.matches();
 * </pre>
 * @author xmlBlaster@marcelruff.info
 * @see <a href="http://www.cacas.org/java/gnu/regexp/" target="others">The GNU regex package</a>
 */
public class GnuRegexFilter implements I_Plugin, I_AccessFilter
{
   private final String ME = "GnuRegexFilter";
   private Global glob;
   private LogChannel log;
   /** Limits max message size to 1 MB as a default */
   private long DEFAULT_MAX_LEN = 1000000;
   /** For testsuite TestAccess.java only to force an XmlBlasterException */
   private int THROW_EXCEPTION_FOR_LEN = -1;

   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(Global glob) {
      this.glob = glob;
      this.log = glob.getLog("mime");
      log.info(ME, "Filter is initialized, we check all mime types if content is not to long");
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
   }

   /**
    * Return plugin type for Plugin loader
    * @return "GnuRegexFilter"
    */
   public String getType() {
      return "GnuRegexFilter";
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
    * @return "GnuRegexFilter"
    */
   public String getName() {
      return "GnuRegexFilter";
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

      RE expression;
      if (query.getPreparedQuery() == null) {
         try {
            expression = new RE(query.getQuery());
            query.setPreparedQuery(expression); // for better performance we remember the regex expression
         } catch (gnu.regexp.REException e) {
            log.error(ME, "Can't compile regular filter expression '" + query + "':" + e.toString());
            throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, "Can't compile regular filter expression '" + query + "':" + e.toString());
         }
      }
      else
         expression = (RE)query.getPreparedQuery();

      return expression.isMatch(msgUnit.getContentStr());
   }

   public void shutdown() {
   }

}

