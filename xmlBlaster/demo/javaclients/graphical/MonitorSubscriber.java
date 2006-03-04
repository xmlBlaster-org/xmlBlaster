package javaclients.graphical;

import java.util.ArrayList;
import java.util.HashMap;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;

import CH.ifa.draw.framework.Drawing;
import CH.ifa.draw.framework.DrawingView;
import CH.ifa.draw.framework.Figure;
import CH.ifa.draw.framework.FigureEnumeration;


/**
 * This client connects to xmlBlaster and subscribes to a message.
 * <p />
 * We then publish the message and receive it asynchronous in the update() method.
 * <p />
 * Invoke: java MonitorSubscriber
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.html" target="others">xmlBlaster interface</a>
 */
public class MonitorSubscriber implements I_Callback {
   
   private final static String ME = "MonitorSubscriber";
   private Global  global;
   private static Logger log = Logger.getLogger(MonitorSubscriber.class.getName());
   private boolean isRunning;
   
   private HashMap cmdInstances;
   private HashMap cmdTemplates;
   /** 
    * instances created from the templated. Needs to be different from cmdTemplates since 
    * as they are created on the fly they also need to be removed once the topic dies.
    */
   private HashMap dynamicInstances;
   private DrawingView view;
   
   public MonitorSubscriber(Global glob, DrawingView view) {
      global = glob.getClone(null);
      this.isRunning = false;

      this.cmdInstances = new HashMap();
      this.cmdTemplates = new HashMap();
      this.dynamicInstances = new HashMap(); 
      this.view = view;
   }
   
   private boolean isCommand(String txt) {
      return txt.startsWith("&");
   }
   
   /**
    * Gets the first connected (associated command text to the figure) found if any.
    * @param fig
    * @return null if none is found or the command text if one is found.
    */
   private String[] getAssociatedTexts(Figure fig) {
      if (fig == null) return null;
      ArrayList list = new ArrayList();
      FigureEnumeration enumer = fig.figures();
      while (enumer.hasNextFigure()) {
         Figure fig1 = enumer.nextFigure();
         if (fig1.getTextHolder() != null) {
            String txt = fig1.getTextHolder().getText();
            if (isCommand(txt)) list.add(txt);
         }
      }
      if (list.size() < 1) return null;
      return (String[])list.toArray(new String[list.size()]);
   }


   private boolean prepareClient(Drawing drawing) {
      FigureEnumeration enumer = drawing.figures();
      boolean ret = false;
      while (enumer.hasNextFigure()) {
         Figure fig = enumer.nextFigure();
         String[] txt = getAssociatedTexts(fig);
         if (txt != null) {
            for (int i=0; i < txt.length; i++) {
               try {
                  MonitorCommand cmd = new MonitorCommand(this.global, txt[i], fig);
                  if (cmd.isInstance()) {
                     this.cmdInstances.put(cmd.getOid(), cmd);
                  }
                  else if (cmd.isTemplate()) {
                     this.cmdTemplates.put(cmd.getType(), cmd);
                  }
                  ret = true;
               }   
               catch (Exception ex) {
                  log.warning(ex.getMessage());
               }
            }
         }
      }
      return ret;
   }


   synchronized public boolean start(String name) {
      if (this.isRunning) return true;
      try {
         if (!prepareClient(this.view.drawing())) return this.isRunning;

         I_XmlBlasterAccess con = global.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(this.global, /*drawing.getTitle()*/ name, "secret");
         con.connect(qos, this);  // Login to xmlBlaster, register for updates

         this.isRunning = true;
      
         if (this.cmdInstances.size() > 0) {
            MonitorCommand[] oidCommands = (MonitorCommand[])this.cmdInstances.values().toArray(new MonitorCommand[this.cmdInstances.size()]);
            for (int i=0; i < oidCommands.length; i++)
               con.subscribe("<key oid='" + oidCommands[i].getOid() + "'/>", "<qos/>");
         }
         if (this.cmdTemplates.size() > 0) {
            MonitorCommand[] xpathCommands = (MonitorCommand[])this.cmdTemplates.values().toArray(new MonitorCommand[this.cmdTemplates.size()]);
            for (int i=0; i < xpathCommands.length; i++)
               con.subscribe("<key oid='' queryType='XPATH'>//" + xpathCommands[i].getType() + "</key>", "<qos/>");
         }
      }
      catch (Exception e) {
         System.err.println(e.getMessage());
      }
      return this.isRunning;
   }


   public void stop() {
      I_XmlBlasterAccess con = global.getXmlBlasterAccess();
      con.disconnect(null);
   }

   private MonitorCommand searchInTemplates(String oid) {
      String[] keys = (String[])this.cmdTemplates.keySet().toArray(new String[this.cmdTemplates.size()]);
      for (int i=0; i < keys.length; i++) {
         if (oid.startsWith(keys[i]))
            return ((MonitorCommand)this.cmdTemplates.get(keys[i])).createInstance(oid);
      }
      return null;      
   }


   synchronized public String update(String cbSessionId, UpdateKey updateKey, byte[] content,
                        UpdateQos updateQos) {
      System.out.println("\nHelloWorld: Received asynchronous message '" +
         updateKey.getOid() + "' state=" + updateQos.getState() + " from xmlBlaster");
         
      String oid = updateKey.getOid();
      if (updateQos.isOk()) {
         MonitorCommand command = (MonitorCommand)this.cmdInstances.get(oid);
         if (command == null) {
            command = (MonitorCommand)this.dynamicInstances.get(oid);
         }
         if (command == null) {
            command = searchInTemplates(oid);
            if (command != null) {
               this.dynamicInstances.put(oid, command);
               this.view.drawing().add(command.getFigure());
            }
         }
         if (command != null) {
            command.doAction(content, this.view);
         }
      }
      else { // then it is erased ...
         MonitorCommand command = (MonitorCommand)this.dynamicInstances.remove(oid);
         if (command != null) {
            command.remove(this.view);
         }
      }
      return "";
   }

}
