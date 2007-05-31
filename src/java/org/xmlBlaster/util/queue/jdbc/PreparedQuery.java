/*------------------------------------------------------------------------------
Name:      PreparedQuery.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import java.sql.Connection;
import java.sql.SQLException;
//import java.sql.PreparedStatement; Changed 2003-06-09 marcel for MS SQL server (thanks to zhang zhi wei)
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 */

class PreparedQuery {
   public final static boolean WITH_FAILURES = false;
   public final static boolean SUCCESS = true;
   
   public final static String ME = "PreparedQuery";
   private static Logger log = Logger.getLogger(PreparedQuery.class.getName());
   Connection conn;
   //private PreparedStatement st; Changed 2003-06-09 marcel for MS SQL server (thanks to zhang zhi wei)
   private Statement st;
   ResultSet rs;
   private JdbcConnectionPool pool;
   private boolean isClosed = true;
   private boolean isException = false;


   /**
    * This constructor can be used if you want to have several invocations
    * whitin the same transaction.
    * @param pool The JdbcConnectionPool to use for this prepared quuery
    * @param request the string to use as the request 
    * @param isAutoCommit
    * @param log
    * @param fetchSize
    */
   public PreparedQuery(JdbcConnectionPool pool, String request, boolean isAutoCommit, int fetchSize)
      throws SQLException, XmlBlasterException {
      this.pool = pool;
      this.isClosed = false;
      if (log.isLoggable(Level.FINER))
         log.finer("Constructor. autocommit is '" + isAutoCommit + "'");

      try {
         this.conn = this.pool.getConnection();
         if (this.conn.getAutoCommit() != isAutoCommit) this.conn.setAutoCommit(isAutoCommit);
         // this.st = conn.prepareStatement(request); Changed 2003-06-09 marcel for MS SQL server (thanks to zhang zhi wei)
         this.st = conn.createStatement();
         this.st.setQueryTimeout(this.pool.getQueryTimeout());

//         if (fetchSize > -1) this.st.setFetchSize(fetchSize);
         this.rs = this.st.executeQuery(request);
      }
      catch (XmlBlasterException ex) {
         String additionalInfo = "request='" + request + "' isAutocommit='" + isAutoCommit + "' fetchSize='" + fetchSize + "' ";
         log.fine("Constructor. Exception. " + additionalInfo + ": " + ex.getMessage());
         if (this.conn != null) {
            try {
               if (!this.conn.getAutoCommit()) {
                  this.conn.rollback();
                  this.conn.setAutoCommit(true);
               }
            }
            catch (Throwable ex2) {
               log.warning("constructor exception occured when rolling back " + additionalInfo + ": " + ex2.toString());
            }
            finally {
               try {
                  if (this.st != null) st.close();
               }
               catch (Throwable ex3) {
                  log.warning("constructor exception occured when closing statement " + additionalInfo + ": " + ex3.toString());
               }
            }   
            if (this.conn != null) 
               this.pool.discardConnection(this.conn);
            this.conn = null;
         }
         this.isClosed = true;
         throw ex;
      }
      catch (SQLException ex) {
         String additionalInfo = "request='" + request + "' isAutocommit='" + isAutoCommit + "' fetchSize='" + fetchSize + "' ";
         log.fine("Constructor. " + additionalInfo + " SQLException: " + ex.getMessage());
         if (this.conn != null) {
            try {
               if (!this.conn.getAutoCommit()) {
                  this.conn.rollback();
                  this.conn.setAutoCommit(true);
               }
               if (this.st != null) st.close();
            }
            catch (Throwable ex2) {
               log.warning("constructor: exception occured when handling SQL Exception: " + additionalInfo + ex2.toString());
            }
            if (this.conn != null) 
               this.pool.discardConnection(this.conn);
            this.conn = null;
         }
         this.isClosed = true;
         throw ex;
      }
      catch (Throwable ex) {
         String additionalInfo = "request='" + request + "' isAutocommit='" + isAutoCommit + "' fetchSize='" + fetchSize + "' ";
         log.warning("Constructor. Throwable: " + additionalInfo + ex.toString());
         if (this.conn != null) {
            try {
               if (!this.conn.getAutoCommit()) {
                  this.conn.rollback();
                  this.conn.setAutoCommit(true);
               }
               if (this.st != null) st.close();
            }
            catch (Throwable ex2) {
               log.warning("constructor: " + additionalInfo + " exception occured when handling SQL Exception: " + ex2.toString());
            }
            if (this.conn != null) 
               this.pool.discardConnection(this.conn);
            this.conn = null;
         }
         this.isClosed = true;
         throw new XmlBlasterException(this.pool.getGlobal(), ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".constructor " + additionalInfo, "", ex);
      }
   }

