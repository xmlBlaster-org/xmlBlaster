/*------------------------------------------------------------------------------
Name:      DropIfNotDeliverable.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.mime;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.admin.I_AdminSession;
import org.xmlBlaster.engine.mime.I_AccessFilter;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.StringPairTokenizer;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.I_ConnectionStatusListener;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * Throw away callback messages and unsubscribe all subscriptions if a client
 * gows to polling.
 * <p>
 * If a client callback goes to polling, this plugin removes all existing subscriptions
 * and clears all messages in the callback queue.<br />
 * The client needs to add a filter Qos to its SubscribeQos to activate the plugin.<br /> 
 * In a next step we could add configuration parameters to only
 * remove specific subscriptions (and not remove other callback queue entries). 
 * </p> 
 * <p>
 * Configuration example (put to xmlBlaster.properties):
 * </p>
 * <pre>
 * MimeAccessPlugin[DropIfNotDeliverable][1.0]=\
 *   org.xmlBlaster.contrib.mime.DropIfNotDeliverable,dropper.types=*
 * </pre>
 * <tt>dropper.types=*</tt> is default and activates the plugin
 * for any published message where a subscriber has set this filter,<br />
 * <tt>dropper.types=text/xml;application/xml</tt> would for example limit the plugin to such
 * messages having his mime type declared on publish</tt>
 * <p />
 * A typical subscribeQos is for example:
 * <pre>
 *&lt;qos>
   &lt;multiSubscribe>false&lt;/multiSubscribe>
   &lt;local>false&lt;/local>
   &lt;initialUpdate>false&lt;/initialUpdate>
   &lt;updateOneway>false&lt;/updateOneway>
   &lt;notify>false&lt;/notify>
   &lt;persistent>false&lt;/persistent>
   &lt;filter type='DropIfNotDeliverable'>
    &lt;![CDATA[_]]>
   &lt;/filter>
 *&lt;/qos>
 *  </pre>
 *  <p>The query statement '_' is ignored by this plugin.</p>
 *  
 *  <p>This plugin is a singleton and loaded once only for multiple subscribers configuring it</p>
 *
 * @author Marcel Ruff
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/mime.plugin.accessfilter.html">The mime.plugin.accessfilter requirement</a>
 */
public class DropIfNotDeliverable implements I_Plugin, I_AccessFilter, I_ConnectionStatusListener {
   private final String ME = "DropIfNotDeliverable";
   private Global glob;
   private static Logger log = Logger.getLogger(DropIfNotDeliverable.class.getName());
   private String [] mimeTypes;
   private PluginInfo pluginInfo;
   public static final String MIME_TYPES = "dropper.types";

