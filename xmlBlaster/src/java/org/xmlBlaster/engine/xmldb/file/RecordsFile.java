/*------------------------------------------------------------------------------
Name:      RecordsFile.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Provide an implementation for the abstract methods.
Version:   $Id: RecordsFile.java,v 1.2 2000/06/18 15:22:00 ruff Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine.xmldb.file;

import org.jutils.log.Log;
import org.xmlBlaster.util.XmlBlasterException;

import java.io.*;
import java.util.*;


public class RecordsFile extends BaseRecordsFile {

  private static String ME = "BaseRecordsFile";
  /**
   * Hashtable which holds the in-memory index. For efficiency, the entire index
   * is cached in memory. The hashtable maps a key of type String to a RecordHeader.
   */
  protected Hashtable memIndex;

  /**
   * Creates a new database file.  The initialSize parameter determines the
   * amount of space which is allocated for the index.  The index can grow
   * dynamically, but the parameter is provide to increase
   * efficiency.
   */
  public RecordsFile(String dbPath, int initialSize) throws IOException, XmlBlasterException {
    super(dbPath, initialSize);
    memIndex = new Hashtable(initialSize);
  }

  /**
   * Opens an existing database and initializes the in-memory index.
   */
  public RecordsFile(String dbPath, String accessFlags) throws IOException, XmlBlasterException {
    super(dbPath, accessFlags);
    int numRecords = readNumRecordsHeader();
    memIndex = new Hashtable(numRecords);
    for (int i = 0; i < numRecords; i++) {
      String key = readKeyFromIndex(i);
      RecordHeader header = readRecordHeaderFromIndex(i);
      header.setIndexPosition(i);
      memIndex.put(key, header);
    }
  }


  /**
   * Returns an enumeration of all the keys in the database.
   */
  public synchronized Enumeration enumerateKeys() {
    return memIndex.keys();
  }

  /**
   * Returns the current number of records in the database.
   */
  public synchronized int getNumRecords() {
    return memIndex.size();
  }

  /**
   * Checks if there is a record belonging to the given key.
   */
  public synchronized boolean recordExists(String key) {
    return memIndex.containsKey(key);
  }

  /**
   * Maps a key to a record header by looking it up in the in-memory index.
   */
  protected RecordHeader keyToRecordHeader(String key) throws XmlBlasterException {
    RecordHeader h = (RecordHeader)memIndex.get(key);
    if (h==null) {
      throw new XmlBlasterException(ME,"Key not found: " + key);
    }
    return h;
  }

  /**
   * This method searches the file for free space and then returns a RecordHeader
   * which uses the space. (O(n) memory accesses)
   */
  protected RecordHeader allocateRecord(String key, int dataLength) throws XmlBlasterException, IOException {
    // search for empty space
    RecordHeader newRecord = null;
    Enumeration e = memIndex.elements();
    while (e.hasMoreElements()) {
      RecordHeader next = (RecordHeader)e.nextElement();
      int free = next.getFreeSpace();
      if (dataLength <= next.getFreeSpace()) {
        newRecord = next.split();
        writeRecordHeaderToIndex(next);
        break;
      }
    }
    if (newRecord == null) {
      // append record to end of file - grows file to allocate space
      long fp = getFileLength();
      setFileLength(fp + dataLength);
      newRecord = new RecordHeader(fp, dataLength);
    }
    return newRecord;
  }

  /**
   * Returns the record to which the target file pointer belongs - meaning the specified location
   * in the file is part of the record data of the RecordHeader which is returned.  Returns null if
   * the location is not part of a record. (O(n) mem accesses)
   */
  protected RecordHeader getRecordAt(long targetFp) throws XmlBlasterException {
    Enumeration e = memIndex.elements();
    while (e.hasMoreElements()) {
      RecordHeader next = (RecordHeader) e.nextElement();
      if (targetFp >= next.dataPointer &&
          targetFp < next.dataPointer + (long)next.dataCapacity) {
        return next;
      }
    }
    return null;
  }


  /**
   * Closes the database.
   */
  public synchronized void close() throws IOException, XmlBlasterException {
    try {
      super.close();
    } finally {
      memIndex.clear();
      memIndex = null;
    }
  }

  /**
   * Adds the new record to the in-memory index and calls the super class add
   * the index entry to the file.
   */
  protected void addEntryToIndex(String key, RecordHeader newRecord, int currentNumRecords) throws IOException, XmlBlasterException {
    super.addEntryToIndex(key, newRecord, currentNumRecords);
    memIndex.put(key, newRecord);
  }

  /**
   * Removes the record from the index. Replaces the target with the entry at the
   * end of the index.
   */
  protected void deleteEntryFromIndex(String key, RecordHeader header, int currentNumRecords) throws IOException, XmlBlasterException {
    super.deleteEntryFromIndex(key, header, currentNumRecords);
    RecordHeader deleted = (RecordHeader)memIndex.remove(key);
  }


}