   public void closeStatement() {
      try {
         if (this.st != null) {
            this.st.close();
            this.st = null;
         }
      }
      catch (Throwable ex) {
         log.warning("closeStatement: exception when closing statement:" + ex.getMessage());
      }
   }

   public final ResultSet inTransactionRequest(String request /*, int fetchSize */)
      throws XmlBlasterException, SQLException {

      log.finer("inTransactionRequest: " + request);
      if (this.conn.getAutoCommit())
         throw new XmlBlasterException(pool.getGlobal(), ErrorCode.INTERNAL_UNKNOWN, ME, "inTransactionRequest should not be called if autocommit is on");

      try {
         if (this.st != null) this.st.close();  // close the previous statement
         // this.st = conn.prepareStatement(request);  Changed 2003-06-09 marcel for MS SQL server (thanks to zhang zhi wei)
         this.st = conn.createStatement();
         this.st.setQueryTimeout(this.pool.getQueryTimeout());
//         if (fetchSize > -1) this.st.setFetchSize(fetchSize);
         this.rs = this.st.executeQuery(request);
      }
      catch (SQLException ex) {
         log.fine("inTransactionRequest. Exception: " + ex.getMessage());
         this.isException = true;
         close(WITH_FAILURES);
         throw ex;
      }
      catch (Throwable ex) {
         log.warning("inTransactionRequest. Throwable: " + ex.toString());
         this.isException = true;
         close(WITH_FAILURES);
         throw new XmlBlasterException(this.pool.getGlobal(), ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".inTransactionRequest", "", ex);
      }
      return this.rs;
   }

   public PreparedQuery(JdbcConnectionPool pool, String request, int fetchSize)
      throws SQLException, XmlBlasterException {
      this(pool, request, false, fetchSize);
   }

   /**
    * Close the connection. 
    * Note that this method must always be invoked since it handles both
    * rollback and commit transparently (i.e. in case of a previous exception
    * it makes a rollback, otherwise a commit in case the flag isAutoCommit
    * is false.
    */
   public final void close(boolean success) throws XmlBlasterException, SQLException {

      if (this.isClosed) return;
      if (!success)
         this.isException = true;
      
      try {
         if (!this.conn.getAutoCommit()) {
            if (log.isLoggable(Level.FINE)) 
               log.fine("close with autocommit 'false'");
            try {
               if (this.isException) {
                  log.warning("close with autocommit 'false': rollback");
                  this.conn.rollback();
               }
               else this.conn.commit();
            }
            catch (Throwable ex) {
               log.warning("close: exception when closing statement: " + ex.toString());
               ex.printStackTrace();
            }
            try {
               this.conn.setAutoCommit(true);
            }
            catch (Throwable ex) {
               log.warning("close: exception when setAutoCommit(true): " + ex.toString());
            }
         }
      }
      catch(Throwable e) {
         log.warning("close: exception in first phase when setAutoCommit(true): " + e.toString());
         e.printStackTrace();
      }

      try {
         if (this.st != null) {
            this.st.close();
            this.st = null;
         }
      }
      catch (Throwable ex) {
         log.warning("close: exception when closing statement: " + ex.toString());
      }

      try {
         if (this.conn != null) {
            boolean finalSuccess = !this.isException;
            this.pool.releaseConnection(conn, finalSuccess);
         }
      }
      finally {
         this.conn = null;
         this.isClosed = true;
      }
   }


   /**
    * TODO this method should not be needed. Check if close is really always invoked.
    */
   public void finalize() {
      try {
         if (!this.isClosed) close(SUCCESS);
      }
      catch(Throwable ex) {
         ex.printStackTrace();
      }
   }

}

