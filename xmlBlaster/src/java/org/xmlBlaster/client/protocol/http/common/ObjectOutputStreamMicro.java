/*------------------------------------------------------------------------------
Name:      ObjectOutputStreamMicro.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.protocol.http.common;

import java.io.IOException;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 * ObjectOutputStreamMicro
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class ObjectOutputStreamMicro implements I_ObjectStream {

   private DataOutputStream out;
   
   public ObjectOutputStreamMicro(OutputStream outStream) throws IOException {
      this.out = new DataOutputStream(outStream);
   }
   
   /**
    * Helper to write the map to an output stream without the
    * use of a ObjectOutputStream (to allow to read it from j2me which
    * does not have ObjectInputStream)
    * @param out
    */
   private void writeHashtable(Hashtable map) throws IOException {
      int nmax = map.size();
      if (nmax < 1) return;
      this.out.writeInt(nmax);
      Enumeration keys = map.keys();
      while (keys.hasMoreElements()) {
         String key = (String)keys.nextElement();
         this.out.writeUTF(key);
         this.out.writeUTF((String)map.get(key));
      }
   }

   private void writeVector(Vector vec) throws IOException {
      int size = vec.size() / 3;
      this.out.writeInt(size);
      for (int i=0; i < size; i++) {
         Hashtable qos = (Hashtable)vec.elementAt(3*i);
         Hashtable key = (Hashtable)vec.elementAt(3*i+1);
         byte[] content = (byte[])vec.elementAt(3*i+2);
         writeHashtable(qos);
         writeHashtable(key);
         this.out.writeInt(content.length);
         this.out.write(content);
      }
   }

   public void writeObject(Object obj) throws IOException {
      if (obj instanceof String) {
         this.out.writeInt(STRING);
         this.out.writeUTF((String)obj);
      }
      else if (obj instanceof Hashtable) {
         this.out.writeInt(HASHTABLE);
         writeHashtable((Hashtable)obj);
      }
      else if (obj instanceof Hashtable[]) {
         this.out.writeInt(HASHTABLE_ARR);
         Hashtable[] tables = (Hashtable[])obj;
         int size = tables.length;
         this.out.writeInt(size);
         for (int i=0; i < size; i++) {
            writeHashtable(tables[i]);
         }
      }
      else if (obj instanceof Vector) {
         this.out.writeInt(VECTOR);
         writeVector((Vector)obj);
      }
      else throw new IOException("object of type '" + obj.getClass().getName() + "' is not supported");
   }
   
}
