/*------------------------------------------------------------------------------
Name:      RecordReader.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Used to read data from a record
Version:   $Id: RecordReader.java,v 1.1 2000/09/03 18:02:59 kron Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence.mudb.file;

import org.jutils.log.Log;
import org.xmlBlaster.util.XmlBlasterException;

import java.io.*;

public class RecordReader {

  private static String ME = "RecordReader";
  String key;
  byte[] data;
  ByteArrayInputStream in;
  ObjectInputStream objIn;

  public RecordReader(String key, byte[] data) {
    this.key = key;
    this.data = data;
    in = new ByteArrayInputStream(data);
  }

  public String getKey() {
    return key;
  }

  public byte[] getData() {
    return data;
  }

  public InputStream getInputStream() throws IOException {
    return in;
  }

  public ObjectInputStream getObjectInputStream() throws IOException {
    if (objIn == null) {
      objIn = new ObjectInputStream(in);
    }
    return objIn;
  }

  /**
   * Reads the next object in the record using an ObjectInputStream.
   */
  public Object readObject() throws IOException, OptionalDataException, ClassNotFoundException {
    return getObjectInputStream().readObject();
  }

}






