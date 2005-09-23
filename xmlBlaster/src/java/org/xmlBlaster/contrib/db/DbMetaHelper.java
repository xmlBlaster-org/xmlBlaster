/*------------------------------------------------------------------------------
Name:      DbMetaHelper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;

/**
 * 
 * DbMetaHelper offers methods which take care of stuff specific to particular databases. For
 * example if a database stores table names in uppercase you don't need to bother about this
 * knowledge. You just pass the name of the table to the method getUnquotedIdentifier and you
 * get back the correct name (if uppercase it will be uppercase).
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class DbMetaHelper {

   private final static int CASE_UNKNOWN = 0;
   private final static int CASE_MIXED = 1;
   private final static int CASE_UPPER = 2;
   private final static int CASE_LOWER = 3;

   private int caseSense = CASE_UNKNOWN;
   
   /**
    * Initializes the object by reading the metadata of this database.
    * @param pool the pool must be non null and initialized.
    * @throws Exception If a backend exception occurs.
    * 
    */
   public DbMetaHelper(I_DbPool pool) throws Exception {
      if (pool == null)
         throw new Exception("DbMetaHelper constructor: the pool is null");
      Connection conn = null;
      try {
         conn = pool.reserve();
         DatabaseMetaData meta = conn.getMetaData();
         if (meta.storesLowerCaseIdentifiers())
            this.caseSense = CASE_LOWER;
         else if (meta.storesUpperCaseIdentifiers())
            this.caseSense = CASE_UPPER;
         else if (meta.storesMixedCaseIdentifiers())
            this.caseSense = CASE_MIXED;
         else
            throw new Exception("DbMetaHelper constructor: can not determine which case the identifiers are stored");
      }
      finally {
         if (conn != null)
            pool.release(conn);
      }
   }
   
   
   /**
    * Returns the correct identifier depending on the properties of the database. If it can not
    * determine the case of the identifier it returns null.
    * @param proposedName the name which is proposed.
    * @return
    */
   public String getIdentifier(String proposedName) {
      switch (this.caseSense) {
      case CASE_MIXED : return proposedName;
      case CASE_UPPER : return proposedName.toUpperCase();
      case CASE_LOWER : return proposedName.toLowerCase();
      default:return null; 
      }
   }
   
   
}
