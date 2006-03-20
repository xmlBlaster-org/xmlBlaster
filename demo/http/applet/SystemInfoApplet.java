package http.applet;

import org.xmlBlaster.client.protocol.http.applet.XmlBlasterAccessRaw;
import org.xmlBlaster.client.protocol.http.common.I_CallbackRaw;
import org.xmlBlaster.client.protocol.http.common.I_XmlBlasterAccessRaw;

import java.applet.Applet;
import java.awt.TextArea;
import java.awt.Color;
import java.util.Hashtable;
//import java.util.Map;

/**
 * An example applet which connects to xmlBlaster using a persistent http tunnel for callbacks
 * and displays the asynchronous delivered <i>cpuinfo</i> and <i>meminfo</i> messages. 
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.java.applet.html">
 *       Applet requirement</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/demo/http/index.html">http://www.xmlblaster.org/xmlBlaster/demo/http/index.html</a>
 * @see org.xmlBlaster.util.qos.MsgQosData#toJXPath()
 * @see org.xmlBlaster.util.key.MsgKeyData#toJXPath()
 */
public class SystemInfoApplet extends Applet implements I_CallbackRaw
{
   I_XmlBlasterAccessRaw xmlBlasterAccess;
   TextArea textArea;
   boolean runAsApplet;

   public void init(){
      this.runAsApplet = true;
      print("Applet.init() called");
      try {
         setBackground(Color.white);
         setForeground(Color.black);
         this.textArea = new TextArea("", 12, 60);
         this.textArea.setBackground(Color.white);
         this.textArea.setForeground(Color.black);
         add(this.textArea);
         repaint();

         this.xmlBlasterAccess = new XmlBlasterAccessRaw(this);
         this.xmlBlasterAccess.connect(null, this);
         print("Connected to xmlBlaster");

         Hashtable subReturnQos = this.xmlBlasterAccess.subscribe("<key oid='cpuinfo'/>", "<qos/>");
         subReturnQos = this.xmlBlasterAccess.subscribe("<key oid='meminfo'/>", "<qos/>");
         print("Subscribed on 'cpuinfo' and 'meminfo' topics");

         showStatus("SystemInfoApplet: Connected to xmlBlaster");
      }
      catch (Exception e) {
         print("No connection to xmlBlaster: " + e.toString());
         e.printStackTrace();
         showStatus("SystemInfoApplet: No connection to xmlBlaster: " + e.toString());
      }
   }
 
   public void init(Hashtable properties){
      try {
         this.xmlBlasterAccess = new XmlBlasterAccessRaw(properties);
         this.xmlBlasterAccess.connect(null, this);

         Hashtable subReturnQos = this.xmlBlasterAccess.subscribe("<key oid='cpuinfo'/>", "<qos/>");
         subReturnQos = this.xmlBlasterAccess.subscribe("<key oid='meminfo'/>", "<qos/>");
      }
      catch (Exception e) {
         e.printStackTrace();
      }
   }
 
   private void print(String text) {
      String id = (this.xmlBlasterAccess==null) ? ": " : (" #"+this.xmlBlasterAccess.getInstanceId()+": ");
      if (this.runAsApplet && this.textArea!=null)
         this.textArea.append("SystemInfoApplet" + id + text + "\n");
      else    
         System.out.println("SystemInfo" + id + text + "\n");
   }

   public void destroy(){
      print("Applet destroy ...");
      if (this.xmlBlasterAccess != null) {
         try {
            this.xmlBlasterAccess.unSubscribe("<key oid='cpuinfo'/>", "<qos/>");
            this.xmlBlasterAccess.unSubscribe("<key oid='meminfo'/>", "<qos/>");
         }
         catch (Exception e) {
            print("UnSubscribe problem: " + e.toString());
         }
         this.xmlBlasterAccess.disconnect("<qos/>");
         this.xmlBlasterAccess = null;
         print("Disconnected from xmlBlaster");
      }
   }

   /**
    * Here you receive the callback messages from xmlBlaster. 
    */
   public String update(String cbSessionId, Hashtable updateKey, byte[] content, Hashtable updateQos) throws Exception {
      print("Asynchronous Update: key=" + updateKey.get("/key/@oid") + "=" + new String(content));
      //repaint();
      return "<qos/>";
   }
   
   /**
    * Start outside of browser: java http.applet.SystemInfoApplet
    */
   public static void main(String[] args) {
      SystemInfoApplet appl = new SystemInfoApplet();
      Hashtable properties = new Hashtable();
      if (args.length < 1) 
         properties.put("xmlBlaster/servletUrl", "http://localhost:8080/xmlBlaster/AppletServlet");
      else properties.put("xmlBlaster/servletUrl", args[0]);
      //properties.put("xmlBlaster/loginName", "tester");
      //properties.put("xmlBlaster/passwd", "secret");
      properties.put("xmlBlaster/logLevels", "ERROR,WARN,INFO");
      appl.init(properties);
   }
   
}

