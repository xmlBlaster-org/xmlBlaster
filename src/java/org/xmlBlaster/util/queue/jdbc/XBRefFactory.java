/*------------------------------------------------------------------------------
Name:      XbMeatFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.xmlBlaster.contrib.I_Info;

/**
 * @author <a href='mailto:mr@ruff.info'>Marcel Ruff</a>
 * @author <a href='mailto:michele@laghi.eu'>Michele Laghi</a>
 */

public class XBRefFactory extends XBFactory {

   private final static int REF_ID = 1;
   private final static int STORE_ID = 2;
   private final static int MEAT_ID = 3;
   private final static int DURABLE = 4;
   private final static int BYTE_SIZE = 5;
   private final static int META_INFO = 6;
   private final static int FLAG1 = 7;
   private final static int PRIO = 8;
   private String base;

   /**
    * <pre>
    *  xbrefid NUMBER(20) primary key,
    *  xbstoreid NUMBER(20) not null,
    *  xbmeatid NUMBER(20) ,
    *  xbdurable char(1) default 'F' not null ,
    *  xbbytesize NUMBER(10) ,
    *  xbmetainfo clob default '',
    *  xbflag1 varchar(32) default '',
    *  xbprio  NUMBER(10)
    * </pre>
    * 
    */
   
   static String getName() {
      return "xbref";
   }
   
   public XBRefFactory(String prefix) {
      super(prefix, getName());
      base = prefix;
      insertSt = "insert into ${table} values ( ?, ?, ?, ?, ?, ?, ?, ?)";
      deleteSt = "delete from ${table} where xbrefid=?";
      getSt = "select * from ${table} where xbrefid=?";
   }

   protected String getDefaultCreateStatement() {
      StringBuffer buf = new StringBuffer(512);
      
      if (getDbVendor().equals(POSTGRES)) {
         buf.append("create table ${table} (\n");
         buf.append("xbrefid int8 primary key unique not null,\n");
         buf.append("xbstoreid int8 not null,\n");
         buf.append("xbmeatid int8,\n");
         buf.append("-- creationts timestamp not null default current_timestamp,\n");
         buf.append("-- modifiedts timestamp not null default current_timestamp,\n");
         buf.append("durable char(1) not null default 'F',\n");
         buf.append("bytesize int4,\n");
         buf.append("metainfo text default '',\n");
         buf.append("flag1 varchar(32) default '',\n");
         buf.append("prio int4)\n");
      }
      else if (getDbVendor().equals(ORACLE)) {
         buf.append("create table ${table} (\n");
         buf.append("      xbrefid NUMBER(20) primary key,\n");
         buf.append("      xbstoreid NUMBER(20) not null,\n");
         buf.append("      xbmeatid NUMBER(20) ,\n");
         buf.append("      xbdurable char(1) default 'F' not null ,\n");
         buf.append("      xbbytesize NUMBER(10) ,\n");
         buf.append("      xbmetainfo clob default '',\n");
         buf.append("      xbflag1 varchar(32) default '',\n");
         buf.append("      xbprio  NUMBER(10)\n");
         buf.append("    );\n");
        
         buf.append("    alter table xbref \n");
         buf.append("            add constraint fkxbstore\n");
         buf.append("            foreign key (xbstoreid) \n");
         buf.append("            references ${xbstore};\n");
         
         buf.append("    alter table xbref \n");
         buf.append("            add constraint fkxbmeat\n");
         buf.append("            foreign key (xbmeatid) \n");
         buf.append("            references ${xbmeat};\n");
      }
      /*
      else if (getDbVendor().equals(DB2)) {
         
      }
      else if (getDbVendor().equals(FIREBIRD)) {
         
      }
      else if (getDbVendor().equals(SQLSERVER_2000) || getDbVendor().equals(SQLSERVER_2005)) {
         
      }
      else if (getDbVendor().equals(MYSQL)) {
         
      }
      else if (getDbVendor().equals(SQLITE)) {
         
      }
      */
      else { // if (getDbVendor().equals(HSQLDB))
         buf.append("create table ${table} (\n");
         buf.append("      xbrefid integer primary key,\n");
         buf.append("      xbstoreid integer not null,\n");
         buf.append("      xbmeatid integer ,\n");
         buf.append("      xbdurable char(1) default 'F' not null ,\n");
         buf.append("      xbbytesize integer ,\n");
         buf.append("      xbmetainfo varchar default '',\n");
         buf.append("      xbflag1 varchar(32) default '',\n");
         buf.append("      xbprio  integer\n");
         buf.append("    );\n");

         buf.append("    alter table ${table} \n");
         buf.append("            add constraint fkxbstore\n");
         buf.append("            foreign key (xbstoreid) \n");
         buf.append("            references ${xbstore};\n");

         buf.append("    alter table ${table} \n");
         buf.append("            add constraint fkxbmeat\n");
         buf.append("            foreign key (xbmeatid) \n");
         buf.append("            references ${xbmeat};\n");
      }
      return buf.toString();
   }
   
