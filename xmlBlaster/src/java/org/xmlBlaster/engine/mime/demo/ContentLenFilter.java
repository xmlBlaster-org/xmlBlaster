/*------------------------------------------------------------------------------
Name:      ContentLenFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime.demo;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.engine.mime.I_AccessFilter;
import org.xmlBlaster.engine.Global;


/**
 * This demo plugin filters away all messages having a content longer than the given filter length. 
 * <p />
 * Message which are longer then the max length are used to filter subscribed messages,
 * they are not send via updated() to the subscriber. The same filter may be used
 * for the synchronous get() access.
 * <p />
 * Please register this plugin in xmlBlaster.properties:
 * <pre>
 * MimeAccessPlugin[ContentLenFilter][1.0]=org.xmlBlaster.engine.mime.demo.ContentLenFilter
 * </pre>
 * Plugins must implement the I_Plugin interface to be loadable by the PluginManager
 * and must implement the I_AccessFilter interface to be usable as a filter.
 * @author xmlBlaster@marcelruff.info
 */
public class ContentLenFilter implements I_Plugin, I_AccessFilter
{
   private final String ME = "ContentLenFilter";
   private Global glob;
   private static Logger log = Logger.getLogger(ContentLenFilter.class.getName());
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

      log.info("Filter is initialized, we check all mime types if content is not too long");
   }

   /**
    * This method is called by the PluginManager.
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(Global,PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws XmlBlasterException {

      java.util.Properties props = pluginInfo.getParameters();

      String lenStr = (String)props.get("DEFAULT_MAX_LEN");
      if (lenStr != null) {
         DEFAULT_MAX_LEN = (new Long(lenStr)).longValue();
         log.info("Setting DEFAULT_MAX_LEN=" + DEFAULT_MAX_LEN + " as configured in xmlBlaster.properties");
      }

      // This is for the testsuite only to test exception
      String throwStr = (String)props.get("THROW_EXCEPTION_FOR_LEN");
      if (throwStr != null) {
         THROW_EXCEPTION_FOR_LEN = (new Integer(throwStr)).intValue();
         log.info("Setting THROW_EXCEPTION_FOR_LEN=" + THROW_EXCEPTION_FOR_LEN + " as configured in xmlBlaster.properties");
      }
   }

   /**
    * Return plugin type for Plugin loader
    * @return "ContentLenFilter"
    */
   public String getType() {
      return "ContentLenFilter";
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
    * @return "ContentLenFilter"
    */
   public String getName() {
      return "ContentLenFilter";
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
    * @param msgUnit The message to check
    * @param query   The max. message length as given by the subscriber/getter.<br />
    *                If null we use 1 MByte as max size
    * @return true   If message is not too long
    * @exception see I_AccessFilter#match()
    * @see I_AccessFilter#match(SessionInfo, SessionInfo, MsgUnit, Query)
    */
   public boolean match(SessionInfo receiver, MsgUnit msgUnit, Query query) throws XmlBlasterException {
      if (msgUnit == null) {
         Thread.dumpStack();
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal argument in match() call");
      }

      try {
         long maxLen;

         if (query != null) { // Subscriber/getter has given own max length
            maxLen = new Long(query.toString().trim()).longValue();
         }
         else                 // Use default max length
            maxLen = DEFAULT_MAX_LEN;

         if (msgUnit.getContent().length == THROW_EXCEPTION_FOR_LEN) {
            if (Constants.OID_DEAD_LETTER.equals(msgUnit.getKeyOid())) {
               log.info("Dead messages pass through");
               return true;  // message will be delivered
            }
            if (msgUnit.getQosData().isErased()) {
               log.info("Messages with state=" + msgUnit.getQosData().getState() + " pass through");
               return true;  // message will be delivered
            }
            log.info("Test what happens if we throw an exception");
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Test what happens if we throw an exception");
         }
         if (msgUnit.getContent().length > maxLen) {
            log.info("Message access denied, msgLen=" + msgUnit.getContent().length + " max allowed=" + maxLen);
            return false; // message will not be send to client
         }
         else {
            log.info("Message access OK, msgLen=" + msgUnit.getContent().length + " max=" + maxLen);
            return true;  // message will be delivered
         }
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         log.severe("Can't filter message, your filter string '" + query + "' is illegal, expected a max size integer: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, "Can't filter message, your filter string '" + query + "' is illegal, expected a max size integer", e);
      }
   }

   public void shutdown() {
   }

}

