/*------------------------------------------------------------------------------
Name:      XbMeatFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;

/**
 * @author <a href='mailto:mr@ruff.info'>Marcel Ruff</a>
 * @author <a href='mailto:michele@laghi.eu'>Michele Laghi</a>
 */

public class XBStoreFactory extends XBFactory {
   private static Logger log = Logger.getLogger(XBStoreFactory.class.getName());
   private final static int ID = 1;
   private final static int NODE = 2;
   private final static int TYPE = 3;
   private final static int POSTFIX = 4;
   private final static int REF_COUNTED = 5;
   private final static int FLAG1 = 6;
   private String getByNameSt;
   private String getAllOfTypeSt;
   protected String pingSt = "select 1";
   
   static String getName() {
      return "xbstore";
   }
   
   public XBStoreFactory(String prefix) {
      super(prefix, getName());
      insertSt = "insert into ${table} values ( ?, ?, ?, ?, ?, ?)";
      deleteSt = "delete from ${table} where xbstoreid=?";
      getSt = "select * from ${table} where xbstoreid=?";
      getByNameSt = "select * from ${table} where xbnode=? and xbtype=? and xbpostfix=?";
      getAllOfTypeSt = "select * from ${table} where xbnode=? and xbtype=?";
   }


   /**
    * Old: xb_entries.queueName=
    * <pre>
 callback_nodefwgwTestclientmarcel28              | UPDATE_REF
 connection_testecsoftclientfwgwTest1             | connect
 history_fwgwTestdevice_lkw5@basis_de_cfg         | HISTORY_REF
 history_fwgwTestHello                            | HISTORY_REF
 history_fwgwTest__sys__remoteProperties          | HISTORY_REF
 history_fwgwTestTEST_TOPIC                       | HISTORY_REF
 msgUnitStore_fwgwTestdevice_lkw5@basis_de_cfg    | MSG_XML
 msgUnitStore_fwgwTestfwauftrag                   | MSG_XML
 msgUnitStore_fwgwTest__sys__remoteProperties     | MSG_XML
 msgUnitStore_fwgwTestTEST_TOPIC                  | MSG_XML
 session_fwgwTestsubPersistence,1_0               | SESSION
 subject_nodefwgwTestclient_monito                | UPDATE_REF
 topicStore_fwgwTest                              | TOPIC_XML
    * </pre>
    * 
    * New:
    * xbnode=clusterNodeId="fwgwTest"
    * xbtype="history" | "msgUnitStore" | "session" | ...
    * xbpostfix="Testclient_monito"
    */
   protected void prepareDefaultStatements() {
      StringBuffer buf = new StringBuffer(512);

      if (getDbVendor().equals(POSTGRES)) {
         buf.append("create table ${table} (\n");
         buf.append("      xbstoreid int8 primary key unique not null,\n");
         buf.append("      xbnode varchar(256) not null,\n");
         buf.append("      xbtype varchar(32) not null,\n");
         buf.append("      xbpostfix varchar(256) not null,\n");
         buf.append("      xbrefcounted char(1) not null default 'F',\n");
         buf.append("      xbflag1 varchar(32) default '');\n");
         buf.append("create unique index xbstoreidx on ${table} (xbnode, xbtype, xbpostfix);\n");
      }
      else if (getDbVendor().equals(ORACLE)) {
         pingSt = "select count(*) from ${table}";
         buf.append("create table ${table} (\n");
         buf.append("      xbstoreid number(20) primary key,\n");
         buf.append("      xbnode varchar(256) not null,\n");
         buf.append("      xbtype varchar(32) not null,\n");
         buf.append("      xbpostfix varchar(256) not null,\n");
         buf.append("      xbrefcounted char(1) default 'F' not null,\n");
         buf.append("      xbflag1 varchar(32) default '');\n");
         buf.append("create unique index xbstoreidx on ${table} (xbnode, xbtype, xbpostfix);\n");
      }
      else if (getDbVendor().equals(SQLSERVER_2000) || getDbVendor().equals(SQLSERVER_2005)) {
         buf.append("create table ${table} (\n");
         buf.append("      xbstoreid bigint primary key not null,\n");
         buf.append("      xbnode varchar(256) not null,\n");
         buf.append("      xbtype varchar(32) not null,\n");
         buf.append("      xbpostfix varchar(256) not null,\n");
         buf.append("      xbrefcounted char(1) not null default 'F',\n");
         buf.append("      xbflag1 varchar(32) default '');\n");
         buf.append("create unique index xbstoreidx on xbstore (xbnode, xbtype, xbpostfix);\n");
      }
      else if (getDbVendor().equals(DB2)) {
         pingSt = "select 1 from ${table}"; // HSQLDB needs from in stmt
         buf.append("create table ${table} (\n");
         buf.append("      xbstoreid bigint primary key not null,\n");
         buf.append("      xbnode varchar(256) not null,\n");
         buf.append("      xbtype varchar(32) not null,\n");
         buf.append("      xbpostfix varchar(256) not null,\n");
         buf.append("      xbrefcounted char(1) default 'F' not null,\n");
         buf.append("      xbflag1 varchar(32) default '');\n");
         buf.append("create unique index xbstoreidx on ${table} (xbnode, xbtype, xbpostfix);\n");
      }
      /*
      else if (getDbVendor().equals(FIREBIRD)) {
         
      }
      else if (getDbVendor().equals(MYSQL)) {
         
      }
      else if (getDbVendor().equals(SQLITE)) {
         
      }
      */
      else { // if (getDbVendor().equals(HSQLDB))
         pingSt = "select 1 from ${table}"; // HSQLDB needs from in stmt
         buf.append("create table ${table} (\n");
         buf.append("      xbstoreid bigint primary key,\n");
         buf.append("      xbnode varchar(256) not null,\n");
         buf.append("      xbtype varchar(32) not null,\n");
         buf.append("      xbpostfix varchar(256) not null,\n");
         buf.append("      xbrefcounted char(1) default 'F' not null,\n");
         buf.append("      xbflag1 varchar(32) default '');\n");
         buf.append("create unique index xbstoreidx on ${table} (xbnode, xbtype, xbpostfix);\n");
      }
      createSt = buf.toString();
   }
   
