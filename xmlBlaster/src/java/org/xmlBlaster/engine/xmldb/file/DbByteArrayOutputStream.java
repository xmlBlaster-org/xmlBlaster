/*------------------------------------------------------------------------------
Name:      DbByteArrayOutputStream.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Extends ByteArrayOutputStream to provide a way of writing the buffer to
           a DataOutput without re-allocating it.
Version:   $Id: DbByteArrayOutputStream.java,v 1.2 2000/06/18 15:22:00 ruff Exp $
Author:    manuel.kron@gmx.net
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine.xmldb.file;

import org.jutils.log.Log;
import org.xmlBlaster.util.XmlBlasterException;
import java.io.*;

public class DbByteArrayOutputStream extends ByteArrayOutputStream {

  private static String ME = "ByteArrayOutputStream";
  public DbByteArrayOutputStream() {
    super();
  }

  public DbByteArrayOutputStream(int size) {
    super(size);
  }

  /**
   * Writes the full contents of the buffer a DataOutput stream.
   */
  public synchronized void writeTo (DataOutput dstr) throws IOException {
    byte[] data = super.buf;
    int l = super.size();
    dstr.write(data, 0, l);
  }

}
