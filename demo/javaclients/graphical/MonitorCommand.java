/*
 * Created on Sep 3, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package javaclients.graphical;

import java.awt.Rectangle;
import java.util.StringTokenizer;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

import CH.ifa.draw.framework.Figure;
import CH.ifa.draw.framework.Drawing;
import CH.ifa.draw.framework.DrawingView;
import CH.ifa.draw.framework.DrawingChangeEvent;
import CH.ifa.draw.framework.FigureChangeEvent;

/**
 * MonitorCommand
 *
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * 
 */
public class MonitorCommand {

   public final static int NONE     = 0;
   public final static int LOCATION = 1;
   public final static int SIZE     = 2;
   public final static int COLOR    = 4;
   public final static int TEXT     = 8;
   public final static String ME = "MonitorCommand";
   
   private Global global;
   private static Logger log = Logger.getLogger(MonitorCommand.class.getName());
   private String oid;
   private String type;
   private int    action = NONE;
   private Figure figure;
   
   private MonitorCommand(Global global, String oid, int action, Figure figure) {
      this.global = global;

      this.oid    = oid;
      this.action = action;
      this.figure = figure;
   }
   
   public MonitorCommand(Global global, String txt, Figure figure) throws XmlBlasterException {
      this.global = global;

      this.figure = figure;
      StringTokenizer tokenizer = new StringTokenizer(txt.trim(), ";");
      if (tokenizer.countTokens() != 3)
         throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, "The text '" + txt + "' is not recognized as a valid command");
      String type   = tokenizer.nextToken().trim();
      if ("&instance".equalsIgnoreCase(type)) {
         this.oid = tokenizer.nextToken().trim();
      }
      else if ("&template".equalsIgnoreCase(type)) {
         this.type = tokenizer.nextToken().trim();
      }
      else    
         throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, "The text '" + txt + "' must either start with '&instance' or '&template'");

      String tmp = tokenizer.nextToken().trim();
      if      ("location".equalsIgnoreCase(tmp)) this.action = LOCATION;
      else if ("size".equalsIgnoreCase(tmp)    ) this.action = SIZE;
      else if ("color".equalsIgnoreCase(tmp)   ) this.action = COLOR;
      else if ("text".equalsIgnoreCase(tmp)    ) this.action = TEXT;
      else throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, "The action (here) '" + tmp + "' must either be 'location', 'size', 'color' or 'text'");
   }
   
   public boolean isInstance() {
      return (this.oid != null);
   }

   public boolean isTemplate() {
      return (this.type != null);
   }

   public String getOid() {
      return this.oid;
   }

   public String getType() {
      return this.type;
   }

   public int getAction() {
      return this.action;
   }

   public Figure getFigure() {
      return this.figure;
   }

   public void doAction(byte[] content, DrawingView view) {
      String data = new String(content);
      // this.figure.willChange();

      Rectangle oldRect = this.figure.displayBox();
      try {
         if (this.action == LOCATION) {
            StringTokenizer tokenizer = new StringTokenizer(data.trim(), ";");
            int x = Integer.parseInt(tokenizer.nextToken().trim());
            int y = Integer.parseInt(tokenizer.nextToken().trim());
            this.figure.moveBy(x-oldRect.x, y-oldRect.y);
            
            if (log.isLoggable(Level.FINE)) this.log.fine("new position: " + x  + " " + y);
         }
      }  
      catch (Exception ex) {
         ex.printStackTrace();         
      }

      // this.figure.changed();
      Rectangle newRect = this.figure.displayBox();
      Drawing drawing = view.drawing();
      FigureChangeEvent ev0 = new FigureChangeEvent(this.figure);
      drawing.figureChanged(ev0);

      DrawingChangeEvent ev = new DrawingChangeEvent(drawing, oldRect);
      view.drawingRequestUpdate(ev);
      ev = new DrawingChangeEvent(drawing, newRect);
      view.drawingRequestUpdate(ev);
      // view.drawingInvalidated(ev);
      // view.repairDamage();
   }

   /**
    * Creates an instance from this template or returns null if this is
    * not a template
    * @param oid the oid to give to the new instance
    * @return
    */
   public MonitorCommand createInstance(String oid) {
      if (!this.isTemplate()) return null;
      Figure fig = (Figure)this.figure.clone();
      return new MonitorCommand(this.global, oid, this.action, fig);
   }

   public void remove(DrawingView view) {
      Drawing drawing = view.drawing();
      drawing.remove(this.figure);
      drawing.orphan(this.figure);
      FigureChangeEvent ev = new FigureChangeEvent(this.figure);
      drawing.figureRequestUpdate(ev);
   }


   public static void main(String[] args) {
      String txt = "&instance;plane.110;LOCATION";

      try {
         MonitorCommand command = new MonitorCommand(new Global(), txt, null);
         System.out.println("is instance: " + command.isInstance());
         System.out.println("is template: " + command.isTemplate());

         if (command.isInstance()) 
            System.out.println("oid: " + command.getOid());
         if (command.isTemplate()) 
            System.out.println("type: " + command.getType());

         System.out.println("action: " + command.getAction());
         
      }
      
      catch (Exception ex) {
         System.err.println(ex.getMessage());         
      }
      
   }
}
