/*------------------------------------------------------------------------------
Name:      TestResultSetToXmlConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher;


import java.sql.ResultSet;
import java.io.ByteArrayOutputStream;
import java.io.BufferedOutputStream;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.StringTokenizer;
import java.sql.Connection;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.DbPool;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.db.I_ResultCb;
import org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter;
import org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer;
import org.xmlBlaster.contrib.dbwatcher.detector.I_ChangeDetector;
import org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher;

/**
 * This is the core processor class to handle database observation. 
 * <p>
 * We handle detected changes of the observed target, typically a database table,
 * and forward them to the {@link I_ChangePublisher} implementation,
 * typically a message oriented middleware (MoM).
 * The changes are XML formatted as implemented by the
 * {@link I_DataConverter} plugin.
 * <p />
 * <p>
 * This class loads all plugins by reflection and starts them. Each plugin
 * has its specific configuration paramaters. Descriptions thereof you find
 * in the plugins documentation.
 * </p>
 * <p>
 * To get you quickly going have a look into <tt>Example.java</tt>
 * </p>
 * Configuration:
 * <ul>
 *   <li><tt>dbPool.class</tt> configures your implementation of interface {@link I_DbPool} which defaults to <tt>org.xmlBlaster.contrib.db.DbPool</tt></li>
 *   <li><tt>db.queryMeatStatement</tt> if given a SQL select string this
 *       is executed on changes and the query result is dumped according
 *       to the configured I_DataConverter plugin and send as message content
 *       to the MoM</li>
 *    <li><tt>converter.class</tt> configures your implementation of interface {@link I_DataConverter} which defaults to <tt>org.xmlBlaster.contrib.dbwatcher.convert.ResultSetToXmlConverter</tt></li>
 *    <li><tt>mom.class</tt> configures your implementation of interface {@link I_ChangePublisher} which defaults to <tt>org.xmlBlaster.contrib.dbwatcher.mom.XmlBlasterPublisher</tt></li>
 *    <li><tt>changeDetector.class</tt> configures your implementation of interface {@link I_ChangeDetector} which defaults to <tt>org.xmlBlaster.contrib.dbwatcher.detector.MD5ChangeDetector</tt></li>
 *    <li><tt>alertProducer.class</tt> configures your implementation of interface {@link I_AlertProducer} which defaults to <tt>org.xmlBlaster.contrib.dbwatcher.detector.AlertScheduler</tt>
 *            Here you can configure multiple classes with a comma separated list.
 *            Each of them can trigger an new check in parallel, for example
 *            <tt>alertProducer.class=org.xmlBlaster.contrib.dbwatcher.mom.XmlBlasterPublisher,org.xmlBlaster.contrib.dbwatcher.detector.AlertSchedulery</tt> will check regularly via
 *            the AlertScheduler and on message via XmlBlasterPublisher</li>.
 * </ul>
 * 
 * @see org.xmlBlaster.contrib.dbwatcher.test.TestResultSetToXmlConverter
 * @author Marcel Ruff
 */
public class DbWatcher implements I_ChangeListener {
   private static Logger log = Logger.getLogger(DbWatcher.class.getName());
   private String queryMeatStatement;
   private I_Info info;
   private I_DataConverter dataConverter;
   private I_ChangePublisher publisher;
   private I_ChangeDetector changeDetector;
   private I_DbPool dbPool;
   private I_AlertProducer[] alertProducerArr;
   private boolean poolOwner;
   private int changeCount;
   
   /**
    * Default constructor, you need to call {@link #init} thereafter. 
    */
   public DbWatcher() {
      // void
   }

   /**
    * Convenience constructor, creates a processor for changes, calls {@link #init}.  
    * @param info Configuration
    * @throws Exception Can be of any type
    */
   public DbWatcher(I_Info info) throws Exception {
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
      //this.dataConverter = converter;
      //this.publisher = publisher;

      ClassLoader cl = this.getClass().getClassLoader();

      this.queryMeatStatement = info.get("db.queryMeatStatement", (String)null);
      if (this.queryMeatStatement != null && this.queryMeatStatement.length() < 1)
         this.queryMeatStatement = null;
      if (this.queryMeatStatement != null) {
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
            this.info.putObject("db.pool", this.dbPool);
         }
      }

      // Now we load all plugins to do the job
      
      String converterClass = this.info.get("converter.class", "org.xmlBlaster.contrib.dbwatcher.convert.ResultSetToXmlConverter");
      String momClass = this.info.get("mom.class", "org.xmlBlaster.contrib.dbwatcher.mom.XmlBlasterPublisher");
      String changeDetectorClass = this.info.get("changeDetector.class", "org.xmlBlaster.contrib.dbwatcher.detector.MD5ChangeDetector");
      String alerSchedulerClasses = this.info.get("alertProducer.class", "org.xmlBlaster.contrib.dbwatcher.detector.AlertScheduler"); // comma separated list
   
