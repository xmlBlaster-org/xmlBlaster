/*------------------------------------------------------------------------------
Name:      UrlConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.protocol.http.applet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;

import org.xmlBlaster.client.protocol.http.common.I_Connection;


/**
 * UrlConnection
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class UrlConnection implements I_Connection {

   private HttpURLConnection conn;
   
   public UrlConnection(String urlString) throws Exception {
      URL url = new URL(urlString);
      this.conn = (HttpURLConnection)url.openConnection();
      this.conn.setDoOutput(true);
   }

   public InputStream getInputStream() throws IOException {
      return this.conn.getInputStream();
   }

   public OutputStream getOutputStream() throws IOException {
      return this.conn.getOutputStream();
   }

   public String getHeaderField(String key) {
      return this.conn.getHeaderField(key);
   }

   public void setRequestProperty(String key, String val) {
      this.conn.setRequestProperty(key, val);
   }

   public void setPostMethod() {
      //this.conn.setDoOutput(true);
      try {
         this.conn.setRequestMethod("POST");
      }
      catch (ProtocolException ex) {
         ex.printStackTrace();
      }
   }

   public void setDoInput(boolean doInput) {
      this.conn.setDoInput(doInput);
   }

   public void setDoOutput(boolean doOutput) {
      this.conn.setDoOutput(doOutput);
   }

   public void setUseCaches(boolean useCaches) {
      this.conn.setUseCaches(useCaches);
   }

   public void connect() throws IOException {
      this.conn.connect();
   }

}
