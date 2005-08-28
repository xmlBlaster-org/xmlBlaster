/*------------------------------------------------------------------------------
Name:      Example.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter;
import java.util.logging.Logger;
import java.util.logging.LogManager;
import java.util.prefs.Preferences;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.Info;

/**
 * Example code to run DbWriter as a standalone application. 
 * <p>
 * You can edit this file and change the configuration settings.
 * </p>
 * <p>
 * Simple usage example:
 * </p>
 * <p>
 * <tt>java org.xmlBlaster.contrib.dbwriter.Example -db.password secret</tt>
 * </p>
 * <p>
 * <tt>java -Djava.util.logging.config.file=testlog.properties org.xmlBlaster.contrib.dbwriter.Example -db.password secret</tt>
 * </p>
 * java -Djdbc.drivers=com.microsoft.sqlserver.jdbc.SQLServerDriver -Ddb.url=jdb
c:sqlserver://localhost:1433/database=xmlBlaster -Ddb.user=xmlblast -Ddb.password=secret org.xmlBlaster.contrib.dbwriter.DbWriterExample -pro
tocol SOCKET -plugin/socket/hostname laghi
 * @author Marcel Ruff
 */
public class DbWriterExample {
   private static Logger log = Logger.getLogger(DbWriterExample.class.getName());


   /**
    * Example for polling the DB
    * @param prefs Configuration
    * @throws Exception Can be of any type 
    */
   private void pollingExample(Preferences prefs) throws Exception {
      log.info("Start polling test");
      
      I_Info info = new Info(prefs);
      DbWriter processor = new DbWriter(info);
      
      long sleep = info.getLong("example.sleep", 0L);
      if (sleep > 0) {
         log.info("Sleeping for '" + sleep / 1000 + " seconds");
         Thread.sleep(sleep);
      }
      else {
         log.info("Sleeping forever");
         while (true) {
            Thread.sleep(5000L);
         }
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
         
         DbWriterExample example = new DbWriterExample();
         example.pollingExample(prefs);
      }
      catch (Throwable e) {
         System.err.println("SEVERE: " + e.toString());
         e.printStackTrace();
      }
   }

   
   private static void setPref(String key, String defVal, Preferences prefs) {
      String prop = System.getProperty(key, defVal);
      if (prop != null)
         prefs.put(key, prop);
      else
         prefs.put(key, defVal);
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
         // String dbUrl = System.getProperty("db.url", "jdbc:oracle:thin:@localhost:1521:orcl");
         String dbUrl = System.getProperty("db.url", "jdbc:postgresql:test//localhost/test");
         // String dbUser = System.getProperty("db.user", "system");
         String dbUser = System.getProperty("db.user", "postgres");
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

         // ----- Other DbWriter settings -----
         // prefs.put("mom.subscribeKey", "<key oid='' queryType='XPATH'>//key</key>");
         String subscribeKey = System.getProperty("mom.subscribeKey", "<key oid='transaction'/>");
         prefs.put("mom.subscribeKey", subscribeKey);
         
         prefs.put("mom.subscribeQos", "<qos><initialUpdate>false</initialUpdate><multiSubscribe>false</multiSubscribe><persistent>true</persistent></qos>");
         
         
         prefs.put("storer.class", "org.xmlBlaster.contrib.replication.ReplicationStorer");
         
         setPref("example.sleep", "0L", prefs);
         // setPref("pingInterval", "10000L", prefs);
         // setPref("retries", ".-1", prefs);
         // setPref("delay", ".4000", prefs);
         setPref("dispatch/callback/retries", ".-1", prefs);
         setPref("dispatch/callback/delay", ".10000", prefs);
         setPref("queue/callback/maxEntries", "10000", prefs);
         
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
      catch (Throwable e) {
         e.printStackTrace();
         log.severe("Problems: " + e.toString());
      }
      return null;
   }
}
