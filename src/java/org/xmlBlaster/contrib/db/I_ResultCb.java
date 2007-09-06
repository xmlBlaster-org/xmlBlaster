/*------------------------------------------------------------------------------
Name:      TestResultSetToXmlConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.db;

import java.sql.Connection;
import java.sql.ResultSet;

/**
 * Interface to receive the ResultSet for a SELECT execution. 
 * <p>Example usage:</p>
 * <pre>
this.dbPool.select("SELECT * FROM A", new I_ResultCb() {
   public void result(ResultSet rs) throws Exception {
      while (rs != null && rs.next()) {
         ... // Process the query results
      }
   }
});
 * </pre>
 * @author Marcel Ruff 
 */
public interface I_ResultCb {

   /**
    * Callback with the ResultSet. 
    * @param rs The current ResultSet, is null if table or view does not exist
    * @throws Exception Can be of any type
    */
   void result(Connection conn, ResultSet rs) throws Exception;
}
