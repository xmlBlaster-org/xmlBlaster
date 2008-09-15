/*------------------------------------------------------------------------------
Name:      I_DBPool.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.db;

import java.sql.Connection;

import org.xmlBlaster.contrib.I_ContribPlugin;
import org.xmlBlaster.contrib.I_Info;


/**
 * Interface to a JDBC pool implementation.
 * @author Marcel Ruff 
 */
public interface I_DbPool extends I_ContribPlugin {
   /**
    * Needs to be called after construction. 
    * @param info The configuration
    */
   void init(I_Info info);
   
   /**
   * Access a JDBC connection. 
   * @return The JDBC connection
   * @throws Exception
   */
   Connection reserve() throws Exception;
   
   /**
   * Return the JDBC connection to the pool. 
   * @param con The JDBC connection
   * @throws Exception
   */
   void release(Connection con)  throws Exception;
   
   /**
    * 
    * @return the name of the user
    */
   String getUser();
   
   /**
   * Destroy the JDBC connection
   * @param con The JDBC connection
   * @throws Exception
   */
   void erase(Connection con) throws Exception;
   
   /**
    * Convenience method to execute a INSERT/UPDATE/CREATE or DROP SQL command in auto commit mode.  
    * @param command for example <tt>INSERT INTO TEST_POLL VALUES ('1', 'EDDI')</tt>
    * @return Number of touched entries
    * @throws Exception Typically a SQLException
    */ 
   int update(String command) throws Exception;

   int update(Connection conn, String command) throws Exception;
   
   /**
    * Convenience method to execute a SELECT SQL command in auto commit mode.  
    * @param command for example <tt>SELECT * FROM TEST_POLL</tt>
    * @param cb The callback handle for the retrieved ResultSet
    * @throws Exception Typically a SQLException
    */ 
   void select(String command, I_ResultCb cb) throws Exception;
   
   /**
    * Convenience method to execute a SELECT SQL command. 
    * <p>
    * On first call pass <tt>connection</tt> with <tt>null</tt> and a valid
    * connection with an open transaction is returned by this method.
    * You can now call this method passing the <tt>connection</tt> multiple times
    * in the same transaction. When you are done you need to close the
    * connection and put it back into the pool.
    * </p>
    * Example:
    * <pre>
      Connection conn = null;

      try {
         conn = this.dbPool.select(conn, "SELECT A FROM B", new I_ResultCb() {
            public void result(ResultSet rs) throws Exception {
               // Processing result set
            }
         });

         conn = this.dbPool.select(conn, "SELECT B FROM  C", new I_ResultCb() {
            public void result(ResultSet rs) throws Exception {
               // Processing result set
            }
         });
      }
      finally {
         if (conn != null) {
            conn.commit();
            this.dbPool.release(conn);
         }
      }
    * </pre>
    *  
    * @param connection If null a connection is created
    * @param command for example <tt>SELECT * FROM TEST_POLL</tt>
    * @param cb The callback handle for the retrieved ResultSet
    * @throws Exception Typically a SQLException
    * @return If not null you need to put the connection back to the pool yourself
    */
   Connection select(Connection connection, String command, I_ResultCb cb) throws Exception;

   /**
    * To have full control. 
    * @param connection If null a connection is created
    * @param command for example <tt>SELECT * FROM TEST_POLL</tt>
    * @param autoCommit if true force auto commit and cleanup the connection, in this case we return null
    * @param cb The callback handle for the retrieved ResultSet
    * @throws Exception Typically a SQLException
    * @return If not null you need to put the connection back to the pool yourself
    */
   Connection select(Connection connection, String command, boolean autoCommit, I_ResultCb cb) throws Exception;

   /**
    * Close all open connections and destroy the pool. 
    * All resources are released.
    * @throws Exception 
    */
   void shutdown() throws Exception;
}
