/*------------------------------------------------------------------------------
Name:      XbMeatFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.xmlBlaster.contrib.I_Info;

/**
 * @author <a href='mailto:mr@ruff.info'>Marcel Ruff</a>
 * @author <a href='mailto:michele@laghi.eu'>Michele Laghi</a>
 */

public class XBMeatFactory extends XBFactory {

   private final static int ID = 1;
   private final static int DURABLE = 2;
   private final static int REF_COUNT = 3;
   private final static int BYTE_SIZE = 4;
   private final static int DATA_TYPE = 5;
   private final static int FLAG1 = 6;
   private final static int QOS = 7;
   private final static int CONTENT = 8;
   private final static int KEY = 9;

   private String updateRefCounterSt;
   private String updateSt;
   private String incRefCounterFunction;
   private String incRefCounterInvoke;
   
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
   
   
   static String getName() {
      return "xbmeat";
   }
   
   public XBMeatFactory(String prefix) {
      super(prefix, getName());
      insertSt = "insert into ${table} values ( ?, ?, ?, ?, ?, ?, ?, ?, ?)";
      deleteSt = "delete from ${table} where xbmeatid=?";
      deleteTransientsSt = deleteAllSt + " where xbdurable='F'";
      getSt = getAllSt + " where xbmeatid=?";
      updateSt = "update ${table} set xbmeatid=?,xbdurable=?,xbrefcount=?,xbbytesize=?,xbdatatype=?,xbflag1=?,xbqos=?,xbcontent=?,xbkey=? where xbmeatid=?";
      updateRefCounterSt = "update ${table} set xbrefcount=? where xbmeatid=?";
      getAllSt = "select * from ${table}";
      inList = " where xbmeatid in ("; 
   }

   
   protected void prepareDefaultStatements() {
      super.prepareDefaultStatements();
      incRefCounterFunction = null;
      incRefCounterInvoke = null;
      StringBuffer buf = new StringBuffer(512);
      if (getDbVendor().equals(POSTGRES)) {
         buf.append("create table ${table} (\n");
         buf.append("      -- xbmeatid bigserial not null,\n");
         buf.append("      xbmeatid int8 primary key unique not null,\n");
         buf.append("      -- creationts timestamp not null default current_timestamp,\n");
         buf.append("      -- modifiedts timestamp not null default current_timestamp,\n");
         buf.append("      xbdurable char not null default 'F',\n");
         buf.append("      xbrefcount int4,\n");
         buf.append("      xbbytesize int4,\n");
         buf.append("      xbdatatype varchar(32) not null default '',\n");
         buf.append("      xbflag1 varchar(32) default '',\n");
         buf.append("      xbmsgqos text default '',\n");
         buf.append("      xbmsgcont bytea default '',\n");
         buf.append("      xbmsgkey text default '')\n");
      }
      else if (getDbVendor().equals(ORACLE)) {
         incRefCounterInvoke = "{call ${table}incr(?,?)}";
         StringBuffer buf1 = new StringBuffer(512);
         
         buf1.append("CREATE OR REPLACE PROCEDURE ${table}incr(id number, incr number) AS\n");
         buf1.append("   oldCounter NUMBER(10);\n");
         buf1.append("   newCounter NUMBER(10);\n");
         buf1.append("   CURSOR c1\n");
         buf1.append("      IS\n");
         buf1.append("         SELECT xbrefcount FROM ${table} WHERE xbmeatid=id FOR UPDATE of xbrefcount;\n");
         buf1.append("\n");
         buf1.append("BEGIN\n");
         buf1.append("   open c1;\n");
         buf1.append("   fetch c1 into oldCounter;\n");
         buf1.append("   newCounter := oldCounter + incr;\n");
         buf1.append("   UPDATE ${table} SET xbrefcount=newCounter WHERE CURRENT OF c1;\n");
         buf1.append("   COMMIT;\n");
         buf1.append("   close c1;\n");
         buf1.append("END;\n");
         incRefCounterFunction = buf1.toString();
         
         // and here the create statement ...
         buf.append("create table ${table} (\n");
         buf.append("      xbmeatid number(20) primary key,\n");
         buf.append("      xbdurable char default 'F' not null,\n");
         buf.append("      xbrefcount number(10),\n");
         buf.append("      xbbytesize number(10),\n");
         buf.append("      xbdatatype varchar(32) default '' not null,\n");
         buf.append("      xbflag1 varchar(32) default '',\n");
         buf.append("      xbmsgqos clob default '',\n");
         buf.append("      xbmsgcont blob default '',\n");
         buf.append("      xbmsgkey clob default '')\n");
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
         buf.append("      xbmeatid integer primary key,\n");
         buf.append("      xbdurable char default 'F' not null,\n");
         buf.append("      xbrefcount integer,\n");
         buf.append("      xbbytesize integer,\n");
         buf.append("      xbdatatype varchar(32) default '' not null,\n");
         buf.append("      xbflag1 varchar(32) default '',\n");
         buf.append("      xbmsgqos varchar default '',\n");
         buf.append("      xbmsgcont binary default '',\n");
         buf.append("      xbmsgkey varchar default '')\n");
      }
      createSt = buf.toString();
   }
   
