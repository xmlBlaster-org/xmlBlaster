/*------------------------------------------------------------------------------
Name:      DefaultMapping.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.InfoHelper;
import org.xmlBlaster.contrib.db.DbMetaHelper;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwriter.DbWriter;
import org.xmlBlaster.contrib.replication.I_Mapper;

/**
 * 
 * DefaultMapper makes a one to one mapping unless for tables in which case it is read from the property 'replication.mapper.tables'.
 * This property will follow the syntax:
 * table1=tableOne,table2=tableTwo,...
 * All tables not found the the configuration are mapped one to one.
 * 
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class DefaultMapper implements I_Mapper {

   private static Logger log = Logger.getLogger(DefaultMapper.class.getName());
   protected Map columnMap;
   protected Map tableMap;
   protected Map schemaMap;
   
   public DefaultMapper() {
      this.schemaMap = new HashMap();
      this.tableMap = new HashMap();
      this.columnMap = new HashMap();
   }
   
   public String getMappedCatalog(String catalog, String schema, String table, String column, String def) {
      return def;
   }

   /**
    * Table and column are ignored here. Schema must be specified. If no schema, 
    * then null is returned.
    */
   public String getMappedSchema(String catalog, String schema, String table, String column, String def) {
      if (schema == null)
         return null;
      String ret = null;
      if (catalog != null) {
         ret = (String)this.schemaMap.get(catalog + "." + schema);
         if (ret != null) 
            return ret;
      }
      ret = (String)this.schemaMap.get(schema);
      if (ret == null)
         return def;
      return ret;
   }

   /**
    * Columns are ignored here.
    */
   public String getMappedTable(String catalog, String schema, String table, String column, String def) {
      if (table == null)
         return null;
      // catalog.schema.table
      // schema.table
      // table
      String ret = null;
      if (catalog != null) {
         if (schema != null) {
            ret = (String)this.tableMap.get(catalog + "." + schema + "." + table);
            if (ret != null) 
               return ret;
         }
      }
      if (schema != null) {
         ret = (String)this.tableMap.get(schema + "." + table);
         if (ret != null)
            return ret;
      }
      ret = (String)this.tableMap.get(table);
      if (ret == null)
         return def;
      return ret;
   }

   public String getMappedColumn(String catalog, String schema, String table, String column, String def) {
      if (table == null)
         return null;
      // catalog.schema.table.column
      // schema.table.column
      // table.column
      // column
      String ret = null;
      if (catalog != null) {
         if (schema != null) {
            if (table != null) {
               ret = (String)this.columnMap.get(catalog + "." + schema + "." + table + "." + column);
               if (ret != null) 
                  return ret;
            }
         }
      }
      if (schema != null) {
         if (table != null) {
            ret = (String)this.columnMap.get(schema + "." + table + "." + column);
            if (ret != null) 
               return ret;
         }
      }
      if (table != null) {
         ret = (String)this.columnMap.get(table + "." + column);
         if (ret != null) 
            return ret;
      }
      
      ret = (String)this.columnMap.get(column);
      if (ret == null)
         return def;
      return ret;
   }

   public String getMappedType(String catalog, String Schema, String table, String column, String type, String def) {
      return def;
   }

   /**
    * @see org.xmlBlaster.contrib.I_ContribPlugin#getUsedPropertyKeys()
    */
   public Set getUsedPropertyKeys() {
      Set set = new HashSet();
      set.add("replication.mapper.schema");
      set.add("replication.mapper.table");
      set.add("replication.mapper.column");
      return set;
   }

   public void init(I_Info info) throws Exception {
      log.info("init");
      I_DbPool pool = (I_DbPool)info.getObject(DbWriter.DB_POOL_KEY);
      DbMetaHelper dbHelper = null;
      if (pool == null) {
         log.warning("DefaultMapper.init: the pool has not been configured, please check your '" + DbWriter.DB_POOL_KEY + "' configuration settings");
      }
      else
         dbHelper = new DbMetaHelper(pool);
      doInit(info, dbHelper);
   }
   
   protected void doInit(I_Info info, DbMetaHelper dbHelper) throws Exception {
      this.schemaMap = InfoHelper.getPropertiesStartingWith("replication.mapper.schema.", info, dbHelper);
      this.tableMap = InfoHelper.getPropertiesStartingWith("replication.mapper.table.", info, dbHelper);
      this.columnMap = InfoHelper.getPropertiesStartingWith("replication.mapper.column.", info, dbHelper);
   }
   
   public void shutdown() throws Exception {
      this.tableMap.clear();
   }

}
