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
      else throw new IOException("object of type with code='" + code + "' is not supported");
   }

   public Object[] readMessage(int length) throws IOException {
      if (length < 3) return new Object[] { "", "", new byte[0] };
      Object[] ret = new Object[3];
      byte[] response = new byte[length];
      int offset = 0;
      while (offset < length-1) {
         int size = in.available();
         size = in.read(response, offset, size);
         offset += size;
      }
      
      int pos = 0, i = pos;
      while (response[i] != 0) i++;
      ret[0] = new String(response, 0, i);
      pos = ++i;

      while (response[i] != 0) i++;
      ret[1] = new String(response, pos, i-pos);
      pos = ++i;

      byte[] tmp = new byte[response.length-pos];
      while (i < response.length) {
         tmp[i-pos] = response[i];
         i++;
      }
      ret[2] = tmp;
      return ret;
   }

}
