package http.applet;

import org.xmlBlaster.client.protocol.http.applet.XmlBlasterAccessRaw;
import org.xmlBlaster.client.protocol.http.common.I_CallbackRaw;
import org.xmlBlaster.client.protocol.http.common.I_XmlBlasterAccessRaw;

import java.applet.Applet;
import java.awt.TextArea;
import java.awt.Color;
import java.util.Hashtable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.TextField;
import java.awt.Button;

/**
 * An example applet which connects to xmlBlaster using xml scripting and a persistent http tunnel for callbacks. 
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.script.html">client.script requirement</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.java.applet.html">
 *       Applet requirement</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/demo/http/index.html">http://www.xmlblaster.org/xmlBlaster/demo/http/index.html</a>
 */
public class XmlScript extends Applet implements I_CallbackRaw, ActionListener
{
   I_XmlBlasterAccessRaw xmlBlasterAccess;
   TextArea textArea, requestArea;
   Button connectButton, sendButton;
   boolean runAsApplet;

   public void init(){
      this.runAsApplet = true;
      System.out.println("XmlScript: Applet.init() called");
      try {
         initUI();
      }
      catch (Exception e) {
         showStatus("XmlScript: Problem in init(): " + e.toString());
      }
   }

   private void initUI() throws Exception {
      setBackground(Color.white);
      setForeground(Color.black);
      this.connectButton = new Button("Connect");
      this.connectButton.setActionCommand("connect");
      this.connectButton.addActionListener(this);
      add(this.connectButton);
      this.sendButton = new Button("Send");
      this.sendButton.setActionCommand("send");
      this.sendButton.addActionListener(this);
      add(this.sendButton);
      this.requestArea = new TextArea("", 9, 62);
      this.requestArea.setBackground(Color.white);
      this.requestArea.setForeground(Color.black);
      // add an example request
      this.requestArea.setText("<xmlBlaster>\n" +
                               "  <get>\n" +
                               "    <key queryType='XPATH'>\n" +
                               "      /xmlBlaster/key\n" +
                               "    </key>\n" +
                               "  </get>\n" +
                               "</xmlBlaster>\n" +
                               "<!-- See http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.script.html -->");
      add(this.requestArea);
      this.textArea = new TextArea("", 24, 80);
      this.textArea.setBackground(Color.white);
      this.textArea.setForeground(Color.black);
      add(this.textArea);
      repaint();
   }
 
   /** Event-handler called on button click */
   public void actionPerformed(ActionEvent ev) {
      String command = ev.getActionCommand();
      try {
         if(command.equals("connect")){
            // Connect to xmlBlaster server
            if((this.connectButton.getLabel()).equals("Connect")){
               this.xmlBlasterAccess = new XmlBlasterAccessRaw(this);
               this.xmlBlasterAccess.connect(null, this);
               print("Connected to xmlBlaster");
               showStatus("XmlScript: Connected to xmlBlaster, please send a request.");
               this.connectButton.setLabel("Logout");
            }
            //logout from server
            else if((this.connectButton.getLabel()).equals("Logout")){
               this.xmlBlasterAccess.disconnect(null);
               this.xmlBlasterAccess = null;
               showStatus("XmlScript: Not connected to xmlBlaster.");
               this.connectButton.setLabel("Connect");
            }
         }
         else if( command.equals("send") || ( (ev.getSource()) instanceof TextField )){
            if (this.xmlBlasterAccess == null || !this.xmlBlasterAccess.isConnected()) {
               showStatus("XmlScript: Please log in first.");
               return;
            }
            String xmlRequest = this.requestArea.getText();
            String response = this.xmlBlasterAccess.sendXmlScript(xmlRequest);
            this.textArea.setText(this.textArea.getText()+response);
            showStatus("XmlScript: Request done.");
         }
      }
      catch (Exception e) {
         print(e.toString());
         e.printStackTrace();
         showStatus("XmlScript: " + e.toString());
      }
   }

   /** For testing without applet GUI */
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
      if (this.runAsApplet)
         this.textArea.append("XmlScript: " + text + "\n");
      else    
         System.out.println("SystemInfo: " + text + "\n");
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
      print("Asynchronous update arrived: key=" + updateKey.get("/key/@oid") + ", status=" + updateQos.get("/qos/state/@id") + ":");
      print("------------------------------------------------");
      print(new String(content));
      print("------------------------------------------------");
      //repaint();
      return "<qos/>";
   }
   
   /** java http.applet.XmlScript */ 
   public static void main(String[] args) {
      XmlScript appl = new XmlScript();
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

