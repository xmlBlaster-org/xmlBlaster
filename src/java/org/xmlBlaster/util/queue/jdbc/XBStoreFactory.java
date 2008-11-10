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
import java.sql.Types;

import org.xmlBlaster.contrib.I_Info;

/**
 * @author <a href='mailto:mr@ruff.info'>Marcel Ruff</a>
 * @author <a href='mailto:michele@laghi.eu'>Michele Laghi</a>
 */

public class XBStoreFactory extends XBFactory {

   private final static int ID = 1;
   private final static int NODE = 2;
   private final static int TYPE = 3;
   private final static int POSTFIX = 4;
   private final static int FLAG1 = 5;
   private String getByNameSt;
   protected String pingSt = "select 1";
   
   static String getName() {
      return "xbstore";
   }
   
   public XBStoreFactory(String prefix) {
      super(prefix, getName());
      insertSt = "insert into ${table} values ( ?, ?, ?, ?, ?)";
      deleteSt = "delete from ${table} where xbstoreid=?";
      getSt = "select * from ${table} where xbstoreid=?";
      getByNameSt = "select * from ${table} where xbnode=? and xbtype=? and xbpostfix=?";
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
         buf.append("      xbflag1 varchar(32) default '');\n");
         buf.append("create unique index xbstoreidx on ${table} (xbnode, xbtype, xbpostfix);\n");
      }
      else if (getDbVendor().equals(SQLSERVER_2000) || getDbVendor().equals(SQLSERVER_2005)) {
         buf.append("create table ${table} (\n");
         buf.append("      xbstoreid bigint primary key not null,\n");
         buf.append("      xbnode varchar(256) not null,\n");
         buf.append("      xbtype varchar(32) not null,\n");
         buf.append("      xbpostfix varchar(256) not null,\n");
         buf.append("      xbflag1 varchar(32) default '');\n");
         buf.append("create unique index xbstoreidx on xbstore (xbnode, xbtype, xbpostfix);\n");
      }
      /*
      else if (getDbVendor().equals(DB2)) {
         
      }
      else if (getDbVendor().equals(FIREBIRD)) {
         
      }
      else if (getDbVendor().equals(MYSQL)) {
         
      }
      else if (getDbVendor().equals(SQLITE)) {
         
      }
      */
      else { // if (getDbVendor().equals(HSQLDB))
         buf.append("create table ${table} (\n");
         buf.append("      xbstoreid bigint primary key,\n");
         buf.append("      xbnode varchar(256) not null,\n");
         buf.append("      xbtype varchar(32) not null,\n");
         buf.append("      xbpostfix varchar(256) not null,\n");
         buf.append("      xbflag1 varchar(32) default '');\n");
         buf.append("create unique index xbstoreidx on ${table} (xbnode, xbtype, xbpostfix);\n");
      }
      createSt = buf.toString();
   }
   
   protected void doInit(I_Info info) {
      getByNameSt = info.get(prefix + ".getByNameStatement", getByNameSt);
      pingSt = info.get(prefix + ".pingStatement", pingSt);
   }
   
   protected XBEntry rsToEntry(ResultSet rs) throws SQLException, IOException {
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
   public void insert(XBStore xbStore, Connection conn, int timeout) throws SQLException {
      if (xbStore == null || conn == null)
         return;
      PreparedStatement preStatement = conn.prepareStatement(insertSt);
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         preStatement.setLong(ID, xbStore.getId());
         
         if (xbStore.getNode() != null)
            preStatement.setString(NODE, xbStore.getNode());
         else
            preStatement.setNull(NODE, Types.VARCHAR);
         
         if (xbStore.getType() != null)
            preStatement.setString(TYPE, xbStore.getType());
         else
            preStatement.setNull(TYPE, Types.VARCHAR);
            
         if (xbStore.getPostfix() == null || xbStore.getPostfix().length() < 1)
            xbStore.setPostfix("  ");

         preStatement.setString(POSTFIX, xbStore.getPostfix());
            
         if (xbStore.getFlag1() != null)
            preStatement.setString(FLAG1, xbStore.getFlag1());
         else
            preStatement.setNull(FLAG1, Types.VARCHAR);
         
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
         rs = preStatement.executeQuery();
         if (!rs.next())
            return null;
         
         xbStore.setId(rs.getLong(ID));
         xbStore.setNode(rs.getString(NODE));
         xbStore.setType(rs.getString(TYPE));
         xbStore.setPostfix(rs.getString(POSTFIX));
         xbStore.setFlag1(rs.getString(FLAG1));
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
         rs = preStatement.executeQuery();
         if (!rs.next())
            return null;
         
         xbStore.setId(rs.getLong(ID));
         xbStore.setNode(rs.getString(NODE));
         xbStore.setType(rs.getString(TYPE));
         xbStore.setPostfix(rs.getString(POSTFIX));
         xbStore.setFlag1(rs.getString(FLAG1));
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
