/*------------------------------------------------------------------------------
Name:      DropIfNotDeliverableUniqueOnly.java
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
import org.xmlBlaster.util.dispatch.I_DispatchManager;
import org.xmlBlaster.util.dispatch.I_ConnectionStatusListener;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * <p>
 * Sometimes we need to support this subscribe pattern:
 * A client only wants the newest message with this details:
 * <pre>
 * - Initial connect and subscribe: send current newest (or given history depth)
 * - If it is online it is a normal subscription
 * - If it is offline don't add messages to callback queue
 * - If it comes online again check if current message was delivered,
 *   if not send it initially (if redeliverNewestOnReconnect==false which is default; else on reconnect the newest is always send. Note: You can't use both variants simultaneously as it operates on the session Map)
 * - Setting useMd5Sum=true will check of content of message was delivered already (checks latest message in history queue only)  
 * </pre>
 * <p>
 * If a client callback goes to polling, this plugin removes all existing subscriptions.
 * The client needs to add a filter Qos to its SubscribeQos to activate the plugin.<br />
 * and needs to re-subscribe on relogin if desired. 
 * </p>
 * <p>
 * The message uniqueness is checked per topic, but if you pass a client property "_uniqueGroupId"
 * this is used to check uniqueness: Messages with the same "_uniqueGroupId" value are
 * compared to decide if delivered already. This is like a sub-group of the topic
 * and is for your convenience only as it could be solved by having a separate topic for each "_uniqueGroupId".
 * </p>
 * <p>This makes only sense if the client is operating in fail save mode (-dispatch/callback/retries -1) and 
 * reconnects with the same publicSessionId > 0 (-session.name jack/session/1) so that its session is found again.
 * </p> 
 * <p>
 * Configuration example (put to xmlBlaster.properties):
 * </p>
 * <pre>
 * MimeAccessPlugin[DropIfNotDeliverableUniqueOnly][1.0]=\
 *   org.xmlBlaster.contrib.mime.DropIfNotDeliverableUniqueOnly,dropper.types=*,uniqueGroupIdKeyName=_myuniqueGroupId,useMd5Sum=true
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
   &lt;filter type='DropIfNotDeliverableUniqueOnly'>
    &lt;![CDATA[_]]>
   &lt;/filter>
 *&lt;/qos>
 *  </pre>
 *  <p>The query statement '_' is ignored by this plugin.</p>
 *  
 *  <p>This plugin is a singleton and loaded once only for multiple subscribers configuring it</p>
 *  
 *  <p>Example to try on command line</p>
 *  <pre>
 *  export CLASSPATH=$XMLBLASTER_HOME/lib/xmlBlaster.jar
 *  
 *  Start the server:
 *    java org.xmlBlaster.Main
 * 
 *  Start a sniffer GUI to see what happens:
 *    cd ~/xmlBlaster
 *    build SimpleReader
 *    
 *  Start the subscriber which wants the newest and unique message only:
 *    java javaclients.HelloWorldSubscribe -session.name subscriber/session/1 -disconnect false -multiSubscribe false -initialUpdate true -unSubscribe false -dispatch/callback/retries -1 -filter.type DropIfNotDeliverableUniqueOnly -filter.query _
 *    (Avoid disconnect as it cleans up the session)
 *    (example to get first ten of history: -historyNumUpdates 10 -historyNewestFirst false)
 *  
 *  Start the publisher for two groupIds:
 *    java javaclients.HelloWorldPublish -clientProperty[_uniqueGroupId] weather.south
 *
 * @author Marcel Ruff
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/mime.plugin.accessfilter.html">The mime.plugin.accessfilter requirement</a>
 */
