/*------------------------------------------------------------------------------
Name:      RecordWriter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Used to construct a new record an dwrite it to file as byte buffer 
Version:   $Id: RecordWriter.java,v 1.1 2000/06/14 10:23:15 kron Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine.xmldb.file;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;

import java.io.*;

public class RecordWriter {

  private static String ME = "RecodWriter";
  String key;
  DbByteArrayOutputStream out;
  ObjectOutputStream objOut;

  public RecordWriter(String key) {
    this.key = key;
    out = new DbByteArrayOutputStream();
  }

  public String getKey() {
    return key;
  }

  public OutputStream getOutputStream() {
    return out;
  }

  public ObjectOutputStream getObjectOutputStream() throws IOException {
    if (objOut == null) {
      objOut = new ObjectOutputStream(out);
    }
    return objOut;
  }

  public void writeObject(Object o) throws IOException {
    getObjectOutputStream().writeObject(o);
    getObjectOutputStream().flush();
  }

  /**
   * Returns the number of bytes in the data.
   */
  public int getDataLength() {
    return out.size();
  }

  /**
   *  Writes the data out to the stream without re-allocating the buffer.
   */
  public void writeTo(DataOutput str) throws IOException {
    out.writeTo(str);
  }

}






