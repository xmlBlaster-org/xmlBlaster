/*------------------------------------------------------------------------------
Name:      SqlPrePostStatement.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.InfoHelper;
import org.xmlBlaster.contrib.db.DbMetaHelper;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwriter.info.I_PrePostStatement;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlRow;

/**
 * SqlPrePostStatement allows the replication to execute a stored procedure before and / or
 * after the operation on the replica (it is invoked as an optional plugin by the 
 * ReplicationWriter).
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class SqlPrePostStatement implements I_PrePostStatement {
   
   private static Logger log = Logger.getLogger(SqlPrePostStatement.class.getName());
   
   private Map sqlInsertPre;
   private Map sqlUpdatePre;
   private Map sqlDeletePre;
   
   private Map sqlInsertPost;
   private Map sqlUpdatePost;
   private Map sqlDeletePost;
     
   public SqlPrePostStatement() {
   }

   private final boolean executeSqlCodeIfNeeded(Connection conn, Map map, String completeTableName) {
      if (map == null)
         return true;
      String sqlCode = (String)map.get(completeTableName);
      if (sqlCode == null)
         return true;
      CallableStatement cs = null;
      try {
         log.fine("Invoking '" + sqlCode + "'");
         cs = conn.prepareCall(sqlCode);
         cs.executeQuery();
      }
      catch (SQLException ex) {
         log.severe("An Exception occured when executing '" + sqlCode + "':" + ex.getMessage());
         ex.printStackTrace();
      }
      finally {
         if (cs != null) {
            try {
               cs.close();
            }
            catch (SQLException ex) {
               ex.printStackTrace();
            }
         }
      }
      return true;
   }
   
   public boolean preStatement(String operation, Connection conn, SqlInfo info, SqlDescription tableDescription, SqlRow currentRow) throws Exception {
      if (INSERT_ACTION.equalsIgnoreCase(operation)) {
         return executeSqlCodeIfNeeded(conn, this.sqlInsertPre, tableDescription.getCompleteTableName());
      }
      if (INSERT_ACTION.equalsIgnoreCase(operation)) {
         return executeSqlCodeIfNeeded(conn, this.sqlUpdatePre, tableDescription.getCompleteTableName());
      }
      if (INSERT_ACTION.equalsIgnoreCase(operation)) {
         return executeSqlCodeIfNeeded(conn, this.sqlDeletePre, tableDescription.getCompleteTableName());
      }
      return true;
   }

   public void postStatement(String operation, Connection conn, SqlInfo info, SqlDescription tableDescription, SqlRow currentRow) throws Exception {
      if (INSERT_ACTION.equalsIgnoreCase(operation)) {
         executeSqlCodeIfNeeded(conn, this.sqlInsertPost, tableDescription.getCompleteTableName());
      }
      if (INSERT_ACTION.equalsIgnoreCase(operation)) {
         executeSqlCodeIfNeeded(conn, this.sqlUpdatePost, tableDescription.getCompleteTableName());
      }
      if (INSERT_ACTION.equalsIgnoreCase(operation)) {
         executeSqlCodeIfNeeded(conn, this.sqlDeletePost, tableDescription.getCompleteTableName());
      }
   }

   public Set getUsedPropertyKeys() {
      return null;
   }

   /**
    * Gets a map of all codes having as key the complete table name and as value
    * the callable statement to invoke (already correct for jdbc callableStatement
    * invocation).
    * 
    * @param prefix
    * @param info
    * @param dbMetaHelper
    * @return
    */
   private Map getCodeMap(String prefix, I_Info info, DbMetaHelper dbMetaHelper) {
      Map map = InfoHelper.getPropertiesStartingWith(prefix, info, dbMetaHelper);
      log.info(prefix + " statements found '" + map.size() + "' times ");
      if (map.size() < 1)
         return null;
      Map ret = new HashMap();
      Iterator iter = map.keySet().iterator();
      while (iter.hasNext()) {
         Object key = iter.next();
         String val = (String)map.get(key);
         String sql = "{call " + val + "}";
         ret.put(key, sql);
      }
      return ret;
   }
   

   /**
    * Properties used are:
    * dbWriter.sqlPrePostStatement.sql.pre.insert.${SCHEMA}.${TABLE}
    * dbWriter.sqlPrePostStatement.sql.pre.update.${SCHEMA}.${TABLE}
    * dbWriter.sqlPrePostStatement.sql.pre.delete.${SCHEMA}.${TABLE}
    * dbWriter.sqlPrePostStatement.sql.post.insert.${SCHEMA}.${TABLE}
    * dbWriter.sqlPrePostStatement.sql.post.update.${SCHEMA}.${TABLE}
    * dbWriter.sqlPrePostStatement.sql.post.delete.${SCHEMA}.${TABLE}
    * 
    */
   public void init(I_Info info) throws Exception {
      I_DbPool dbPool = (I_DbPool)info.getObject("db.pool");
      DbMetaHelper dbMetaHelper = null;
      if (dbPool != null) {
         dbMetaHelper = new DbMetaHelper(dbPool);
      }
      else
         log.warning("The database pool has not been registered ('db.pool')");
      
      this.sqlInsertPre = getCodeMap("dbWriter.sqlPrePostStatement.sql.pre.insert.", info, dbMetaHelper);
      this.sqlUpdatePre = getCodeMap("dbWriter.sqlPrePostStatement.sql.pre.update.", info, dbMetaHelper);
      this.sqlDeletePre = getCodeMap("dbWriter.sqlPrePostStatement.sql.pre.delete.", info, dbMetaHelper);

      this.sqlInsertPost = getCodeMap("dbWriter.sqlPrePostStatement.sql.post.insert.", info, dbMetaHelper);
      this.sqlUpdatePost = getCodeMap("dbWriter.sqlPrePostStatement.sql.post.update.", info, dbMetaHelper);
      this.sqlDeletePost = getCodeMap("dbWriter.sqlPrePostStatement.sql.post.delete.", info, dbMetaHelper);
   }

   public void shutdown() throws Exception {
      this.sqlInsertPre = null;
      this.sqlUpdatePre = null;
      this.sqlDeletePre = null;
      this.sqlInsertPost = null;
      this.sqlUpdatePost = null;
      this.sqlDeletePost = null;
   }
   
}
