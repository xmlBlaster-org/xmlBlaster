/*------------------------------------------------------------------------------
Name:      SerializeHelper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin.extern;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;

/**
 * Used for Serialization and deserialization for Objects
 */

public class SerializeHelper {

  private final Global glob;
  private final LogChannel log;
  private final String ME;

  public SerializeHelper(Global glob) {
    this.glob = glob;
    this.log = glob.getLog("jmx");
    this.ME = "SerializeHelper" + this.glob.getLogPrefixDashed();
  }


  /**
   * Serializes object to byteArray
   */
  public byte[] serializeObject(Object obj) throws IOException {
    log.info(ME,"Serializing object " + obj);
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try {
      ObjectOutputStream oos = new ObjectOutputStream(bos);
      oos.writeObject(obj);
    }
    catch (IOException ex) {
      ex.printStackTrace();
      throw new IOException("Unable to serializeObject " + ex.toString());
    }
    return bos.toByteArray();
  }


/**
 * Deserializes byteArray to Java-Object
 */
  public Object deserializeObject(byte[] mybyte) throws IOException {
    log.info(ME,"Deserializing object ");
    Object obj = new Object();
    ByteArrayInputStream bas = new ByteArrayInputStream(mybyte);
    try {
      ObjectInputStream ois = new ObjectInputStream(bas);
      obj = ois.readObject();
    }
    catch (ClassNotFoundException ex) {
      throw new IOException("Unable to rebuild Object  "+ ex.toString());
    }catch (IOException ex) {
      ex.printStackTrace();
    }

    return obj;
  }


}