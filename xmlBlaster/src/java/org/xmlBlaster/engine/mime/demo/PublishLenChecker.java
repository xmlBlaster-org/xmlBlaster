/*------------------------------------------------------------------------------
Name:      PublishLenChecker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: PublishLenChecker.java,v 1.8 2002/06/15 16:05:31 ruff Exp $
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
import org.xmlBlaster.engine.mime.I_PublishFilter;
import org.xmlBlaster.engine.helper.Constants;
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
 * @author ruff@swand.lake.de
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
    * This method is called by the PluginManager. 
    * <p />
    * This xmlBlaster.properties example
    * <pre>
    *   MimePublishPlugin[PublishLenChecker][1.0]=org.xmlBlaster.engine.mime.demo.PublishLenChecker,DEFAULT_MAX_LEN=200
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
    * @param publisher The subject object describing the publisher
    * @param msgUnit The message to check
    * @return "" or "OK": The message is accepted<br />
    *         Any other string: The message is rejected and your string is passed back to the publisher.
    * @exception XmlBlasterException Is thrown on problems, for example if the MIME type
    *            does not fit to message content.<br />
    *            Take care throwing an exception, as the
    *            exception is routed back to the publisher.
    *            If the publish() had many messages (a MessageUnit[]), all other messages are lost
    *            as well.
    *            Probably it is best to return 'ERROR' instead and log the situation.
    */
   public String intercept(SubjectInfo publisher, MessageUnitWrapper msgUnitWrapper) throws XmlBlasterException {
      if (msgUnitWrapper == null) {
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "Illegal argument in intercept() call - msgUnitWrapper is null");
      }

      if (msgUnitWrapper.getXmlKey().isInternalMsg())
         return "";  // ignore internal messages

      MessageUnit msgUnit = msgUnitWrapper.getMessageUnit();

      try {
         long maxLen = DEFAULT_MAX_LEN;  // Use default max length

         if (msgUnit.getContent().length == THROW_EXCEPTION_FOR_LEN) {
            log.info(ME, "Test what happens if we throw an exception");
            throw new XmlBlasterException(ME, "Test what happens if we throw an exception");
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
         throw new XmlBlasterException(ME, tmp);
      }
   }

   public void shutdown() {
   }
}