      if (converterClass.length() > 0) {
          this.dataConverter = (I_DataConverter)cl.loadClass(converterClass).newInstance();
          this.dataConverter.init(info);
          if (log.isLoggable(Level.FINE)) log.fine(converterClass + " created and initialized");
      }
      else
         log.info("Couldn't initialize I_DataConverter, please configure 'converter.class' if you need a conversion.");
      
      if (momClass.length() > 0) {
         this.publisher = (I_ChangePublisher)cl.loadClass(momClass).newInstance();
         this.publisher.init(info);
          if (log.isLoggable(Level.FINE)) log.fine(momClass + " created and initialized");
      }
      else
         log.severe("Couldn't initialize I_ChangePublisher, please configure 'mom.class'.");
      
      if (changeDetectorClass.length() > 0) {
         this.changeDetector = (I_ChangeDetector)cl.loadClass(changeDetectorClass).newInstance();
         this.changeDetector.init(info, (I_ChangeListener)this, this.dataConverter);
          if (log.isLoggable(Level.FINE)) log.fine(changeDetectorClass + " created and initialized");
      }
      else
         log.severe("Couldn't initialize I_ChangeDetector, please configure 'changeDetector.class'.");

      StringTokenizer st = new StringTokenizer(alerSchedulerClasses, ":, ");
      int countPlugins = st.countTokens();
      this.alertProducerArr = new I_AlertProducer[countPlugins];
      for (int i = 0; i < countPlugins; i++) {
         String clazz = st.nextToken().trim();
         try {
            Object o = info.getObject(clazz);
            if (o != null) {
               this.alertProducerArr[i] = (I_AlertProducer)o;
               if (log.isLoggable(Level.FINE)) log.fine("Existing AlertProducer '" + this.alertProducerArr[i] + "' reused.");
            }
            else {
               this.alertProducerArr[i] = (I_AlertProducer)cl.loadClass(clazz).newInstance();
               if (log.isLoggable(Level.FINE)) log.fine("AlertProducer '" + this.alertProducerArr[i] + "' created.");
            }
            this.alertProducerArr[i].init(info, this.changeDetector);
            //this.alertProducerArr[i].startProducing();
         }
         catch (Throwable e) {
            this.alertProducerArr[i] = null;
            log.severe("Couldn't initialize I_AlertProducer '" + clazz + "', the reason is: " + e.toString());
         }
      }
      if (countPlugins == 0) {
         log.warning("No AlertProducers are registered, set 'alertProducer.class' to point to your plugin class name");
      }
      if (log.isLoggable(Level.FINE)) log.fine("DbWatcher created");
   }
   
   /**
    * Start the work. 
    */
   public void startAlertProducers() {
      for (int i=0; i<this.alertProducerArr.length; i++) {
         try { this.alertProducerArr[i].startProducing(); } catch(Throwable e) { log.warning(e.toString()); }
      }
      log.info("DbWatcher is running");
   }

   /**
    * Suspend processing. 
    */
   public void stopAlertProducers() {
      for (int i=0; i<this.alertProducerArr.length; i++) {
         try { this.alertProducerArr[i].shutdown(); } catch(Throwable e) { log.warning(e.toString()); }
      }
   }

   /**
    * Access the MoM handele. 
    * @return The I_ChangePublisher plugin
    */
   public I_ChangePublisher getMom() {
      return this.publisher;
   }
   
   /**
    * Access the change detector handele. 
    * @return The I_ChangeDetector plugin
    */
   public I_ChangeDetector getChangeDetector() {
      return this.changeDetector;
   }
    
   /**
    * @see org.xmlBlaster.contrib.dbwatcher.I_ChangeListener#hasChanged(ChangeEvent)
    */
   public void hasChanged(final ChangeEvent changeEvent) {
          hasChanged(changeEvent, false);
   }
    
   /**
    * @param changeEvent The event data
    * @param recursion To detect recursion
    */
   private void hasChanged(final ChangeEvent changeEvent, boolean recursion) {
      try {
         if (log.isLoggable(Level.FINE)) log.fine("hasChanged() invoked for groupColValue=" + changeEvent.getGroupColValue());
         this.publisher.publish(changeEvent.getGroupColValue(),
               changeEvent.getXml(), changeEvent.getAttributeMap());
      }
      catch(Exception e) {
         e.printStackTrace();
         log.severe("Can't publish change event " + e.toString() +
                     " Event was: " + changeEvent.toString()); 
      }
   }         
         
   /**
    * @see I_ChangeListener#publishMessagesFromStmt
    */
   public int publishMessagesFromStmt(final String stmt, final boolean useGroupCol,
                               final ChangeEvent changeEvent,
                               Connection conn) throws Exception {
      if (conn == null) {
         throw new IllegalArgumentException("publishMessagesFromStmt() does not support passing a null JDBC-Connection");
      }
      this.changeCount = 0;
      final String command = (changeEvent.getCommand() == null) ? "UPDATE" : changeEvent.getCommand();
      Connection connRet = null;
      try {
          connRet = this.dbPool.select(conn, stmt, new I_ResultCb() {
             public void result(ResultSet rs) throws Exception {
                if (log.isLoggable(Level.FINE)) log.fine("Processing result set for '" + stmt + "'");
                String groupColName = changeEvent.getGroupColName();
                try {
                   ByteArrayOutputStream bout = null;
                   BufferedOutputStream out = null;
    
                   // default if no grouping is configured
                   if (groupColName == null)
                      groupColName = info.get("mom.topicName", "db.change");
                   String groupColValue = "DELETE".equals(command) ? changeEvent.getGroupColValue() : "${"+groupColName+"}";
                   String newGroupColValue = null;
                   boolean first = true;
    
                   while (rs != null && rs.next()) {
                      if (useGroupCol) {
                          newGroupColValue = rs.getString(groupColName);
                          if (rs.wasNull()) newGroupColValue = "__NULL__";
                      }
                      if (log.isLoggable(Level.FINEST)) log.finest("useGroupCol="+useGroupCol+", groupColName="+groupColName+", groupColValue="+groupColValue+", newGroupColValue="+newGroupColValue);
                      
                      if (!first && !groupColValue.equals(newGroupColValue)) {
                         first = false;
                         if (log.isLoggable(Level.FINE)) log.fine("Processing " + groupColName + "=" +
                            groupColValue + " has changed to '" +
                            newGroupColValue + "'");
                         if (dataConverter != null) dataConverter.done();
                         String resultXml = bout.toString();
                         hasChanged(new ChangeEvent(groupColName, groupColValue, resultXml, command), true);
                         changeCount++;
                         bout = null;
                      }
    
                      groupColValue = newGroupColValue;
                      
                      if (bout == null && dataConverter != null) {
                         bout = new ByteArrayOutputStream();
                         out = new BufferedOutputStream(bout);
                         dataConverter.setOutputStream(out, command, newGroupColValue);
                      }
                      
                      if (dataConverter != null) dataConverter.addInfo(rs, I_DataConverter.ALL); // collect data
    
                      first = false;
                   } // end while
                   
                   if (bout == null && dataConverter != null) {
                      bout = new ByteArrayOutputStream();
                      out = new BufferedOutputStream(bout);
                      dataConverter.setOutputStream(out, command, groupColValue);
                   }
                   String resultXml = "";
                   if (dataConverter != null) {
                      dataConverter.done();
                      resultXml = bout.toString();
                   }
                   hasChanged(new ChangeEvent(groupColName, groupColValue, resultXml, command), true);
                   changeCount++;
                }
                catch (Exception e) {
                   e.printStackTrace();
                   log.severe("Can't publish change event meat for groupColName='" +
                       groupColName + "': " + e.toString() + " Query was: " + stmt);
                }
             }
          });
      }
      finally {
         if (conn == null) { // If given conn was null we need to take care ourself
            if (connRet != null) this.dbPool.release(connRet);
         }
      }
      return this.changeCount;
   }
   
   /**
    * Replace e.g. ${XY} with the given token. 
    * @param text The complete string which may contain zero to many ${...}
    *             variables, if null we return null
    * @param token The replacement token, if null the original text is returned
    * @return The new value where all ${} are replaced.
    */
   public static String replaceVariable(String text, String token) {
      if (text == null) return null;
      if (token == null) return text;
      //if (token.indexOf("${") >= 0) return text; // Protect against recursion
      //while (true) {
      int lastFrom = -1;
      for (int i=0; i<10; i++) {
         int from = text.indexOf("${");
         if (from == -1) {
            from = text.indexOf("$_{"); // jutils suppresses replacement of such variables
            if (from == -1) return text;
         }
         if (lastFrom != -1 && lastFrom == from) return text; // recursion
         int to = text.indexOf("}", from);
         if (to == -1) return text;
         text = text.substring(0,from) + token + text.substring(to+1);
         lastFrom = from;
      }
      return text;
   }
   
   /**
    * Cleanup resources.
    * @throws Exception Can be of any type 
    */
   public void shutdown() throws Exception {
      for (int i=0; i<this.alertProducerArr.length; i++) {
         try { this.alertProducerArr[i].shutdown(); } catch(Throwable e) { e.printStackTrace(); log.warning(e.toString()); }
      }
      try { this.changeDetector.shutdown(); } catch(Throwable e) { e.printStackTrace(); log.warning(e.toString()); }
      try { this.dataConverter.shutdown(); } catch(Throwable e) { e.printStackTrace(); log.warning(e.toString()); }
      try { this.publisher.shutdown(); } catch(Throwable e) { e.printStackTrace(); log.warning(e.toString()); }
      if (this.poolOwner && this.dbPool != null) {
         this.dbPool.shutdown();
         this.dbPool = null;
         this.info.putObject("db.pool", null);
      }
   }
}
