/*------------------------------------------------------------------------------
Name:      ObjectOutputStreamMicro.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.protocol.http.common;

import java.io.ByteArrayOutputStream;
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

   /**
    * Returns the length of the message in bytes.
    * @param key
    * @param qos
    * @param content
    * @return
    */
   public static int getMessageLength(String oid, String key, String qos, byte[] content) {
      int ret = 3;
      if (oid != null) ret += oid.length();
      if (key != null) ret += key.length();
      if (qos != null) ret += qos.length();
      if (content != null) ret += content.length;
      return ret;
   }
   
   /**
    * writes a message to the output stream. The format of the message is
    * [oid]\0[key]\0[qos]\0[content]
    * where key and qos are (optional) String objects, and the content an (optional) byte[].
    * So the example '\0\0' of length 2 is an empty message (which is not written to the stream),
    * '\0\0aaa' is a message with no key no qos and a content 'aaa'.
    * @param key The key of the message (can be null)
    * @param qos The qos of the message (can be null)
    * @param content The content of the message (can be null) 
    * @return The length of the message. If the length is less than 2 (i.e. the message is empty
    * because no key, no qos nor content), then it is not written to the stream.
    * 
    * @throws IOException
    */
   public static int writeMessage(OutputStream out, String oid, String key, String qos, byte[] content) 
      throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      if (oid != null) {
         baos.write(oid.getBytes());
      } 
      baos.write(0);
      if (key != null) {
         baos.write(key.getBytes());
      } 
      baos.write(0);
      if (qos != null) baos.write(qos.getBytes());
      baos.write(0);
      if (content != null) baos.write(content);
      byte[] buf = baos.toByteArray();
      if (buf.length > 2) {
         DataOutputStream dos = new DataOutputStream(out);
         //dos.write(buf, 0, buf.length); 
         dos.write(buf); 
         dos.flush();
      }
      return buf.length;
   }

}
