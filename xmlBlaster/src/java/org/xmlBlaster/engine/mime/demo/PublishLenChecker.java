/*------------------------------------------------------------------------------
Name:      PublishLenChecker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id$
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime.demo;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.mime.I_PublishFilter;
import org.xmlBlaster.engine.Global;


/**
 * This demo plugin filters away all messages having a content longer than the given filter length. 
 * <p />
 * Published messages which are longer then the max length are rejected. 
 * <p />
 * Please register this plugin in xmlBlaster.properties:
 * <pre>
 * MimePublishPlugin[PublishLenChecker][1.0]=org.xmlBlaster.engine.mime.demo.PublishLenChecker
 * </pre>
 * Plugins must implement the I_Plugin interface to be loadable by the PluginManager
 * and must implement the I_PublishFilter interface to be usable as a filter.
 * @author xmlBlaster@marcelruff.info
 */
public class PublishLenChecker implements I_Plugin, I_PublishFilter
{
   private final String ME = "PublishLenChecker";
   private Global glob;
   private LogChannel log;
   /** Limits max message size to 1 MB as a default */
   private long DEFAULT_MAX_LEN = 1000000;
   /** For testsuite TestPublish.java only to force an XmlBlasterException */
   private int THROW_EXCEPTION_FOR_LEN = -1;

   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(Global glob) {
      this.glob = glob;
      this.log = glob.getLog("mime");
      log.info(ME, "Filter is initialized, we check all mime types if content is not too long");
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
      this.log = glob.getLog("mime");

      java.util.Properties props = pluginInfo.getParameters();

      String lenStr = (String)props.get("DEFAULT_MAX_LEN");
      if (lenStr != null) {
         DEFAULT_MAX_LEN = (new Long(lenStr)).longValue();
         log.info(ME, "Setting DEFAULT_MAX_LEN=" + DEFAULT_MAX_LEN + " as configured in xmlBlaster.properties");
      }

      // This is for the testsuite only to test exception
      String throwStr = (String)props.get("THROW_EXCEPTION_FOR_LEN");
      if (throwStr != null) {
         THROW_EXCEPTION_FOR_LEN = (new Integer(throwStr)).intValue();
         log.info(ME, "Setting THROW_EXCEPTION_FOR_LEN=" + THROW_EXCEPTION_FOR_LEN + " as configured in xmlBlaster.properties");
      }
   }

   /**
    * Return plugin type for Plugin loader
    * @return "PublishLenChecker"
    */
   public String getType() {
      return "PublishLenChecker";
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
    * @return "PublishLenChecker"
    */
   public String getName() {
      return "PublishLenChecker";
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
    * Please read the I_PublisheFilter.intercept() Javadoc.
    * @return "" or "OK": The message is accepted<br />
    * @see I_PublisheFilter#intercept(SubjectInfo, MsgUnit)
    */
   public String intercept(SubjectInfo publisher, MsgUnit msgUnit) throws XmlBlasterException {
      if (msgUnit == null) {
         Thread.dumpStack();
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal argument in intercept() call - msgUnit is null");
      }

      if (msgUnit.getKeyData().isInternal())
         return "";  // ignore internal messages

      try {
         long maxLen = DEFAULT_MAX_LEN;  // Use default max length

         if (msgUnit.getContent().length == THROW_EXCEPTION_FOR_LEN) {
            log.info(ME, "Test what happens if we throw an exception");
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Test what happens if we throw an exception");
         }
         if (msgUnit.getContent().length > maxLen) {
            log.info(ME, "Message REJECTED, msgLen=" + msgUnit.getContent().length + " max allowed=" + maxLen);
            return "REJECTED";
         }
         else {
            log.info(ME, "Message access OK, msgLen=" + msgUnit.getContent().length + " max=" + maxLen);
            return Constants.STATE_OK; // "OK" message is accepted
         }
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         String tmp = "Can't filter message because of an unexpected problem: " + e.toString();
         log.error(ME, tmp);
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, tmp);
      }
   }

   public void shutdown() {
   }

}

