/*------------------------------------------------------------------------------
Name:      TestResultSetToXmlConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwriter;

import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.contrib.I_ChangePublisher;
import org.xmlBlaster.contrib.I_ContribPlugin;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;
import org.xmlBlaster.contrib.replication.ReplicationConstants;

/**
 * 
 * @author Michele Laghi mailto:laghi@swissinfo.org
 */
public class DbWriter implements I_Update {
   
   private static Logger log = Logger.getLogger(DbWriter.class.getName());
   public final static String DB_POOL_KEY = "db.pool";
   public final static String CASE_SENSITIVE_KEY = "dbWriter.caseSensitive";
   private I_Info info;
   private I_ChangePublisher eventEngine;
   private I_Parser parser;
   private I_Writer writer;
   private I_DbPool dbPool;
   private boolean poolOwner;
   private boolean isAlive;
   
   /**
    * Default constructor, you need to call {@link #init} thereafter. 
    */
   public DbWriter() {
      // void
   }

   /**
    * Convenience constructor, creates a processor for changes, calls {@link #init}.  
    * @param info Configuration
    * @throws Exception Can be of any type
    */
   public DbWriter(I_Info info) throws Exception {
      init(info);
   }
   
   /**
    * Creates a processor for changes. 
    * The alert producers need to be started later with a call to
    * {@link #startAlertProducers} 
    * @param info Configuration
    * @throws Exception Can be of any type
    */
   public synchronized void init(I_Info info) throws Exception {
      if (info == null) throw new IllegalArgumentException("Missing configuration, info is null");
      this.info = info;
      this.info.putObject("org.xmlBlaster.contrib.dbwriter.DbWriter", this);
      
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

      // Now we load all plugins to do the job
      String momClass = this.info.get("mom.class", "org.xmlBlaster.contrib.MomEventEngine").trim();
      String parserClass = this.info.get("parser.class", "org.xmlBlaster.contrib.dbwriter.DbUpdateParser").trim();
      String writerClass = this.info.get("dbWriter.writer.class", "org.xmlBlaster.contrib.dbwriter.Writer").trim();
   
      if (parserClass.length() > 0) {
          this.parser = (I_Parser)cl.loadClass(parserClass).newInstance();
          this.parser.init(info);
          if (log.isLoggable(Level.FINE)) log.fine(parserClass + " created and initialized");
      }
      else
         log.info("Couldn't initialize I_Parser, please configure 'parser.class' if you need a conversion.");
      
      if (writerClass.length() > 0) {
         this.writer = (I_Writer)cl.loadClass(writerClass).newInstance();
         this.writer.init(info);
          if (log.isLoggable(Level.FINE)) log.fine(writerClass + " created and initialized");
      }
      else
         log.severe("Couldn't initialize I_Storer, please configure 'dbWriter.writer.class'.");

      this.isAlive = true;
      if (momClass.length() > 0) {
         this.eventEngine = (I_ChangePublisher)cl.loadClass(momClass).newInstance();
         this.eventEngine.init(info);
         this.eventEngine.registerAlertListener(this, null);
         if (log.isLoggable(Level.FINE)) log.fine(momClass + " created and initialized");
      }
      else
         log.severe("Couldn't initialize I_EventEngine, please configure 'mom.class'.");
      
      if (log.isLoggable(Level.FINE)) log.fine("DbWriter created");
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
   public synchronized void shutdown() throws Exception {
      this.isAlive = false;
      // shutdown(this.eventEngine);
      shutdown(this.parser);
      shutdown(this.writer);
      if (this.poolOwner && this.dbPool != null) {
         this.dbPool.shutdown();
         this.dbPool = null;
         this.info.putObject("db.pool", null);
      }
   }

   /**
    * Determines wether the message is a database dump or not. If it is a database dump it stores the content to a file,
    * otherwise it processes the message according to the normal processing flow.
    */
   public synchronized void update(String topic, byte[] content, Map attrMap) throws Exception {
      if (!this.isAlive) {
         throw new Exception("update topic='" + topic + "' happens when we not alive: \n" + content);
      }
      String cmd = (String)attrMap.get(ReplicationConstants.DUMP_ACTION);
      if (cmd != null) {
         this.writer.update(topic, content, attrMap);
      }
      else {
         DbUpdateInfo updateInfo = this.parser.parse(new String(content));
         this.writer.store(updateInfo);
      }
   }
   
   
   
}
