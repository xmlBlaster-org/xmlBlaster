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

import org.xmlBlaster.contrib.I_Info;

/**
 * @author <a href='mailto:mr@ruff.info'>Marcel Ruff</a>
 * @author <a href='mailto:michele@laghi.eu'>Michele Laghi</a>
 */

public class XBStoreFactory extends XBFactory {

   private final static int ID = 1;
   private final static int NAME = 2;
   private final static int FLAG1 = 3;
   private String getByNameSt;
   
   static String getName() {
      return "xbstore";
   }
   
   public XBStoreFactory(String prefix) {
      super(prefix, getName());
      insertSt = "insert into ${table} values ( ?, ?, ?)";
      deleteSt = "delete from ${table} where xbstoreid=?";
      getSt = "select * from ${table} where xbstoreid=?";
      getByNameSt = "select * from ${table} where xbname=?";
   }


   protected String getDefaultCreateStatement() {
      StringBuffer buf = new StringBuffer(512);
      
      if (getDbVendor().equals(POSTGRES)) {
         buf.append("create table ${table} (\n");
         buf.append("      xbstoreid int8 primary key unique not null,\n");
         buf.append("      storename varchar(512) not null unique,\n");
         buf.append("      flag1 varchar(32) default '')\n");
      }
      else if (getDbVendor().equals(ORACLE)) {
         buf.append("create table ${table} (\n");
         buf.append("      xbstoreid number(20) primary key,\n");
         buf.append("      xbname varchar(512) not null unique,\n");
         buf.append("      xbflag1 varchar(32) default '')\n");
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
         buf.append("create table xbstore (\n");
         buf.append("      xbstoreid integer primary key,\n");
         buf.append("      xbname varchar(512) not null ,\n");
         buf.append("      xbflag1 varchar(32) default '', constraint xbstoreix1 unique(xbname) )\n");
         
      }
      return buf.toString();
   }
   
   protected void doInit(I_Info info) {
      getByNameSt = info.get(prefix + ".getByNameStatement", getByNameSt);
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
         preStatement.setString(NAME, xbStore.getName());
         preStatement.setString(FLAG1, xbStore.getFlag1());
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
         xbStore.setName(rs.getString(NAME));
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
   public XBStore getByName(String name, Connection conn, int timeout) throws SQLException, IOException {
      XBStore xbStore = new XBStore();
      if (conn == null)
         return xbStore;
      PreparedStatement preStatement = conn.prepareStatement(getByNameSt);
      ResultSet rs = null;
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         preStatement.setString(1, name);
         rs = preStatement.executeQuery();
         if (!rs.next())
            return null;
         
         xbStore.setId(rs.getLong(ID));
         xbStore.setName(rs.getString(NAME));
         xbStore.setFlag1(rs.getString(FLAG1));
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
      
      return xbStore;
   }
   
}
