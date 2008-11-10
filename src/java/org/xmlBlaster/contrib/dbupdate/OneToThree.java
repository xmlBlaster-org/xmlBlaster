package org.xmlBlaster.contrib.dbupdate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.queuemsg.ReferenceEntry;
import org.xmlBlaster.engine.queuemsg.ServerEntryFactory;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_EntryFilter;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queue.I_Storage;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.jdbc.JdbcConnectionPool;
import org.xmlBlaster.util.queue.jdbc.JdbcManagerCommonTable;
import org.xmlBlaster.util.queue.jdbc.JdbcQueue;

public class OneToThree {
   private static Logger log = Logger.getLogger(OneToThree.class.getName());
   private ServerScope globOne;
   private File to_file;
   private FileOutputStream out_;
   private Map jdbcQueueMap = new TreeMap();

   public OneToThree(ServerScope glob) throws XmlBlasterException {
      this.globOne = glob;
      /*
       * String[] args = { "-QueuePlugin[JDBC][1.0]",
       * "org.xmlBlaster.util.queue.jdbc.JdbcQueueCommonTablePlugin",
       * "-StoragePlugin[JDBC][1.0]",
       * "org.xmlBlaster.util.queue.jdbc.JdbcQueueCommonTablePlugin" };
       * this.globOne.getProperty().addArgs2Props(args);
       */
   }

   public JdbcManagerCommonTable createInstance() throws Exception {
      ServerEntryFactory sf = new ServerEntryFactory();
      PluginInfo pluginInfoTmp = new PluginInfo(globOne, null, "JDBC", "1.0");
      PluginInfo pluginInfo = new PluginInfo(globOne, "JDBC",
            "org.xmlBlaster.util.queue.jdbc.JdbcQueueCommonTablePlugin", pluginInfoTmp.getParameters());
      JdbcConnectionPool pool = new JdbcConnectionPool();
      pool.initialize(globOne, pluginInfo.getParameters());

      JdbcManagerCommonTable manager = new JdbcManagerCommonTable(pool, sf, "cleaner", null);
      pool.registerStorageProblemListener(manager);
      manager.setUp();
      return manager;
   }

   public void transform() throws Exception {
      final JdbcManagerCommonTable manager = createInstance();
      String queueNamePattern = Constants.RELATING_CALLBACK + "%";
      String flag = "UPDATE_REF";
      manager.getEntriesLike(queueNamePattern, flag, -1, -1, new I_EntryFilter() {
         public I_Entry intercept(I_Entry ent, I_Storage storage) {
            try {
               if (ent instanceof ReferenceEntry) {
                  ReferenceEntry refEntry = (ReferenceEntry) ent;
                  String queueName = "history:/node/heron/client/joe/-2";
                  JdbcQueue jdbcQueue = getJdbcQueue(queueName);
                  jdbcQueue.put((I_QueueEntry) ent, true);
               } else {
                  log.warning("Todo: other transforms");
               }
               return null; // Filter away so getAll returns nothing
            } catch (Throwable e) {
               log.warning("Ignoring during callback queue processing exception: " + e.toString());
               return null; // Filter away so getAll returns nothing
            }
         }
      });
   }

   public void createReportFile() throws Exception {
      String reportFileName = "OneToThree-report.xml";
      to_file = new File(reportFileName);
      if (to_file.getParent() != null) {
         to_file.getParentFile().mkdirs();
      }
      final FileOutputStream out = new FileOutputStream(to_file);
      out_ = out;
      out_.write(("XmlBlaster " + new Timestamp().toString()).getBytes());
      out_.write(("\n" + XmlBlasterException.createVersionInfo() + "\n").getBytes());

      log.info("Reporting check to '" + to_file.getAbsolutePath() + "'");
   }

   public void closeReportFile() {
      try {
         out_.close();
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public JdbcQueue getJdbcQueue(String oldQueueName) throws XmlBlasterException {
      String key = oldQueueName;
      JdbcQueue jdbcQueue = (JdbcQueue) jdbcQueueMap.get(key);
      if (jdbcQueue == null) {
         jdbcQueue = new JdbcQueue();
         StorageId uniqueQueueId = new StorageId(globOne, oldQueueName);
         jdbcQueue.initialize(uniqueQueueId, null);
         jdbcQueueMap.put(key, jdbcQueue);
      }
      return jdbcQueue;
   }

   // java org.xmlBlaster.contrib.dbupdate.OneToThree -cluster.node.id heron
   public static void main(String[] args) {
      OneToThree ott = null;
      try {
         ott = new OneToThree(new ServerScope(args));
         ott.createReportFile();
         ott.transform();
      } catch (Exception e) {
         e.printStackTrace();
         if (ott != null)
            ott.closeReportFile();
      }
   }
}