   protected void doInit(I_Info info) {
      getByNameSt = info.get(prefix + ".getByNameStatement", getByNameSt);
      getAllOfTypeSt = info.get(prefix + ".getAllOfTypeStatement", getAllOfTypeSt);
      pingSt = info.get(prefix + ".pingStatement", pingSt);
   }
   
   protected XBEntry rsToEntry(XBStore store, ResultSet rs) throws SQLException, IOException {
      return null; // to make compiler happy
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
   public void insert(XBStore xbStore, Connection conn, int timeout) throws SQLException, IOException {
      if (xbStore == null || conn == null)
         return;
      PreparedStatement preStatement = conn.prepareStatement(insertSt);
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         preStatement.setLong(ID, xbStore.getId());

         fillDbCol(preStatement, NODE, xbStore.getNode());
         fillDbCol(preStatement, TYPE, xbStore.getType());
         fillDbCol(preStatement, POSTFIX, xbStore.getPostfix());
         if (xbStore.isRefCounted())
            preStatement.setString(REF_COUNTED, "T");
         else
            preStatement.setString(REF_COUNTED, "F");
         fillDbCol(preStatement, FLAG1, xbStore.getFlag1());
         
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
   public XBStore get(long id, Connection conn, int timeout) throws SQLException, IOException {
      XBStore xbStore = new XBStore();
      if (conn == null)
         return xbStore;
      PreparedStatement preStatement = conn.prepareStatement(getSt);
      ResultSet rs = null;
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         preStatement.setLong(1, id);
         if (log.isLoggable(Level.FINEST)) 
            log.finest(getSt);
         rs = preStatement.executeQuery();
         if (!rs.next())
            return null;
         
         xbStore.setId(rs.getLong(ID));
         xbStore.setNode(getDbCol(rs, NODE));
         xbStore.setType(getDbCol(rs, TYPE));
         xbStore.setPostfix(getDbCol(rs, POSTFIX));
         String tmp = rs.getString(REF_COUNTED);
         xbStore.setRefCounted(isTrue(tmp));
         xbStore.setFlag1(getDbCol(rs, FLAG1));

      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
      
      return xbStore;
   }
   
   /**
    * 
    * @param sql The select statement to use to fill the objects.
    * @param conn
    * @return null if the object has not been found or the object if it has been found on the backend.
    * @throws SQLException
    */
   public List<XBStore> getAllOfType(String node, String type, Connection conn, int timeout) throws SQLException, IOException {
          List<XBStore> list = new ArrayList<XBStore>();
      if (conn == null)
         return list;
      PreparedStatement preStatement = conn.prepareStatement(getAllOfTypeSt);
      ResultSet rs = null;
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         preStatement.setString(1, node);
         preStatement.setString(2, type);
         if (log.isLoggable(Level.FINEST)) 
            log.finest(getAllOfTypeSt);
         rs = preStatement.executeQuery();
         while (rs.next()) {
             XBStore xbStore = new XBStore();
                 xbStore.setId(rs.getLong(ID));
                 xbStore.setNode(getDbCol(rs, NODE));
                 xbStore.setType(getDbCol(rs, TYPE));
                 xbStore.setPostfix(getDbCol(rs, POSTFIX));
                 String tmp = rs.getString(REF_COUNTED);
                 xbStore.setRefCounted(isTrue(tmp));
                 xbStore.setFlag1(getDbCol(rs, FLAG1));
                 list.add(xbStore);
         }
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
      
      return list;
   }
   
   /**
    * @param sql The select statement to use to fill the objects.
    * @param conn
    * @return null if the object has not been found or the object if it has been found on the backend.
    * @throws SQLException
    */
   public XBStore getByName(String node, String type, String postfix, Connection conn, int timeout) throws SQLException, IOException {
      XBStore xbStore = new XBStore();
      if (conn == null)
         return xbStore;
      PreparedStatement preStatement = conn.prepareStatement(getByNameSt);
      ResultSet rs = null;
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         preStatement.setString(1, node);
         preStatement.setString(2, type);
         preStatement.setString(3, postfix);
         if (log.isLoggable(Level.FINEST)) 
            log.finest(getByNameSt);
         rs = preStatement.executeQuery();
         if (!rs.next())
            return null;
         
         xbStore.setId(rs.getLong(ID));
         xbStore.setNode(getDbCol(rs, NODE));
         xbStore.setType(getDbCol(rs, TYPE));
         xbStore.setPostfix(getDbCol(rs, POSTFIX));
         String tmp = rs.getString(REF_COUNTED);
         xbStore.setRefCounted(isTrue(tmp));
         xbStore.setFlag1(getDbCol(rs, FLAG1));
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
      
      return xbStore;
   }
   
   public void ping(Connection conn, int timeout) throws SQLException {
      if (conn == null)
         throw new SQLException("ping: The connection was null");
      PreparedStatement preStatement = conn.prepareStatement(pingSt);
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         if (log.isLoggable(Level.FINEST)) 
            log.finest(pingSt);
          preStatement.executeQuery();
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
   }


   protected long getByteSize(ResultSet rs, int offset) throws SQLException {
      return 0;
   }
   
}
