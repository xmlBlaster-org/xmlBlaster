/*------------------------------------------------------------------------------
Name:      ContentLenFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: ContentLenFilter.java,v 1.2 2002/03/15 13:04:56 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime.demo;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.I_Plugin;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.mime.I_SubscribeFilter;
import org.xmlBlaster.engine.Global;


/**
 * This demo plugin filters away all messages having a content longer than the given filter length. 
 * <p />
 * Message which are longer then the max length are used to filter subscribed messages,
 * they are not send via updated() to the subscriber.
 * <p />
 * Please register this plugin in xmlBlaster.properties:
 * <pre>
 * MimeSubscribePlugin[ContentLenFilter][1.0]=org.xmlBlaster.engine.mime.demo.ContentLenFilter
 * </pre>
 * Plugins must implement the I_Plugin interface to be loadable by the PluginManager
 * and must implement the I_SubscribeFilter interface to be usable as a filter.
 * @author ruff@swand.lake.de
 */
public class ContentLenFilter implements I_Plugin, I_SubscribeFilter
{
   private final String ME = "ContentLenFilter";
   private Global glob;
   private Log log;
   private long DEFAULT_MAX_LEN = 1000000; // 1 Mbyte

   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(Global glob) {
      this.glob = glob;
      this.log = glob.getLog();
      log.info(ME, "Filter is initialized, we check all mime types if content is not to long");
   }

   /**
    * This method is called by the PluginManager.
    * <pre>
    *   MimeSubscribePlugin[ContentLenFilter][1.0]=org.xmlBlaster.engine.mime.demo.ContentLenFilter,DEFAULT_MAX_LEN=200
    * </pre>
    * <p/>
    * @param String[] Some arguments from xmlBlaster.properties.
    */
   public void init(String[] options) throws XmlBlasterException {
      if (options != null) {
         for (int ii=0; ii<options.length-1; ii++) {
            if (options[ii].equalsIgnoreCase("DEFAULT_MAX_LEN")) {
               DEFAULT_MAX_LEN = (new Long(options[++ii])).longValue();
               Log.info(ME, "Setting DEFAULT_MAX_LEN=" + DEFAULT_MAX_LEN + " as configured in xmlBlaster.properties");
            }
         }
      }
   }

   /** Return plugin type for Plugin loader */
   public String getType() {
      return "ContentLenFilter";
   }

   /** Return plugin version for Plugin loader */
   public String getVersion() {
      return "1.0";
   }

   /** Get a human readable name of this filter implementation */
   public String getName() {
      return "ContentLenFilter";
   }

   /** Get the content MIME type for which this plugin applies */
   public String[] getMimeTypes() {
      String[] mimeTypes = { "*" };
      return mimeTypes;
   }

   /** Get the content MIME version number for which this plugin applies */
   public String[] getMimeExtended() {
      String[] mimeExtended = { XmlKeyBase.DEFAULT_contentMimeExtended }; // "1.0"
      return mimeExtended;
   }

   /**
    * Check if the filter rule matches for this message. 
    * @param msgUnit The message to check
    * @param query   The max. message length as given by the subscriber.<br />
    *                If null we use 1 MByte as max size
    * @return true   If message is not to long
    * @exception XmlBlasterException Is thrown on problems, for example if MIME type
    *            does not fit to message content
    */
   public boolean match(MessageUnitWrapper msgUnitWrapper, String query) throws XmlBlasterException {
      if (msgUnitWrapper == null) {
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Illegal argument in match() call");
      }

      MessageUnit msgUnit = msgUnitWrapper.getMessageUnit();

      try {
         long maxLen;

         if (query != null) { // Subscriber has given own max length
            maxLen = new Long(query.trim()).longValue();
         }
         else                 // Use default max length
            maxLen = DEFAULT_MAX_LEN;

         if (msgUnit.getContent().length > maxLen) {
            log.info(ME, "Message update denied, msgLen=" + msgUnit.getContent().length + " max allowed=" + maxLen);
            return false; // message will not be send to client
         }
         else {
            log.info(ME, "Message update OK, msgLen=" + msgUnit.getContent().length + " max=" + maxLen);
            return true;  // message will be delivered
         }
      }
      catch (Throwable e) {
         String tmp = "Can't filter message, your filter string '" + query + "' is illegal, expected a max size integer: " + e.toString();
         log.error(ME, tmp);
         throw new XmlBlasterException(ME, tmp);
      }
   }
}

