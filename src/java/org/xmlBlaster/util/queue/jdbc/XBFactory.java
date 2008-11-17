/*------------------------------------------------------------------------------
Name:      XbMeatFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * @author <a href='mailto:mr@ruff.info'>Marcel Ruff</a>
 * @author <a href='mailto:michele@laghi.eu'>Michele Laghi</a>
 */

public abstract class XBFactory extends XBFactoryBase {

   private final static Logger log = Logger.getLogger(XBFactory.class.getName());
   protected String insertSt;
   protected String getSt;
   protected String getAllSt;
   protected String getCompleteSt;
   protected String deleteAllSt;
   protected String deleteCompleteSt;
   protected String deleteTransientsSt;
   protected String getFirstEntriesSt;
   protected String getNumOfAllSt;
   protected String deleteSt;
   protected String createSt;
   protected String countSt = "select count(*) from ${table} where xbstoreid=?";
   protected String dropSt = "drop table ${table}";
   protected String prefix = "queue.jdbc";
   protected String table;
   private String tableNameDefault;
   protected String inList;
   protected boolean limitAtEnd;
   protected String base;
   
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
      base = prefix;
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
      
      String tmp = info.get(base + ".table." + XBStoreFactory.getName(), XBStoreFactory.getName());
      info.put(XBStoreFactory.getName(), tmp);
      tmp = info.get(base + ".table." + XBMeatFactory.getName(), XBMeatFactory.getName());
      info.put(XBMeatFactory.getName(), tmp);
      doInit(info);

