/*------------------------------------------------------------------------------
Name:      ObjectInputStreamMicro.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.protocol.http.common;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Hashtable;
import java.util.Vector;

/**
 * ObjectInputStreamMicro
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class ObjectInputStreamMicro implements I_ObjectStream {

   private DataInputStream in;

   public ObjectInputStreamMicro(InputStream inStream) throws IOException {
      this.in = new DataInputStream(inStream);       
   }
   
   private Hashtable readHashtable() throws IOException {
      int size = this.in.readInt();
      Hashtable ret = new Hashtable();
      for (int i=0; i < size; i++) {
         String key = this.in.readUTF();
         String val = this.in.readUTF();
         ret.put(key, val);
      }
      return ret;
   }

   private Vector readVector() throws IOException {
      int size = this.in.readInt();
      Vector ret = new Vector();
      for (int i=0; i < size; i++) {
         ret.addElement(readHashtable());
         ret.addElement(readHashtable());
         int length = this.in.readInt();
         byte[] content = new byte[length];
         this.in.read(content);
         ret.addElement(content);
      }
      return ret;
   }

   public Object readObject() throws IOException, ClassNotFoundException {
      int code = this.in.readInt();
      if (code == STRING) {
         return this.in.readUTF();
      }
      else if (code == HASHTABLE) {
         return readHashtable();
      }
      else if (code == HASHTABLE_ARR) {
         int size  = this.in.readInt();
         Hashtable[] ret = new Hashtable[size];
         for (int i=0; i < size; i++) {
            ret[i] = readHashtable();
         }
         return ret;
      }
      else if (code == VECTOR) {
         return readVector();
      }
      else {
         StringBuffer buf = new StringBuffer();
         buf.append("object of type with code='").append(code).append("' is not supported");
         throw new IOException(buf.toString());
      }
   }

   /**
    * 
    * @param buffer the buffer from which to read the message
    * @return the MsgHolder object.
    * If one of the parameters was null or empty before serialization, it
    * will be null here after reading it from the buffer.
    * @throws IOException
    */
   public static MsgHolder readMessage(byte[] buffer) throws IOException {
      if (buffer.length < 4) return new MsgHolder(null, null, null, null);
      
      int pos = 0, i = pos;
      while (buffer[i] != 0) i++;
      String oid = ((i == 0) ? (String)null : new String(buffer, 0, i));
      pos = ++i;

      while (buffer[i] != 0) i++;
      String key = new String(buffer, pos, i-pos);
      if (key.length() < 1) key = null;
      pos = ++i;

      while (buffer[i] != 0) i++;
      String qos = new String(buffer, pos, i-pos);
      if (qos.length() < 1) qos = null;
      pos = ++i;

      byte[] content = new byte[buffer.length-pos];
      while (i < buffer.length) {
         content[i-pos] = buffer[i];
         i++;
      }
      return new MsgHolder(oid, key, qos, content);
   }

}
