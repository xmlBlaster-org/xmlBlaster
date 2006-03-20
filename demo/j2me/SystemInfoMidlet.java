import java.util.Hashtable;

import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

import org.xmlBlaster.client.protocol.http.common.I_CallbackRaw;
import org.xmlBlaster.client.protocol.http.j2me.XmlBlasterAccessJ2ME;

public class SystemInfoMidlet extends MIDlet implements CommandListener, I_CallbackRaw, Runnable {
   // the display manager
   Display display;

   // ticker
   Ticker ticker = new Ticker("XmlBlaster: SystemInfo Demo");

   Form form, mainForm;
   StringItem[] lines;
   int count = 0;
   TextField addr;
   
   // command
   static final Command exitCommand = new Command("Exit", Command.STOP, 2);
   static final Command backCommand = new Command("Back", Command.STOP, 2);
   static final Command goCommand = new Command("Go", Command.SCREEN, 2);
   
   private XmlBlasterAccessJ2ME xmlBlasterAccess;
   private Hashtable properties;
   
   // constructor.
   public SystemInfoMidlet() {
      this.properties = new Hashtable();
      String servletUrl = this.getAppProperty("servletUrl");
      if (servletUrl == null) servletUrl = "http://localhost:8080/xmlBlaster/AppletServlet";
      this.properties.put("xmlBlaster/servletUrl", servletUrl);
      this.properties.put("xmlBlaster/logLevels", "ERROR,WARN,INFO");
   }

   /**
    * Start the MIDlet by creating a list of
    * items and associating the
    * exit command with it.
    */
   public void startApp() throws MIDletStateChangeException {
      display = Display.getDisplay(this);
      //startXmlBlaster();
      mainMenu();
   }

   private void mainMenu() {
      this.mainForm = new Form("Entry Point");
      String url = (String)this.properties.get("xmlBlaster/servletUrl");
      this.addr = new TextField("url", url, 100, 0);
      this.mainForm.append(addr);      
      this.mainForm.addCommand(exitCommand);
      this.mainForm.addCommand(goCommand);
      this.mainForm.setCommandListener(this);
      this.display.setCurrent(this.mainForm);
   }

   public void run() {
      startXmlBlaster();
   }

   private void startXmlBlaster() {
      this.form = new Form("SystemInfo");
      this.lines = new StringItem[4];
      this.lines[0] = new StringItem("key       :", "   ");
      this.form.append(lines[0]);
      this.lines[1] = new StringItem("value     :", "   ");
      this.form.append(lines[1]);
      this.lines[2] = new StringItem("update nr.:", "   ");
      this.form.append(lines[2]);
      this.lines[3] = new StringItem("comments  :", "   ");
      this.form.append(lines[3]);

      this.form.addCommand(backCommand);
      this.form.setCommandListener(this);
      this.form.setTicker(ticker);
      display.setCurrent(this.form);
      init(this.properties);
   }

   private void stopXmlBlaster() {
      if (this.xmlBlasterAccess != null) {
         print("Applet destroy ...");
         try {
            this.xmlBlasterAccess.unSubscribe("<key oid='cpuinfo'/>", "<qos/>");
            this.xmlBlasterAccess.unSubscribe("<key oid='meminfo'/>", "<qos/>");
         }
         catch (Exception e) {
            print("UnSubscribe problem");
         }
         this.xmlBlasterAccess.disconnect("<qos/>");
         this.xmlBlasterAccess = null;
         print("Disconnected");
      }
   }


   public void pauseApp() {
      display = null;
      ticker = null;
      lines = null;
      this.addr = null;
   }

   public void destroyApp(boolean unconditional) {
      stopXmlBlaster();
      notifyDestroyed();
   }

   /**
    * Handle events.
    */  
   public void commandAction(Command c, Displayable d) {
      String label = c.getLabel();
      if (label.equals("Exit")) {
         destroyApp(true);
      }
      else if(label.equals("Go")) {
         String val = this.addr.getString();
         this.properties.put("xmlBlaster/servletUrl", val);
         //startXmlBlaster();
         // to be able to use the commands
         Thread thread = new Thread(this);
         thread.start();
      }
      else if(label.equals("Back")) {
         stopXmlBlaster();
         mainMenu();
      }
   }
  
   public void init(Hashtable properties){
      try {
         this.xmlBlasterAccess = new XmlBlasterAccessJ2ME(properties);
         this.xmlBlasterAccess.connect(null, this);

         Hashtable subReturnQos = this.xmlBlasterAccess.subscribe("<key oid='cpuinfo'/>", "<qos/>");
         subReturnQos = this.xmlBlasterAccess.subscribe("<key oid='meminfo'/>", "<qos/>");
      }
      catch (Exception e) {
         e.printStackTrace();
      }
   }

   private void print(String text) {
      this.lines[3].setText(text);
   }

   /**
    * Here you receive the callback messages from xmlBlaster. 
    */
   public String update(String cbSessionId, Hashtable updateKey, byte[] content, Hashtable updateQos) throws Exception {
      this.count++;
      this.lines[0].setText((String)updateKey.get("/key/@oid"));
      this.lines[1].setText(new String(content));
      this.lines[2].setText(String.valueOf(this.count));
      return "<qos/>";
   }
}
