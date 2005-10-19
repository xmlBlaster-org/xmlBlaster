/*------------------------------------------------------------------------------
Name:      DefaultMapping.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.PropertiesInfo;
import org.xmlBlaster.contrib.dbwriter.DbWriter;
import org.xmlBlaster.contrib.replication.I_DbSpecific;
import org.xmlBlaster.contrib.replication.I_Mapper;
import org.xmlBlaster.contrib.replication.ReplicationConverter;

/**
 * 
 * DefaultMapper makes a one to one mapping unless for tables in which case it is read from the property 'replication.mapper.tables'.
 * This property will follow the syntax:
 * table1=tableOne,table2=tableTwo,...
 * All tables not found the the configuration are mapped one to one.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class DefaultMapper implements I_Mapper {

   private static Logger log = Logger.getLogger(DefaultMapper.class.getName());
   private Map tableMap;
   private boolean caseSensitive;
   
   public DefaultMapper() {
      this.tableMap = new HashMap();
   }
   
   public String getMappedCatalog(String catalog, String schema, String table, String column) {
      return catalog;
   }

   public String getMappedSchema(String catalog, String schema, String table, String column) {
      return schema;
   }

   public String getMappedTable(String catalog, String schema, String table, String column) {
      if (table == null)
         return null;
      String ret = (String)this.tableMap.get(table);
      if (!this.caseSensitive && ret == null) {
         ret = (String)this.tableMap.get(table.toLowerCase());
         if (ret == null)
            ret = (String)this.tableMap.get(table.toUpperCase());
      }
      if (ret != null)
         return ret;
      return table;
   }

   public String getMappedColumn(String catalog, String Schema, String table, String column) {
      return column;
   }

   public String getMappedType(String catalog, String Schema, String table, String column, String type) {
      return type;
   }

   public void init(I_Info info) throws Exception {
      log.info("init");
      String tableMapping = info.get("replication.mapper.tables", null);
      if (tableMapping != null && tableMapping.trim().length() > 0) {
         StringTokenizer tokenizer = new StringTokenizer(tableMapping, ",");
         while (tokenizer.hasMoreTokens()) {
            String tablePair = tokenizer.nextToken().trim();
            int pos = tablePair.indexOf('=');
            if (pos < 0)
               throw new Exception("Configuration error in Table Mapper: '" + tableMapping + "' is wrong at '" + tablePair + "' please check the syntax");
            String sourceTable = tablePair.substring(0, pos);
            String destTable = tablePair.substring(pos+1);
            log.fine("init: mapping for source table '" + sourceTable + "' to destination '" + destTable + "'");
            this.tableMap.put(sourceTable, destTable);
         }
         
         this.caseSensitive = info.getBoolean(DbWriter.CASE_SENSITIVE_KEY, false);
         
      }
   }

   public void shutdown() throws Exception {
      this.tableMap.clear();
   }

   public void main(String[] args) {
      try {
         I_Info info = new PropertiesInfo(System.getProperties());
         I_DbSpecific dbSpecific = ReplicationConverter.getDbSpecific(info);
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
      
      
   }
   
   
}
