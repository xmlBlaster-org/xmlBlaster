package org.xmlBlaster.client.protocol.http.applet;

import java.applet.Applet;
import java.net.URLConnection;
import java.net.URL;
import java.io.*;

import org.xmlBlaster.util.MsgUnitRaw;

/**
 * A java client implementation to access xmlBlaster using a persistent http connection
 * for instant callback messages. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class XmlBlasterAccessRaw implements I_XmlBlasterAccessRaw
{
   private Applet applet;
   /** Typically "http://localhost:8080/xmlBlaster/BlasterHttpProxyServlet" */
   private String xmlBlasterServletUrl;
   private PersistentRequest persistentHttpConnection;
   private I_CallbackRaw callback;
   public static final boolean ONEWAY = true;
   public static final boolean POST = true;
   public static final boolean GET = false;
   private boolean isConnected = false;

   /**
    * @param applet My environment
    */
   public XmlBlasterAccessRaw(Applet applet) {
      System.out.println("XmlBlasterAccessRaw: ctor");
      this.applet = applet;
      this.xmlBlasterServletUrl = this.applet.getParameter("xmlBlasterServletUrl"); //param from html page
      if (this.xmlBlasterServletUrl == null) {
         // getCodeBase() == http://localhost:8080/xmlBlaster/
         this.xmlBlasterServletUrl = this.applet.getCodeBase() + "BlasterHttpProxyServlet";
      }
      System.out.println("XmlBlasterAccessRaw: " + this.xmlBlasterServletUrl);
   }

   /** 
    * Access the URL of the xmlBlaster servlet. 
    * @return Typically "http://localhost:8080/xmlBlaster/BlasterHttpProxyServlet"
    */
   public String getXmlBlasterServletUrl() {
      return this.xmlBlasterServletUrl;
   }

   public void isConnected(boolean isConnected) {
      this.isConnected = isConnected;
      System.out.println("XmlBlasterAccessRaw: isConnected(" + isConnected + ")");
   }

   public boolean isConnected() {
      return this.isConnected;
   }

   public String connect(String qos, I_CallbackRaw callback) throws Exception {
      this.callback = callback;
      
      // TODO!!!: extract loginName and passwd out from qos
      String loginName = this.applet.getParameter("xmlBlaster.loginName");
      if (loginName == null) loginName = "joe";
      String passwd = this.applet.getParameter("xmlBlaster.passwd");
      if (passwd == null) passwd = "secret";

      this.persistentHttpConnection = new PersistentRequest(this, this.xmlBlasterServletUrl, loginName, passwd);
      this.persistentHttpConnection.start();
      System.out.println("XmlBlasterAccessRaw: Waiting for connect() to establish ...");

      int num = 100;
      int i;
      for (i=0; i<num; i++) {
         if (this.isConnected) {
            break;
         }
         try {
            Thread.sleep(500);
         } catch(java.lang.InterruptedException e){
            System.out.println(e);
         }
      }
      if (i >= num) {
         throw new Exception("XmlBlasterAccessRaw: Can't login to xmlBlaster, timed out.");
      }
      System.out.println("XmlBlasterAccessRaw: Successfully connected to xmlBlaster as user " + loginName);
      return this.persistentHttpConnection.getConnectReturnQos();
   }

   public String subscribe(java.lang.String xmlKey, java.lang.String qos) throws Exception {
      throw new Exception("XmlBlasterAccessRaw: subscribe() is not implemented");
      // "ActionType=subscribe", POST, !ONEWAY);
   }

   public MsgUnitRaw[] get(java.lang.String xmlKey, java.lang.String qos) throws Exception {
      throw new Exception("XmlBlasterAccessRaw: get() is not implemented");
      //request("ActionType=unSubscribe", POST, !ONEWAY);
   }

   public String[] unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws Exception {
      throw new Exception("XmlBlasterAccessRaw: unSubscribe() is not implemented");
   }

   public String publish(MsgUnitRaw msgUnit) throws Exception {
      throw new Exception("XmlBlasterAccessRaw: publish() is not implemented");
   }

   public String[] erase(java.lang.String xmlKey, java.lang.String qos) throws Exception {
      throw new Exception("XmlBlasterAccessRaw: erase() is not implemented");
   }

   public void disconnect(String qos) {
      //URL url = new URL(xmlBlasterServletUrl+"?ActionType=logout", POST, ONEWAY);
   }

   /**
    * Send a http request to the servlet. 
    * @param request The request string without the URL prefix, e.g. "?XmlBlasterAccessRawType=pong"
    * @param doPost if true POST else GET
    * @param oneway true for requests returning void
    * @return The returned value for the given request, "" on error or for oneway messages
    */
   public String request(String request, boolean doPost, boolean oneway) {
      try {
         // applet.getAppletContext().showDocument(URL url, String target);
         
         URL url = (doPost) ? new URL(this.xmlBlasterServletUrl) : new URL(this.xmlBlasterServletUrl + request);
         URLConnection conn = url.openConnection();

         System.out.println("XmlBlasterAccessRaw: doPost=" + doPost + ", sending '" + this.xmlBlasterServletUrl + request + "' ...");
         if(doPost){  // for HTTP-POST, e.g. for  publish(), subscribe()
            conn.setDoOutput(true);
            DataOutputStream dataOutput = new DataOutputStream(conn.getOutputStream());
            dataOutput.writeBytes(request);
            dataOutput.close();
         }

         if (oneway) {
            return "";
         }

         // Read the return value ...
         BufferedReader dataInput = new BufferedReader(new InputStreamReader(conn.getInputStream()));
         String line;
         StringBuffer ret = new StringBuffer(1024);
         int count = 0;
         while ((line = dataInput.readLine()) != null){
            System.out.println("XmlBlasterAccessRaw: Return value for '" + request + "' = '" + line + "'");
            if (count > 0) {
               ret.append("\n");
            }
            count++;
            ret.append(line);
         }
         return ret.toString();
      }
      catch (IOException e) {
         e.printStackTrace();
         System.out.println("XmlBlasterAccessRaw: " + e.toString());
      }
      return "";
   }

   /**
    * !!! TODO
    */
   public String update(String func, Object args[]) throws Exception {
      System.out.println("XmlBlasterAccessRaw: Receiving update message");
      if (this.callback == null) {
         throw new Exception("XmlBlasterAccessRaw: Receiving unexpected update message, no callback handle available");
      }
      String cbSessionId = "";
      return this.callback.update(cbSessionId, (String)args[0], (new String("")).getBytes(), (String)args[1]);
   }
}


