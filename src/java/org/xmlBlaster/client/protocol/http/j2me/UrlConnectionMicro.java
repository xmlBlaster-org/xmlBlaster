/*------------------------------------------------------------------------------
Name:      UrlConnectionMicro.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.protocol.http.j2me;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;

import org.xmlBlaster.client.protocol.http.common.I_Connection;

/**
 * UrlConnectionMicro
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class UrlConnectionMicro implements I_Connection {

   private HttpConnection conn;

   public UrlConnectionMicro(String url) throws IOException {
      this.conn = (HttpConnection)Connector.open(url);
   }

   public InputStream getInputStream() throws IOException {
      return this.conn.openInputStream();
   }
   
   public OutputStream getOutputStream() throws IOException {
      return conn.openOutputStream();
   }
   
   public String getHeaderField(String key) {
      try {
         return this.conn.getHeaderField(key);
      }
      catch(IOException ex) {
         ex.printStackTrace();
         return null;
      }
   }
   
   public void setRequestProperty(String key, String val) {
      try {
         this.conn.setRequestProperty(key, val);
      }
      catch(IOException ex) {
         ex.printStackTrace();
      }
   }
   
   public void setPostMethod() {
      try {
         this.conn.setRequestMethod(HttpConnection.POST);
      }
      catch (IOException ex) {
         ex.printStackTrace();
      }
   }
   
   public void setDoInput(boolean doInput) {
      // TODO implement it
   }
   
   public void setDoOutput(boolean doOutput) {
      // TODO implement it
   }

   public void setUseCaches(boolean useCaches) {
      // TODO implement it
   }
   
   public void connect() throws IOException {
      int ret = this.conn.getResponseCode();
   }

}
