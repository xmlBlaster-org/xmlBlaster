/*------------------------------------------------------------------------------
Name:      RecordHeader.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Provides a wrapper to hold key information about a record
Version:   $Id: RecordHeader.java,v 1.2 2000/09/15 17:16:16 ruff Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence.mudb.file;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import java.io.*;


public class RecordHeader {

  private static String ME = "RecordHeader";
  /**
   * File pointer to the first byte of record data (8 bytes).
   */
  protected long dataPointer;

  /**
   * Actual number of bytes of data held in this record (4 bytes).
   */
  protected int dataCount;

  /**
   * Number of bytes of data that this record can hold (4 bytes).
   */
  protected int dataCapacity;

  /**
   * Indicates this header's position in the file index.
   */
  protected int indexPosition;

  protected RecordHeader() {
  }

  protected RecordHeader(long dataPointer, int dataCapacity) {
    if (dataCapacity < 1) {
      throw new IllegalArgumentException("Bad record size: " + dataCapacity);
    }
    this.dataPointer = dataPointer;
    this.dataCapacity = dataCapacity;
    this.dataCount = 0;
  }

  protected int getIndexPosition() {
    return indexPosition;
  }

  protected void setIndexPosition(int indexPosition) {
    this.indexPosition = indexPosition;
  }

  protected int getDataCapacity() {
    return dataCapacity;
  }

  protected int getFreeSpace() {
    return dataCapacity - dataCount;
  }

  protected void read(DataInput in) throws IOException {
    dataPointer = in.readLong();
    dataCapacity = in.readInt();
    dataCount = in.readInt();
  }

  protected void write(DataOutput out) throws IOException {
    out.writeLong(dataPointer);
    out.writeInt(dataCapacity);
    out.writeInt(dataCount);
  }

  protected static RecordHeader readHeader(DataInput in) throws IOException {
    RecordHeader r = new RecordHeader();
    r.read(in);
    return r;
  }

  /**
   * Returns a new record header which occupies the free space of this record.
   * Shrinks this record size by the size of its free space.
   */
  protected RecordHeader split() throws XmlBlasterException {
    long newFp = dataPointer + (long)dataCount;
    RecordHeader newRecord = new RecordHeader(newFp, getFreeSpace());
    dataCapacity = dataCount;
    return newRecord;
  }

}










