/*------------------------------------------------------------------------------
Name:      BufferedInputStreamMicro.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.protocol.http.applet;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * BufferedInputStreamMicro
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class BufferedInputStreamMicro implements I_ObjectStream {

   private InputStream in;
   private String rest = "";
   private BufferedReader bufIn;
   

   public BufferedInputStreamMicro(InputStream inStream) throws IOException {
      this.in = inStream;
      this.bufIn = new BufferedReader(new InputStreamReader(this.in)); 
   }

   synchronized public String readLine() throws IOException {
      if (true) return this.bufIn.readLine();
      
      int pos = this.rest.indexOf("\n");
      if (pos > -1) {
         String ret = this.rest.substring(0, pos);
         this.rest = this.rest.substring(pos+1);
         return ret;
      }
      StringBuffer ret = new StringBuffer(this.rest);

      this.rest = "";
      boolean doRun = true;
      while (doRun) {
         int first = this.in.read();
         if (first < 0) return null;
         int nmax = this.in.available();
         byte[] buffer = new byte[nmax+1];
         buffer[0] = (byte)first;
         if (nmax > 0) {
            int length = this.in.read(buffer, 1, nmax);
            if (length < nmax) doRun = false;
         }
         String tmp = new String(buffer);
         pos = tmp.indexOf("\n");
         if (pos < 0) ret.append(tmp);
         else {
            if (pos > 0) {
               this.rest = tmp.substring(pos+1);
               tmp = tmp.substring(0, pos);
               ret.append(tmp);
            }
            else this.rest = "";
            break;
         }
      }
      return ret.toString();      
   }

   
   public static void main(String[] args) {
      
      try {
         String tmp = "this is a\nvery strange\nstory\n\n\nfor me, but\nI will wait\n\n";
         ByteArrayInputStream bais = new ByteArrayInputStream(tmp.getBytes());
         BufferedInputStreamMicro in = new BufferedInputStreamMicro(bais);
         String line = "";
         System.out.println("--------------");
         while ( (line = in.readLine()) != null) {
            System.out.println(line);
         }
         System.out.println("--------------");
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }

   
}
