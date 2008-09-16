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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.util.queue.ReturnDataHolder;

/**
 * @author <a href='mailto:mr@ruff.info'>Marcel Ruff</a>
 * @author <a href='mailto:michele@laghi.eu'>Michele Laghi</a>
 */

public class XBRefFactory extends XBFactory {

   private final static Logger log = Logger.getLogger(XBRefFactory.class.getName());
   
   private final static int REF_ID = 1;
   private final static int STORE_ID = 2;
   private final static int MEAT_ID = 3;
   private final static int DURABLE = 4;
   private final static int BYTE_SIZE = 5;
   private final static int META_INFO = 6;
   private final static int FLAG1 = 7;
   private final static int PRIO = 8;
   private String base;
   private String getAndDeleteSt;
   private String getBySamePrioSt;
   private String getByPrioSt;
   private String getFirstEntriesSt;
   private String deleteWithLimitInclSt;
   private String deleteWithLimitExclSt;
   private String deleteAllStoreSt;
   private String getNumOfAllSt;
   
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
      deleteAllSt = "delete from ${table}";
      deleteSt = deleteAllSt + " where xbrefid=?";
      deleteAllStoreSt = deleteAllSt + " where xbstoreid=?";
      deleteTransientsSt = deleteAllSt + " where xbdurable='F'";
      getAllSt = "select * from ${table}";
      getSt = getAllSt + " where xbrefid=?";
      getAndDeleteSt = "select * from ${table} where xbstoreid=? order by xbprio asc, xbmeatid desc";
      deleteWithLimitInclSt = "delete from ${table} where xbstoreid=? and (xbprio > ? or (xbprio = ? and xbrefid <= ?) )";
      deleteWithLimitExclSt = "delete from ${table} where xbstoreid=? and (xbprio > ? or (xbprio = ? and xbrefid < ?) )";
      getNumOfAllSt = "select xbdurable, count(xbbytesize), sum(xbbytesize) from ${table} where xbstoreid=? group by xbdurable";

