/*------------------------------------------------------------------------------
Name:      DbWriterPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwriter;

import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginInfo;

import java.util.List;
import java.util.logging.Logger;

/**
 * DbWriterPlugin is a plugin wrapper if you want to run DbWriter inside xmlBlaster. 
 * <p>
 * This plugin uses <tt>java.util.logging</tt> and redirects the logging to xmlBlasters default
 * logging framework. You can switch this off by setting the attribute <tt>xmlBlaster/jdk14loggingCapture</tt> to false.
 * </p>
 * 
 * @author <a href="mailto:xmlblast@marcelruff.info">Marcel Ruff</a>
 */
public class DbWriterPlugin extends GlobalInfo implements DbWriterPluginMBean {
   private static Logger log = Logger.getLogger(DbWriterPlugin.class.getName());
   // private DbWriter dbWriter;
   private List dbWriterList;

   /**
    * Default constructor, you need to call {@link #doInit(Global, PluginInfo)} thereafter.
    */
   public DbWriterPlugin() {
      super(new String[] {"dbPool.class", "mom.class", "parser.class", "dbWriter.writer.class"});
   }
   
   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   protected void doInit(Global global_, PluginInfo pluginInfo) throws XmlBlasterException {
      try {
         this.dbWriterList = DbWriter.createDbWriters(this);
         this.putObject(JMX_PREFIX + "DbWriterPlugin", this);
      }
      catch (Throwable e) {
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_CONFIGURATION, "DbWriterPlugin", "init failed", e); 
      }
      log.info("Loaded DbWriter plugin '" + getType() + "'");
   }
   
   public String addDbWriter(String loginName, String password) {
      if (loginName == null || loginName.trim().length() < 1) {
         String txt = "The login name is null or empty, will not add anything";
         log.warning(txt);
         return txt;
      }
      if (password == null || password.length() < 1) { // do not trim the password here
         log.warning("The password was null, will use an empty password for user '" + loginName + "'");
         password = "";
      }
      synchronized(this.dbWriterList) {
         try {
            this.dbWriterList.add(DbWriter.createSingleDbWriter(this, loginName, password));
            return "successfully added a new DbWriter for '" + loginName + "' and password='" + password + "'";
         }
         catch (Exception ex) {
            String txt = "failed to add DbWriter for user '" + loginName + "' and password + '" + password + "' reason:" + ex.getMessage() + "'";
            log.severe(txt);
            return txt;
         }
      }
   }

   public String getClients() {
      DbWriter[] dbWriters = (DbWriter[])this.dbWriterList.toArray(new DbWriter[this.dbWriterList.size()]);
      StringBuffer buf = new StringBuffer(1024);
      for (int i=0; i < dbWriters.length; i++) {
         if (i > 0)
            buf.append(",");
         buf.append(dbWriters[i].getInfo().get("mom.loginName", "???"));
      }
      return buf.toString();
   }
   
   public String removeDbWriter(String loginName) {
      DbWriter[] dbWriters = (DbWriter[])this.dbWriterList.toArray(new DbWriter[this.dbWriterList.size()]);
      for (int i=0; i < dbWriters.length; i++) {
         String name = dbWriters[i].getInfo().get("mom.loginName", "???");
         if (loginName.equals(name)) {
            if (dbWriters.length > 1 && dbWriters[i].getPoolOwner()) {
               DbWriter.setPoolOwner(dbWriters[i], false);
               if (i == 0)
                  DbWriter.setPoolOwner(dbWriters[1], true);
               else
                  DbWriter.setPoolOwner(dbWriters[0], true);
            }
            this.dbWriterList.remove(i);
            dbWriters[i].shutdown();
            return "successfully shut down DbWriter for user '" + loginName + "'";
         }
      }
      return "could not shut down DbWriter for user '" + loginName + "' since not found in the list";
   }
   
   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#shutdown()
    */
   public void shutdown() throws XmlBlasterException {
      super.shutdown();
      if (dbWriterList == null)
         return;
      synchronized (this.dbWriterList) {
         for (int i=this.dbWriterList.size()-1; i >=0; i--) {
            try {
               DbWriter dbWriter = (DbWriter)this.dbWriterList.get(i);
               dbWriter.shutdown();
            }
            catch (Throwable e) {
               log.warning("Ignoring shutdown problem: " + e.toString());
            }
         }
      }
      log.info("Stopped DbWriter plugin '" + getType() + "'");
   }

}
