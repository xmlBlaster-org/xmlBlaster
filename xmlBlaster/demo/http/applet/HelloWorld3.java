package http.applet;

import org.xmlBlaster.client.protocol.http.applet.I_CallbackRaw;
import org.xmlBlaster.client.protocol.http.applet.I_XmlBlasterAccessRaw;
import org.xmlBlaster.client.protocol.http.applet.XmlBlasterAccessRaw;
import org.xmlBlaster.client.protocol.http.applet.Msg;
import java.applet.Applet;
import java.awt.TextArea;
import java.awt.Color;
import java.util.Hashtable;

/**
 * An example applet which connects to xmlBlaster using a persistent
 * http tunnel for callbacks. 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.java.applet.html">
 *       Applet requirement</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/demo/http/index.html">
 *       http://www.xmlblaster.org/xmlBlaster/demo/http/index.html</a>
 * @see org.xmlBlaster.util.qos.MsgQosData#toJXPath()
 * @see org.xmlBlaster.util.key.MsgKeyData#toJXPath()
 */
public class HelloWorld3 extends Applet implements I_CallbackRaw
{
   I_XmlBlasterAccessRaw xb;
   TextArea textArea;

   public void init(){
      System.out.println("HelloWorld3: Applet.init() called");
      try {
         setBackground(Color.white);
         setForeground(Color.black);
         this.textArea = new TextArea("", 12, 60);
         this.textArea.setBackground(Color.white);
         this.textArea.setForeground(Color.black);
         add(this.textArea);
         repaint();

         this.xb = new XmlBlasterAccessRaw(this);
         this.xb.connect(null, this);
         print("Connected to xmlBlaster");

         Hashtable subReturnQos = this.xb.subscribe("<key oid='HELLO'/>", "<qos/>");
         print("Subscribed, id=" + subReturnQos.get("/qos/subscribe/@id"));

         Hashtable pubReturnQos = this.xb.publish("<key oid='HELLO'/>",
                                 "Hello World".getBytes(), "<qos/>");
         print("Published 'HELLO', returned status is " +
                     pubReturnQos.get("/qos/state/@id"));

         Hashtable[] unSubReturnQos = this.xb.unSubscribe("<key oid='" +
                   subReturnQos.get("/qos/subscribe/@id")+"'/>", "<qos/>");
         print("UnSubscribed " + unSubReturnQos.length + " topics");

         Msg[] msgs = this.xb.get("<key oid='HELLO'/>", "<qos/>");
         for (int i=0; i<msgs.length; i++) {
            print("Get returned key=" + msgs[i].getKey().get("/key/@oid") +
                                    " content=" + msgs[i].getContentStr());
         }

         Hashtable[] eraseReturnQos=this.xb.erase("<key oid='HELLO'/>","<qos/>");
         print("Erase " + eraseReturnQos.length + " topics");
      }
      catch (Exception e) {
         print("No connection to xmlBlaster: " + e.toString());
         e.printStackTrace();
         showStatus("HelloWorld3: No connection to xmlBlaster");
      }
   }
 
   private void print(String text) {
      this.textArea.append("HelloWorld3: " + text + "\n");
   }

   public void destroy(){
      print("Applet destroy ...");
      if (this.xb != null) {
         this.xb.disconnect("<qos/>");
         this.xb = null;
         print("Disconnected from xmlBlaster");
      }
   }

   /**
    * Here you receive the callback messages from xmlBlaster. 
    */
   public String update(String cbSessionId, Hashtable updateKey, byte[] content,
                        Hashtable updateQos) throws Exception {
      print("---- START update received -----");
      print("key=" + updateKey.get("/key/@oid") + " state=" +
            updateQos.get("/qos/state/@id"));
      print("update received: content=" + new String(content));
      print("---- END   update received -----");
      //repaint();
      return "<qos/>";
   }
}

