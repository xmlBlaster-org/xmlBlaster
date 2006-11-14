/*------------------------------------------------------------------------------
Name:      DebugConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Map;
import java.util.logging.Logger;

import org.xmlBlaster.util.ThreadLister;

/**
 * DebugConnection
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class DebugConnection implements Connection {

   private Connection conn;
   private static Logger log = Logger.getLogger(DebugConnection.class.getName());
   private boolean inPool;
   
   public DebugConnection(Connection conn) {
      if (this.inPool)
         throw new IllegalStateException("entry in pool, can not invoke any operation");
      this.conn = conn;
   }

   private final void checkPool() {
      log.severe(ThreadLister.getAllStackTraces());
      throw new IllegalStateException("the connection is in the pool and an invocation occured");
   }
      
   public void setInPool(boolean inPool) {
      checkPool();
      this.inPool = inPool;
   }
   
   /**
    * @see java.sql.Connection#clearWarnings()
    */
   public void clearWarnings() throws SQLException {
      checkPool();
      this.conn.clearWarnings();
   }

   /**
    * @see java.sql.Connection#close()
    */
   public void close() throws SQLException {
      checkPool();
      this.conn.close();
   }

   /**
    * @see java.sql.Connection#commit()
    */
   public void commit() throws SQLException {
      checkPool();
      this.conn.commit();
   }

   /**
    * @see java.sql.Connection#createStatement()
    */
   public Statement createStatement() throws SQLException {
      checkPool();
      return this.conn.createStatement();
   }

   /**
    * @see java.sql.Connection#createStatement(int, int)
    */
   public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
      checkPool();
      return this.conn.createStatement(resultSetType, resultSetConcurrency);
   }

   /**
    * @see java.sql.Connection#createStatement(int, int, int)
    */
   public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
   throws SQLException {
      checkPool();
         return this.conn.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
   }

   /**
    * @see java.sql.Connection#getAutoCommit()
    */
   public boolean getAutoCommit() throws SQLException {
      checkPool();
      return this.conn.getAutoCommit();
   }

   /**
    * @see java.sql.Connection#getCatalog()
    */
   public String getCatalog() throws SQLException {
      checkPool();
      return this.conn.getCatalog();
   }

   /**
    * @see java.sql.Connection#getHoldability()
    */
   public int getHoldability() throws SQLException {
      checkPool();
      return this.getHoldability();
   }

   /**
    * @see java.sql.Connection#getMetaData()
    */
   public DatabaseMetaData getMetaData() throws SQLException {
      checkPool();
      return this.getMetaData();
   }

   /* (non-Javadoc)
    * @see java.sql.Connection#getTransactionIsolation()
    */
   public int getTransactionIsolation() throws SQLException {
      checkPool();
      return this.conn.getTransactionIsolation();
   }

   /* (non-Javadoc)
    * @see java.sql.Connection#getTypeMap()
    */
   public Map getTypeMap() throws SQLException {
      checkPool();
      return this.conn.getTypeMap();
   }

   /* (non-Javadoc)
    * @see java.sql.Connection#getWarnings()
    */
   public SQLWarning getWarnings() throws SQLException {
      checkPool();
      return this.conn.getWarnings();
   }

   /**
    * @see java.sql.Connection#isClosed()
    */
   public boolean isClosed() throws SQLException {
      checkPool();
      return this.conn.isClosed();
   }

   /**
    * @see java.sql.Connection#isReadOnly()
    */
   public boolean isReadOnly() throws SQLException {
      checkPool();
      return this.conn.isReadOnly();
   }

   /**
    * @see java.sql.Connection#nativeSQL(java.lang.String)
    */
   public String nativeSQL(String sql) throws SQLException {
      checkPool();
      return this.nativeSQL(sql);
   }

   /**
    * @see java.sql.Connection#prepareCall(java.lang.String)
    */
   public CallableStatement prepareCall(String sql) throws SQLException {
      checkPool();
      return this.conn.prepareCall(sql);
   }

   /**
    * @see java.sql.Connection#prepareCall(java.lang.String, int, int)
    */
   public CallableStatement prepareCall(String sql, int resultSetType,
         int resultSetConcurrency) throws SQLException {
      checkPool();
      return this.conn.prepareCall(sql, resultSetType, resultSetConcurrency);
   }

   /**
    * @see java.sql.Connection#prepareCall(java.lang.String, int, int, int)
    */
   public CallableStatement prepareCall(String sql, int resultSetType,
         int resultSetConcurrency, int resultSetHoldability)
         throws SQLException {
      checkPool();
      return this.conn.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
   }

   /**
    * @see java.sql.Connection#prepareStatement(java.lang.String)
    */
   public PreparedStatement prepareStatement(String sql) throws SQLException {
      checkPool();
      return this.conn.prepareStatement(sql);
   }

   /**
    * @see java.sql.Connection#prepareStatement(java.lang.String, int)
    */
   public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
         throws SQLException {
      checkPool();
      return this.conn.prepareStatement(sql, autoGeneratedKeys);
   }

   /**
    * @see java.sql.Connection#prepareStatement(java.lang.String, int[])
    */
   public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
         throws SQLException {
      checkPool();
      return this.conn.prepareStatement(sql, columnIndexes);
   }

   /**
    * @see java.sql.Connection#prepareStatement(java.lang.String, java.lang.String[])
    */
   public PreparedStatement prepareStatement(String sql, String[] columnNames)
         throws SQLException {
      checkPool();
      return this.conn.prepareStatement(sql, columnNames);
   }

   /**
    * @see java.sql.Connection#prepareStatement(java.lang.String, int, int)
    */
   public PreparedStatement prepareStatement(String sql, int resultSetType,
         int resultSetConcurrency) throws SQLException {
      checkPool();
      return this.conn.prepareStatement(sql, resultSetType, resultSetConcurrency);
   }

   /**
    * @see java.sql.Connection#prepareStatement(java.lang.String, int, int, int)
    */
   public PreparedStatement prepareStatement(String sql, int resultSetType,
         int resultSetConcurrency, int resultSetHoldability)
         throws SQLException {
      checkPool();
      return this.conn.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
   }

   /**
    * @see java.sql.Connection#releaseSavepoint(java.sql.Savepoint)
    */
   public void releaseSavepoint(Savepoint savepoint) throws SQLException {
      checkPool();
      this.conn.releaseSavepoint(savepoint);
   }

   /**
    * @see java.sql.Connection#rollback()
    */
   public void rollback() throws SQLException {
      checkPool();
      this.conn.rollback();
   }

   /**
    * @see java.sql.Connection#rollback(java.sql.Savepoint)
    */
   public void rollback(Savepoint savepoint) throws SQLException {
      checkPool();
      this.conn.rollback(savepoint);
   }

   /**
    * @see java.sql.Connection#setAutoCommit(boolean)
    */
   public void setAutoCommit(boolean autoCommit) throws SQLException {
      checkPool();
      this.conn.setAutoCommit(autoCommit);
   }

   /**
    * @see java.sql.Connection#setCatalog(java.lang.String)
    */
   public void setCatalog(String catalog) throws SQLException {
      checkPool();
      this.conn.setCatalog(catalog);
   }

   /**
    * @see java.sql.Connection#setHoldability(int)
    */
   public void setHoldability(int holdability) throws SQLException {
      checkPool();
      this.conn.setHoldability(holdability);
   }

   /**
    * @see java.sql.Connection#setReadOnly(boolean)
    */
   public void setReadOnly(boolean readOnly) throws SQLException {
      checkPool();
      this.conn.setReadOnly(readOnly);
   }

   /**
    * @see java.sql.Connection#setSavepoint()
    */
   public Savepoint setSavepoint() throws SQLException {
      checkPool();
      return this.conn.setSavepoint();
   }

   /**
    * @see java.sql.Connection#setSavepoint(java.lang.String)
    */
   public Savepoint setSavepoint(String name) throws SQLException {
      checkPool();
      return this.conn.setSavepoint(name);
   }

   /**
    * @see java.sql.Connection#setTransactionIsolation(int)
    */
   public void setTransactionIsolation(int level) throws SQLException {
      checkPool();
      this.conn.setTransactionIsolation(level);
   }

   /**
    * @see java.sql.Connection#setTypeMap(java.util.Map)
    */
   public void setTypeMap(Map arg0) throws SQLException {
      checkPool();
      this.conn.setTypeMap(arg0);
   }

}
