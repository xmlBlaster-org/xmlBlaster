/*------------------------------------------------------------------------------
Name:      DebugConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
//  JDK 1.6
import java.sql.BaseQuery;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.SQLClientInfoException;
import java.sql.SQLXML;
//
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.xmlBlaster.util.ThreadLister;

/**
 * DebugConnection wraps a java.sql.Connection to intercept
 * calls for debugging / assertion purposes.
 * 
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class DebugConnection implements Connection {

   private Connection conn;
   private static Logger log = Logger.getLogger(DebugConnection.class.getName());
   private boolean inPool;
   private boolean wasUsed;
   private boolean commitOrRollbackCalled;
   private boolean autoCommitSwitchedOff;
   
   public DebugConnection(Connection conn) {
      if (this.inPool)
         throw new IllegalStateException("entry in pool, can not invoke any operation");
      this.conn = conn;
   }

   private final void checkIfOutsidePool() {
      if (this.inPool) {
         log.severe(ThreadLister.getAllStackTraces());
         throw new IllegalStateException("the connection is in the pool and an invocation occured");
      }
   }
      
   public void setInPool(boolean addingToPool) {
      this.inPool = addingToPool;
      
      boolean retrieveFromPool = !addingToPool;
      
      if (addingToPool == true) { // pool.put
         if (this.autoCommitSwitchedOff && this.wasUsed) {
            if (!this.commitOrRollbackCalled) {
               log.severe(ThreadLister.getAllStackTraces());
               throw new IllegalStateException("the connection with autoCommit=false was never committed/rolleback");
            }
         }
      }
      if (retrieveFromPool) {// pool.get
         // reset
         this.wasUsed = false; 
         this.commitOrRollbackCalled = false;
         this.autoCommitSwitchedOff = false;
      }
   }
   
   /**
    * @return the inPool
    */
   public boolean isInPool() {
      return this.inPool;
   }

   /**
    * @see java.sql.Connection#clearWarnings()
    */
   public void clearWarnings() throws SQLException {
      this.conn.clearWarnings();
   }

   /**
    * @see java.sql.Connection#close()
    */
   public void close() throws SQLException {
      checkIfOutsidePool();
      this.conn.close();
   }

   /**
    * @see java.sql.Connection#commit()
    */
   public void commit() throws SQLException {
      checkIfOutsidePool();
      this.commitOrRollbackCalled = true;
      this.conn.commit();
   }

   /**
    * @see java.sql.Connection#createStatement()
    */
   public Statement createStatement() throws SQLException {
      checkIfOutsidePool();
      this.wasUsed = true;
      return this.conn.createStatement();
   }

   /**
    * @see java.sql.Connection#createStatement(int, int)
    */
   public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
      checkIfOutsidePool();
      this.wasUsed = true;
      return this.conn.createStatement(resultSetType, resultSetConcurrency);
   }

   /**
    * @see java.sql.Connection#createStatement(int, int, int)
    */
   public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability)
   throws SQLException {
      checkIfOutsidePool();
      this.wasUsed = true;
      return this.conn.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
   }

   /**
    * @see java.sql.Connection#getAutoCommit()
    */
   public boolean getAutoCommit() throws SQLException {
      // checkIfOutsidePool();
      return this.conn.getAutoCommit();
   }

   /**
    * @see java.sql.Connection#getCatalog()
    */
   public String getCatalog() throws SQLException {
      checkIfOutsidePool();
      return this.conn.getCatalog();
   }

   /**
    * @see java.sql.Connection#getHoldability()
    */
   public int getHoldability() throws SQLException {
      checkIfOutsidePool();
      return this.conn.getHoldability();
   }

   /**
    * @see java.sql.Connection#getMetaData()
    */
   public DatabaseMetaData getMetaData() throws SQLException {
      //checkPool(); can be called from pool itself
      return this.conn.getMetaData();
   }

   /* (non-Javadoc)
    * @see java.sql.Connection#getTransactionIsolation()
    */
   public int getTransactionIsolation() throws SQLException {
      //checkPool(); can be called from pool itself
      return this.conn.getTransactionIsolation();
   }

   /* (non-Javadoc)
    * @see java.sql.Connection#getTypeMap()
    */
   public Map getTypeMap() throws SQLException {
      //checkPool(); can be called from pool itself
      return this.conn.getTypeMap();
   }

   /* (non-Javadoc)
    * @see java.sql.Connection#getWarnings()
    */
   public SQLWarning getWarnings() throws SQLException {
      //checkPool(); can be called from pool itself
      return this.conn.getWarnings();
   }

   /**
    * @see java.sql.Connection#isClosed()
    */
   public boolean isClosed() throws SQLException {
      //checkPool(); can be called from pool itself
      return this.conn.isClosed();
   }

   /**
    * @see java.sql.Connection#isReadOnly()
    */
   public boolean isReadOnly() throws SQLException {
      //checkPool(); can be called from pool itself
      return this.conn.isReadOnly();
   }

   /**
    * @see java.sql.Connection#nativeSQL(java.lang.String)
    */
   public String nativeSQL(String sql) throws SQLException {
      checkIfOutsidePool();
      return this.conn.nativeSQL(sql);
   }

   /**
    * @see java.sql.Connection#prepareCall(java.lang.String)
    */
   public CallableStatement prepareCall(String sql) throws SQLException {
      checkIfOutsidePool();
      return this.conn.prepareCall(sql);
   }

   /**
    * @see java.sql.Connection#prepareCall(java.lang.String, int, int)
    */
   public CallableStatement prepareCall(String sql, int resultSetType,
         int resultSetConcurrency) throws SQLException {
      checkIfOutsidePool();
      return this.conn.prepareCall(sql, resultSetType, resultSetConcurrency);
   }

   /**
    * @see java.sql.Connection#prepareCall(java.lang.String, int, int, int)
    */
   public CallableStatement prepareCall(String sql, int resultSetType,
         int resultSetConcurrency, int resultSetHoldability)
         throws SQLException {
      checkIfOutsidePool();
      return this.conn.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
   }

   /**
    * @see java.sql.Connection#prepareStatement(java.lang.String)
    */
   public PreparedStatement prepareStatement(String sql) throws SQLException {
      checkIfOutsidePool();
      this.wasUsed = true;
      return this.conn.prepareStatement(sql);
   }

   /**
    * @see java.sql.Connection#prepareStatement(java.lang.String, int)
    */
   public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys)
         throws SQLException {
      checkIfOutsidePool();
      this.wasUsed = true;
      return this.conn.prepareStatement(sql, autoGeneratedKeys);
   }

   /**
    * @see java.sql.Connection#prepareStatement(java.lang.String, int[])
    */
   public PreparedStatement prepareStatement(String sql, int[] columnIndexes)
         throws SQLException {
      checkIfOutsidePool();
      this.wasUsed = true;
      return this.conn.prepareStatement(sql, columnIndexes);
   }

   /**
    * @see java.sql.Connection#prepareStatement(java.lang.String, java.lang.String[])
    */
   public PreparedStatement prepareStatement(String sql, String[] columnNames)
         throws SQLException {
      checkIfOutsidePool();
      this.wasUsed = true;
      return this.conn.prepareStatement(sql, columnNames);
   }

   /**
    * @see java.sql.Connection#prepareStatement(java.lang.String, int, int)
    */
   public PreparedStatement prepareStatement(String sql, int resultSetType,
         int resultSetConcurrency) throws SQLException {
      this.wasUsed = true;
      checkIfOutsidePool();
      return this.conn.prepareStatement(sql, resultSetType, resultSetConcurrency);
   }

   /**
    * @see java.sql.Connection#prepareStatement(java.lang.String, int, int, int)
    */
   public PreparedStatement prepareStatement(String sql, int resultSetType,
         int resultSetConcurrency, int resultSetHoldability)
         throws SQLException {
      this.wasUsed = true;
      checkIfOutsidePool();
      return this.conn.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
   }

   /**
    * @see java.sql.Connection#releaseSavepoint(java.sql.Savepoint)
    */
   public void releaseSavepoint(Savepoint savepoint) throws SQLException {
      checkIfOutsidePool();
      this.conn.releaseSavepoint(savepoint);
   }

   /**
    * @see java.sql.Connection#rollback()
    */
   public void rollback() throws SQLException {
      checkIfOutsidePool();
      this.commitOrRollbackCalled = true;
      this.conn.rollback();
   }

   /**
    * @see java.sql.Connection#rollback(java.sql.Savepoint)
    */
   public void rollback(Savepoint savepoint) throws SQLException {
      checkIfOutsidePool();
      this.conn.rollback(savepoint);
   }

   /**
    * @see java.sql.Connection#setAutoCommit(boolean)
    */
   public void setAutoCommit(boolean autoCommit) throws SQLException {
      // checkIfOutsidePool();
      if (autoCommit == false)
         this.autoCommitSwitchedOff = true;
      this.conn.setAutoCommit(autoCommit);
   }

   /**
    * @see java.sql.Connection#setCatalog(java.lang.String)
    */
   public void setCatalog(String catalog) throws SQLException {
      checkIfOutsidePool();
      this.conn.setCatalog(catalog);
   }

   /**
    * @see java.sql.Connection#setHoldability(int)
    */
   public void setHoldability(int holdability) throws SQLException {
      checkIfOutsidePool();
      this.conn.setHoldability(holdability);
   }

   /**
    * @see java.sql.Connection#setReadOnly(boolean)
    */
   public void setReadOnly(boolean readOnly) throws SQLException {
      checkIfOutsidePool();
      this.conn.setReadOnly(readOnly);
   }

   /**
    * @see java.sql.Connection#setSavepoint()
    */
   public Savepoint setSavepoint() throws SQLException {
      checkIfOutsidePool();
      return this.conn.setSavepoint();
   }

   /**
    * @see java.sql.Connection#setSavepoint(java.lang.String)
    */
   public Savepoint setSavepoint(String name) throws SQLException {
      checkIfOutsidePool();
      return this.conn.setSavepoint(name);
   }

   /**
    * @see java.sql.Connection#setTransactionIsolation(int)
    */
   public void setTransactionIsolation(int level) throws SQLException {
      checkIfOutsidePool();
      this.conn.setTransactionIsolation(level);
   }

   /**
    * @see java.sql.Connection#setTypeMap(java.util.Map)
    */
   public void setTypeMap(Map arg0) throws SQLException {
      checkIfOutsidePool();
      this.conn.setTypeMap(arg0);
   }

