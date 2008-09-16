/*------------------------------------------------------------------------------
Name:      XbMeatFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import java.util.Properties;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.InfoHelper;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * @author <a href='mailto:mr@ruff.info'>Marcel Ruff</a>
 * @author <a href='mailto:michele@laghi.eu'>Michele Laghi</a>
 */

public abstract class XBFactoryBase {

   private final static Logger log = Logger.getLogger(XBFactoryBase.class.getName());
   public final static String POSTGRES = "postgres";
   public final static String ORACLE = "oracle";
   public final static String DB2 = "db2";
   public final static String FIREBIRD = "firebird";
   public final static String SQLSERVER_2000 = "sqlserver2000";
   public final static String SQLSERVER_2005 = "sqlserver2005";
   public final static String HSQLDB = "hsqldb";
   public final static String MYSQL = "mysql";
   public final static String LDBC = "ldbc";
   public final static String SQLITE = "sqlite";
   public final static String UNKNOWN = "unknown";
   
   private String dbVendor;
   
   /**
    * 
    * <pre>
    * xbmeatid NUMBER(20) primary key,
    * xbdurable char default 'F' not null,
    * xbrefcount NUMBER(10),
    * xbbytesize NUMBER(10),
    * xbdatatype varchar(32) default '' not null,
    * xbflag1 varchar(32) default '',
    * xbmsgqos clob default '',
    * xbmsgcont blob default '',
    * xbmsgkey clob default ''
    * </pre>
    * 
    * @return
    */
   
   
   public XBFactoryBase() {
   }

   abstract protected void doInit(I_Info info) throws XmlBlasterException;

   public I_Info init(I_Info origInfo) throws XmlBlasterException {
      // we take a clone to avoid contaminate the original info with the table settings
      I_Info info = new PropertiesInfo(new Properties());
      InfoHelper.fillInfoWithEntriesFromInfo(info, origInfo);
      
      
      String url = info.get("db.url", null);
      if (url != null) {
         if (url.startsWith("jdbc:postgresql:"))
            dbVendor = POSTGRES;
         else if (url.startsWith("jdbc:oracle:"))
            dbVendor = ORACLE;
         else if (url.startsWith("jdbc:db2:"))
            dbVendor = DB2;
         else if (url.startsWith("jdbc:firebirdsql:"))
            dbVendor = FIREBIRD;
         else if (url.startsWith("jdbc:microsoft:sqlserver:"))
            dbVendor = SQLSERVER_2000;
         else if (url.startsWith("jdbc:sqlserver:"))
            dbVendor = SQLSERVER_2005;
         else if (url.startsWith("jdbc:hsqldb:"))
            dbVendor = HSQLDB;
         else if (url.startsWith("jdbc:mysql:"))
            dbVendor = MYSQL;
         else if (url.startsWith("jdbc:ldbc:"))
            dbVendor = LDBC;
         else if (url.startsWith("jdbc:sqlite:"))
            dbVendor = SQLITE;
         else {
            log.info("Could not determine the database type by analyzing the url '" + url + "' will set it to " + UNKNOWN + "'");
            dbVendor = UNKNOWN;
         }
      }
      return info;
   }
   
   public final String getDbVendor() {
      return dbVendor;
   }
   
}