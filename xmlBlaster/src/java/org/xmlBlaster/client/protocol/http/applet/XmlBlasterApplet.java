package org.xmlBlaster.client.protocol.http.applet;

import java.applet.Applet;
import java.awt.Graphics;

/**
 * An example applet which connects to xmlBlaster using a persistent http tunnel. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class XmlBlasterApplet extends Applet implements I_CallbackRaw
{
   I_XmlBlasterAccessRaw xmlBlasterAccess;

   public void init(){
      System.out.println("XmlBlasterApplet: init");
      try {
         this.xmlBlasterAccess = new XmlBlasterAccessRaw(this);
         this.xmlBlasterAccess.connect("<qos/>", this);
      }
      catch (Exception e) {
         System.out.println("XmlBlasterApplet: No connection to xmlBlaster: " + e.toString());
         showStatus("XmlBlasterApplet: No connection to xmlBlaster: " + e.toString());
      }
   }
 
   /**
    * Gaining focus. 
    */
   public void start() {
      System.out.println("XmlBlasterApplet: start");
   }

   /**
    * Loosing focus. 
    */
   public void stop() {
      System.out.println("XmlBlasterApplet: stop");
   }
   
   public void destroy(){
      System.out.println("XmlBlasterApplet: destroy");
      if (this.xmlBlasterAccess != null) {
         this.xmlBlasterAccess.disconnect("<qos/>");
         this.xmlBlasterAccess = null;
      }
   }

   public String getAppletInfo() {
      return "Demo for a simple applet with access to xmlBlaster";
   }

   /**
    * Here you receive the callback messages from xmlBlaster. 
    */
   public String update(String cbSessionId, String updateKey, byte[] content, String updateQos) throws Exception {
      System.out.println("XmlBlasterApplet: update received: key=" + updateKey + " content=" + new String(content));
      return "<qos/>";
   }

   public void paint(Graphics g) {
      System.out.println("XmlBlasterApplet: paint called");
      g.drawString(getAppletInfo(), 5, 25);
   }
}

