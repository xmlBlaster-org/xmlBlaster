/*------------------------------------------------------------------------------
Name:      ContentLenFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: ContentLenFilter.java,v 1.12 2002/05/16 18:34:42 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime.demo;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.MessageUnitWrapper;
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
 * @author ruff@swand.lake.de
 */
public class ContentLenFilter implements I_Plugin, I_AccessFilter
{
   private final String ME = "ContentLenFilter";
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
    * This method is called by the PluginManager.
    * <p />
    * This xmlBlaster.properties entry example
    * <pre>
    *   MimeAccessPlugin[ContentLenFilter][1.0]=org.xmlBlaster.engine.mime.demo.ContentLenFilter,DEFAULT_MAX_LEN=200
    * </pre>
    * passes 
    * <pre>
    *   options[0]="DEFAULT_MAX_LEN"
    *   options[1]="200"
    * </pre>
    * <p/>
    * @param Global   An xmlBlaster instance global object holding logging and property informations
    * @param String[] Some arguments from xmlBlaster.properties.
    */
   public void init(org.xmlBlaster.util.Global glob, String[] options) throws XmlBlasterException {
      this.log = glob.getLog("mime");
      if (options != null) {
         for (int ii=0; ii<options.length-1; ii++) {
            if (options[ii].equalsIgnoreCase("DEFAULT_MAX_LEN")) {
               DEFAULT_MAX_LEN = (new Long(options[++ii])).longValue();
               log.info(ME, "Setting DEFAULT_MAX_LEN=" + DEFAULT_MAX_LEN + " as configured in xmlBlaster.properties");
            }
         }
         for (int ii=0; ii<options.length-1; ii++) { // This is for the testsuite only to test exception
            if (options[ii].equalsIgnoreCase("THROW_EXCEPTION_FOR_LEN")) {
               THROW_EXCEPTION_FOR_LEN = (new Integer(options[++ii])).intValue();
               log.info(ME, "Setting THROW_EXCEPTION_FOR_LEN=" + THROW_EXCEPTION_FOR_LEN + " as configured in xmlBlaster.properties");
            }
         }
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
    * @param publisher The subject object describing the publisher
    * @param receiver The subject object describing the receiver
    * @param msgUnit The message to check
    * @param query   The max. message length as given by the subscriber/getter.<br />
    *                If null we use 1 MByte as max size
    * @return true   If message is not too long
    * @exception XmlBlasterException Is thrown on problems, for example if the MIME type
    *            does not fit to message content.<br />
    *            Take care throwing an exception, as the
    *            exception is routed back to the publisher. Subscribers which where served before
    *            may receive the update, subscribers which are served after us won't get it.
    *            For the publisher it looks as if the publish failed completely. Probably it is
    *            best to return 'false' instead and log the situation.
    */
   public boolean match(SubjectInfo publisher, SubjectInfo receiver, MessageUnitWrapper msgUnitWrapper, Query query) throws XmlBlasterException {
      if (msgUnitWrapper == null) {
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Illegal argument in match() call");
      }

      MessageUnit msgUnit = msgUnitWrapper.getMessageUnit();

      try {
         long maxLen;

         if (query != null) { // Subscriber/getter has given own max length
            maxLen = new Long(query.toString().trim()).longValue();
         }
         else                 // Use default max length
            maxLen = DEFAULT_MAX_LEN;

         if (msgUnit.getContent().length == THROW_EXCEPTION_FOR_LEN) {
            log.info(ME, "Test what happens if we throw an exception");
            throw new XmlBlasterException(ME, "Test what happens if we throw an exception");
         }
         if (msgUnit.getContent().length > maxLen) {
            log.info(ME, "Message access denied, msgLen=" + msgUnit.getContent().length + " max allowed=" + maxLen);
            return false; // message will not be send to client
         }
         else {
            log.info(ME, "Message access OK, msgLen=" + msgUnit.getContent().length + " max=" + maxLen);
            return true;  // message will be delivered
         }
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         String tmp = "Can't filter message, your filter string '" + query + "' is illegal, expected a max size integer: " + e.toString();
         log.error(ME, tmp);
         throw new XmlBlasterException(ME, tmp);
      }
   }
}