public class DropIfNotDeliverableUniqueOnly implements I_Plugin, I_AccessFilter, I_ConnectionStatusListener {
   private final String ME = "DropIfNotDeliverableUniqueOnly";
   private Global glob;
   private static Logger log = Logger.getLogger(DropIfNotDeliverableUniqueOnly.class.getName());
   private String [] mimeTypes;
   private PluginInfo pluginInfo;
   public static final String MIME_TYPES = "dropper.types";
   private String uniqueGroupIdKeyName = "_uniqueGroupId";
   private boolean redeliverNewestOnReconnect;
   private boolean useMd5Sum;

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
      // the client property key defaults to "_uniqueGroupId"
      this.uniqueGroupIdKeyName = prop.getProperty("uniqueGroupIdKeyName", this.uniqueGroupIdKeyName);
      this.redeliverNewestOnReconnect = Boolean.valueOf(prop.getProperty("redeliverNewestOnReconnect", ""+this.redeliverNewestOnReconnect));
      this.useMd5Sum = Boolean.valueOf(prop.getProperty("useMd5Sum", ""+this.useMd5Sum));
      log.info("Plugin " + getType() + " " + getVersion() + " loaded for mimeTypes '" + someMimeTypes + "' uniqueGroupIdKeyName=" + this.uniqueGroupIdKeyName + " redeliverNewestOnReconnect=" + this.redeliverNewestOnReconnect + " useMd5Sum=" + this.useMd5Sum);
   }

   /**
    * Return plugin type for Plugin loader
    * @return "DropIfNotDeliverableUniqueOnly"
    */
   public String getType() {
      return (this.pluginInfo==null) ? "DropIfNotDeliverableUniqueOnly" : this.pluginInfo.getType();
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
    * @return "DropIfNotDeliverableUniqueOnly"
    */
   public String getName() {
      return getType(); //"DropIfNotDeliverableUniqueOnly";
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
      I_DispatchManager dm = receiver.getDispatchManager();
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
         
         {
            // Check if message instance was delivered already to callback queue
            String topicId = msgUnit.getKeyOid();
            long timestampMillisCurr = msgUnit.getQosData().getRcvTimestamp().getMillis();
            // groupId is only check for newest, use &lt;history numEntries='10' newestFirst='false'/> to scan old messages as well
            String groupId = msgUnit.getQosData().getClientProperty(this.uniqueGroupIdKeyName, topicId);
            String key = getType() + ":" + groupId;// "DropIfNotDeliverableUniqueOnly:myTopic"
            synchronized (receiver.getUserObjectMap()) {
               Long timestampPrevious = (Long)receiver.getUserObject(key, null);
               if (timestampPrevious != null) {
                  if (timestampMillisCurr <= timestampPrevious) {
                     log.info("Message topicId=" + topicId + " received=" + msgUnit.getQosData().getRcvTimestamp().toString() + " " + this.uniqueGroupIdKeyName+ "=" + groupId + " is delivered already to client " + receiver.getSessionName().getRelativeName() + ", not putting it to callback queue");
                     return false;
                  }
               }
               receiver.setUserObject(key, timestampMillisCurr);
            }
         }
         
         if (this.useMd5Sum) {
            try {
               String md5sum = Constants.md5sum(msgUnit.getContent());
               //log.info("DEBUG ONLY useMd5Sum=" + md5sum + ": " + msgUnit.getContentStr());
               //String key = getType() + ":md5sum";// "DropIfNotDeliverableUniqueOnly:md5sum"
               // "DropIfNotDeliverableUniqueOnly:md5sum:company.labk.PoiAttribConfigList.lukas_PgmGeraete"
               String key = getType() + ":md5sum:" + msgUnit.getKeyOid();
               synchronized (receiver.getUserObjectMap()) {
                   String md5sumPrevious = (String)receiver.getUserObject(key, null);
                   if (md5sumPrevious != null && md5sum.equals(md5sumPrevious)) {
                      log.info("Message topicId=" + msgUnit.getKeyOid() + " received md5sum=" + md5sum + " is delivered already to client " + receiver.getSessionName().getRelativeName() + ", not putting it to callback queue");
                      return false;
                   }
                   receiver.setUserObject(key, md5sum);
               }
            }
            catch (XmlBlasterException e) {
               e.printStackTrace();
            }
         }
         /* Does not work as the SubscriptionInfo disappears on unSubscribe
         ClientSubscriptions mgr = ((ServerScope)glob).getRequestBroker().getClientSubscriptions();
         boolean exactOnly = true;
         Vector<SubscriptionInfo> subs = mgr.getSubscriptionByOid(receiver, topicId, exactOnly);
         for (SubscriptionInfo sub: subs) {
            String key = getType() + ":" + groupId;// "DropIfNotDeliverableUniqueOnly:myTopic"
            synchronized (sub) {
               Long timestampPrevious = (Long)sub.getUserObject(key, null);
               if (timestampPrevious != null) {
                  if (timestampMillisCurr <= timestampPrevious) {
                     log.info("Message topicId=" + topicId + " " + this.uniqueGroupIdKeyName+ "=" + groupId + " is delivered already to client " + receiver.getSessionName().getRelativeName() + ", not putting it to callback queue");
                     return false;
                  }
               }
               sub.setUserObject(key, timestampMillisCurr);
            }
         }
         */

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
   public void toAlive(I_DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      if (log.isLoggable(Level.FINE))
         log.fine("toAlive");
      //try {
      //   dispatchManager.getQueue().clear();
      //} catch (Throwable e) {
      //   e.printStackTrace();
      //}
   }

   public void toAliveSync(I_DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      if (log.isLoggable(Level.FINE))
         log.fine("toAliveSync");
   }
   
   // @see org.xmlBlaster.util.dispatch.I_ConnectionStatusListener#toDead(org.xmlBlaster.util.dispatch.DispatchManager, org.xmlBlaster.util.dispatch.ConnectionStateEnum, java.lang.String)
   public void toDead(I_DispatchManager dispatchManager, ConnectionStateEnum oldState, XmlBlasterException xmlBlasterException) {
      if (log.isLoggable(Level.FINE))
         log.fine("toDead");
   }

   // @see org.xmlBlaster.util.dispatch.I_ConnectionStatusListener#toPolling(org.xmlBlaster.util.dispatch.DispatchManager, org.xmlBlaster.util.dispatch.ConnectionStateEnum)
   public void toPolling(I_DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      try {
         I_AdminSession receiver = ((ServerScope)glob).getAuthenticate().getSubjectInfoByName(dispatchManager.getSessionName()).getSessionByPubSessionId(dispatchManager.getSessionName().getPublicSessionId());
         String[] subIds = receiver.getRootSubscriptions(); // receiver.getSubscriptions();
         if (log.isLoggable(Level.FINE))
            log.fine(receiver.getLoginName() + "/" + receiver.getPublicSessionId() + " toPolling, removing " + subIds.length + " subscriptions");
         for (int i=0; i<subIds.length; i++) {
        	 String subId = subIds[i];
        	// __subId:marcelruff-XPATH1306100581978000000 (and its childs like __subId:marcelruff-XPATH1306100866146000000:1306100866148000000')
            // __subId:marcelruff-1306100582129000000
        	 if (subId.startsWith("__subId")) {
                 receiver.unSubscribe(subId, null);
        	 }
        	 else {
        		// SUBSCRIPTIONID_URL_PREFIX=subscriptionId is optional, it is stripped internally
                receiver.unSubscribe(Constants.SUBSCRIPTIONID_URL_PREFIX+subId, null);
        	 }
         }
         if (this.redeliverNewestOnReconnect) {
        	 receiver.getUserObjectMap().clear();
         }
      } catch (Throwable e) {
         e.printStackTrace();
      }
   }
}
