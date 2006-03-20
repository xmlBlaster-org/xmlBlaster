/*------------------------------------------------------------------------------
Name:      BufferedInputStreamMicro.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.protocol.http.common;

import java.io.InputStream;
import java.io.IOException;

/**
 * BufferedInputStreamMicro
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class BufferedInputStreamMicro implements I_ObjectStream {

   private InputStream in;
   /** The buffer holding the rest of the data coming after a previous EOL */
   private String rest = "";

   public BufferedInputStreamMicro(InputStream inStream) throws IOException {
      this.in = inStream;
   }

   /**
    * 
    * @return null if the stream is closed (finished to read data), a String otherwise. It will always wait until there is data available.
    * @throws IOException
    */
   private final String readNewData() throws IOException {
      int first = this.in.read();
      if (first < 0)
         return null;
      int nmax = this.in.available() + 1;
      if (nmax < 2) {
         byte[] tmp = new byte[1];
         tmp[0] = (byte)first;
         return new String(tmp);
      }

      byte[] buffer = new byte[nmax];
      buffer[0] = (byte)first;
      int read = 1;
      while(read < nmax) {
         read += this.in.read(buffer, read, nmax-read);
      }
      return new String(buffer);
   }
   
   /**
    * It reads until at least one EOL or an EOF is reached.  
    * @return
    * @throws IOException
    */
   private final String readNewDataUntilEolOrEof() throws IOException {
      while (true) {
         String newData = readNewData();
         if (newData == null)
            return null;
         this.rest += newData;
         String tmp = getLineFromBuffer();
         if (tmp != null)
            return tmp;
      }
   }

   private final String getLineFromBuffer() {
      int pos = this.rest.indexOf("\n");
      if (pos > -1) {
         String firstPart = this.rest.substring(0, pos);
         int tmp = this.rest.length() - pos - "\n".length();
         if (tmp > 0)
            this.rest = this.rest.substring(pos+1);
         else
            this.rest = "";
         return firstPart;
      }
      return null;
   }
   
   synchronized public String readLine() throws IOException {
      String ret = getLineFromBuffer();
      if (ret != null)
         return ret;

      ret = readNewDataUntilEolOrEof();
      if (ret != null)
         return ret;
      
      if (this.rest.length() > 0)
         return this.rest;
      else
         return null;
   }
}
