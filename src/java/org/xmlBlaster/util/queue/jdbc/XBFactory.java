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
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * @author <a href='mailto:mr@ruff.info'>Marcel Ruff</a>
 * @author <a href='mailto:michele@laghi.eu'>Michele Laghi</a>
 */

public abstract class XBFactory extends XBFactoryBase {

   protected String insertSt;
   protected String getSt;
   protected String getAllSt;
   protected String deleteAllSt;
   protected String deleteTransientsSt;
   protected String deleteSt;
   protected String createSt;
   protected String dropSt = "drop table ${table}";
   protected String prefix = "queue.jdbc";
   protected String table;
   private String tableNameDefault;
   protected String inList;
   protected boolean limitAtEnd;
   
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

   public final I_Info init(I_Info origInfo) throws XmlBlasterException {
      table = origInfo.get(prefix + ".table." + tableNameDefault, tableNameDefault);
      I_Info info = super.init(origInfo);
      prepareDefaultStatements();
      info.put("table", table);
      doInit(info);

      insertSt = info.get(prefix + ".insertStatement", insertSt);
      deleteAllSt = info.get(prefix + ".deleteAllStatement", deleteAllSt);
      deleteSt = info.get(prefix + ".deleteStatement", deleteSt);
      deleteTransientsSt = info.get(prefix + ".deleteTransientsSttatement", deleteTransientsSt);
      getSt = info.get(prefix + ".getStatement", getSt);
      getAllSt = info.get(prefix + ".getAllStatement", getAllSt);
      createSt = info.get(prefix + ".createStatement", createSt);
      dropSt = info.get(prefix + ".dropStatement", dropSt);
      
      return info;
   }
   
