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
import org.jutils.log.LogChannel;

/**
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 */

public class PreparedQuery {
   public final static String ME = "PreparedQuery";
   private LogChannel log;
   public Connection conn;
   //private PreparedStatement st; Changed 2003-06-09 marcel for MS SQL server (thanks to zhang zhi wei)
   private Statement st;
   public ResultSet rs;
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
   public PreparedQuery(JdbcConnectionPool pool, String request, boolean isAutoCommit, LogChannel log, int fetchSize)
      throws SQLException, XmlBlasterException {
      this.log = log;
      this.pool = pool;
      this.isClosed = false;
      if (this.log.CALL)
         this.log.call(ME, "Constructor. autocommit is '" + isAutoCommit + "'");

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
         this.log.trace(ME, "Constructor. Exception. " + additionalInfo + ": " + ex.getMessage());
         if (this.conn != null) {
            try {
               if (!this.conn.getAutoCommit()) {
                  this.conn.rollback();
                  this.conn.setAutoCommit(true);
               }
               if (this.st != null) st.close();
            }
            catch (Throwable ex2) {
               this.log.warn(ME, "constructor exception occured when rolling back " + additionalInfo + ": " + ex2.toString());
            }
            if (this.conn != null) this.pool.releaseConnection(this.conn);
            this.conn = null;
         }
         this.isClosed = true;
         throw ex;
      }
      catch (SQLException ex) {
         String additionalInfo = "request='" + request + "' isAutocommit='" + isAutoCommit + "' fetchSize='" + fetchSize + "' ";
         this.log.trace(ME, "Constructor. " + additionalInfo + " SQLException: " + ex.getMessage());
         if (this.conn != null) {
            try {
               if (!this.conn.getAutoCommit()) {
                  this.conn.rollback();
                  this.conn.setAutoCommit(true);
               }
               if (this.st != null) st.close();
            }
            catch (Throwable ex2) {
               this.log.warn(ME, "constructor: exception occured when handling SQL Exception: " + additionalInfo + ex2.toString());
            }
            if (this.conn != null) this.pool.releaseConnection(this.conn);
            this.conn = null;
         }
         this.isClosed = true;
         throw ex;
      }
      catch (Throwable ex) {
         String additionalInfo = "request='" + request + "' isAutocommit='" + isAutoCommit + "' fetchSize='" + fetchSize + "' ";
         this.log.warn(ME, "Constructor. Throwable: " + additionalInfo + ex.toString());
         if (this.conn != null) {
            try {
               if (!this.conn.getAutoCommit()) {
                  this.conn.rollback();
                  this.conn.setAutoCommit(true);
               }
               if (this.st != null) st.close();
            }
            catch (Throwable ex2) {
               this.log.warn(ME, "constructor: " + additionalInfo + " exception occured when handling SQL Exception: " + ex2.toString());
            }
            if (this.conn != null) this.pool.releaseConnection(this.conn);
            this.conn = null;
         }
         this.isClosed = true;
         throw new XmlBlasterException(this.pool.getGlobal(), ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".constructor " + additionalInfo, "", ex);
      }
   }

   public final ResultSet inTransactionRequest(String request /*, int fetchSize */)
      throws XmlBlasterException, SQLException {

      this.log.call(ME, "inTransactionRequest: " + request);
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
         this.log.trace(ME, "inTransactionRequest. Exception: " + ex.getMessage());
         this.isException = true;
         close();
         throw ex;
      }
      catch (Throwable ex) {
         this.log.warn(ME, "inTransactionRequest. Throwable: " + ex.toString());
         close();
         throw new XmlBlasterException(this.pool.getGlobal(), ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".inTransactionRequest", "", ex);
      }
      return this.rs;
   }


   public PreparedQuery(JdbcConnectionPool pool, String request, LogChannel log, int fetchSize)
      throws SQLException, XmlBlasterException {
      this(pool, request, false, log, fetchSize);
   }


   /**
    * Close the connection. 
    * Note that this method must always be invoked since it handles both
    * rollback and commit transparently (i.e. in case of a previous exception
    * it makes a rollback, otherwise a commit in case the flag isAutoCommit
    * is false.
    */
   public final void close() throws XmlBlasterException, SQLException {

      if (this.isClosed) return;

      if (!this.conn.getAutoCommit()) {
         if (this.log.TRACE) this.log.trace(ME, "close with autocommit 'false'");
         try {
            if (this.isException) {
               this.log.warn(ME, "close with autocommit 'false': rollback");
               this.conn.rollback();
            }
            else this.conn.commit();
         }
         catch (Throwable ex) {
            this.log.warn(ME, "close: exception when closing statement: " + ex.toString());
         }

         try {
            this.conn.setAutoCommit(true);
         }
         catch (Throwable ex) {
            this.log.warn(ME, "close: exception when setAutoCommit(true): " + ex.toString());
         }
      }

     try {
         if (this.st != null) {
            st.close();
            this.st = null;
         }
     }
     catch (Throwable ex) {
        this.log.warn(ME, "close: exception when closing statement: " + ex.toString());
     }

     if (this.conn != null) this.pool.releaseConnection(this.conn);
     // TODO: if we had an exception: this.pool.destroyConnection(this.conn)
     this.conn = null;
     this.isClosed = true;
   }


   public void finalize() {
      try {
         if (!this.isClosed) close();
      }
      catch(Exception ex) {
      }
   }

}

