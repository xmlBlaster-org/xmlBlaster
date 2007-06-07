/*------------------------------------------------------------------------------
Name:      TestResultSetToXmlConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwriter;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xml.sax.InputSource;
import org.xmlBlaster.contrib.I_ChangePublisher;
import org.xmlBlaster.contrib.I_ContribPlugin;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.InfoHelper;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.replication.ReplicationConstants;
import org.xmlBlaster.util.qos.ClientProperty;

/**
 * 
 * @author Michele Laghi mailto:laghi@swissinfo.org
 */
public class DbWriter implements I_Update {

   public final static String INITIAL_UPDATE_EVENT_PRE = "initialUpdatePre";
   public final static String INITIAL_UPDATE_EVENT_POST = "initialUpdatePost";
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
   // this is used mainly when testing to get an event when an update is finished
   private I_Update registeredForUpdates;
   private String charSet = null;
   
   /**
    * Default constructor, you need to call {@link #init} thereafter. 
    */
   public DbWriter() {
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
    * Creates an Instance of a DbWriter with the given configuration. If loginName is null, then 
    * it is created with no manipulation of the mom.loginName and mom.password properties, othewise
    * a clone of the I_Info is taken and a DbWriter instance is created with the loginName specified
    * in the argument list.
    * 
    * @param masterInfo
    * @param loginName
    * @param password
    * @return
    */
   public static DbWriter createSingleDbWriter(I_Info masterInfo, String loginName, String password) throws Exception {
      if (loginName == null || loginName.length() < 0) {
         log.warning("The configuration contains no 'mom.loginName' property, will only use one instance of DbWriter based on other configuration parameters");
         return new DbWriter(masterInfo);
      }
      I_Info instanceInfo = new PropertiesInfo(new Properties()); // could be another implementation too
      InfoHelper.fillInfoWithEntriesFromInfo(instanceInfo, masterInfo);
      instanceInfo.put("mom.loginName", loginName);
      instanceInfo.put("mom.password", password);
      log.fine("instancing a DbWriter for mom.loginName '" + loginName + "' with password='" + password + "'");
      return new DbWriter(instanceInfo);
   }
   
   /**
    * Creates a list of DbWriter instances. One instance for each mom.loginName entry found. Note that
    * the mom.loginName can contain a list of comma separated session names (or subject names). For
    * each such name an instance is created. If the mom.password entries are less than the mom.loginName
    * ones, the last password will be used for all undefined passwords. 
    * @param info
    * @return
    * @throws Exception
    */
   public static List createDbWriters(I_Info masterInfo) throws Exception {
      // clone the I_Info object first
      List dbWriterList = new ArrayList();
      String loginNames = masterInfo.get("mom.loginName", null);
      if (loginNames == null || loginNames.length() < 0) {
         dbWriterList.add(createSingleDbWriter(masterInfo, null, null));
         return dbWriterList;
      }
      String passwords = masterInfo.get("mom.password", "secret");
      String password = "secret";
      StringTokenizer nameTokenizer = new StringTokenizer(loginNames, ",");
      StringTokenizer passwordTokenizer = new StringTokenizer(passwords, ",");
      while (nameTokenizer.hasMoreTokens()) {
         String loginName = nameTokenizer.nextToken().trim();
         if (passwordTokenizer.hasMoreTokens())
            password = passwordTokenizer.nextToken().trim();
         if (loginName != null && loginName.length() > 0) {
            dbWriterList.add(createSingleDbWriter(masterInfo, loginName, password));
         }
      }
      log.info("Created and configured '" + dbWriterList.size() + "' instances of DbWriter with mom.loginName='" + loginNames + "'");
      return dbWriterList;
   }
   
   public void setPoolOwner(boolean poolOwner) {
      this.poolOwner = poolOwner;
   }
   
   public boolean getPoolOwner() {
      return this.poolOwner;
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
         setPoolOwner(true);
         this.info.putObject(DB_POOL_KEY, this.dbPool);
      }

      // Now we load all plugins to do the job
      String momClass = this.info.get("mom.class", "org.xmlBlaster.contrib.MomEventEngine").trim();
      String parserClass = this.info.get("parser.class", "org.xmlBlaster.contrib.dbwriter.SqlInfoParser").trim();
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
         this.eventEngine.registerAlertListener(this, null);
         this.eventEngine.init(info);
         if (log.isLoggable(Level.FINE)) log.fine(momClass + " created and initialized");
      }
      else
         log.severe("Couldn't initialize I_EventEngine, please configure 'mom.class'.");
      this.charSet = info.get("charSet", null);
      if (log.isLoggable(Level.FINE)) log.fine("DbWriter created");
   }
   
   public I_Info getInfo() {
      return this.info;
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
   public synchronized void shutdown() {
      try {
         shutdown(this.eventEngine);
         this.isAlive = false;
         shutdown(this.parser);
         shutdown(this.writer);
         if (this.poolOwner && this.dbPool != null) {
            this.dbPool.shutdown();
            this.dbPool = null;
            this.info.putObject("db.pool", null);
         }
      }
      catch (Exception ex) {
         ex.printStackTrace();
         log.severe("An exception occured when shutting down: " + ex.getMessage());
      }
   }

   /**
    * Determines wether the message is a database dump or not. If it is a database dump it stores the content to a file,
    * otherwise it processes the message according to the normal processing flow.
    */
   public synchronized void update(String topic, InputStream is, Map attrMap) throws Exception {
      if (!this.isAlive) {
         throw new Exception("update topic='" + topic + "' happens when we not alive: \n");
      }
      try {
         ClientProperty dumpProp = (ClientProperty)attrMap.get(ReplicationConstants.DUMP_ACTION);
         ClientProperty endToRemoteProp = (ClientProperty)attrMap.get(ReplicationConstants.INITIAL_DATA_END_TO_REMOTE);
         ClientProperty initialDumpAsXml = (ClientProperty)attrMap.get(ReplicationConstants.INITIAL_DUMP_AS_XML);
         ClientProperty endOfTransition = (ClientProperty)attrMap.get(ReplicationConstants.END_OF_TRANSITION);
         
         if (dumpProp != null && initialDumpAsXml == null) { // initial dump (xml)
            this.writer.update(topic, is, attrMap);
         }
         else if (endToRemoteProp != null) { // initial dump, binary
            this.writer.update(topic, is, attrMap);
         }
         else if (endOfTransition != null) { // initial dum, binary, ready to import from file system (last file part arrived)
            this.writer.update(topic, is, attrMap);
         }
         else { // replication xml, build an SqlInfo from XML
            SqlInfo updateInfo = this.parser.parse(is, this.charSet);
            if (updateInfo == null) {
               log.warning("The entry was not for us");
               return;
            }
            ClientProperty keepTransactionOpenProp = (ClientProperty)attrMap.get(ReplicationConstants.KEEP_TRANSACTION_OPEN);
            if (keepTransactionOpenProp != null && keepTransactionOpenProp.getBooleanValue()) {
               // is the property already set ? No, then pass it to the info object
               if (updateInfo.getDescription().getAttribute(ReplicationConstants.KEEP_TRANSACTION_OPEN) == null) {
                  log.fine("Setting the property '" + ReplicationConstants.KEEP_TRANSACTION_OPEN + "' to true");
                  updateInfo.getDescription().setAttribute(keepTransactionOpenProp);
               }
               else
                  log.warning("The property '" + ReplicationConstants.KEEP_TRANSACTION_OPEN + "' was already set as a description attribute (will not overwrite it)");
            }
            this.writer.store(updateInfo);
         }
      }
      finally {
         if (this.registeredForUpdates != null)
            this.registeredForUpdates.update(topic, null, attrMap);
      }
   }
   
   public void registerForUpdates(I_Update registeredForUpdates) {
      this.registeredForUpdates = registeredForUpdates;
   }
   
   
}
