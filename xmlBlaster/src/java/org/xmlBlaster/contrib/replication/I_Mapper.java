/*------------------------------------------------------------------------------
Name:      I_Mapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import org.xmlBlaster.contrib.dbwriter.I_ContribPlugin;

public interface I_Mapper extends I_ContribPlugin {

   /**
    * Returns the mapped catalog or null.
    * 
    * @param catalog The catalog name of the source (the master catalog). Null is allowed and means unspecified.
    * @param schema The schema name of the source (the master). Null is allowed and means unspecified.
    * @param table The table name of the source. On null it is considered a default.
    * @param column The column name of the source. On null it is considered a default.
    * @return the catalog name of the destination (the slave catalog), or null which means that catalog info will not be
    * used on the destination.
    * 
    */
   String getMappedCatalog(String catalog, String schema, String table, String column);
   
   /**
    * Returns the mapped schema or null.
    * 
    * @param catalog The catalog name of the source (the master). Null is allowed and means unspecified.
    * @param schema The schema name of the source (the master). Null is allowed and means unspecified.
    * @param table The table name of the source. On null it is considered a default.
    * @param column The column name of the source. On null it is considered a default.
    * @return the schema name of the destination (the slave), or null which means that schema info will not be
    * used on the destination.
    */
   String getMappedSchema(String catalog, String schema, String table, String column);
   
   /**
    * 
    * Gets the mapped table.
    * 
    * @param catalog The catalog name of the source (the master). Null is allowed and means unspecified.
    * @param schema The schema name of the source (the master). Null is allowed and means unspecified.
    * @param table The table name of the source. On null the behaviour is unspecified and implementation dependant.
    * @param column The column name of the source. On null the behaviour is unspecified and implementation dependant.
    * @return the table name to be used on the slave. If null it means that this table is not further processed.
    */
   String getMappedTable(String catalog, String schema, String table, String column);
   
   /**
    * Gets the mapped column.
    * 
    * @param catalog The catalog name of the source (the master). Null is allowed and means unspecified.
    * @param schema The schema name of the source (the master). Null is allowed and means unspecified.
    * @param table The table name of the source. On null the behaviour is unspecified and implementation dependant.
    * @param column The column name of the source. On null the behaviour is unspecified and implementation dependant.
    * @return the table name to be used on the slave. If null it means that this table is not further processed.
    */
   String getMappedColumn(String catalog, String Schema, String table, String column);

   /**
    * This method is currently unused, and is here for future releases.
    * 
    * @param catalog The catalog name of the source (the master). Null is allowed and means unspecified.
    * @param schema The schema name of the source (the master). Null is allowed and means unspecified.
    * @param table The table name of the source. On null the behaviour is unspecified and implementation dependant.
    * @param column The column name of the source. On null the behaviour is unspecified and implementation dependant.
    * @param type
    * @return
    */
   String getMappedType(String catalog, String Schema, String table, String column, String type);
   
}
