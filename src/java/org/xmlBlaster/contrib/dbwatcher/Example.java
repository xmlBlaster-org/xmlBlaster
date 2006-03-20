package org.xmlBlaster.contrib.dbwatcher;

import java.util.logging.Logger;
import java.util.logging.LogManager;
import java.util.prefs.Preferences;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.Info;

/**
 * Example code to run DbWatcher as a standalone application. 
 * <p>
 * You can edit this file, change the configuration settings to
 * your polling problem and play interactively with it.
 * </p>
 * <p>
 * Simple usage example:
 * </p>
 * <p>
 * <tt>java org.xmlBlaster.contrib.dbwatcher.Example -db.password secret</tt>
 * </p>
 * <p>
 * <tt>java -Djava.util.logging.config.file=testlog.properties org.xmlBlaster.contrib.dbwatcher.Example -alertScheduler.pollInterval 10000 -db.password secret</tt>
 * </p>
 * @author Marcel Ruff
 */
public class Example {
   private static Logger log = Logger.getLogger(Example.class.getName());

   /**
    * Example for polling the DB
    * @param prefs Configuration
    * @throws Exception Can be of any type 
    */
   private void pollingExample(Preferences prefs) throws Exception {
      log.info("Start polling test");
      
      I_Info info = new Info(prefs);
      DbWatcher processor = new DbWatcher(info);
      processor.startAlertProducers();

      boolean interactive = info.getBoolean("interactive", true);
      if (interactive) {
         // Manually trigger db checking ...
         BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
         while (true) {
            System.out.print("Hit 'q' to exit, 't' to trigger an event > ");
            String line = in.readLine(); // Blocking in I/O
            if (line == null) continue;
            line = line.trim();
            if (line.toLowerCase().equals("q"))
               break;
            if (line.toLowerCase().equals("t"))
               processor.getChangeDetector().checkAgain(null);
         }
      }
      else {
         /*
         for (int i=0; i<10; i++) {
            processor.getChangeDetector().checkAgain(null);
            Thread.sleep(1000);
         }
         */
         log.info("Sleeping no a long time ...");
         Thread.sleep(1000000);
      }


      processor.shutdown();
      log.info("Done!");
   }

   /**
    * Example code. 
    * <p />
    * <tt>java -Djava.util.logging.config.file=testlog.properties org.xmlBlaster.contrib.dbwatcher.Example -alertScheduler.pollInterval 10000 -db.password secret</tt>
    * @param args Command line
    */
   public static void main(String[] args) {
      try {
         System.setProperty("java.util.logging.config.file", "testlog.properties");
         LogManager.getLogManager().readConfiguration();

         Preferences prefs = loadArgs(args);
         
         Example example = new Example();
         example.pollingExample(prefs);
      }
      catch (Throwable e) {
         System.err.println("SEVERE: " + e.toString()); 
      }
   }

   /**
    * Parse command line arguments
    * @param args Command line
    * @return Configuration
    */
   public static Preferences loadArgs(String[] args) {
      try {
         // user: See $HOME/.java/.userPrefs
         // root: See /opt/j2sdk1.4.2_06/jre/.systemPrefs/prefs.xml
         Preferences prefs = Preferences.userRoot();
         prefs.clear();

         // ---- Database settings -----
         String driverClass = System.getProperty("jdbc.drivers", "org.hsqldb.jdbcDriver:oracle.jdbc.driver.OracleDriver:com.microsoft.jdbc.sqlserver.SQLServerDriver:org.postgresql.Driver");
         String dbUrl = System.getProperty("db.url", "jdbc:oracle:thin:@localhost:1521:orcl");
         String dbUser = System.getProperty("db.user", "system");
         String dbPassword = System.getProperty("db.password", "");
      
         prefs.put("jdbc.drivers", driverClass);
         prefs.put("db.url", dbUrl);
         prefs.put("db.user", dbUser);
         prefs.put("db.password", dbPassword);

         // ---- Mom settings -----
         /*
         prefs.put("mom.connectQos", 
                     "<qos>" +
                     " <securityService type='htpasswd' version='1.0'>" +
                     "   <![CDATA[" + 
                     "   <user>michele</user>" +
                     "   <passwd>secret</passwd>" +
                     "   ]]>" +
                     " </securityService>" +
                     " <session name='joe/3'/>'" +
                     " <address type='SOCKET'>" +
                     "   socket://192.168.110.10:7607" +
                     " </address>" +
                     " </qos>");
         System.setProperty("protocol", "SOCKET");
         System.setProperty("protocol/socket/hostname", "192.168.110.10");
         */

         // ----- Other DbWatcher settings -----
         prefs.put("alertScheduler.pollInterval", "0"); // 0: No polling, is triggered for example by MoM
         prefs.put("changeDetector.groupColName", "CAR");
         prefs.put("changeDetector.detectStatement", "SELECT MAX(TO_CHAR(TS, 'YYYY-MM-DD HH24:MI:SSXFF')) from TEST_POLL");
         prefs.put("db.queryMeatStatement", "SELECT * FROM TEST_POLL WHERE TO_CHAR(TS, 'YYYY-MM-DD HH24:MI:SSXFF') > '${oldTimestamp}' ORDER BY CAR");
         prefs.put("mom.topicName", "db.change.${colGroupValue}");
         prefs.put("mom.alertSubscribeKey", "<key oid='db.notification'/>");
         prefs.put("changeDetector.class", "org.xmlBlaster.contrib.dbwatcher.detector.TimestampChangeDetector");

         for (int i=0; i<args.length-1; i++) {
            if (args[i].startsWith("-")) {
               prefs.put(args[i].substring(1), args[++i]);
            }
         }
         prefs.flush();

         // Log output:
         //prefs.exportSubtree(System.out);
         return prefs;
      }
      catch (Exception e) {
         log.severe("Problems: " + e.toString());
      }
      return null;
   }
}
