package org.xmlBlaster.client.protocol.http.common;

import java.io.ByteArrayInputStream;
import java.util.Hashtable;

/**
 * Opens a persistent http connection to the servlet which is the proxy to xmlBlaster. 
 * <p>
 * All asynchronous callback messages are received here.
 * </p>
 * Synchronous requests like <i>subscribe()</i> are NOT handled here.
 */
public class PersistentRequest extends Thread {

   private String xmlBlasterServletUrl;
   private XmlBlasterAccessRawBase xmlBlasterAccess;
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
   * @param xmlBlasterServletUrl "http://localhost:8080/xmlBlaster/BlasterHttpProxyServlet&appletInstanceCount=1"
   * @param loginName
   * @param passwd
   */
   PersistentRequest(XmlBlasterAccessRawBase xmlBlasterAccess, String xmlBlasterServletUrl, String loginName, String passwd) {
      this(xmlBlasterAccess, xmlBlasterServletUrl,
         new StringBuffer().append("<qos>").
             append("<securityService type='htpasswd' version='1.0'>").
             append("<![CDATA[").
             append("<user>").append(loginName).append("</user>").
             append("<passwd>").append(passwd).append("</passwd>").
             append("]]>").
             append("</securityService>").
             append("</qos>").toString()
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
   PersistentRequest(XmlBlasterAccessRawBase xmlBlasterAccess, String xmlBlasterServletUrl, String connectQos) {
      super();
      this.xmlBlasterAccess = xmlBlasterAccess;
      this.xmlBlasterServletUrl = this.xmlBlasterAccess.getXmlBlasterServletUrl();
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
         // "dummyToCreateASessionId"
         String ret = (String)this.xmlBlasterAccess.postRequest(I_XmlBlasterAccessRaw.CREATE_SESSIONID_NAME, null, null, null, !XmlBlasterAccessRawBase.ONEWAY);
         log("DEBUG", new StringBuffer().append("POST, send '").append(I_XmlBlasterAccessRaw.CREATE_SESSIONID_NAME).append("' -> returned '").append(ret).append("'").toString());

         /*
          NOTE: We are sending the paramters encoded into the URL because i don't
                know how to send them with the POST.
                Probably one day somebody will mail me the solution: 
                  xmlBlaster@marcelruff.info 2003-11-09
         */

         //String request = "?ActionType="+I_XmlBlasterAccessRaw.CONNECT_NAME +
         //                 "&xmlBlaster.connectQos=" + this.xmlBlasterAccess.encode(this.connectQos, "UTF-8");
         
         //String url = xmlBlasterServletUrl+request;
         String url = xmlBlasterServletUrl;
         I_Connection conn = this.xmlBlasterAccess.createConnection(url);  // This works fine but is more a GET variant
         this.xmlBlasterAccess.writeCookie(conn);
         conn.setDoInput(true);
         conn.setPostMethod();
         conn.setUseCaches(false);
         
         //"connect"
         XmlBlasterAccessRawBase.writeRequest(conn, I_XmlBlasterAccessRaw.CONNECT_NAME, null, this.connectQos, null);
         
         // HTTP POST the connect() request ...
         //conn.setRequestProperty("Content-length", ""+request.length());
         //conn.setRequestProperty("User-Agent","XmlBlasterApplet 1.0");


         // DataOutputStream dataOutput = new DataOutputStream(conn.getOutputStream());
         log("DEBUG", new StringBuffer().append("POST, sending '").append(url).append("' ...").toString());

         //dataOutput.writeBytes("ActionType="+MethodName.CONNECT.toString()+"\n");
         //dataOutput.writeBytes("xmlBlaster.connectQos=" + XmlBlasterAccessRaw.encode(this.connectQos) + "\n");

         // dataOutput.close();

         //log("DEBUG", new StringBuffer().append("Creating now a persistent connection to '").append(url).append("'").toString());

         conn.connect();
         
         this.xmlBlasterAccess.readCookie(conn);
         BufferedInputStreamMicro dataInput = new BufferedInputStreamMicro(conn.getInputStream());
         String line;
         while ((line = dataInput.readLine()) != null) {
            log("DEBUG", new StringBuffer().append("Receiving Base64: <").append(line).append("> with length ").append(line.length()).toString());
            if (line == null || line.length() < 1)
               continue;
            if (line.indexOf("--End") != -1) { // base64 may not contain "--"
               continue;
            }
            byte[] serial = this.xmlBlasterAccess.decodeBase64(line);
            log("DEBUG", new StringBuffer().append("Parsing now: <").append(serial).append("> with length ").append(serial.length).toString());

            // don't know why but sometimes a special character resulting in a converted empty string
            if (serial.length < 1) continue;

            ByteArrayInputStream in = new ByteArrayInputStream(serial);
            ObjectInputStreamMicro ois = new ObjectInputStreamMicro(in);

            String method = (String)ois.readObject(); // e.g. "update"
            //log("DEBUG", "Received method '" + method + "'");

            if (I_XmlBlasterAccessRaw.PING_NAME.equals(method)) { // "ping"
               String qos = (String)ois.readObject();
               //log("DEBUG", "Received ping '" + qos + "'");
               if (qos.indexOf("loginSucceeded") != -1) {
                  this.connectReturnQos = "<qos/>";
                  this.xmlBlasterAccess.isConnected(true);
                  this.xmlBlasterAccess.postRequest("pong", null, null, null, !XmlBlasterAccessRawBase.ONEWAY);
               }
               else { // An ordinary ping arrived
                  if (!this.xmlBlasterAccess.isConnected()) {
                     // Don't send pong, we have not yet connected and our sessionId would be invalid
                     continue;
                  }
                  this.xmlBlasterAccess.postRequest("pong", null, null, null, !XmlBlasterAccessRawBase.ONEWAY);
               }
            }
            else if (I_XmlBlasterAccessRaw.UPDATE_NAME.equals(method)) { // "update"
               String cbSessionId = (String)ois.readObject();
               Hashtable qosMap = (Hashtable)ois.readObject();
               Hashtable keyMap = (Hashtable)ois.readObject();
               String contentBase64 = (String)ois.readObject();
               byte[] content = this.xmlBlasterAccess.decodeBase64(contentBase64);
               log("DEBUG", new StringBuffer().append("Received update keyOid='").append(keyMap.get("/key/@oid")).append("' stateId=").append(qosMap.get("/qos/state/@id")).toString());
               this.xmlBlasterAccess.update(cbSessionId, keyMap, content, qosMap);
            }
            else if ("exception".equals(method)) { // "exception"
               String err = (String)ois.readObject();
               log("ERROR", new StringBuffer().append("Received exception: ").append(err).toString());
            }
            else {
               log("ERROR", new StringBuffer().append("Ignoring response for methodName=").append(method).toString());
            }
         }
      }
      catch (Exception e) {
         e.printStackTrace();
         log("ERROR", new StringBuffer().append("Can't handle exception: ").append(e.toString()).toString());
      }
      finally {
         this.xmlBlasterAccess.isConnected(false);   
      }
   }
}