// JDK 1.6 dummies   
public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        // TODO Auto-generated method stub
        return null;
}

public Blob createBlob() throws SQLException {
        // TODO Auto-generated method stub
        return null;
}

public Clob createClob() throws SQLException {
        // TODO Auto-generated method stub
        return null;
}

public NClob createNClob() throws SQLException {
        // TODO Auto-generated method stub
        return null;
}

public BaseQuery createQueryObject(Class arg0) throws SQLException {
        // TODO Auto-generated method stub
        return null;
}

public BaseQuery createQueryObject(Class arg0, Connection arg1) throws SQLException {
        // TODO Auto-generated method stub
        return null;
}

public SQLXML createSQLXML() throws SQLException {
        // TODO Auto-generated method stub
        return null;
}

public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        // TODO Auto-generated method stub
        return null;
}

public Properties getClientInfo() throws SQLException {
        // TODO Auto-generated method stub
        return null;
}

public String getClientInfo(String name) throws SQLException {
        // TODO Auto-generated method stub
        return null;
}

public boolean isValid(int timeout) throws SQLException {
        // TODO Auto-generated method stub
        return false;
}

public void setClientInfo(Properties properties) throws SQLClientInfoException {
        // TODO Auto-generated method stub
        
}

public void setClientInfo(String name, String value) throws SQLClientInfoException {
        // TODO Auto-generated method stub
        
}

public boolean isWrapperFor(Class arg0) throws SQLException {
        // TODO Auto-generated method stub
        return false;
}

public Object unwrap(Class arg0) throws SQLException {
        // TODO Auto-generated method stub
        return null;
}

}