   public int delete(long id, Connection conn, int timeout) throws SQLException {
      if (conn == null)
         return 0;
      PreparedStatement preStatement = conn.prepareStatement(deleteSt);
      if (timeout > 0)
         preStatement.setQueryTimeout(timeout);
      try {
         preStatement.setLong(1, id);
         return preStatement.executeUpdate();
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
   }

   public int deleteTransients(Connection conn, int timeout) throws SQLException {
      if (conn == null)
         return 0;
      PreparedStatement preStatement = conn.prepareStatement(deleteTransientsSt);
      if (timeout > 0)
         preStatement.setQueryTimeout(timeout);
      try {
         return preStatement.executeUpdate();
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
   }

   protected abstract XBEntry rsToEntry(ResultSet rs) throws SQLException, IOException;

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
   
   /**
    * This method must be implemented in all underlying extentions to this class. It returns (for the different
    * database vendors a default for the creation of the table.
    * @return
    */
   protected void prepareDefaultStatements() {
      if (getDbVendor().equals(POSTGRES)) {
         limitAtEnd = true;
      }
      else if (getDbVendor().equals(ORACLE)) {
         limitAtEnd = true;
      }
      /*
      else if (getDbVendor().equals(DB2)) {
         
      }
      else if (getDbVendor().equals(FIREBIRD)) {
         limitAtEnd = false;
      }
      else if (getDbVendor().equals(SQLSERVER_2000) || getDbVendor().equals(SQLSERVER_2005)) {
         limitAtEnd = false;
      }
      else if (getDbVendor().equals(MYSQL)) {
         limitAtEnd = true;
      }
      else if (getDbVendor().equals(SQLITE)) {
         limitAtEnd = true;
      }
      */
      else { // if (getDbVendor().equals(HSQLDB))
         limitAtEnd = false;
      }
   }
   
   
   
   /**
    * The prefix is the initial part of the SQL update/query. Note that this
    * method can be used both for SELECT statements as for updates such as
    * DELETE or UPDATE.
    * An example of prefix:
    * "delete from tableName where dataId in(";
    */
   protected final List whereInStatement(String reqPrefix, long[] uniqueIds, int maxStatementLength, int maxNumStatements) {
      final String reqPostfix = ")";
      boolean isFirst = true;
      int initialLength = reqPrefix.length() + reqPostfix.length() + 2;
      StringBuffer buf = new StringBuffer();
      int length = initialLength;
      int currentLength = 0;

      List ret = new ArrayList();
      int count = 0;
      for (int i=0; i<uniqueIds.length; i++) {
         String req = null;
         String entryId = Long.toString(uniqueIds[i]);
         currentLength = entryId.length();
         length += currentLength;
         if ((length > maxStatementLength) || (i == (uniqueIds.length-1)) || count >= maxNumStatements) { // then make the update
            if (i == (uniqueIds.length-1)) {
               if (!isFirst) buf.append(",");
               count++;
               buf.append(entryId);
            }
            req = reqPrefix + buf.toString() + reqPostfix;
            if (count > 0)
               ret.add(req);

            length = initialLength + currentLength;
            buf = new StringBuffer();
            count = 0;
            isFirst = true;
         }
         else
            count++;

         if (!isFirst) {
            buf.append(",");
            length++;
         }
         else 
            isFirst = false;
         count++;
         buf.append(entryId);
      }

      return ret;
   }

   
   /**
    * Deletes the specified entries. Since all entry may not fit in one single operation,
    * they are splitted over different operations.
    * If you specified  commitInBetween or the auto-commit flag is set to true,
    * It always returns the number of deleted entries. If a batch could not be completely deleted,
    * it returns the number of operations previously deleted.
    *
    * @param  store the store to use.
    * @param  the connection to be used.
    * @param   ids the array containing all ids to delete.
    * @return the number of entries successfully processed. These are the first. If an
    * error occurs it stops.
    * 
    */
   public long deleteList(XBStore store, Connection conn, long[] ids, int maxStLength, int maxNumSt, boolean commitInBetween, int timeout) throws SQLException {
      String reqPrefix = deleteAllSt + inList;
      List reqList = whereInStatement(reqPrefix, ids, maxStLength, maxNumSt);
      commitInBetween = commitInBetween && !conn.getAutoCommit();
      long sum = 0;
      try {
         for (int i=0; i < reqList.size(); i++) {
            String req = (String)reqList.get(i);
            Statement st = null;
            try {
               st = conn.createStatement();
               st.setQueryTimeout(timeout);
               int num = st.executeUpdate(req);
               if (commitInBetween)
                  conn.commit();
               sum += num;
            }
            finally {
               if (st != null)
                  st.close();
            }
         }
      }
      catch (SQLException ex) {
         if (!commitInBetween)
            sum = 0;
         else
            conn.rollback();
      }
      return sum;
   }

   
   /**
    * Gets the specified entries. Since all entry may not fit in one single operation,
    * they are splitted over different operations.
    * If you specified  commitInBetween or the auto-commit flag is set to true,
    * It always returns the number of deleted entries. If a batch could not be completely deleted,
    * it returns the number of operations previously deleted.
    *
    * @param  store the store to use.
    * @param  the connection to be used.
    * @param   ids the array containing all ids to delete.
    * @return the number of entries successfully processed. These are the first. If an
    * error occurs it stops.
    * 
    */
   public XBEntry[] getList(XBStore store, Connection conn, long[] ids, int maxStLength, int maxNumSt, int timeout) throws SQLException, IOException {
      String reqPrefix = getAllSt + inList;
      List ret = new ArrayList();
      List reqList = whereInStatement(reqPrefix, ids, maxStLength, maxNumSt);
      try {
         for (int i=0; i < reqList.size(); i++) {
            String req = (String)reqList.get(i);
            Statement st = null;
            try {
               st = conn.createStatement();
               st.setQueryTimeout(timeout);
               ResultSet rs = st.executeQuery(req);
               while (rs.next()) {
                  XBEntry entry = rsToEntry(rs);
                  ret.add(entry);
               }
            }
            finally {
               if (st != null)
                  st.close();
            }
         }
      }
      catch (SQLException ex) {
      }
      return (XBEntry[])ret.toArray(new XBEntry[ret.size()]);
   }

}