   /**
    * This is called after instantiation of the plugin 
    * @param glob The Global handle of this xmlBlaster server instance.
    */
   public void initialize(ServerScope serverScope) {
   }
   
   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.glob = glob;
      this.pluginInfo = pluginInfo;
      Properties prop = pluginInfo.getParameters();
      String someMimeTypes = prop.getProperty(MIME_TYPES, "*"); //"text/xml;image/svg+xml"
      this.mimeTypes = StringPairTokenizer.parseLine(someMimeTypes, ';');
      log.info("Plugin " + getType() + " " + getVersion() + " loaded for mimeTypes '" + someMimeTypes + "'");
   }

   /**
    * Return plugin type for Plugin loader
    * @return "DropIfNotDeliverable"
    */
   public String getType() {
      return (this.pluginInfo==null) ? "DropIfNotDeliverable" : this.pluginInfo.getType();
   }

   /**
    * Return plugin version for Plugin loader
    * @return "1.0"
    */
   public String getVersion() {
      return (this.pluginInfo==null) ? "1.0" : this.pluginInfo.getVersion();
   }

   /**
    * Get a human readable name of this filter implementation
    * @return "DropIfNotDeliverable"
    */
   public String getName() {
      return getType(); //"DropIfNotDeliverable";
   }

   /**
    * Get the content MIME type for which this plugin applies,
    * currently { "*" }.
    * Is configurable with
    * <tt>dropper.types=text/xml;image/svg+xml;application/xml</tt>
    * @return { "*" } This plugin handles all mime types
    */
   public String[] getMimeTypes() {
      return this.mimeTypes;
   }

   /**
    * Get the content MIME version number for which this plugin applies
    * @return "1.0" (this is the default version number)
    */
   public String[] getMimeExtended() {
      String[] mimeExtended = new String[this.mimeTypes.length];
      for (int i=0; i<mimeExtended.length; i++)
         mimeExtended[i] = Constants.DEFAULT_CONTENT_MIME_EXTENDED;
      return mimeExtended;
   }

   // @see org.xmlBlaster.engine.mime.I_AccessFilter#match(SessionInfo,MsgUnit,Query)
   public boolean match(SessionInfo receiver, MsgUnit msgUnit, Query query) throws XmlBlasterException {
      if (msgUnit == null) {
         Thread.dumpStack();
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal argument in match() call");
      }
      DispatchManager dm = receiver.getDispatchManager();
      dm.addConnectionStatusListener(this); // register for toAlive() and toPolling() events (multiple calls don't harm)
      try {
         if (dm.isPolling() || dm.isShutdown()) {
            String[] subIds = receiver.getSubscriptions();
            if (log.isLoggable(Level.FINE))
               log.fine(dm.getSessionName().getAbsoluteName() + " is not reachable, cleaning "
                     + subIds.length + " subscriptions and "
                     + dm.getQueue().getNumOfEntries() + " callbackQueue entries");
            for (int i=0; i<subIds.length; i++) {
               receiver.unSubscribe(Constants.SUBSCRIPTIONID_URL_PREFIX+subIds[i], null);
            }
            //Can cause deadlock between CacheQueueInterceptor.clear sync and TopicHandler sync (see from toPolling or toAlive below) 
            //dm.getQueue().clear();
            return false;
         }
         return true;
      }
      catch (Throwable e) {
         log.warning("Error filtering message for topic " + msgUnit.getKeyOid() + " and session "+receiver.getSessionName().toString()+": " + e.toString());
         e.printStackTrace();
         return true;
      }
   }
   
   public void shutdown() {
   }

   // @see org.xmlBlaster.util.dispatch.I_ConnectionStatusListener#toAlive(org.xmlBlaster.util.dispatch.DispatchManager, org.xmlBlaster.util.dispatch.ConnectionStateEnum)
   public void toAlive(DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      try {
         dispatchManager.getQueue().clear();
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }

   // @see org.xmlBlaster.util.dispatch.I_ConnectionStatusListener#toDead(org.xmlBlaster.util.dispatch.DispatchManager, org.xmlBlaster.util.dispatch.ConnectionStateEnum, java.lang.String)
   public void toDead(DispatchManager dispatchManager, ConnectionStateEnum oldState, XmlBlasterException xmlBlasterException) {
   }

   // @see org.xmlBlaster.util.dispatch.I_ConnectionStatusListener#toPolling(org.xmlBlaster.util.dispatch.DispatchManager, org.xmlBlaster.util.dispatch.ConnectionStateEnum)
   public void toPolling(DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      try {
         I_AdminSession receiver = ((ServerScope)glob).getAuthenticate().getSubjectInfoByName(dispatchManager.getSessionName()).getSessionByPubSessionId(dispatchManager.getSessionName().getPublicSessionId());
         String[] subIds = receiver.getSubscriptions();
         if (log.isLoggable(Level.FINE))
            log.fine(receiver.getLoginName() + "/" + receiver.getPublicSessionId() + " toPolling, removing " + subIds.length + " subscriptions");
         for (int i=0; i<subIds.length; i++) {
            receiver.unSubscribe(Constants.SUBSCRIPTIONID_URL_PREFIX+subIds[i], null);
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }
}