   protected void doInit(I_Info info) {
      String tmp = info.get(base + ".table." + XBStoreFactory.getName(), XBStoreFactory.getName());
      info.put(XBStoreFactory.getName(), tmp);
      tmp = info.get(base + ".table." + XBMeatFactory.getName(), XBMeatFactory.getName());
      info.put(XBMeatFactory.getName(), tmp);
   }
   
   /**
    * Inserts an entry in the database
    * @param table
    * @param xbMeat The object to store. Note that 
    * @param conn The database connection to use
    * @param timeout the time in seconds it has to wait for a response. If less than 1 it is not
    * set.
    * @throws SQLException If an exception occurs in the backend. For example if the entry already
    * exists in the database.
    */
   public void insert(XBRef xbRef, Connection conn, int timeout) throws SQLException, UnsupportedEncodingException {
      if (xbRef == null || conn == null)
         return;
      PreparedStatement preStatement = conn.prepareStatement(insertSt);
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         preStatement.setLong(REF_ID, xbRef.getRefId());
         preStatement.setLong(STORE_ID, xbRef.getStoreId());
         long val = xbRef.getMeatId();
         if (val > -1)
            preStatement.setLong(MEAT_ID, val);
         else
            preStatement.setNull(MEAT_ID, Types.NUMERIC);

         if (xbRef.isDurable())
            preStatement.setString(DURABLE, "T");
         else 
            preStatement.setString(DURABLE, "F");

         preStatement.setLong(BYTE_SIZE, xbRef.getByteSize());
         
         InputStream qosStream = new ByteArrayInputStream(xbRef.getMetaInfo().getBytes("UTF-8"));
         preStatement.setAsciiStream(META_INFO, qosStream, xbRef.getMetaInfo().length());
         
         preStatement.setString(FLAG1, xbRef.getFlag1());
         preStatement.setInt(PRIO, xbRef.getPrio());

         preStatement.execute();
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
   }

   /**
    * 
    * @param sql The select statement to use to fill the objects.
    * @param conn
    * @return null if the object has not been found or the object if it has been found on the backend.
    * @throws SQLException
    */
   public XBRef get(long id, Connection conn, int timeout) throws SQLException, IOException {
      XBRef xbRef = new XBRef();
      if (conn == null)
         return xbRef;
      PreparedStatement preStatement = conn.prepareStatement(deleteSt);
      ResultSet rs = null;
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         preStatement.setLong(1, id);
         rs = preStatement.executeQuery();
         if (!rs.next())
            return null;
         
         xbRef.setRefId(rs.getLong(REF_ID));
         xbRef.setStoreId(rs.getLong(STORE_ID));
         xbRef.setMeatId(rs.getLong(MEAT_ID));

         String tmp = rs.getString(DURABLE);
         if ("T".equalsIgnoreCase(tmp))
            xbRef.setDurable(true);

         xbRef.setByteSize(rs.getLong(BYTE_SIZE));
         
         InputStream stream = rs.getAsciiStream(META_INFO);
         xbRef.setMetaInfo(new String(readStream(stream), "UTF-8"));

         xbRef.setFlag1(rs.getString(FLAG1));
         xbRef.setPrio(rs.getInt(PRIO));
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
      
      return xbRef;
   }
   
}
