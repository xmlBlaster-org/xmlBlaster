package org.xmlBlaster.client.protocol.http.applet;

import java.net.URLConnection;
import java.net.URL;
import java.io.*;
import java.util.*;
import org.apache.commons.codec.binary.Base64;
import org.xmlBlaster.util.def.MethodName;

/**
 * Opens a persistent http connection to the servlet which is the proxy to xmlBlaster. 
 * <p>
 * All asynchronous callback messages are received here.
 * </p>
 * Synchronous requests like <i>subscribe()</i> are NOT handled here.
 */
public class PersistentRequest extends Thread {

   private String xmlBlasterServletUrl;
   //private String request;
   private XmlBlasterAccessRaw xmlBlasterAccess;
   private String connectReturnQos;
   private String loginName;
   private String passwd;
   private String connectQos;
 
  /**
   * Connect to the BlasterHttpProxyServlet. 
   * <p>
   * This is a convenience constructor if you don't want to create the connect QoS yourself.
   * </p>
   * @param xmlBlasterAccess My creator
   * @param xmlBlasterServletUrl "http://localhost:8080/xmlBlaster/BlasterHttpProxyServlet"
   * @param loginName
   * @param passwd
   */
   PersistentRequest(XmlBlasterAccessRaw xmlBlasterAccess, String xmlBlasterServletUrl, String loginName, String passwd) {
      this(xmlBlasterAccess, xmlBlasterServletUrl,
         "<qos>" +
            "<securityService type='htpasswd' version='1.0'>" +
            "<![CDATA[" +
            "<user>" + loginName + "</user>" +
            "<passwd>" + passwd + "</passwd>" +
            "]]>" +
            "</securityService>" +
         "</qos>"
      );
   }

  /**
   * Connect to the BlasterHttpProxyServlet. 
   *
   * @param xmlBlasterAccess My creator
   * @param xmlBlasterServletUrl "http://localhost:8080/xmlBlaster/BlasterHttpProxyServlet"
   * @param connectQos It must at least contain the "securityService" settings to be evaluated!
   * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">The interface.connect requirement</a>
   */
   PersistentRequest(XmlBlasterAccessRaw xmlBlasterAccess, String xmlBlasterServletUrl, String connectQos) {
      this.xmlBlasterAccess = xmlBlasterAccess;
      this.xmlBlasterServletUrl = this.xmlBlasterAccess.getXmlBlasterServletUrl();
      this.loginName = loginName;
      this.passwd = passwd;
      this.connectQos = connectQos;
   }

   String getConnectReturnQos() {
      return this.connectReturnQos;
   }

   /**
    * Log to java console of the browser
    */
   private void log(String level, String text) {
      this.xmlBlasterAccess.log("PersistentRequest", level, text);
   }

   /**
    * Connect to xmlBlaster. 
    * The InputStream is never closed so the servlet can push
    * new messages to us.
    * To keep the http connection alive we do a ping pong.
    */
   public void run(){
      try{
         this.xmlBlasterAccess.request("?ActionType=dummyToCreateASessionId",
                           XmlBlasterAccessRaw.GET, !XmlBlasterAccessRaw.ONEWAY);

         /*
          NOTE: We are sending the paramters encoded into the URL because i don't
                know how to send them with the POST.
                Probably one day somebody will mail me the solution: 
                  xmlBlaster@marcelruff.info 2003-11-09
         */

         String request = "?ActionType="+MethodName.CONNECT.toString() +
                          "&xmlBlaster.connectQos=" + XmlBlasterAccessRaw.encode(this.connectQos, "UTF-8");
         URL url = new URL(xmlBlasterServletUrl+request);  // This works fine but is more a GET variant
         //URL url = new URL(xmlBlasterServletUrl);
         
         URLConnection conn = url.openConnection();
         conn.setDoInput(true);
         conn.setDoOutput(true);
         conn.setUseCaches(false);
         
         // HTTP POST the connect() request ...
         //conn.setRequestProperty("Content-length", ""+request.length());
         //conn.setRequestProperty("User-Agent","XmlBlasterApplet 1.0");
         DataOutputStream dataOutput = new DataOutputStream(conn.getOutputStream());
         log("DEBUG", "POST, sending '" + url.toString() + "' ...");
         //dataOutput.writeBytes("ActionType="+MethodName.CONNECT.toString()+"\n");
         //dataOutput.writeBytes("xmlBlaster.connectQos=" + XmlBlasterAccessRaw.encode(this.connectQos) + "\n");
         dataOutput.close();

         log("DEBUG", "Creating now a persistent connection to '" + url.toString() + "'");
         conn.connect();
         BufferedReader dataInput = new BufferedReader(new InputStreamReader(conn.getInputStream()));
         String line;
         while ((line = dataInput.readLine()) != null) {
            //log("DEBUG", "Receiving Base64: <" + line + ">");
            if (line == null || line.length() < 1)
               continue;
            if (line.indexOf("--End") != -1) { // base64 may not contain "--"
               continue;
            }
            byte[] serial = Base64.decodeBase64(line.getBytes());
            //log("DEBUG", "Parsing now: <" + new String(serial) + ">");

            ByteArrayInputStream in = new ByteArrayInputStream(serial);
            ObjectInputStream ois = new ObjectInputStream(in);

            String method = (String)ois.readObject(); // e.g. "update"

            if (MethodName.PING.toString().equals(method)) { // "ping"
               String qos = (String)ois.readObject();
               log("DEBUG", "Received ping '" + qos + "'");
               if (qos.indexOf("loginSucceeded") != -1) {
                  this.connectReturnQos = "<qos/>";
                  this.xmlBlasterAccess.isConnected(true);
                  this.xmlBlasterAccess.request("?ActionType=pong",
                                   XmlBlasterAccessRaw.GET, !XmlBlasterAccessRaw.ONEWAY);
               }
               else { // An ordinary ping arrived
                  if (!this.xmlBlasterAccess.isConnected()) {
                     // Don't send pong, we have not yet connected and our sessionId would be invalid
                     continue;
                  }
                  this.xmlBlasterAccess.request("?ActionType=pong",
                                   XmlBlasterAccessRaw.GET, !XmlBlasterAccessRaw.ONEWAY);
               }
            }
            else if (MethodName.UPDATE.toString().equals(method)) { // "update"
               String cbSessionId = (String)ois.readObject();
               Map qosMap = (Map)ois.readObject();
               Map keyMap = (Map)ois.readObject();
               String contentBase64 = (String)ois.readObject();
               byte[] content = Base64.decodeBase64(contentBase64.getBytes());
               log("DEBUG", "Received update keyOid='" + keyMap.get("/key/@oid") + "' stateId=" + qosMap.get("/qos/state/@id"));
               this.xmlBlasterAccess.update(cbSessionId, keyMap, content, qosMap);
            }
            else if ("exception".equals(method)) { // "exception"
               String err = (String)ois.readObject();
               log("ERROR", "Received exception: " + err);
            }
            else {
               log("ERROR", "Ignoring response for methodName=" + method);
            }
         }
      }
      catch (Exception e) {
         e.printStackTrace();
         log("ERROR", "Can't handle exception: " + e.toString());
      }
      finally {
         this.xmlBlasterAccess.isConnected(false);   
      }
   }
}
