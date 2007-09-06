/*------------------------------------------------------------------------------
Name:      SearchableConfig.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.InfoHelper;
import org.xmlBlaster.contrib.db.DbMetaHelper;

/**
 * SearchableConfig
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class SearchableConfig extends DefaultMapper {

   public final static String NAME = "__" + SearchableConfig.class.getName();
   
   public SearchableConfig() {
      super();
   }

   /**
    * @see org.xmlBlaster.contrib.replication.impl.DefaultMapper#doInit(org.xmlBlaster.contrib.I_Info, org.xmlBlaster.contrib.db.DbMetaHelper)
    */
   protected void doInit(I_Info info, DbMetaHelper dbHelper) throws Exception {
      String prefix = "replication.searchable.";
      this.schemaMap = new HashMap();
      this.tableMap = InfoHelper.getPropertiesStartingWith(prefix, info, dbHelper);
      this.columnMap = new HashMap();
   }

   
   public Set getSearchableColumnNames(String catalog, String schema, String table) {
      String ret = getMappedTable(catalog, schema, table, null, null);
      if (ret == null)
         return null;
      final String separator = ",";
      Set set = new HashSet();
      if (ret.indexOf(separator) > -1) {
         StringTokenizer tokenizer = new StringTokenizer(ret, ",");
         while (tokenizer.hasMoreElements()) {
            String columnName = tokenizer.nextToken().trim();
            set.add(columnName);
         }
      }
      else 
         set.add(ret);
      return set;
   }
   
}
