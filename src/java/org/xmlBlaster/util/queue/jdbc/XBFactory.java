/*------------------------------------------------------------------------------
Name:      XbMeatFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.InfoHelper;
import org.xmlBlaster.contrib.PropertiesInfo;

/**
 * @author <a href='mailto:mr@ruff.info'>Marcel Ruff</a>
 * @author <a href='mailto:michele@laghi.eu'>Michele Laghi</a>
 */

public abstract class XBFactory {

   private final static Logger log = Logger.getLogger(XBMeatFactory.class.getName());
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
   
   protected String insertSt;
   protected String getSt;
   protected String deleteSt;
   protected String createSt;
   protected String dropSt = "drop table ${table}";
   protected String prefix = "queue.jdbc";
   protected String table;
   private String tableNameDefault;
   
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
   
   
   public XBFactory(String prefix, String name) {
      tableNameDefault = name;
      if (prefix != null)
         this.prefix = prefix + "." + name;
      else
         prefix += "." + name;
   }

   abstract protected void doInit(I_Info info);

   public final void init(I_Info origInfo) {
      table = origInfo.get(prefix + ".table." + tableNameDefault, tableNameDefault);

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
      
      createSt = getDefaultCreateStatement();
      info.put("table", table);
      doInit(info);

      insertSt = info.get(prefix + ".insertStatement", insertSt);
      deleteSt = info.get(prefix + ".deleteStatement", deleteSt);
      getSt = info.get(prefix + ".getStatement", getSt);
      createSt = info.get(prefix + ".getStatement", createSt);
      dropSt = info.get(prefix + ".getStatement", dropSt);
   }
   
   public void delete(long id, Connection conn, int timeout) throws SQLException {
      if (conn == null)
         return;
      PreparedStatement preStatement = conn.prepareStatement(deleteSt);
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         preStatement.setLong(1, id);
         preStatement.execute();
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
   }

   /**
    * Checks if the table already exists, the check is done against
    * the meta data.
    * 
    * @param conn
    * @return
    * @throws SQLException
    */
   private final boolean exists(Connection conn) throws SQLException {
      final String[] types = { "TABLE" };

      ResultSet rs = null;
      try {
         // specifying a table does not seem to work with hsqldb
         rs = conn.getMetaData().getTables(null, null, table, types);
         if (rs.next())
            return true;
         rs.close();
         rs = conn.getMetaData().getTables(null, null, null, types);

         while (rs.next()) { // retrieve the result set ...
            String tmp = rs.getString(3).toUpperCase();
            if (tmp.equalsIgnoreCase(table))
               return true;
         }
         return false;
      }
      finally {
         if (rs != null)
            rs.close();
      }
   }
   
   /**
    * Returns true if the table has been created, false otherwise. It returns false for example
    * if the table existed already, in which case it is not newly created.
    * @param conn
    * @param timeout
    * @return
    * @throws SQLException
    */
   public  boolean create(Connection conn) throws SQLException {
      if (exists(conn))
         return false;
      Statement st = null;
      try {
         st = conn.createStatement();
         
         StringTokenizer tokenizer = new StringTokenizer(createSt, ";");
         while (tokenizer.hasMoreTokens()) {
            String sql = tokenizer.nextToken();
            if (sql.trim().length() > 0)
               st.executeUpdate(sql);
         }
      }
      finally {
         if (st != null)
            st.close();
      }
      return true;
   }
   
   /**
    * Returns true if it could delete the table, false otherwise. It would return false 
    * if the table did not exist.
    * @param conn
    * @param timeout
    * @return
    * @throws SQLException
    */
   public final boolean drop(Connection conn) throws SQLException {
      if (!exists(conn))
         return false;
      Statement st = null;
      try {
         st = conn.createStatement();
         st.executeUpdate(dropSt);
         return true;
      }
      finally {
         if (st != null)
            st.close();
      }
   }
   
   protected final byte[] readStream(InputStream inStream) throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      int nmax = 262144; // max 200 kb at a time
      int avail = 0;
      byte[] buf = new byte[nmax];
      while ( (avail = inStream.available()) > 0) {
         if (avail > nmax)
            avail = nmax;
         inStream.read(buf, 0, avail);
         baos.write(buf, 0, avail);
      }
      return baos.toByteArray();
   }
   
   public final String getDbVendor() {
      return dbVendor;
   }
   

   /**
    * This method must be implemented in all underlying extentions to this class. It returns (for the different
    * database vendors a default for the creation of the table.
    * @return
    */
   protected abstract String getDefaultCreateStatement();
   
}