      inList = " where xbrefid in ("; 
   }

   protected void prepareDefaultStatements() {
      super.prepareDefaultStatements();
      StringBuffer buf = new StringBuffer(512);
      
      if (getDbVendor().equals(POSTGRES)) {
         buf.append("create table ${table} (\n");
         buf.append("xbrefid int8 primary key unique not null,\n");
         buf.append("xbstoreid int8 not null,\n");
         buf.append("xbmeatid int8,\n");
         buf.append("-- creationts timestamp not null default current_timestamp,\n");
         buf.append("-- modifiedts timestamp not null default current_timestamp,\n");
         buf.append("xbdurable char(1) not null default 'F',\n");
         buf.append("xbbytesize int4,\n");
         buf.append("xbmetainfo text default '',\n");
         buf.append("xbflag1 varchar(32) default '',\n");
         buf.append("xbprio int4)\n");
         getFirstEntriesSt = "select xbrefid, xbbytesize from ${table} where xbstoreid=? order by xbprio desc, xbrefid asc limit ?";
         getByPrioSt = "select * from ${table} where xbstoreid=? and xbprio >= ? and xbprio <= ? order by xbprio desc, xbrefid asc limit ?";
         getBySamePrioSt = "select * from ${table} where xbstoreid=? and xbprio=(select max(prio) from ${table} where xbstoreid=?) order by xbrefid asc limit ?";
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
         getFirstEntriesSt = "select * from (select xbrefid, xbbytesize from ${table} where xbstoreid=? order by xbprio desc, xbrefid asc) where rownum <= ?";
         getByPrioSt = "select * from (select * from ${table} where xbstoreid=? and xbprio >= ? and xbprio <= ? order by xbprio desc, xbrefid asc) where rownum <= ?";
         getBySamePrioSt = "select * from (select * from ${table} where xbstoreid=? and xbprio=(select max(prio) from ${table} where xbstoreid=?) order by xbrefid asc) where rownum <= ?";
      }
      /*
      else if (getDbVendor().equals(DB2)) {
         // create statements
      }
      else if (getDbVendor().equals(FIREBIRD)) {
         // create statements
         deleteFirstEntriesSt = "select first ? xbrefid, xbbytesize from ${table} where xbstoreid=? order by xbprio desc, xbrefid asc";
         getByPrioSt = "select first ? * from ${table} where xbstoreid=? and xbprio >= ? and xbprio <= ? order by xbprio desc, xbrefid asc";
         getBySamePrioSt = "select first ? * from ${table} where xbstoreid=? and xbprio=(select max(prio) from ${table} where xbstoreid=?) order by xbrefid asc";
      }
      else if (getDbVendor().equals(SQLSERVER_2000) || getDbVendor().equals(SQLSERVER_2005)) {
         // create statements
         deleteFirstEntriesSt = "select top ? xbrefid, xbbytesize from ${table} where xbstoreid=? order by xbprio desc, xbrefid asc";
         getEntriesByPrioSt = "select top ? * from ${table} where xbstoreid=? and xbprio >= ? and xbprio <= ? order by xbprio desc, xbrefid asc";
         getBySamePrioSt = "select top ? * from ${table} where xbstoreid=? and xbprio=(select max(prio) from ${table} where xbstoreid=?) order by xbrefid asc";
      }
      else if (getDbVendor().equals(MYSQL)) {
         deleteFirstEntriesSt = "select xbrefid, xbbytesize from ${table} where xbstoreid=? order by xbprio desc, xbrefid asc limit ?";
         getEntriesByPrioSt = "select * from ${table} where xbstoreid=? and xbprio >= ? and xbprio <= ? order by xbprio desc, xbrefid asc limit ?";
         getBySamePrioSt = "select * from ${table} where xbstoreid=? and xbprio=(select max(prio) from ${table} where xbstoreid=?) order by xbrefid asc limit ?";
      }
      else if (getDbVendor().equals(SQLITE)) {
         deleteFirstEntriesSt = "select xbrefid, xbbytesize from ${table} where xbstoreid=? order by xbprio desc, xbrefid asc limit ?";
         getEntriesByPrioSt = "select * from ${table} where xbstoreid=? and xbprio >= ? and xbprio <= ? order by xbprio desc, xbrefid asc limit ?";
         getBySamePrioSt = "select * from ${table} where xbstoreid=? and xbprio=(select max(prio) from ${table} where xbstoreid=?) order by xbrefid asc limit ?";
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

         getFirstEntriesSt = "select limit ? xbrefid, xbbytesize from ${table} where xbstoreid=? order by xbprio desc, xbrefid asc";
         getByPrioSt = "select limit ? * from ${table} where xbstoreid=? and xbprio >= ? and xbprio <= ? order by xbprio desc, xbrefid asc";
         getBySamePrioSt = "select limit ? * from ${table} where xbstoreid=? and xbprio=(select max(prio) from ${table} where xbstoreid=?) order by xbrefid asc";
      }
      createSt =  buf.toString();
   }
   
   protected void doInit(I_Info info) {
      String tmp = info.get(base + ".table." + XBStoreFactory.getName(), XBStoreFactory.getName());
      info.put(XBStoreFactory.getName(), tmp);
      tmp = info.get(base + ".table." + XBMeatFactory.getName(), XBMeatFactory.getName());
      info.put(XBMeatFactory.getName(), tmp);
      getAndDeleteSt = info.get(prefix + ".getAndDeleteStatement", getAndDeleteSt);
      getFirstEntriesSt = info.get(prefix + ".getFirstEntriesStatement", getFirstEntriesSt);
      getByPrioSt = info.get(prefix + ".getByPrioStatement", getByPrioSt);
      getBySamePrioSt = info.get(prefix + ".getBySamePrioStatement", getBySamePrioSt);
      deleteWithLimitInclSt = info.get(prefix + ".deleteWithLimitInclStatement", deleteWithLimitInclSt);
      deleteWithLimitExclSt = info.get(prefix + ".deleteWithLimitExclStatement", deleteWithLimitExclSt);
      getNumOfAllSt = info.get(prefix + ".getNumOfAllStatement", getNumOfAllSt);
      deleteAllStoreSt = info.get(prefix + ".deleteAllStoreStatement", deleteAllStoreSt);
      inList = " where xbrefid in (";
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
         preStatement.setLong(REF_ID, xbRef.getId());
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

   private final boolean isDurable(String asTxt) {
      return "T".equalsIgnoreCase(asTxt);
   }
   
   protected XBEntry rsToEntry(ResultSet rs) throws SQLException, IOException {
      XBRef xbRef = new XBRef();
      xbRef.setId(rs.getLong(REF_ID));
      xbRef.setStoreId(rs.getLong(STORE_ID));
      xbRef.setMeatId(rs.getLong(MEAT_ID));

      String tmp = rs.getString(DURABLE);
      xbRef.setDurable(isDurable(tmp));
      xbRef.setByteSize(rs.getLong(BYTE_SIZE));
      
      InputStream stream = rs.getAsciiStream(META_INFO);
      xbRef.setMetaInfo(new String(readStream(stream), "UTF-8"));

      xbRef.setFlag1(rs.getString(FLAG1));
      xbRef.setPrio(rs.getInt(PRIO));
      return xbRef;
   }
   
   /**
    * 
    * @param sql The select statement to use to fill the objects.
    * @param conn
    * @return null if the object has not been found or the object if it has been found on the backend.
    * @throws SQLException
    */
   public XBRef get(long id, Connection conn, int timeout) throws SQLException, IOException {
      if (conn == null)
         return null;
      PreparedStatement preStatement = conn.prepareStatement(deleteSt);
      ResultSet rs = null;
      try {
         if (timeout > 0)
            preStatement.setQueryTimeout(timeout);
         preStatement.setLong(1, id);
         rs = preStatement.executeQuery();
         if (!rs.next())
            return null;
         return (XBRef)rsToEntry(rs);
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
   }
   
   
   /**
    * Helper method to find out if still to retrieve entries in getAndDeleteLowest or not. 
    */
   private final boolean isInsideRange(int numEntries, int maxNumEntries, long numBytes, long maxNumBytes) {
      if (maxNumEntries < 0) {
         if (maxNumBytes <0L) return true;
         return numBytes < maxNumBytes;
      }
      // then maxNumEntries >= 0
      if (maxNumBytes <0L) return numEntries < maxNumEntries;
      // then the less restrictive of both is used (since none is negative)
      return numEntries < maxNumEntries || numBytes < maxNumBytes;
   }

   /**
    * Under the same transaction it gets and deletes all the entries which fit
    * into the constrains specified in the argument list.
    * The entries are really deleted only if doDelete is true, otherwise they are left untouched on the queue
    * @see org.xmlBlaster.util.queue.I_Queue#takeLowest(int, long, org.xmlBlaster.util.queue.I_QueueEntry, boolean)
    */
   public ReturnDataHolder getAndDeleteLowest(XBStore store, Connection conn, int numOfEntries, long numOfBytes,
      int maxPriority, long minUniqueId, boolean leaveOne, boolean doDelete, int maxStLength, int maxNumSt, int timeout) throws SQLException, IOException {

      ReturnDataHolder ret = new ReturnDataHolder();
      PreparedStatement ps = null;
      try {
         ps = conn.prepareStatement(getAndDeleteSt);   
         // String req = "select * from ${table} where xbstoreid=? order by xbprio asc, xbmeatid desc";
         ResultSet rs = ps.executeQuery();
         boolean doContinue = true;
         boolean stillEntriesInQueue = false;

         while ( (stillEntriesInQueue=rs.next()) && doContinue) {
            XBRef ref = (XBRef)rsToEntry(rs);
            if (!isInsideRange((int)ret.countEntries, numOfEntries, ret.countBytes, numOfBytes)) 
               break;
            // check if allowed or already outside the range ...
            int prio = ref.getPrio();
            long dataId = ref.getId();
            if ((prio < maxPriority) || ((prio == maxPriority) && (dataId > minUniqueId)) ) {
               ret.list.add(ref);
               ret.countBytes += ref.getByteSize();
               if (ref.isDurable())
                  ret.countPersistentBytes += ref.getByteSize();
            }
            else 
               doContinue = false;
            ret.countEntries++;
            if (ref.isDurable())
               ret.countPersistentEntries++;
         }

         // prepare for deleting (we don't use deleteEntries since we want
         // to use the same transaction (and the same connection)
         if (leaveOne) {
            // leave at least one entry
            if (stillEntriesInQueue) 
               stillEntriesInQueue = rs.next();
            if ((!stillEntriesInQueue) && (ret.list.size()>0)) {
               ret.countEntries--;
               XBRef entryToDelete = (XBRef)ret.list.remove(ret.list.size()-1);
               ret.countBytes -= entryToDelete.getByteSize();
               boolean persistent = entryToDelete.isDurable();
               if (persistent) {
                  ret.countPersistentEntries--;
                  ret.countPersistentBytes -= entryToDelete.getByteSize();
               }
               if (log.isLoggable(Level.FINE)) 
                  log.fine("takeLowest size to delete: "  + entryToDelete.getByteSize());
            }
         }

         if (doDelete) {
            //first strip the unique ids:
            long[] ids = new long[ret.list.size()];
            for (int i=0; i < ids.length; i++)
               ids[i] = ((XBRef)ret.list.get(i)).getId();
            final boolean commitInBetween = false;
            deleteList(store, conn, ids, maxStLength, maxNumSt, commitInBetween, timeout);
         }
         return ret;
      }
      finally {
         if (ps != null)
            ps.close();
      }
   }

   
   /**
    * Note that this method returns the list of deleted entries, but they are only filled with the id and
    * the byteSize.
    * 
    * @param store
    * @param conn
    * @param numOfEntries
    * @param numOfBytes
    * @param timeout
    * @return
    * @throws SQLException
    */
   public XBRef[] getFirstEntries(XBStore store, Connection conn, long numOfEntries, long numOfBytes, int timeout)
      throws SQLException {
      PreparedStatement ps = null;
      try {
         ps = conn.prepareStatement(getFirstEntriesSt);
         // to make sure the question marks are filled in the correct order since this depends on the vendor
         int limit = 1;
         int qId = 1;
         if (limitAtEnd)
            limit = 2;
         else
            qId = 2;
         
         ps.setLong(limit, numOfEntries);
         ps.setLong(qId, store.getId());
      
         ResultSet rs = ps.executeQuery();
         long countEntries = 0L;
         long countBytes = 0L;
         List list = new ArrayList();
         while ( (rs.next()) && ((countEntries < numOfEntries) || (numOfEntries < 0)) &&
               ((countBytes < numOfBytes) || (numOfBytes < 0))) {
            long byteSize = rs.getLong(2);
            if ( (numOfBytes < 0) || (countBytes + byteSize < numOfBytes) || (countEntries == 0)) {
               XBRef ref = new XBRef();
               ref.setId(rs.getLong(1));
               ref.setByteSize(byteSize);
               list.add(ref);
               countBytes += byteSize;
               countEntries++;
            }
         }
         return (XBRef[])list.toArray(new XBRef[list.size()]);
      }
      finally {
         if (ps != null)
            ps.close();
      }
   
   }


   private XBRef[] rs2Array(ResultSet rs, long numOfEntries, long numOfBytes, boolean onlyId) throws SQLException, IOException {
      List entries = new ArrayList();
      int count = 0;
      long amount = 0L;

      while ( (rs.next()) && ((count < numOfEntries) || (numOfEntries < 0)) &&
         ((amount < numOfBytes) || (numOfBytes < 0))) {
         XBRef ref = null;
         if(onlyId) {
            ref = new XBRef();
            ref.setId(rs.getLong(1));
         }
         else {
            ref = (XBRef)rsToEntry(rs);
            if ( (numOfBytes < 0) || (ref.getByteSize() + amount < numOfBytes) || (count == 0)) {
               amount += ref.getByteSize();
            }
         }
         entries.add(ref);
         count++;
      }
      return (XBRef[])entries.toArray(new XBRef[entries.size()]);
      
   }
   
   
   /**
    * gets the first numOfEntries of the queue which have the priority in the
    * range specified by prioMin and prioMax (inclusive).
    * If there are not so many entries in the queue, all elements in the queue
    * are returned.
    *
    * @param storageId the storageId of the queue/storage from which to retrieve the information.
    * @param numOfEntries the maximum number of elements to retrieve. If negative there is no constriction.
    * @param numOfBytes the maximum number of bytes to retrieve. If negative, there is no constriction.
    * @param minPrio the minimum priority to retreive (inclusive). 
    * @param maxPrio the maximum priority to retrieve (inclusive).
    *
    */
   public XBRef[] getEntriesByPriority(XBStore store, Connection conn, int numOfEntries,
                             long numOfBytes, int minPrio, int maxPrio, boolean onlyId)
      throws SQLException, IOException {

      PreparedStatement st = null;
      try {
         st = conn.prepareStatement(getByPrioSt);
         int pos = 1;
         if (!limitAtEnd) {
            st.setLong(pos, numOfEntries);
            pos++;
         }
         st.setLong(pos, store.getId());
         pos++;
         st.setInt(pos, minPrio);
         pos++;
         st.setInt(pos, maxPrio);
         pos++;
         if (limitAtEnd) {
            st.setLong(pos, numOfEntries);
            pos++;
         }

         ResultSet rs = st.executeQuery();

         return rs2Array(rs, numOfEntries, numOfBytes, onlyId);
      }
      finally {
         if (st != null)
            st.close();
      }
   }

   /**
    * gets the first numOfEntries of the queue which have the same priority.
    * If there are not so many entries in the queue, all elements in the queue
    * are returned.
    *
    * @param numOfEntries the maximum number of elements to retrieve
    *
    */
   public XBRef[] getEntriesBySamePriority(XBStore store, Connection conn, int numOfEntries, long numOfBytes)
      throws SQLException, IOException {
      PreparedStatement st = null;
      try {
         st = conn.prepareStatement(getByPrioSt);
         int pos = 1;
         if (!limitAtEnd) {
            st.setLong(pos, numOfEntries);
            pos++;
         }
         st.setLong(pos, store.getId());
         pos++;
         st.setLong(pos, store.getId());
         pos++;
         if (limitAtEnd) {
            st.setLong(pos, numOfEntries);
            pos++;
         }

         ResultSet rs = st.executeQuery();
         final boolean onlyId = false;
         return rs2Array(rs, numOfEntries, numOfBytes, onlyId);
      }
      finally {
         if (st != null)
            st.close();
      }
   }
   
   
   /**
    * deletes the first numOfEntries of the queue until the limitEntry is reached.
    * @param numOfEntries the maximum number of elements to retrieve
    */
   public long deleteWithLimit(XBStore store, Connection conn, XBRef limitEntry, boolean inclusive)
      throws SQLException {
      PreparedStatement st = null;
      try {
         if (inclusive)
            st = conn.prepareStatement(deleteWithLimitInclSt);
         else
            st = conn.prepareStatement(deleteWithLimitExclSt);
         
         int limitPrio = limitEntry.getPrio();
         long limitId = limitEntry.getId();
         st.setLong(1, store.getId());
         st.setInt(2, limitPrio);
         st.setInt(3, limitPrio);
         st.setLong(4, limitId);
         return st.executeUpdate();
      }
      finally {
         if (st != null)
            st.close();
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

      if (log.isLoggable(Level.FINE)) 
         log.fine("Request: '" + getNumOfAllSt + "'");
      PreparedStatement st = null;
      try {
         st = conn.prepareStatement(getNumOfAllSt);
         st.setLong(1, store.getId());
         ResultSet rs = st.executeQuery();
         EntryCount entryCount = new EntryCount();
         if (rs.next()) {
            long transientOfEntries = 0;
            long transientOfBytes = 0;
            boolean persistent = isDurable(rs.getString(1));
            if (persistent) {
               entryCount.numOfPersistentEntries = rs.getLong(2);
               entryCount.numOfPersistentBytes = rs.getLong(3);
            }
            else {
               transientOfEntries = rs.getLong(2);
               transientOfBytes = rs.getLong(3);
            }
            if (rs.next()) {
               persistent = isDurable(rs.getString(1));
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
         if (log.isLoggable(Level.FINE)) log.fine("Num=" + entryCount.toString());
         return entryCount;
      }
      finally {
         if (st != null)
            st.close();
      }
   }

   public int deleteAllStore(XBStore store, Connection conn, int timeout) throws SQLException {
      if (conn == null)
         return 0;
      PreparedStatement preStatement = conn.prepareStatement(deleteAllStoreSt);
      if (timeout > 0)
         preStatement.setQueryTimeout(timeout);
      try {
         preStatement.setLong(1, store.getId());
         return preStatement.executeUpdate();
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
   }

   
   
   
}