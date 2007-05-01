package org.xmlBlaster.test.classtest.queue;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.queuemsg.MsgQueueConnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueSubscribeEntry;
import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.qos.PublishQosServer;
import org.xmlBlaster.engine.queuemsg.MsgQueueHistoryEntry;
import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.queue.StorageId;

/**
 * Helper to find out size of Queue entries. 
 * Use it together with a profiling tool.
 * @see org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry
 * @see org.xmlBlaster.engine.queuemsg.MsgQueueHistoryEntry
 * @see org.xmlBlaster.engine.MsgUnitWrapper
 */
public class EntrySize {
   protected ServerScope glob;
   private static Logger log = Logger.getLogger(EntrySize.class.getName());

   public EntrySize(ServerScope glob) {
      this.glob = glob;

      String testcase = this.glob.getProperty().get("testcase", "subscribe");
      if ("update".equalsIgnoreCase(testcase)) {
         updateEntry();
      }
      else if ("history".equalsIgnoreCase(testcase)) {
         historyEntry();
      }
      else if ("msgUnitWrapper".equalsIgnoreCase(testcase)) {
         msgUnitWrapperEntry();
      }
      else if ("publish".equalsIgnoreCase(testcase)) {
         publishEntry();
      }
      else if ("subscribe".equalsIgnoreCase(testcase)) {
         subscribeEntry();
      }
      else if ("connect".equalsIgnoreCase(testcase)) {
         connectEntry();
      }
      else {
         log.severe("Unkonwn testcase '" + testcase + "' please provide e.g. 'publish'");
      }
   }

   /**
    * Sun JDK 1.4.1 b19 / Linux 2.4.19: 179 bytes/entry
    * IBM JDK 1.3.1 / Linux 2.4.19    : 172 bytes/entry
    * Created objects:
    * - Timestamp
    * - MsgQueueUpdateEntry
    */
   public void updateEntry() {
      log.info("************ Starting updateEntry " + System.getProperty("java.vm.vendor") + ": " + System.getProperty("java.vm.version"));
      StorageId storageId = new StorageId(Constants.RELATING_CALLBACK, "updateEntry");
      try {
         SessionName receiver = new SessionName(glob, "receiver1");
         PublishQosServer publishQosServer = new PublishQosServer(glob, "<qos/>");
         MsgUnit msgUnit = new MsgUnit(glob, "<key oid='XX'/>", new byte[0], publishQosServer.toXml());
         org.xmlBlaster.engine.ServerScope global = new org.xmlBlaster.engine.ServerScope();
         MsgUnitWrapper msgWrapper = new MsgUnitWrapper(glob, msgUnit, storageId);

         int step = 1000;
         int numCreate = 1000000;
         MsgQueueUpdateEntry entryArr[] = new MsgQueueUpdateEntry[numCreate];
         for(int i=0; i<numCreate; i++) {
            entryArr[i] = new MsgQueueUpdateEntry(global, msgWrapper, storageId,
                                             receiver, "__subId", false);
            MsgUnitWrapper w = entryArr[i].getMsgUnitWrapper();
            if ((i % step) == 0) {
               log.info("Overall created #" + i + ": Created " + step + " new MsgQueueUpdateEntry instances, hit a key to create more ...");
               try { System.in.read(); } catch(java.io.IOException e) {}
            }
            if (log.isLoggable(Level.FINE)) log.fine("Dump meat: " + w.toXml());
         }
         //log.info("entry=" + entryArr[0].toXml());
         //log.info("sizeInBytes=" + entryArr[0].getSizeInBytes() + " msgUnit.size()=" + msgUnit.size());
         //log.info("Created " + numCreate + " MsgQueueUpdateEntry instances, hit a key to create more ...");
         //try { System.in.read(); } catch(java.io.IOException e) {}
      }
      catch (XmlBlasterException ex) {          
         ex.printStackTrace();
         log.severe("exception occured : " + ex.getMessage());
      }
   }

   /**
    * Object creation RAM without payload:<br />
    * <pre>
    * Sun JDK 1.4.1 b19 / Linux 2.4.19: 176 bytes/entry
    * IBM JDK 1.3.1 / Linux 2.4.19    : 164 bytes/entry
    * Created objects:
    * - Timestamp
    * - MsgQueueUpdateEntry
    * </pre>
    */
   public void historyEntry() {
      log.info("************ Starting historyEntry " + System.getProperty("java.vm.vendor") + ": " + System.getProperty("java.vm.version"));
      StorageId storageId = new StorageId(Constants.RELATING_HISTORY, "historyEntry");
      try {
         PublishQosServer publishQosServer = new PublishQosServer(glob, "<qos/>");
         MsgUnit msgUnit = new MsgUnit(glob, "<key oid='XX'/>", new byte[0], publishQosServer.toXml());
         org.xmlBlaster.engine.ServerScope global = new org.xmlBlaster.engine.ServerScope();
         MsgUnitWrapper msgWrapper = new MsgUnitWrapper(glob, msgUnit, storageId);

         int step = 1000;
         int numCreate = 1000000;
         MsgQueueHistoryEntry entryArr[] = new MsgQueueHistoryEntry[numCreate];
         for(int i=0; i<numCreate; i++) {
            entryArr[i] = new MsgQueueHistoryEntry(global, msgWrapper, storageId);
            MsgUnitWrapper w = entryArr[i].getMsgUnitWrapper();
            if ((i % step) == 0) {
               log.info("Overall created #" + i + ": Created " + step + " new MsgQueueHistoryEntry instances, hit a key to create more ...");
               try { System.in.read(); } catch(java.io.IOException e) {}
            }
            if (log.isLoggable(Level.FINE)) log.fine("Dump meat: " + w.toXml());
         }
      }
      catch (XmlBlasterException ex) {          
         ex.printStackTrace();
         log.severe("exception occured : " + ex.getMessage());
      }
   }

