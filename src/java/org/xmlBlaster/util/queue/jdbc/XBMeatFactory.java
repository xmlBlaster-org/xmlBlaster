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
import java.sql.Types;

import org.xmlBlaster.contrib.I_Info;

/**
 * @author <a href='mailto:mr@ruff.info'>Marcel Ruff</a>
 * @author <a href='mailto:michele@laghi.eu'>Michele Laghi</a>
 */

public class XBMeatFactory extends XBFactory {

   private final static int ID = 1;
   private final static int DURABLE = 2;
   private final static int REF_COUNT = 3;
   private final static int REF_COUNT2 = 4;
   private final static int BYTE_SIZE = 5;
   private final static int DATA_TYPE = 6;
   private final static int FLAG1 = 7;
   private final static int QOS = 8;
   private final static int CONTENT = 9;
   private final static int KEY = 10;
   private final static int STORE_ID = 11;
   private final static int LAST_ROW = STORE_ID;
   
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
      base = prefix;
      insertSt = "insert into ${table} values ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
      deleteCompleteSt = "delete from ${table}";
      deleteAllSt = deleteCompleteSt + " where xbstoreid=?";
      deleteSt = deleteAllSt + " and xbmeatid=?";
      deleteTransientsSt = deleteAllSt + " and xbdurable='F'";
      getCompleteSt = "select * from ${table}";
      getAllSt = getCompleteSt + " where xbstoreid=?";
      getFirstEntriesSt = getAllSt + " order by xbmeatid asc";
      getNumOfAllSt = "select xbdurable, count(xbbytesize), sum(xbbytesize) from ${table} where xbstoreid=? group by xbdurable";
      
