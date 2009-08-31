package org.xmlBlaster.util.protocol.xmlrpc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcSun15HttpTransport;

public class XblHttpTransport extends XmlRpcSun15HttpTransport {

   public XblHttpTransport(XmlRpcClient pClient) {
      super(pClient);
   }
   
   protected InputStream getInputStream() throws XmlRpcException {
      try {
         return getURLConnection().getInputStream();
      } catch (IOException e) {
         // since the error content (normally the html code) is not propagated
         // we need to doit by our self.
         if (getURLConnection() instanceof HttpURLConnection) {
            try {
               HttpURLConnection errUrl = (HttpURLConnection)getURLConnection();
               if (errUrl != null) {
                  InputStream errStr = errUrl.getErrorStream();
                  BufferedReader br = new BufferedReader(new InputStreamReader(errStr));
                  StringBuffer buf = new StringBuffer();
                  String line = "";
                  while (line != null) {
                     line = br.readLine();
                     if (line == null)
                        break;
                     buf.append(line).append("\n");
                  }
                  errStr.close();
                  if (buf.length() > 0) {
                     throw new XmlRpcException(errUrl.getResponseCode(), buf.toString(), e);
                  }
               }
            }
            catch (IOException ex) {
               ex.printStackTrace();
            }
         }
         
         throw new XmlRpcException("Failed to create input stream: " + e.getMessage(), e);
      }
   }

   
   
   
}