   protected void doInit(I_Info info) {
      updateRefCounterSt = info.get(prefix + ".updateRefCounterStatement", updateRefCounterSt);
      incRefCounterFunction = info.get(prefix + ".incRefCounterFunction", incRefCounterFunction);
      if (incRefCounterFunction != null && incRefCounterFunction.trim().length() < 1)
         incRefCounterFunction = null;
      incRefCounterInvoke = info.get(prefix + ".incRefCounterInvoke", incRefCounterInvoke);
      if (incRefCounterInvoke != null && incRefCounterInvoke.trim().length() < 1)
         incRefCounterInvoke = null;

      updateSt = info.get(prefix + ".updateStatement", updateSt);
      inList = " where xbmeatid in (";
   }

   public boolean create(Connection conn) throws SQLException {
      boolean ret = super.create(conn);
      if (incRefCounterFunction != null && incRefCounterInvoke != null) {
         Statement st = null;
         try {
            st = conn.createStatement();
            st.executeUpdate(incRefCounterFunction);
         }
         finally {
            if (st != null)
               st.close();
         }
         return true;
      }
      return ret;
   }

   private void fillStatement(PreparedStatement preStatement, XBMeat xbMeat) throws SQLException, IOException {
      preStatement.setLong(ID, xbMeat.getId());

      if (xbMeat.isDurable())
         preStatement.setString(DURABLE, "T");
      else 
         preStatement.setString(DURABLE, "F");

      preStatement.setLong(REF_COUNT, xbMeat.getRefCount());
      preStatement.setLong(BYTE_SIZE, xbMeat.getByteSize());
      preStatement.setString(DATA_TYPE, xbMeat.getDataType());
      
      preStatement.setString(FLAG1, xbMeat.getFlag1());

      InputStream qosStream = new ByteArrayInputStream(xbMeat.getQos().getBytes("UTF-8"));
      preStatement.setAsciiStream(QOS, qosStream, xbMeat.getQos().length());
      
      InputStream contentStream = new ByteArrayInputStream(xbMeat.getContent());
      preStatement.setBinaryStream(CONTENT, contentStream, xbMeat.getContent().length);
      
      InputStream keyStream = new ByteArrayInputStream(xbMeat.getKey().getBytes("UTF-8"));
      preStatement.setAsciiStream(KEY, keyStream, xbMeat.getKey().length());
      
   }
   