   /**
    * Object creation RAM without payload (the payload here is additional 78 bytes):<br />
    * <pre>
    * Sun JDK 1.4.1 b19 / Linux 2.4.19: 3214 bytes/entry
    * IBM JDK 1.3.1 / Linux 2.4.19    : 2920 bytes/entry
    *
    * Estimated calculation of used memory by one MsgUnitWrapper instance
    * = Object memory + payload(=msgUnit.size())
    *
    * Where following objects need to be created:
    * 5 PropBoolean
    * 1 PropLong
    * 1 RcvTimestamp
    * 1 MsgQosData
    * 1 MsgKeyData
    * 1 MsgUnit
    * 1 MsgUnitWrapper
    * </pre>
    */
   public void msgUnitWrapperEntry() {
      log.info("************ Starting msgUnitWrapperEntry " + System.getProperty("java.vm.vendor") + ": " + System.getProperty("java.vm.version"));
      try {
         PublishQosServer publishQosServer = new PublishQosServer(glob, "<qos/>");
         StorageId storageId = new StorageId(Constants.RELATING_MSGUNITSTORE, "msgUnitWrapperEntry");

         int step = 1000;
         int numCreate = 1000000;
         MsgUnitWrapper entryArr[] = new MsgUnitWrapper[numCreate];
         log.info("Hit a key for new MsgUnitWrapper RAM size test ...");
         try { System.in.read(); } catch(java.io.IOException e) {}
         for(int i=0; i<numCreate; i++) {
            MsgUnit msgUnit = new MsgUnit(glob, "<key oid='XX'/>", new byte[0], publishQosServer.toXml());
            entryArr[i] = new MsgUnitWrapper(glob, msgUnit, storageId);
            if (i > 0 && (i % step) == 0) {
               log.info("Overall created #" + i + ": Created " + step + " new MsgUnitWrapper instances, msgUnitSize=" + msgUnit.size() + ", hit a key to create more ...");
               try { System.in.read(); } catch(java.io.IOException e) {}
            }
         }
      }
      catch (XmlBlasterException ex) {          
         ex.printStackTrace();
         log.severe("exception occured : " + ex.getMessage());
      }
   }

   /**
    * Object creation RAM without payload (the payload here is additional 23 bytes):<br />
    * <pre>
    * Sun JDK 1.4.1 b19 / Linux 2.4.19: 659 bytes/entry
    * IBM JDK 1.3.1 / Linux 2.4.19    : 627 bytes/entry
    *
    * Estimated calculation of used memory by one MsgUnitWrapper instance
    * = Object memory + payload(=msgUnit.size() here is 23 bytes)
    *
    * Where following objects need to be created:
    * 6 PropBoolean
    * 1 PropLong
    * 1 Timestamp
    * 1 MsgQosData
    * 1 MsgKeyData
    * 1 MsgUnit
    * 1 MsgQueuePublishEntry
    *
    * Performance: Most performance is consumed here for SAX parsing of key and qos
    * </pre>
    */
   public void publishEntry() {
      log.info("************ Starting publishEntry " + System.getProperty("java.vm.vendor") + ": " + System.getProperty("java.vm.version"));
      try {
         PublishQos publishQos = new PublishQos(glob);
         StorageId storageId = new StorageId(Constants.RELATING_CLIENT, "publishEntry");

         int step = 1000;
         int numCreate = 1000000;
         MsgQueuePublishEntry entryArr[] = new MsgQueuePublishEntry[numCreate];
         log.info("Hit a key for new MsgQueuePublishEntry RAM size test ...");
         try { System.in.read(); } catch(java.io.IOException e) {}
         for(int i=0; i<numCreate; i++) {
            MsgUnit msgUnit = new MsgUnit(glob, "<key oid='XX'/>", new byte[0], publishQos.toXml());
            entryArr[i] = new MsgQueuePublishEntry(glob, msgUnit, storageId);
            if (i > 0 && (i % step) == 0) {
               log.info("Overall created #" + i + ": Created " + step + " new MsgQueuePublishEntry instances, msgUnitSize=" + msgUnit.size() + ", hit a key to create more ...");
               try { System.in.read(); } catch(java.io.IOException e) {}
            }
            if (log.isLoggable(Level.FINE)) log.fine("Dump publish meat: " + entryArr[i].toXml());
         }
      }
      catch (XmlBlasterException ex) {          
         ex.printStackTrace();
         log.severe("exception occured : " + ex.getMessage());
      }
   }

