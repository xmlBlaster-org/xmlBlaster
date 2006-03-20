/*------------------------------------------------------------------------------
Name:      PublishDelayer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface hiding the real callback protocol
Version:   $Id: PublishDelayer.java 12936 2004-11-24 20:15:11Z ruff $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime.demo;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.mime.I_PublishFilter;
import org.xmlBlaster.engine.ServerScope;


/**
 * This demo plugin delays incoming (published) messages. 
 * <p />
 * Please register this plugin in xmlBlaster.properties:
 * <pre>
 * MimePublishPlugin[PublishDelayer][1.0]=org.xmlBlaster.engine.mime.demo.PublishDelayer,delayMillis=2000
 * </pre>
 * Plugins must implement the I_Plugin interface to be loadable by the PluginManager
 * and must implement the I_PublishFilter interface to be usable as a filter.
 * @author xmlBlaster@marcelruff.info
 */
public class PublishDelayer implements I_Plugin, I_PublishFilter
{
   private final String ME = "PublishDelayer";
   private ServerScope glob;
   private static Logger log = Logger.getLogger(PublishDelayer.class.getName());
   /** How long to delay an incoming publish message */
   private long delayMillis = 0;

   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(ServerScope glob) {
      this.glob = glob;

      log.info("Filter is initialized, we check all mime types if content is not too long");
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {


      java.util.Properties props = pluginInfo.getParameters();

      String lenStr = (String)props.get("delayMillis");
      if (lenStr != null) {
         delayMillis = (new Long(lenStr)).longValue();
         log.info("Setting delayMillis=" + delayMillis + " as configured in xmlBlaster.properties");
      }
   }

   /**
    * Return plugin type for Plugin loader
    * @return "PublishDelayer"
    */
   public String getType() {
      return "PublishDelayer";
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
    * @return "PublishDelayer"
    */
   public String getName() {
      return "PublishDelayer";
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
    * Delay the message for the configured amount of time. 
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
         if (delayMillis > 0) {
            Thread.sleep(delayMillis);
            log.info("Waking up after delaying message for " + delayMillis + " milli seconds");
         }
         return Constants.STATE_OK; // "OK" message is accepted
      }
      catch (Throwable e) {
         String tmp = "Can't delay message because of an unexpected problem: " + e.toString();
         log.severe(tmp);
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, tmp);
      }
   }

   public void shutdown() {
   }

}

