package org.xmlBlaster.util.dispatch.plugins;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.xmlBlaster.engine.dispatch.ServerDispatchManager;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.dispatch.I_DispatchManager;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;

/**
 * A minimal message interceptor demo code which changes a message content just
 * before delivering it to a subscriber.
 * <p>
 *
 * Register this plugin on server side in <code>xmlBlaster.properties</code>
 * 
 * <pre>
 * DispatchPlugin[ContentManipulator][1.0]=org.xmlBlaster.util.dispatch.plugins.DispatchPluginContentManipulator,someTestContent=dummyContent
 * </pre>
 * 
 * Normally a client activates this plugin during connection in ConnectQos:
 * 
 * <pre>
 * <connect>
 * <qos> ... 
 *   <queue relating='callback'> 
 *   <callback ...
 *   dispatchPlugin='ContentManipulator,1.0'> ... </callback> </queue> 
 * </qos>
 * </connect>
 * </pre>
 * 
 * In case ALL clients shall use this plugin, add on server side to
 * <code>xmlBlaster.properties</code>:
 * 
 * <pre>
 *   DispatchPlugin/defaultPlugin=ContentManipulator,1.0
 * </pre>
 * 
 * Test example (run in four different shells):
 * 
 * <pre>
 * java org.xmlBlaster.Main
 * java javaclienst.HelloWorldSubscribe                                 (and hit key to subscribe)
 * java javaclients.HelloWorldSubscribe -session.name "Subscriber/2"    (and hit key to subscribe)
 * java javaclients.HelloWorldPublish                                   (and hit enter to publish next message)
 * </pre>
 * 
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/dispatch.plugin.priorizedDispatch.html
 */
public class DispatchPluginContentManipulator implements I_Plugin, I_MsgDispatchInterceptor {
   private static Logger log = Logger.getLogger(DispatchPluginContentManipulator.class.getName());
   public static final String PLUGIN_NAME = "ContentManipulator";
   private java.util.Properties pluginProperties;

   /**
    * @see I_Plugin#init(Global, PluginInfo)
    */
   @Override
   public void init(Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.pluginProperties = pluginInfo.getParameters();
      // someTestContent=dummyContent from above registration
      log.info(usage() + " properties are: " + this.pluginProperties);
   }

   /**
    * @see I_Plugin#getType()
    */
   @Override
   public String getType() {
      return PLUGIN_NAME;
   }

   /**
    * @see I_Plugin#getVersion()
    */
   @Override
   public String getVersion() {
      return "1.0";
   }

   /**
    * @see I_Plugin#shutdown()
    */
   @Override
   public void shutdown() throws XmlBlasterException {
      log.info("...");
   }

   /**
    * @see I_MsgDispatchInterceptor#toAlive(I_DispatchManager, ConnectionStateEnum)
    */
   @Override
   public void toAlive(I_DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      ServerDispatchManager mgr = (ServerDispatchManager) dispatchManager;
      log.info("... " + mgr.getSessionName());
   }

   /**
    * @see I_MsgDispatchInterceptor#
    */
   @Override
   public void toAliveSync(I_DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      log.info("...");
   }

   /**
    * @see I_MsgDispatchInterceptor#
    */
   @Override
   public void toPolling(I_DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      log.info("...");
   }

   /**
    * @see I_MsgDispatchInterceptor#
    */
   @Override
   public void toDead(I_DispatchManager dispatchManager, ConnectionStateEnum oldState,
         XmlBlasterException xmlBlasterException) {
      log.info("...");
   }

   /**
    * @see I_MsgDispatchInterceptor#initialize(Global, String)
    */
   @Override
   public void initialize(Global glob, String typeVersion) throws XmlBlasterException {
      log.info("I_MsgDispatchInterceptor succefully initialized " + typeVersion);
   }

   /**
    * @see I_MsgDispatchInterceptor#addDispatchManager(I_DispatchManager)
    */
   @Override
   public void addDispatchManager(I_DispatchManager dispatchManager) {
      ServerDispatchManager mgr = (ServerDispatchManager) dispatchManager;
      // /node/heron/client/joe/-1 callback:/node/heron/client/joe/-1
      log.info("... " + mgr.getSessionName() + " " + mgr.getQueue().getStorageId().getId());
   }

   /**
    * @see I_MsgDispatchInterceptor#doActivate(I_DispatchManager)
    */
   @Override
   public boolean doActivate(I_DispatchManager dispatchManager) {
      log.info("...");
      return true; // The DispatchManager knows what and why it does it
   }

   /**
    * @see I_MsgDispatchInterceptor#handleNextMessages(I_DispatchManager, List)
    */
   @Override
   public List<I_Entry> handleNextMessages(I_DispatchManager dispatchManager, List<I_Entry> pushEntries)
         throws XmlBlasterException {
      ServerDispatchManager mgr = (ServerDispatchManager) dispatchManager;

      // take messages from queue (none blocking), we take all messages with same
      // priority as a bulk ...
      List<I_Entry> entryList = dispatchManager.getQueue().peekSamePriority(-1, -1L);
      entryList = dispatchManager.prepareMsgsFromQueue(entryList);
      log.info("... " + mgr.getSessionName().getRelativeName() + " -> num messages=" + entryList.size());

      // manipulate entries
      for (I_Entry ientry : entryList) {
         MsgQueueEntry entry = (MsgQueueEntry) ientry;
         MsgUnit msgUnit = entry.getMsgUnit();
         String contentStr = msgUnit.getContentStr();
         log.info("Processing " + contentStr);
         String testContent = this.pluginProperties.getProperty("someTestContent");
         contentStr = "Manipulated " + testContent + " random=" + UUID.randomUUID(); // RandomGenerator.getDefault().nextInt(1,
                                                                                     // 101);
         msgUnit.setContentStr(contentStr);
      }

      return entryList;
   }

   /**
    * @see I_MsgDispatchInterceptor#postHandleNextMessages(I_DispatchManager,
    *      MsgUnit[])
    */
   @Override
   public void postHandleNextMessages(I_DispatchManager dispatchManager, MsgUnit[] processedEntries)
         throws XmlBlasterException {
      log.info("...");
   }

   /**
    * @see I_MsgDispatchInterceptor#shutdown()
    */
   @Override
   public void shutdown(I_DispatchManager dispatchManager) throws XmlBlasterException {
      log.info("...");
   }

   /**
    * @see I_MsgDispatchInterceptor#isShutdown()
    */
   @Override
   public boolean isShutdown() {
      log.info("...");
      return false;
   }

   /**
    * Register with xmlBlaster.properties
    * 
    * @return DispatchPlugin[ContentManipulator][1.0]=org.xmlBlaster.util.dispatch.plugins.DispatchPluginContentManipulator
    * @see I_MsgDispatchInterceptor#usage()
    */
   @Override
   public String usage() {
      return "DispatchPlugin[" + PLUGIN_NAME + "][1.0]=" + DispatchPluginContentManipulator.class.getName();
   }

   /**
    * @see I_MsgDispatchInterceptor#toXml(String)
    */
   @Override
   public String toXml(String extraOffset) {
      return "";
   }

   /**
    * @see I_MsgDispatchInterceptor#onDispatchWorkerException(I_DispatchManager,
    *      Throwable)
    */
   @Override
   public void onDispatchWorkerException(I_DispatchManager dispatchManager, Throwable ex) {
      log.info("...");
   }
}
