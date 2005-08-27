/*------------------------------------------------------------------------------
Name:      TestResultSetToXmlConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwriter;

import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;

/**
 * 
 * @author Michele Laghi mailto:laghi@swissinfo.org
 */
public class DbWriter implements I_EventHandler {
   
   private static Logger log = Logger.getLogger(DbWriter.class.getName());
   public final static String DB_POOL_KEY = "db.pool";
   
   private I_Info info;
   private I_ContribPlugin eventEngine;
   private I_Parser parser;
   private I_Storer storer;
   private I_DbPool dbPool;
   private boolean poolOwner;
   private int changeCount;
   private boolean isAlive;
   private int oldSequenceId = 0;
   
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
   public void init(I_Info info) throws Exception {
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
      String momClass = this.info.get("mom.class", "org.xmlBlaster.contrib.dbwriter.MomEventEngine").trim();
      String parserClass = this.info.get("parser.class", "org.xmlBlaster.contrib.dbwriter.DbUpdateParser").trim();
      String storerClass = this.info.get("storer.class", "org.xmlBlaster.contrib.dbwriter.Storer").trim();
   
      if (parserClass.length() > 0) {
          this.parser = (I_Parser)cl.loadClass(parserClass).newInstance();
          this.parser.init(info);
          if (log.isLoggable(Level.FINE)) log.fine(parserClass + " created and initialized");
      }
      else
         log.info("Couldn't initialize I_Parser, please configure 'parser.class' if you need a conversion.");
      
      if (storerClass.length() > 0) {
         this.storer = (I_Storer)cl.loadClass(storerClass).newInstance();
         this.storer.init(info);
          if (log.isLoggable(Level.FINE)) log.fine(storerClass + " created and initialized");
      }
      else
         log.severe("Couldn't initialize I_Storer, please configure 'storer.class'.");

      this.isAlive = true;
      if (momClass.length() > 0) {
         this.eventEngine = (I_ContribPlugin)cl.loadClass(momClass).newInstance();
         this.eventEngine.init(info);
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
   public void shutdown() throws Exception {
      this.isAlive = false;
      shutdown(this.eventEngine);
      shutdown(this.parser);
      shutdown(this.storer);
      if (this.poolOwner && this.dbPool != null) {
         this.dbPool.shutdown();
         this.dbPool = null;
         this.info.putObject("db.pool", null);
      }
   }

   public void update(String topic, String content, Map attrMap) throws Exception {
      if (!this.isAlive) {
         throw new Exception("update topic='" + topic + "' happens when we not alive");
      }
      DbUpdateInfo updateInfo = this.parser.parse(content);
      this.storer.store(updateInfo);
   }
   
   
   
}
