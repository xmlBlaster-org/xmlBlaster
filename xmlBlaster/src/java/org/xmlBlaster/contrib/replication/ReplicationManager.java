/*------------------------------------------------------------------------------
Name:      ReplicationManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwatcher.Info;
import org.xmlBlaster.contrib.dbwriter.I_ContribPlugin;

public class ReplicationManager {

   private static Logger log = Logger.getLogger(ReplicationManager.class.getName());
   public final static String DB_POOL_KEY = "db.pool";
   
   private I_Info info;
   private I_DbPool dbPool;
   private boolean poolOwner;
   
   /** if true, then the initial bootstrap is done. You need admin rights on the database */
   private boolean bootstrap;
   
   private I_DbSpecific dbSpecific;
   
   /**
    * Default constructor, you need to call {@link #init} thereafter. 
    */
   public ReplicationManager() {
      // void
   }

   /**
    * Convenience constructor, creates a processor for changes, calls {@link #init}.  
    * @param info Configuration
    * @throws Exception Can be of any type
    */
   public ReplicationManager(I_Info info) throws Exception {
      init(info);
   }
   
   
   /**
    * 
    * @param filename
    * @param method
    * @return List of String[]
    * @throws Exception
    */
   public static List getContentFromClasspath(String filename, String method, String flushSeparator, String cmdSeparator) throws Exception {
      if (filename == null)
         throw new Exception(method + ": no filename specified");
      ArrayList list = new ArrayList();
      ArrayList internalList = new ArrayList();
      try {
         Enumeration enm = ReplicationManager.class.getClassLoader().getResources(filename);
         if(enm.hasMoreElements()) {
            URL url = (URL)enm.nextElement();
            log.fine(method + ": : loading file '" + url.getFile() + "'");
            try {
               StringBuffer buf = new StringBuffer();
               BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
               String line = null;
               while ( (line = reader.readLine()) != null) {
                  if (line.trim().startsWith(cmdSeparator)) {
                     String tmp =  buf.toString();
                     if (tmp.length() > 0)
                        internalList.add(tmp);
                     buf = new StringBuffer();
                  }
                  else if (line.trim().startsWith(flushSeparator)) {
                     if (buf.length() > 0)
                        internalList.add(buf.toString());
                     if (internalList.size() >0) {
                        String[] tmp = (String[])internalList.toArray(new String[internalList.size()]);
                        list.add(tmp);
                        internalList.clear();
                        buf = new StringBuffer();
                     }
                  }
                  else {
                     line = line.trim();
                     if (line.length() > 0 && !line.startsWith("--"))
                        buf.append(line).append("\n");
                  }
               }
               String end = buf.toString().trim();
               if (end.length() > 0)
                  internalList.add(end);
               if (internalList.size() >0) {
                  String[] tmp = (String[])internalList.toArray(new String[internalList.size()]);
                  list.add(tmp);
                  internalList.clear();
                  buf = null;
               }
               
            }
            catch(IOException ex) {
               log.warning("init: could not read properties from '" + url.getFile() + "' : " + ex.getMessage());
            }

            while(enm.hasMoreElements()) {
               url = (URL)enm.nextElement();
               log.warning("init: an additional matching file has been found in the classpath at '"
                  + url.getFile() + "' please check that the correct one has been loaded (see info above)"
               );
            }
            return list;
         }
         else {
            ClassLoader cl = ReplicationManager.class.getClassLoader();
            StringBuffer buf = new StringBuffer();
            if (cl instanceof URLClassLoader) {
               URL[] urls = ((URLClassLoader)cl).getURLs();
               for (int i=0; i < urls.length; i++) 
                  buf.append(urls[i].toString()).append("\n");
            }
            throw new Exception("init: no file found with the name '" + filename + "' : " + (buf.length() > 0 ? " classpath: " + buf.toString() : ""));
         }
      }
      catch(IOException e) {
         throw new Exception("init: an IOException occured when trying to load property file '" + filename + "'", e);
      }
   }
   
   /**
    * @param info Configuration
    * @throws Exception Can be of any type
    */
   public void init(I_Info info) throws Exception {
      if (info == null) throw new IllegalArgumentException("Missing configuration, info is null");
      this.info = info;
      this.info.putObject("org.xmlBlaster.contrib.replication.ReplicationManager", this);
      
      ClassLoader cl = this.getClass().getClassLoader();
      this.dbPool = (I_DbPool)info.getObject("db.pool");
      if (this.dbPool == null) {
         String dbPoolClass = this.info.get("dbPool.class", "org.xmlBlaster.contrib.db.DbPool");
         if (dbPoolClass.length() > 0) {
             this.dbPool = (I_DbPool)cl.loadClass(dbPoolClass).newInstance();
             this.dbPool.init(info);
             if (log.isLoggable(Level.FINE)) log.fine(dbPoolClass + " created and initialized");
         }
         else
            throw new IllegalArgumentException("Couldn't initialize I_DbPool, please configure 'dbPool.class' to provide a valid JDBC access.");
         this.poolOwner = true;
         this.info.putObject(DB_POOL_KEY, this.dbPool);
      }
      
      String dbSpecificClass = this.info.get("replication.dbSpecific.class", "org.xmlBlaster.contrib.replication.impl.SpecificDefault").trim();
   
      if (dbSpecificClass.length() > 0) {
          this.dbSpecific = (I_DbSpecific)cl.loadClass(dbSpecificClass).newInstance();
          this.dbSpecific.init(info);
          if (log.isLoggable(Level.FINE)) 
             log.fine(dbSpecificClass + " created and initialized");
      }
      else
         log.info("Couldn't initialize I_Parser, please configure 'parser.class' if you need a conversion.");
      
      
      
      
      
      boolean doBootstrap = this.info.getBoolean("replication.bootstrap", false);
      if (doBootstrap) {
         log.info("init: bootstrapping now ...");
         bootstrap();
         log.info("init: bootstrapping completed ready to initiate replication");
      }
   }

   private void bootstrap() throws Exception {
      
      Connection conn = null;
      try {
         // check the functions which are already defined ...
         conn = this.dbPool.reserve();
         conn.clearWarnings();
         conn.setAutoCommit(true);
         boolean force = false;
         boolean doWarn = true;
         this.dbSpecific.bootstrap(conn, doWarn, force);
         // TODO remove this after testing ...
         // this.dbSpecific.addTable(conn);
      }
      finally {
         if (conn != null) {
            this.dbPool.release(conn);
         }
      }
      
   }
   
   
   
   
   
   
   
   
   
   
   
   
   
   private void shutdown(I_ContribPlugin plugin) {
      try { 
         if (plugin != null) 
            plugin.shutdown(); 
      } 
      catch(Throwable e) { 
         e.printStackTrace(); 
         log.warning(e.toString()); 
      }
   }
   
   /**
    * Cleanup resources.
    * @throws Exception Can be of any type 
    */
   public void shutdown() throws Exception {
      if (this.poolOwner && this.dbPool != null) {
         this.dbSpecific.shutdown();
         this.dbSpecific = null;
         this.dbPool.shutdown();
         this.dbPool = null;
         this.info.putObject("db.pool", null);
      }
   }

   
   
   
   
   // THESE ARE ONLY NEEDED FOR TESTING
   
   /**
    * Parse command line arguments
    * @param args Command line
    * @return Configuration
    */
   public static Preferences loadArgs(String[] args) {
      try {
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
         prefs.put("replication.bootstrap", "true");
         
         for (int i=0; i<args.length-1; i++) {
            if (args[i].startsWith("-")) {
               prefs.put(args[i].substring(1), args[++i]);
            }
         }
         prefs.flush();
         return prefs;
      }
      catch (Throwable e) {
         e.printStackTrace();
         log.severe("Problems: " + e.toString());
      }
      return null;
   }
   
   /**
    * Example code. 
    * <p />
    * <tt>java -Djava.util.logging.config.file=testlog.properties org.xmlBlaster.contrib.replication.ReplicationManager -db.password secret</tt>
    * @param args Command line
    */
   public static void main(String[] args) {
      try {
         System.setProperty("java.util.logging.config.file", "testlog.properties");
         LogManager.getLogManager().readConfiguration();

         Preferences prefs = loadArgs(args);
         
         ReplicationManager manager = new ReplicationManager();
         I_Info info = new Info(prefs);
         manager.init(info);
         /*
         I_DbPool pool = (I_DbPool)info.getObject("db.pool");
         Connection conn = pool.reserve();
         
         int nmax = 50;
         long t0 = System.currentTimeMillis();
         
         for (int i=0; i < nmax; i++) {
            Statement st = conn.createStatement();
            st.executeQuery("SELECT * from pg_tables");
            st.close();
            
         }
         long t1 = System.currentTimeMillis();
         System.out.println("ms per request (without close): '" + ((t1-t0)/nmax) + "'");
         t0 = System.currentTimeMillis();
         for (int i=0; i < 50; i++) {
            Statement st = conn.createStatement();
            st.executeQuery("SELECT * from pg_tables");
            st.close();
            conn.close();
         }
         t1 = System.currentTimeMillis();
         System.out.println("ms per request (with close): '" + ((t1-t0)/nmax) + "'");
         */
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
   
   
}