      insertSt = info.get(prefix + ".insertStatement", insertSt);
      deleteAllSt = info.get(prefix + ".deleteAllStatement", deleteAllSt);
      deleteSt = info.get(prefix + ".deleteStatement", deleteSt);
      deleteTransientsSt = info.get(prefix + ".deleteTransientsSttatement", deleteTransientsSt);
      getSt = info.get(prefix + ".getStatement", getSt);
      getAllSt = info.get(prefix + ".getAllStatement", getAllSt);
      createSt = info.get(prefix + ".createStatement", createSt);
      dropSt = info.get(prefix + ".dropStatement", dropSt);
      countSt = info.get(prefix + ".countStatement", countSt);
      getNumOfAllSt = info.get(prefix + ".getNumOfAllStatement", getNumOfAllSt);
      return info;
   }
   
   public int delete(long storeId, long id, Connection conn, int timeout) throws SQLException {
      if (conn == null)
         return 0;
      PreparedStatement preStatement = conn.prepareStatement(deleteSt);
      if (timeout > 0)
         preStatement.setQueryTimeout(timeout);
      
      try {
         preStatement.setLong(1, storeId);
         if (id != 0)
            preStatement.setLong(2, id);
         return preStatement.executeUpdate();
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
   }

   public int deleteTransients(long storeId, Connection conn, int timeout) throws SQLException {
      if (conn == null)
         return 0;
      PreparedStatement preStatement = conn.prepareStatement(deleteTransientsSt);
      preStatement.setLong(1, storeId);
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
            if (sql.trim().length() > 0) {
               log.info("Executing create statement >" + sql + "<");
               st.executeUpdate(sql);
            }
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
   
   protected final static byte[] readStream(InputStream inStream) throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      int nmax = 262144; // max 200 kb at a time
      int avail = 0;
      byte[] buf = new byte[nmax];
      while ((avail = inStream.read(buf, 0, nmax)) > -1) {
         baos.write(buf, 0, avail);
      }
      return baos.toByteArray();
   }

   private final static boolean useString = true;
   private final static boolean useString2 = false;
   private final static boolean useBinary = false;
   private final static boolean useCharacter = false;

   /**
    * String/VARCHAR/CLOB helper to avoid NULL and to take care on Umlauts/UTF-8
    * 
    * @param preStatement
    * @param index
    * @param value
    * @throws SQLException
    */
   public final static String getDbCol(ResultSet rs, int index) throws SQLException, IOException {
      if (useString) {
         return rs.getString(index);
      } else if (useString2) {
         byte[] bytes = rs.getBytes(index);
         if (bytes != null) {
            try {
               return new String(rs.getBytes(index), "UTF-8");
            } catch (UnsupportedEncodingException e) {
               e.printStackTrace();
               return rs.getString(index);
            }
         }
         else
            return "";
      } else if (useBinary) {
         InputStream stream = rs.getAsciiStream(index);
         if (stream != null) {
            try {
               return new String(readStream(stream), "UTF-8");
            } catch (UnsupportedEncodingException e) {
               e.printStackTrace();
               return new String(readStream(stream));
            }
         } else {
            return "";
         }
      } else {
         throw new IllegalArgumentException("NOT IMPLEMENTED");
      }
   }

   /**
    * String/VARCHAR/CLOB helper to avoid NULL and to take care on Umlauts/UTF-8
    * 
    * @param preStatement
    * @param index
    * @param value
    * @throws SQLException
    * @throws UnsupportedEncodingException
    */
   public final static void fillDbCol(PreparedStatement preStatement, int index, String value) throws SQLException,
         UnsupportedEncodingException {
      if (value == null) {
         preStatement.setNull(index, Types.VARCHAR);
         // preStatement.setNull(index QOS KEY, Types.CLOB);
         return;
      }

      boolean isOracleNullFix = true;
      if (isOracleNullFix) {
         if (value == null || value.length() == 0)
            value = " ";
      }

      if (useString) {
         preStatement.setString(index, value);
      } else if (useString2) {
         byte[] bytes = value.getBytes("UTF-8");
         preStatement.setBytes(index, bytes);
      } else if (useBinary) {
         byte[] bytes = value.getBytes("UTF-8");
         InputStream stream = new ByteArrayInputStream(bytes);
         preStatement.setAsciiStream(index, stream, bytes.length);
      } else {
         // preStatement.getCharacterStream(index, )
      }
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
      */
      else if (getDbVendor().equals(SQLSERVER_2000) || getDbVendor().equals(SQLSERVER_2005)) {
         limitAtEnd = false;
      }
      /*
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
   protected final List whereInStatement(String reqPrefix, XBEntry[] entries, int maxStatementLength, int maxNumStatements) {
      final String reqPostfix = ")";
      boolean isFirst = true;
      int initialLength = reqPrefix.length() + reqPostfix.length() + 2;
      StringBuffer buf = new StringBuffer();
      int length = initialLength;
      int currentLength = 0;

      List ret = new ArrayList();
      int count = 0;
      for (int i=0; i<entries.length; i++) {
         String req = null;
         String entryId = Long.toString(entries[i].getId());
         currentLength = entryId.length();
         length += currentLength;
         if ((length > maxStatementLength) || (i == (entries.length-1)) || count >= maxNumStatements) { // then make the update
            if (i == (entries.length-1)) {
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
   public long deleteList(XBStore store, Connection conn, XBEntry[] entries, int maxStLength, int maxNumSt, boolean commitInBetween, int timeout) throws SQLException {
      String reqPrefix = deleteCompleteSt + " where xbstoreid=" + store.getId() + " " + inList;
      List reqList = whereInStatement(reqPrefix, entries, maxStLength, maxNumSt);
      commitInBetween = commitInBetween && !conn.getAutoCommit();
      long sum = 0;
      try {
         for (int i=0; i < reqList.size(); i++) {
            String req = (String)reqList.get(i);
            Statement st = null;
            try {
               st = conn.createStatement();
               if (timeout > 0)
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
   public long count(XBStore store, Connection conn, int timeout) throws SQLException {
      PreparedStatement st = null;
      try {
         st = conn.prepareStatement(countSt);
         if (timeout > 0)
            st.setQueryTimeout(timeout);
         st.setLong(1, store.getId());
         ResultSet rs = st.executeQuery();
         if (rs.next())
            return rs.getLong(1);
         return 0L;
      }
      finally {
         if (st != null)
            st.close();
      }
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
   public List/*<XBEntry>*/ getList(XBStore store, Connection conn, XBEntry[] entries, int maxStLength, int maxNumSt, int timeout) throws SQLException, IOException {
      String reqPrefix = getCompleteSt + " where xbstoreid=" + store.getId() + " " + inList;
      List ret = new ArrayList();
      List reqList = whereInStatement(reqPrefix, entries, maxStLength, maxNumSt);
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
      return ret;
   }
   
   protected abstract long getByteSize(ResultSet rs, int offset) throws SQLException;
   
   /**
    * @param store
    * @param conn
    * @param numOfEntries
    * @param numOfBytes
    * @param timeout
    * @return
    * @throws SQLException
    */
   public List/*<XBEntry>*/ getFirstEntries(XBStore store, Connection conn, long numOfEntries, long numOfBytes, int timeout)
      throws SQLException, IOException {
      PreparedStatement ps = null;
      try {
         ps = conn.prepareStatement(getFirstEntriesSt);
         // to make sure the question marks are filled in the correct order since this depends on the vendor
         /*
         int limit = 1;
         int qId = 1;
         if (limitAtEnd)
            limit = 2;
         else
            qId = 2;
         
         ps.setLong(limit, numOfEntries);
         ps.setLong(qId, store.getId());
         */
         if (numOfEntries != -1)
            ps.setMaxRows((int)numOfEntries);
         boolean storeMustBeSet = getFirstEntriesSt.indexOf('?') > -1;
         if (storeMustBeSet)
            ps.setLong(1, store.getId());
      
         ResultSet rs = ps.executeQuery();
         long countEntries = 0L;
         long countBytes = 0L;
         List list = new ArrayList();
         while ( (rs.next()) && ((countEntries < numOfEntries) || (numOfEntries < 0)) &&
               ((countBytes < numOfBytes) || (numOfBytes < 0))) {
            long byteSize = getByteSize(rs, 0);
            if ( (numOfBytes < 0) || (countBytes + byteSize < numOfBytes) || (countEntries == 0)) {
               XBEntry entry = rsToEntry(rs);
               list.add(entry);
               countBytes += byteSize;
               countEntries++;
            }
         }
         return list;
      }
      finally {
         if (ps != null)
            ps.close();
      }
   }
   
   /**
    * Gets the real number of entries. 
    * That is it really makes a call to the DB to find out
    * how big the size is.
    * @return never null
    */
   public final EntryCount getNumOfAll(XBStore store, Connection conn)
      throws SQLException {

      PreparedStatement st = null;
      try {
         st = conn.prepareStatement(getNumOfAllSt);
         st.setLong(1, store.getId());
         ResultSet rs = st.executeQuery();
         EntryCount entryCount = new EntryCount();
         if (rs.next()) {
            long transientOfEntries = 0;
            long transientOfBytes = 0;
            boolean persistent = isTrue(rs.getString(1));
            if (persistent) {
               entryCount.numOfPersistentEntries = rs.getLong(2);
               entryCount.numOfPersistentBytes = rs.getLong(3);
            }
            else {
               transientOfEntries = rs.getLong(2);
               transientOfBytes = rs.getLong(3);
            }
            if (rs.next()) {
               persistent = isTrue(rs.getString(1));
               if (persistent) {
                  entryCount.numOfPersistentEntries = rs.getLong(2);
                  entryCount.numOfPersistentBytes = rs.getLong(3);
               }
               else {
                  transientOfEntries = rs.getLong(2);
                  transientOfBytes = rs.getLong(3);
               }
            }
            entryCount.numOfEntries = transientOfEntries + entryCount.numOfPersistentEntries;
            entryCount.numOfBytes = transientOfBytes + entryCount.numOfPersistentBytes;
         }
         return entryCount;
      }
      finally {
         if (st != null)
            st.close();
      }
   }

   protected final static boolean isTrue(String asTxt) {
      return "T".equalsIgnoreCase(asTxt);
   }
   
   protected final boolean checkSameStore(XBStore store, XBEntry entry) {
      if (store == null)
         return false;
      if (entry == null)
         return false;
      boolean ret = entry.getStoreId() == store.getId();
      if (!ret)
         log.severe("Meat and Store are inconsistent " + entry.toXml("") + " store " + store.getId());
      return ret;
   }

   
}