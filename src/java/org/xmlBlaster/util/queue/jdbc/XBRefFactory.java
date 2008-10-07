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
import org.xmlBlaster.util.XmlBlasterException;
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
   private final static int METHOD_NAME = 9;
   private final static int ONE_TO_MANY = 10;
   private final static int LAST_ROW = ONE_TO_MANY;
   
   private String getAndDeleteSt;
   private String getBySamePrioSt;
   private String getByPrioSt;
   private String deleteWithLimitInclSt;
   private String deleteWithLimitExclSt;
   private String getWithLimitSt;
   
   private XBMeatFactory meatFactory;
   
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
   }

   protected void prepareDefaultStatements() {
      super.prepareDefaultStatements();
      StringBuffer buf = new StringBuffer(512);
      
      if (getDbVendor().equals(POSTGRES)) {
         getCompleteSt = "select * from ${table} left outer join ${xbmeat} on (${table}.xbmeatid=${xbmeat}.xbmeatid)";

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
         buf.append("xbprio int4,\n");
         buf.append("xbmethodname varchar(32) default '',\n");
         buf.append("xbonetomany char(1) not null default 'F');\n");
         
         buf.append("    alter table ${table} \n");
         buf.append("            add constraint fkxbstoreref\n");
         buf.append("            foreign key (xbstoreid) \n");
         buf.append("            references ${xbstore} on delete cascade;\n");
         /*
         buf.append("    alter table ${table} \n");
         buf.append("            add constraint fkxbmeat\n");
         buf.append("            foreign key (xbmeatid) \n");
         buf.append("            references ${xbmeat};\n");
         */

         //  "select * from ${table}  limit ?";
      }
      else if (getDbVendor().equals(ORACLE)) {
         getCompleteSt = "select * from ${table} join ${xbmeat} on (${table}.xbmeatid=${xbmeat}.xbmeatid(+))";

         buf.append("create table ${table} (\n");
         buf.append("      xbrefid NUMBER(20) primary key,\n");
         buf.append("      xbstoreid NUMBER(20) not null,\n");
         buf.append("      xbmeatid NUMBER(20) ,\n");
         buf.append("      xbdurable char(1) default 'F' not null ,\n");
         buf.append("      xbbytesize NUMBER(10) ,\n");
         buf.append("      xbmetainfo clob default '',\n");
         buf.append("      xbflag1 varchar(32) default '',\n");
         buf.append("      xbprio  NUMBER(10),\n");
         buf.append("      xbmethodname varchar(32) default '',\n");
         buf.append("      xbonetomany char(1) default 'F' not null\n");
         buf.append("    );\n");
        
         buf.append("    alter table ${table} \n");
         buf.append("            add constraint fkxbstoreref\n");
         buf.append("            foreign key (xbstoreid) \n");
         buf.append("            references ${xbstore} on delete cascade;\n");
         
         /*
         buf.append("    alter table ${table} \n");
         buf.append("            add constraint fkxbmeat\n");
         buf.append("            foreign key (xbmeatid) \n");
         buf.append("            references ${xbmeat};\n");
         */
         // "select * from (select * from ${table}) where rownum <= ?";
      }
      /*
      else if (getDbVendor().equals(DB2)) {
         // create statements
      }
      else if (getDbVendor().equals(FIREBIRD)) {
         // create statements
         // "select first ? * from ${table}";
      }
      else if (getDbVendor().equals(SQLSERVER_2000) || getDbVendor().equals(SQLSERVER_2005)) {
         // create statements
         // "select top ? * from ${table}";
      }
      else if (getDbVendor().equals(MYSQL)) {
         // "select * from ${table} limit ?";
      }
      else if (getDbVendor().equals(SQLITE)) {
         // "select * from ${table} limit ?";
      }
      */
      else { // if (getDbVendor().equals(HSQLDB))
         getCompleteSt = "select * from ${table} left outer join ${xbmeat} on (${table}.xbmeatid=${xbmeat}.xbmeatid)";

         buf.append("create table ${table} (\n");
         buf.append("      xbrefid bigint primary key,\n");
         buf.append("      xbstoreid bigint not null,\n");
         buf.append("      xbmeatid bigint ,\n");
         buf.append("      xbdurable char(1) default 'F' not null ,\n");
         buf.append("      xbbytesize bigint ,\n");
         buf.append("      xbmetainfo varchar default '',\n");
         buf.append("      xbflag1 varchar(32) default '',\n");
         buf.append("      xbprio  integer,\n");
         buf.append("      xbmethodname varchar(32) default '',\n");
         buf.append("      xbonetomany char(1) default 'F' not null);\n");

         buf.append("    alter table ${table} \n");
         buf.append("            add constraint fkxbstoreref\n");
         buf.append("            foreign key (xbstoreid) \n");
         buf.append("            references ${xbstore} on delete cascade;\n");
         /*
         buf.append("    alter table ${table} \n");
         buf.append("            add constraint fkxbmeat\n");
         buf.append("            foreign key (xbmeatid) \n");
         buf.append("            references ${xbmeat};\n");
         */
         // "select limit ? * from ${table}";
      }
      createSt =  buf.toString();

      insertSt = "insert into ${table} values ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
      deleteCompleteSt = "delete from ${table}";
      deleteAllSt = deleteCompleteSt + " where xbstoreid=?";
      deleteSt = deleteAllSt + " and xbrefid=?";
      deleteTransientsSt = deleteAllSt + " and xbdurable='F'";
      // getAllSt = "select * from ${table}";
      deleteWithLimitInclSt = "delete from ${table} where xbstoreid=? and (xbprio > ? or (xbprio = ? and xbrefid <= ?) )";
      deleteWithLimitExclSt = "delete from ${table} where xbstoreid=? and (xbprio > ? or (xbprio = ? and xbrefid < ?) )";
      getNumOfAllSt = "select xbdurable, count(xbbytesize), sum(xbbytesize) from ${table} where xbstoreid=? group by xbdurable";

      getAllSt = getCompleteSt + " where ${table}.xbstoreid=?";
      getWithLimitSt = getAllSt + " and (xbprio > ? or (xbprio = ? and xbrefid < ?) ) order by  xbprio desc, xbrefid asc";

      getSt = getAllSt + " and xbrefid=?";
      getAndDeleteSt = getAllSt + " order by xbprio asc, ${table}.xbrefid desc";
      // these all can be optimized when numEntries is not -1 (see limit alternatives below)
      getFirstEntriesSt = getAllSt + " order by xbprio desc, xbrefid asc";
      getByPrioSt = getAllSt + " and xbprio >= ? and xbprio <= ? order by xbprio desc, xbrefid asc";
      getBySamePrioSt = getAllSt + " and xbprio=(select max(xbprio) from ${table} where xbstoreid=?) order by xbrefid asc";

      inList = " and xbrefid in ("; 
   }
   
   protected void doInit(I_Info info) throws XmlBlasterException {
      getCompleteSt =  info.get(prefix + ".getCompleteStatement", getCompleteSt);
      deleteCompleteSt =  info.get(prefix + ".deleteCompleteStatement", deleteCompleteSt);
      getAndDeleteSt = info.get(prefix + ".getAndDeleteStatement", getAndDeleteSt);
      getFirstEntriesSt = info.get(prefix + ".getFirstEntriesStatement", getFirstEntriesSt);
      getByPrioSt = info.get(prefix + ".getByPrioStatement", getByPrioSt);
      getBySamePrioSt = info.get(prefix + ".getBySamePrioStatement", getBySamePrioSt);
      deleteWithLimitInclSt = info.get(prefix + ".deleteWithLimitInclStatement", deleteWithLimitInclSt);
      deleteWithLimitExclSt = info.get(prefix + ".deleteWithLimitExclStatement", deleteWithLimitExclSt);
      getWithLimitSt = info.get(prefix + ".getWithLimitStatement", getWithLimitSt);
      
      meatFactory = new XBMeatFactory(prefix);
      meatFactory.init(info);
      
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
         if (val != 0)
            preStatement.setLong(MEAT_ID, val);
         else
            preStatement.setNull(MEAT_ID, Types.NUMERIC);

         if (xbRef.isDurable())
            preStatement.setString(DURABLE, "T");
         else 
            preStatement.setString(DURABLE, "F");

         preStatement.setLong(BYTE_SIZE, xbRef.getByteSize());


         if (xbRef.getMetaInfo() != null) {
            InputStream qosStream = new ByteArrayInputStream(xbRef.getMetaInfo().getBytes("UTF-8"));
            preStatement.setAsciiStream(META_INFO, qosStream, xbRef.getMetaInfo().length());
         }
         else
            preStatement.setNull(META_INFO, Types.CLOB);
         
         if (xbRef.getFlag1() != null)
            preStatement.setString(FLAG1, xbRef.getFlag1());
         else
            preStatement.setNull(FLAG1, Types.VARCHAR);

         preStatement.setInt(PRIO, xbRef.getPrio());
         if (xbRef.getMethodName() != null)
            preStatement.setString(METHOD_NAME, xbRef.getMethodName());
         else
            preStatement.setNull(METHOD_NAME, Types.VARCHAR);

         if (xbRef.isOneToMany())
            preStatement.setString(ONE_TO_MANY, "T");
         else 
            preStatement.setString(ONE_TO_MANY, "F");

         preStatement.execute();
      }
      finally {
         if (preStatement != null)
            preStatement.close();
      }
   }

   protected XBEntry rsToEntry(ResultSet rs) throws SQLException, IOException {
      XBRef xbRef = new XBRef();
      xbRef.setId(rs.getLong(REF_ID));
      xbRef.setStoreId(rs.getLong(STORE_ID));
      xbRef.setMeatId(rs.getLong(MEAT_ID));

      String tmp = rs.getString(DURABLE);
      xbRef.setDurable(isTrue(tmp));
      xbRef.setByteSize(rs.getLong(BYTE_SIZE));
      
      InputStream stream = rs.getAsciiStream(META_INFO);
      if (stream != null)
         xbRef.setMetaInfo(new String(readStream(stream), "UTF-8"));
      else
         xbRef.setMetaInfo(null);

      xbRef.setFlag1(rs.getString(FLAG1));
      xbRef.setPrio(rs.getInt(PRIO));
      xbRef.setMethodName(rs.getString(METHOD_NAME));
      tmp = rs.getString(ONE_TO_MANY);
      xbRef.setOneToMany(isTrue(tmp));
      boolean buildMeat = !xbRef.isOneToMany();
      if (buildMeat && xbRef.getMeatId() != 0) {
         XBMeat meat = XBMeatFactory.buildFromRs(rs, LAST_ROW);
         if (meat != null)
            xbRef.setMeat(meat);
      }
      return xbRef;
   }
   
   /**
    * 
    * @param sql The select statement to use to fill the objects.
    * @param conn
    * @return null if the object has not been found or the object if it has been found on the backend.
    * @throws SQLException
    */
   public XBRef get(XBStore store, long id, Connection conn, int timeout) throws SQLException, IOException {
      if (conn == null)
         return null;
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
      PreparedStatement st = null;
      try {
         // TODO optimize this statement by adding a LIMIT
         st = conn.prepareStatement(getAndDeleteSt);
         // String req = "select * from ${table} where xbstoreid=? order by xbprio asc, xbmeatid desc";
         st.setLong(1, store.getId());
         ResultSet rs = st.executeQuery();
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
            final boolean commitInBetween = false;
            XBRef[] refs = (XBRef[])ret.list.toArray(new XBRef[ret.list.size()]);
            deleteList(store, conn, refs, maxStLength, maxNumSt, commitInBetween, timeout);
         }
         return ret;
      }
      finally {
         if (st != null)
            st.close();
      }
   }


   private List/*<XBRef>*/ rs2List(ResultSet rs, long numOfEntries, long numOfBytes, boolean onlyId) throws SQLException, IOException {
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
      return entries;
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
   public List/*<XBRef>*/ getEntriesByPriority(XBStore store, Connection conn, int numOfEntries,
                             long numOfBytes, int minPrio, int maxPrio, boolean onlyId)
      throws SQLException, IOException {

      PreparedStatement st = null;
      try {
         st = conn.prepareStatement(getByPrioSt);
         int pos = 1;
         st.setLong(pos, store.getId());
         pos++;
         st.setInt(pos, minPrio);
         pos++;
         st.setInt(pos, maxPrio);
         pos++;
         ResultSet rs = st.executeQuery();

         return rs2List(rs, numOfEntries, numOfBytes, onlyId);
      }
      finally {
         if (st != null)
            st.close();
      }
   }


   public List/*<XBRef>*/ getWithLimit(XBStore store, Connection conn, XBRef limitRef) throws SQLException, IOException {
      PreparedStatement st = null;
      try {
         st = conn.prepareStatement(getWithLimitSt);
         //  getWithLimitSt = getAllSt + " where xbstoreid=? and (xbprio > ? or (xbprio = ? and xbrefid < ?) ) order by  xbprio desc, xbrefid asc";
         st.setLong(1, store.getId());
         st.setInt(2, limitRef.getPrio());
         st.setInt(3, limitRef.getPrio());
         st.setLong(4, limitRef.getId());
         ResultSet rs = st.executeQuery();
         final long numEntries = -1L;
         final long numBytes = -1L;
         final boolean onlyId = false;
         return rs2List(rs, numEntries, numBytes, onlyId);
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
   public List/*<XBRef>*/ getEntriesBySamePriority(XBStore store, Connection conn, int numOfEntries, long numOfBytes)
      throws SQLException, IOException {
      PreparedStatement st = null;
      try {
         st = conn.prepareStatement(getBySamePrioSt);
         int pos = 1;
         st.setLong(pos, store.getId());
         pos++;
         st.setLong(pos, store.getId());
         pos++;

         ResultSet rs = st.executeQuery();
         final boolean onlyId = false;
         return rs2List(rs, numOfEntries, numOfBytes, onlyId);
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
   

   public int deleteAllStore(XBStore store, Connection conn, int timeout) throws SQLException {
      if (conn == null)
         return 0;
      PreparedStatement preStatement = conn.prepareStatement(deleteAllSt);
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

   protected long getByteSize(ResultSet rs, int offset) throws SQLException {
      return rs.getLong(BYTE_SIZE + offset);
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
      long ret = super.deleteList(store, conn, entries, maxStLength, maxNumSt, commitInBetween, timeout);
      // prepare to delete meats if any:
      List/*<XBMeat>*/ meatList = new ArrayList/*<XBMeat>*/();
      for (int i=0; i < entries.length; i++) {
         XBRef ref = (XBRef)entries[i];
         if (!ref.isOneToMany()) {
            XBMeat meat = ref.getMeat();
            if (meat == null) {
               if (ref.getMeatId() == 0)
                  throw new SQLException("The reference " + ref.toXml("") + " has 'oneToMany' set to false but has no meat defined: this is not allowed");
               meat = new XBMeat(ref.getMeatId());
            }
            meatList.add(meat);
         }
      }
      if (meatList.size() > 0) {
         XBEntry[] meatEntries = (XBEntry[])meatList.toArray(new XBEntry[meatList.size()]);
         meatFactory.deleteList(store, conn, meatEntries, maxStLength, maxNumSt, commitInBetween, timeout);
      }
      return ret;
   }

}