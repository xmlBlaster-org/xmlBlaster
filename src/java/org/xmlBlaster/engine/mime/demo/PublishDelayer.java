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
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.mime.I_PublishFilter;
import org.xmlBlaster.engine.ServerScope;


/**
 * This demo plugin delays incoming (published) messages. 
 * You can also provide for example an exceptionErrorCode=internal.publish and
 * each message will throw such an exception (usually for testing).  
 * <p />
 * Please register this plugin in xmlBlaster.properties
 * <pre>
 * MimePublishPlugin[TestDelayer][1.0]=org.xmlBlaster.engine.mime.demo.PublishDelayer,delayMillis=200,exceptionErrorCode=,filterKeyOid=
 * </pre>
 * Plugins must implement the I_Plugin interface to be loadable by the PluginManager
 * and must implement the I_PublishFilter interface to be usable as a filter.
 * @author xmlBlaster@marcelruff.info
 */
public class PublishDelayer implements I_Plugin, I_PublishFilter, PublishDelayerMBean
{
   private final String ME = "PublishDelayer";
   private Global glob;
   /** My JMX registration */
   private Object mbeanHandle;
   private ContextNode contextNode;
   private PluginInfo pluginConfig;
   private static Logger log = Logger.getLogger(PublishDelayer.class.getName());
   /** How long to delay an incoming publish message */
   private long delayMillis = 0;
   private String exceptionErrorCode = "";
   private String filterKeyOid = "";

   /**
    * This is called after instantiation of the plugin 
    * @param glob The global handle of this xmlBlaster server instance.
    */
   public void initialize(ServerScope serverScope) {
      log.info("Filter is initialized, we check all mime types if content is not too long");
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.glob = glob;
      this.pluginConfig = pluginInfo;

      java.util.Properties props = pluginInfo.getParameters();

      String lenStr = (String)props.get("delayMillis");
      if (lenStr != null) {
         delayMillis = (new Long(lenStr)).longValue();
         log.info("Setting delayMillis=" + delayMillis + " as configured in xmlBlaster.properties");
      }
      this.exceptionErrorCode = (String)props.getProperty("exceptionErrorCode", "");
      this.filterKeyOid = (String)props.getProperty("filterKeyOid", "");

      // For JMX instanceName may not contain ","
      this.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG,
            "PublishDelayer[" + getType() + "]", glob.getScopeContextNode());
      this.mbeanHandle = glob.registerMBean(this.contextNode, this);
   }

   /**
    * 
    * @see org.xmlBlaster.util.plugin.I_Plugin#getType()
    */
   public String getType() {
      if (this.pluginConfig != null)
         return this.pluginConfig.getType();
      return ME;
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#getVersion()
    */
   public String getVersion() {
      if (this.pluginConfig != null)
         return this.pluginConfig.getVersion();
      return "1.0";
   }

   /**
    * Get a human readable name of this filter implementation
    * @return "PublishDelayer"
    */
   public String getName() {
      return getType();
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
      
      String ec = this.exceptionErrorCode;
      if (ec != null && ec.length() > 0) {
         ErrorCode errorCode = ErrorCode.INTERNAL_PUBLISH;
         try {
            errorCode = ErrorCode.toErrorCode(ec);
         }
         catch (IllegalArgumentException e) {}
         log.warning("Throwing test exception '" + errorCode + "' for published message " + msgUnit.getKeyOid());
         throw new XmlBlasterException(glob, errorCode, getType());
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
      if (this.glob != null && this.mbeanHandle != null)
         this.glob.unregisterMBean(this.mbeanHandle);
      //this.isShutdown = true;
      log.fine(ME+": shutdown done");
   }

   public long getDelayMillis() {
      return delayMillis;
   }

   public void setDelayMillis(long delayMillis) {
      this.delayMillis = delayMillis;
   }

   public String getExceptionErrorCode() {
      return exceptionErrorCode;
   }

   /**
    * By setting an errorCode String != "" the published message is rejected
    * with the given exception type.
    * Typically used for testing.
    * @param exceptionErrorCode e.g. "internal.publish" to be thrown
    * @see org.xmlBlaster.util.def.ErrorCode 
    */
   public void setExceptionErrorCode(String exceptionErrorCode) {
      this.exceptionErrorCode = exceptionErrorCode;
   }

   public String getFilterKeyOid() {
      return filterKeyOid;
   }

   /**
    * By setting a topicId != "" the plugin is only applied for the given messages oid.
    * If set to "" all messages are checked. 
    * @param filterKeyOid e.g. "Hello" to only slow down "Hello" messages, others are ignored
    * @see org.xmlBlaster.util.def.ErrorCode 
    */
   public void setFilterKeyOid(String filterKeyOid) {
      this.filterKeyOid = filterKeyOid;
   }
}

