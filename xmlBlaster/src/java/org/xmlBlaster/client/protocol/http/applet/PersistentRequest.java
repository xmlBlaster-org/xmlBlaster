package org.xmlBlaster.client.protocol.http.applet;

import java.net.URLConnection;
import java.net.URL;
import java.io.*;
import java.util.*;

/**
 * Opens a persistent http connection to the servlet which is the proxy to xmlBlaster. 
 * <p>
 * All callback messages are received here.
 * </p>
 * Request like <i>subscribe()</i> are NOT handled here.
 */
public class PersistentRequest extends Thread {

   private String xmlBlasterServletUrl;
   private String request;
   private XmlBlasterAccessRaw xmlBlasterAccess;
   private String connectReturnQos;
 
  /**
   * Connect to the BlasterHttpProxyServlet. 
   *
   * @param xmlBlasterAccess My creator
   * @param xmlBlasterServletUrl "http://localhost:8080/xmlBlaster/BlasterHttpProxyServlet"
   * @param loginName
   * @param passwd
   */
   public PersistentRequest(XmlBlasterAccessRaw xmlBlasterAccess, String xmlBlasterServletUrl, String loginName, String passwd) {
      this.xmlBlasterAccess = xmlBlasterAccess;
      this.xmlBlasterServletUrl = this.xmlBlasterAccess.getXmlBlasterServletUrl();
      this.request = "?ActionType=login&xmlBlaster.isApplet=true&xmlBlaster.loginName=" + loginName + "&xmlBlaster.passwd=" + passwd;
   }

   public String getConnectReturnQos() {
      return this.connectReturnQos;
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

         URL url = new URL(xmlBlasterServletUrl+this.request);
         URLConnection conn = url.openConnection();
         conn.setDoInput(true);
         conn.setDoOutput(true);
         System.out.println("PersistentRequest: Creating now a persistent connection to '" + url.toString() + "'");
         conn.connect();
         BufferedReader dataInput = new BufferedReader(new InputStreamReader(conn.getInputStream()));
         String line;
         while ((line = dataInput.readLine()) != null) {
            System.out.println("PersistentRequest: Receiving: " + line);
            
            if (line.indexOf("parent.ping('loginSucceeded')") != -1) {
               //if (line.regionMatches(0,"##ping##loginSucceeded##",0,24)) {
               // !!!! TODO extract the returned QoS !!!
               this.connectReturnQos = "<qos/>";
               this.xmlBlasterAccess.isConnected(true);
               this.xmlBlasterAccess.request("?ActionType=pong",
                                XmlBlasterAccessRaw.GET, !XmlBlasterAccessRaw.ONEWAY);
            }
            else if (line.indexOf("parent.ping('refresh") != -1) {
               //if (line.regionMatches(0,"##ping##",0,8)) {
               if (!this.xmlBlasterAccess.isConnected()) {
                  // Don't send pong, we have not yet connected and our sessionId would be invalid
                  continue;
               }
               this.xmlBlasterAccess.request("?ActionType=pong",
                                XmlBlasterAccessRaw.GET, !XmlBlasterAccessRaw.ONEWAY);
            }
            else if (line.indexOf("if (parent.update != null) parent.update(") != -1) {
               //if (line.regionMatches(0,"##content##",0,11)) {
               update(line);
               // !!! TODO: In an applet we don't need the 'browserReady' handshake, change code in servlet
               this.xmlBlasterAccess.request("?ActionType=browserReady",
                                XmlBlasterAccessRaw.GET, !XmlBlasterAccessRaw.ONEWAY);
            }
            else {
               //System.out.println("PersistentRequest: Ignoring response");
            }
         }
      }
      catch (Exception e) {
         e.printStackTrace();
         System.out.println("PersistentRequest: Can't handle exception: " + e.toString());
      }
      finally {
         this.xmlBlasterAccess.isConnected(false);   
      }
   }

   /**
    * Receive the callback message. 
    */
   public void update(String cbMessage) throws Exception {
      System.out.println("PersistentRequest: Handling callback message");
      Object args[] = new Object[2];
      StringTokenizer strTok =new StringTokenizer(cbMessage, "##");
      String content=java.net.URLDecoder.decode(strTok.nextToken());
      args[0] = java.net.URLDecoder.decode(strTok.nextToken());
      args[1] = java.net.URLDecoder.decode(strTok.nextToken());
      this.xmlBlasterAccess.update("updateFrame", args);
   }
}