   /**
    * Object creation RAM without payload (the payload here is additional 23 bytes):<br />
    * <pre>
    * Sun JDK 1.4.1 b19 / Linux 2.4.19: 567 bytes/entry
    *
    * Where following objects need to be created:
    * 7 PropBoolean
    * 1 Timestamp
    * 1 QueryQosData
    * 1 SubscribeQos
    * 1 QueryKeyData
    * 1 SubscribeKey
    * 1 HistoryQos
    * 1 MsgQueueSubscribeEntry
    * </pre>
    */
   public void subscribeEntry() {
      log.info("************ Starting subscribeEntry " + System.getProperty("java.vm.vendor") + ": " + System.getProperty("java.vm.version"));
      try {
         StorageId storageId = new StorageId(Constants.RELATING_CLIENT, "subscribeEntry");

         int step = 1000;
         int numCreate = 1000000;
         MsgQueueSubscribeEntry entryArr[] = new MsgQueueSubscribeEntry[numCreate];
         log.info("Hit a key for new MsgQueueSubscribeEntry RAM size test ...");
         try { System.in.read(); } catch(java.io.IOException e) {}
         for(int i=0; i<numCreate; i++) {
            SubscribeKey sk = new SubscribeKey(glob, "XX");
            SubscribeQos sq = new SubscribeQos(glob);
            entryArr[i] = new MsgQueueSubscribeEntry(glob, storageId, sk.getData(), sq.getData());
            if (i > 0 && (i % step) == 0) {
               int loadSize = sk.toXml().length() + sq.toXml().length();
               log.info("Overall created #" + i + ": Created " + step + " new MsgQueueSubscribeEntry instances, key+qos size=" + loadSize + ", hit a key to create more ...");
               try { System.in.read(); } catch(java.io.IOException e) {}
            }
            if (log.isLoggable(Level.FINE)) log.fine("Dump meat: " + entryArr[i].toXml());
         }
      }
      catch (XmlBlasterException ex) {          
         ex.printStackTrace();
         log.severe("exception occured : " + ex.getMessage());
      }
   }

   /**
    * Object creation RAM of MsgQueueConnectEntry without payload (the payload here is additional 456 bytes):<br />
    * <pre>
    * Sun JDK 1.4.1 b19 / Linux 2.4.19: 2400 bytes/entry
    *
    * Almost everything is consumed by the expensive ConnectQos object creation.
    * 6 PropBoolean
    * 5 PropLong
    * 4 PropString
    * 1 NodeId
    * 1 SessionName
    * 1 SecurityQos
    * 1 ConnectQos
    * 1 SessionQos
    * 1 MsgQueueConnectEntry
    * 1 QueueProperty
    * 1 PropInt
    * 1 ConnectQosData
    * 1 Timestamp
    *
    * The toXml() size is 456 bytes
    * </pre>
    */
   public void connectEntry() {
      log.info("************ Starting connectEntry " + System.getProperty("java.vm.vendor") + ": " + System.getProperty("java.vm.version"));
      try {
         StorageId storageId = new StorageId(Constants.RELATING_CLIENT, "connectEntry");

         int step = 1000;
         int numCreate = 1000000;
         MsgQueueConnectEntry entryArr[] = new MsgQueueConnectEntry[numCreate];
         log.info("Hit a key for new MsgQueueConnectEntry RAM size test ...");
         try { System.in.read(); } catch(java.io.IOException e) {}
         for(int i=0; i<numCreate; i++) {
            ConnectQos connectQos = new ConnectQos(glob);
            entryArr[i] = new MsgQueueConnectEntry(glob, storageId, connectQos.getData());
            if (i > 0 && (i % step) == 0) {
               log.info("Overall created #" + i + ": Created " + step + " new MsgQueueConnectEntry instances, connectQosSize=" + connectQos.size() + ", hit a key to create more ...");
               try { System.in.read(); } catch(java.io.IOException e) {}
            }
            if (log.isLoggable(Level.FINE)) log.fine("Dump publis meat: " + entryArr[i].toXml());
         }
      }
      catch (XmlBlasterException ex) {          
         ex.printStackTrace();
         log.severe("exception occured : " + ex.getMessage());
      }
   }

    /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.queue.EntrySize
    * </pre>
    */
   public static void main(String args[]) {
      ServerScope glob = new ServerScope(args);
      new EntrySize(glob);
      //   long startTime = System.currentTimeMillis();
      //   long usedTime = System.currentTimeMillis() - startTime;
      //   testSub.log.info("time used for tests: " + usedTime/1000 + " seconds");
   }
}