   /**
    * Inserts an entry in the database
    * @param table
    * @param xbMeat The object to store. Note that 
    * @param conn The database connection to use
    * set.
    * @throws SQLException If an exception occurs in the backend. For example if the entry already
    * exists in the database.
    */
   public void insert(XBMeat xbMeat, Connection conn, int timeout) throws SQLException, IOException {
      if (xbMeat == null || conn == null)
         return;
      PreparedStatement preStatement = conn.prepareStatement(insertSt);
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         fillStatement(preStatement, xbMeat);
         preStatement.execute();
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
      
   }

   /**
    * Updates the XBMeat object. If qos, flag or 
    * @param table
    * @param xbMeat
    * @param conn
    * @throws SQLException
    */
   public void updateRefCounter(XBMeat xbMeat, Connection conn, int timeout) throws SQLException {
      if (xbMeat == null || conn == null)
         return;
      PreparedStatement preStatement = conn.prepareStatement(updateRefCounterSt);
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         preStatement.setLong(1, xbMeat.getRefCount());
         preStatement.setLong(2, xbMeat.getId());
         preStatement.execute();
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
   }
   
   /**
    * Updates the XBMeat object. If qos, flag or 
    * @param table
    * @param xbMeat
    * @param conn
    * @throws SQLException
    */
   public void update(XBMeat xbMeat, Connection conn, int timeout) throws SQLException, IOException {
      if (xbMeat == null || conn == null)
         return;
      PreparedStatement preStatement = conn.prepareStatement(updateRefCounterSt);
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         fillStatement(preStatement, xbMeat);
         preStatement.setLong(10, xbMeat.getId());
         preStatement.executeUpdate();
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
   }
   
   
   public void incrementRefCounter(long meatId, long increment, Connection conn, int timeout) throws SQLException, IOException {
      if (increment == 0)
         return;
      if (true && incRefCounterFunction != null && incRefCounterInvoke != null) {
         CallableStatement st = null;
         try {
            st = conn.prepareCall(incRefCounterInvoke);
            st.setLong(1, meatId);
            st.setLong(2, increment);
            st.executeUpdate();
         }
         finally {
            if (st != null)
               st.close();
         }
      }
      else {
         XBMeat meat = get(meatId, conn, timeout);
         meat.setRefCount(meat.getRefCount() + increment);
         updateRefCounter(meat, conn, timeout);
      }
   }
   
   protected XBEntry rsToEntry(ResultSet rs) throws SQLException, IOException {
      XBMeat xbMeat = new XBMeat();
      xbMeat.setId(rs.getLong(ID));
      String tmp = rs.getString(DURABLE);
      if (tmp != null && "F".equalsIgnoreCase(tmp))
         xbMeat.setDurable(true);
      
      xbMeat.setRefCount(rs.getLong(REF_COUNT));
      xbMeat.setByteSize(rs.getLong(BYTE_SIZE));
      xbMeat.setDataType(rs.getString(DATA_TYPE));
      xbMeat.setFlag1(rs.getString(FLAG1));
      InputStream stream = rs.getAsciiStream(QOS);
      xbMeat.setQos(new String(readStream(stream), "UTF-8"));
      stream = rs.getBinaryStream(CONTENT);
      xbMeat.setContent(readStream(stream));
      stream = rs.getAsciiStream(KEY);
      xbMeat.setKey(new String(readStream(stream), "UTF-8"));

      return xbMeat;
   }
   
   /**
    * 
    * @param sql The select statement to use to fill the objects.
    * @param conn
    * @return null if the object has not been found or the object if it has been found on the backend.
    * @throws SQLException
    */
   public XBMeat get(long id, Connection conn, int timeout) throws SQLException, IOException {
      XBMeat xbMeat = null;
      if (conn == null)
         return xbMeat;
      PreparedStatement preStatement = conn.prepareStatement(getSt);
      ResultSet rs = null;
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         preStatement.setLong(1, id);
         rs = preStatement.executeQuery();
         if (!rs.next())
            return null;
         xbMeat = (XBMeat)rsToEntry(rs);
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
      
      return xbMeat;
   }

}