      getSt = getAllSt + " and xbmeatid=?";
      updateSt = "update ${table} set xbmeatid=?,xbdurable=?,xbrefcount=?,xbrefcount2=?,xbbytesize=?,xbdatatype=?,xbflag1=?,xbmsgqos=?,xbmsgcont=?,xbmsgkey=?,xbstoreid=? where xbmeatid=? and xbstoreid=?";
      updateRefCounterSt = "update ${table} set xbrefcount=?,xbrefcount2=? where xbmeatid=? and xbstoreid=?";
      inList = " and xbmeatid in ("; 
   }

   
   protected void prepareDefaultStatements() {
      super.prepareDefaultStatements();
      incRefCounterFunction = null;
      incRefCounterInvoke = null;
      StringBuffer buf = new StringBuffer(512);
      if (getDbVendor().equals(POSTGRES)) {
         buf.append("create table ${table} (\n");
         buf.append("      xbmeatid int8 primary key unique not null,\n");
         buf.append("      xbdurable char not null default 'F',\n");
         buf.append("      xbrefcount int4,\n");
         buf.append("      xbrefcount2 int4,\n");
         buf.append("      xbbytesize int4,\n");
         buf.append("      xbdatatype varchar(32) not null default '',\n");
         buf.append("      xbflag1 varchar(32) default '',\n");
         buf.append("      xbmsgqos text default '',\n");
         buf.append("      xbmsgcont bytea default '',\n");
         buf.append("      xbmsgkey text default '',\n");
         buf.append("      xbstoreid int8 not null);\n");

         buf.append("alter table ${table} \n");
         buf.append("      add constraint fkxbstoremeat\n");
         buf.append("      foreign key (xbstoreid) \n");
         buf.append("      references ${xbstore} on delete cascade;\n");
         
         buf.append("create index ${table}stix on ${table}(xbmeatid,xbstoreid);\n");
         
      }
      else if (getDbVendor().equals(ORACLE)) {
         // currently disabled since not really performant and since I do not know how to use fetch with multiparams
         // incRefCounterInvoke = "{call ${table}incr(?,?,?)}";
         incRefCounterInvoke = null;
         StringBuffer buf1 = new StringBuffer(512);
         
         buf1.append("CREATE OR REPLACE PROCEDURE ${table}incr(id number, storeid number, incr number, incr2 number) AS\n");
         buf1.append("   oldCounter NUMBER(10);\n");
         buf1.append("   newCounter NUMBER(10);\n");
         buf1.append("   oldCounter2 NUMBER(10);\n");
         buf1.append("   newCounter2 NUMBER(10);\n");
         buf1.append("   CURSOR c1\n");
         buf1.append("      IS\n");
         buf1.append("         SELECT xbrefcount,xbrefcount2 FROM ${table} WHERE xbmeatid=id and xbstoreid=storeid FOR UPDATE of xbrefcount,xbrefcount2;\n");
         buf1.append("\n");
         buf1.append("BEGIN\n");
         buf1.append("   open c1;\n");
         buf1.append("   fetch c1 into oldCounter;\n");
         buf1.append("   newCounter := oldCounter + incr;\n");
         buf1.append("   UPDATE ${table} SET xbrefcount=newCounter WHERE CURRENT OF c1;\n");
         buf1.append("   COMMIT;\n");
         buf1.append("   close c1;\n");
         buf1.append("END;\n");
         // incRefCounterFunction = buf1.toString();
         incRefCounterFunction = null;
         
         // and here the create statement ...
         buf.append("create table ${table} (\n");
         buf.append("      xbmeatid number(20),\n");
         buf.append("      xbdurable char default 'F' not null,\n");
         buf.append("      xbrefcount number(10),\n");
         buf.append("      xbrefcount2 number(10),\n");
         buf.append("      xbbytesize number(10),\n");
         buf.append("      xbdatatype varchar(32) default '' not null,\n");
         buf.append("      xbflag1 varchar(32) default '',\n");
         buf.append("      xbmsgqos clob default '',\n");
         buf.append("      xbmsgcont blob default '',\n");
         buf.append("      xbmsgkey clob default '',\n");
         buf.append("      xbstoreid number(20), constraint xbmeatpk primary key(xbmeatid));\n");
         
         buf.append("alter table ${table} \n");
         buf.append("      add constraint fkxbstoremeat\n");
         buf.append("      foreign key (xbstoreid) \n");
         buf.append("      references ${xbstore} on delete cascade;\n");

         buf.append("create index ${table}stix on ${table}(xbmeatid,xbstoreid);\n");
         
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
         buf.append("      xbmeatid bigint,\n");
         buf.append("      xbdurable char default 'F' not null,\n");
         buf.append("      xbrefcount integer,\n");
         buf.append("      xbrefcount2 integer,\n");
         buf.append("      xbbytesize bigint,\n");
         buf.append("      xbdatatype varchar(32) default '' not null,\n");
         buf.append("      xbflag1 varchar(32) default '',\n");
         buf.append("      xbmsgqos varchar default '',\n");
         buf.append("      xbmsgcont binary default '',\n");
         buf.append("      xbmsgkey varchar default '',\n");
         buf.append("      xbstoreid bigint, constraint xbmeatpk primary key(xbmeatid));\n");

         buf.append("alter table ${table} \n");
         buf.append("      add constraint fkxbstoremeat\n");
         buf.append("      foreign key (xbstoreid) \n");
         buf.append("      references ${xbstore} on delete cascade;\n");
         
         buf.append("create index ${table}stix on ${table}(xbmeatid,xbstoreid);\n");
         
      }
      createSt = buf.toString();
   }
   
   protected void doInit(I_Info info) {
      String tmp = info.get(base + ".table." + XBStoreFactory.getName(), XBStoreFactory.getName());
      info.put(XBStoreFactory.getName(), tmp);
      getCompleteSt =  info.get(prefix + ".getCompleteStatement", getCompleteSt);
      deleteCompleteSt =  info.get(prefix + ".deleteCompleteStatement", deleteCompleteSt);
         
      updateRefCounterSt = info.get(prefix + ".updateRefCounterStatement", updateRefCounterSt);
      incRefCounterFunction = info.get(prefix + ".incRefCounterFunction", incRefCounterFunction);
      if (incRefCounterFunction != null && incRefCounterFunction.trim().length() < 1)
         incRefCounterFunction = null;
      incRefCounterInvoke = info.get(prefix + ".incRefCounterInvoke", incRefCounterInvoke);
      if (incRefCounterInvoke != null && incRefCounterInvoke.trim().length() < 1)
         incRefCounterInvoke = null;

      updateSt = info.get(prefix + ".updateStatement", updateSt);
      getFirstEntriesSt = info.get(prefix + ".getFirstEntriesStatement", getFirstEntriesSt);
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
      preStatement.setLong(REF_COUNT2, xbMeat.getRefCount2());
      preStatement.setLong(BYTE_SIZE, xbMeat.getByteSize());
      
      if (xbMeat.getDataType() != null)
         preStatement.setString(DATA_TYPE, xbMeat.getDataType());
      else
         preStatement.setNull(DATA_TYPE, Types.VARCHAR);
      
      if (xbMeat.getFlag1() != null)
         preStatement.setString(FLAG1, xbMeat.getFlag1());
      else
         preStatement.setNull(FLAG1, Types.VARCHAR);

      if (xbMeat.getQos() != null) {
         InputStream qosStream = new ByteArrayInputStream(xbMeat.getQos().getBytes("UTF-8"));
         preStatement.setAsciiStream(QOS, qosStream, xbMeat.getQos().length());
      }
      else
         preStatement.setNull(QOS, Types.CLOB);
      
      if (xbMeat.getContent() != null) {
         InputStream contentStream = new ByteArrayInputStream(xbMeat.getContent());
         preStatement.setBinaryStream(CONTENT, contentStream, xbMeat.getContent().length);
      }
      else
         preStatement.setNull(CONTENT, Types.BINARY); // Types.BLOB fails on Postgres
         
      if (xbMeat.getKey() != null) {
         InputStream keyStream = new ByteArrayInputStream(xbMeat.getKey().getBytes("UTF-8"));
         preStatement.setAsciiStream(KEY, keyStream, xbMeat.getKey().length());
      }
      else
         preStatement.setNull(KEY, Types.CLOB);
      preStatement.setLong(STORE_ID, xbMeat.getStoreId());
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
   public void updateRefCounters(XBMeat xbMeat, Connection conn, int timeout) throws SQLException {
      if (xbMeat == null || conn == null)
         return;
      PreparedStatement preStatement = conn.prepareStatement(updateRefCounterSt);
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         // Id
         preStatement.setLong(1, xbMeat.getRefCount());
         preStatement.setLong(2, xbMeat.getRefCount2());
         preStatement.setLong(3, xbMeat.getId());
         preStatement.setLong(4, xbMeat.getStoreId());
         preStatement.executeUpdate();
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
      PreparedStatement preStatement = conn.prepareStatement(updateSt);
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         fillStatement(preStatement, xbMeat);
         preStatement.setLong(LAST_ROW+1, xbMeat.getId());
         preStatement.setLong(LAST_ROW+2, xbMeat.getStoreId());
         preStatement.executeUpdate();
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
   }
   
   public void incrementRefCounters(XBStore store, XBMeat meat, long increment, Connection conn, int timeout) throws SQLException, IOException {
      if (increment == 0)
         return;
      if (true && incRefCounterFunction != null && incRefCounterInvoke != null) {
         CallableStatement st = null;
         try {
            st = conn.prepareCall(incRefCounterInvoke);
            st.setLong(1, meat.getId());
            st.setLong(2, store.getId());
            st.setLong(3, increment);
            st.executeUpdate();
         }
         finally {
            if (st != null)
               st.close();
         }
      }
      else {
         XBMeat meatRet = get(store, meat.getId(), conn, timeout);
         meatRet.setRefCount(meatRet.getRefCount() + increment);
         updateRefCounters(meatRet, conn, timeout);
      }
   }
   
   static final XBMeat buildFromRs(ResultSet rs, int offset) throws SQLException, IOException {
      long meatId = rs.getLong(ID + offset);
      if (meatId == 0)
         return null; // then the meat is only in memory (for example for swapped callback queues)
      XBMeat xbMeat = new XBMeat();
      xbMeat.setId(meatId);
      String tmp = rs.getString(DURABLE + offset);
      if (isTrue(tmp))
         xbMeat.setDurable(true);
      xbMeat.setRefCount(rs.getLong(REF_COUNT + offset));
      xbMeat.setByteSize(rs.getLong(BYTE_SIZE + offset));
      xbMeat.setDataType(rs.getString(DATA_TYPE + offset));
      xbMeat.setFlag1(rs.getString(FLAG1 + offset));
      //xbMeat.setQos(rs.getString(QOS + offset));
      InputStream stream = rs.getAsciiStream(QOS + offset);
      if (stream != null)
         xbMeat.setQos(new String(readStream(stream), "UTF-8"));
      else
         xbMeat.setQos(null);
      stream = rs.getBinaryStream(CONTENT + offset);
      if (stream != null)
         xbMeat.setContent(readStream(stream));
      else
         xbMeat.setContent(null);
      //xbMeat.setKey(rs.getString(KEY + offset));
      stream = rs.getAsciiStream(KEY + offset);
      if (stream != null)
         xbMeat.setKey(new String(readStream(stream), "UTF-8"));
      else
         xbMeat.setKey(null);
      xbMeat.setStoreId(xbMeat.getStoreId());
      return xbMeat;
   }

   protected XBEntry rsToEntry(ResultSet rs) throws SQLException, IOException {
      return buildFromRs(rs, 0);
   }
   
   /**
    * 
    * @param sql The select statement to use to fill the objects.
    * @param conn
    * @return null if the object has not been found or the object if it has been found on the backend.
    * @throws SQLException
    */
   public XBMeat get(XBStore store, long id, Connection conn, int timeout) throws SQLException, IOException {
      XBMeat xbMeat = null;
      if (conn == null)
         return xbMeat;
      PreparedStatement preStatement = conn.prepareStatement(getSt);
      ResultSet rs = null;
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         preStatement.setLong(1, store.getId());
         preStatement.setLong(2, id);
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

   protected long getByteSize(ResultSet rs, int offset) throws SQLException {
      return rs.getLong(BYTE_SIZE + offset);
   }

}
