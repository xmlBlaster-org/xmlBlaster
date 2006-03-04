/*------------------------------------------------------------------------------
Name:      GraphicChat.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package javaclients.graphical;

import javax.swing.JToolBar;

import CH.ifa.draw.figures.*;
import CH.ifa.draw.contrib.*;

import CH.ifa.draw.framework.Drawing;
import CH.ifa.draw.framework.DrawingView;
import CH.ifa.draw.framework.Tool;
import CH.ifa.draw.util.UndoableTool;
import CH.ifa.draw.standard.CreationTool;
import CH.ifa.draw.contrib.html.HTMLTextAreaFigure;
import CH.ifa.draw.contrib.html.HTMLTextAreaTool;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;

/**
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class GraphicChat extends MDI_DrawApplication implements I_Callback {

   private Global global;
   private static Logger log = Logger.getLogger(GraphicChat.class.getName());
   private String ME = "GraphicChat";
   private I_XmlBlasterAccess accessor;

   public GraphicChat(Global global) {
      super("GraphicChat");
      this.global = global;

   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      log.info("update for '" + cbSessionId + "', '" + updateKey.getOid() + "' length of msg is '" + content.length + "'");
      return "OK";
   }

   protected void init() {
      this.accessor = this.global.getXmlBlasterAccess();
   }


   protected Drawing createDrawing() {
      XmlBlasterDrawing drawing = new XmlBlasterDrawing(this.global);
      // drawing.init(this.global);
      return drawing;
   }

   protected void createTools(JToolBar palette) {
      super.createTools(palette);
      Tool tool = new UndoableTool(new TextTool(this, new TextFigure()));
      palette.add(createToolButton(IMAGES + "TEXT", "Text Tool", tool));

      tool = new UndoableTool(new CreationTool(this, new RectangleFigure()));
      palette.add(createToolButton(IMAGES + "RECT", "Rectangle Tool", tool));

      tool = new UndoableTool(new CreationTool(this, new RoundRectangleFigure()));
      palette.add(createToolButton(IMAGES + "RRECT", "Round Rectangle Tool", tool));

      tool = new UndoableTool(new CreationTool(this, new EllipseFigure()));
      palette.add(createToolButton(IMAGES + "ELLIPSE", "Ellipse Tool", tool));

      tool = new UndoableTool(new PolygonTool(this));
      palette.add(createToolButton(IMAGES + "POLYGON", "Polygon Tool", tool));

      tool = new UndoableTool(new CreationTool(this, new TriangleFigure()));
      palette.add(createToolButton(IMAGES + "TRIANGLE", "Triangle Tool", tool));

      tool = new UndoableTool(new CreationTool(this, new DiamondFigure()));
      palette.add(createToolButton(IMAGES + "DIAMOND", "Diamond Tool", tool));

      tool = new UndoableTool(new CreationTool(this, new LineFigure()));
      palette.add(createToolButton(IMAGES + "LINE", "Line Tool", tool));

      tool = new TextAreaTool(this, new TextAreaFigure());
      palette.add(createToolButton(IMAGES + "TEXTAREA", "TextArea Tool", tool));

      tool = new HTMLTextAreaTool(this, new HTMLTextAreaFigure());
      palette.add(createToolButton(IMAGES + "TEXTAREA", "HTML TextArea Tool", tool));
   }

   /**
    * invoked on exit
    */
   protected void destroy() {
      log.info("destroy invoked");
      DrawingView[] views = this.views();
      for (int i=0; i < views.length; i++) {
         views[i].drawing().release();
      }
      super.destroy();
   }

   protected void fireViewDestroyingEvent(DrawingView view) {
      log.info("Destroying view '" + view.drawing().getTitle() + "'");
      Drawing drawing = view.drawing();
      super.fireViewDestroyingEvent(view);
      drawing.release();
   }


   /**
    * Factory method to create a StorageFormatManager for supported storage formats.
    * Different applications might want to use different storage formats and can return
    * their own format manager by overriding this method.
    * 
    * TODO: Read storage formats from a config file.
    */
/*
   public StorageFormatManager createStorageFormatManager() {
      StorageFormatManager storageFormatManager = new StorageFormatManager();
      SVGStorageFormat format = new SVGStorageFormat();
      storageFormatManager.addStorageFormat(format);
      storageFormatManager.setDefaultStorageFormat(format);
      return storageFormatManager;
   }
*/

   


   //-- main -----------------------------------------------------------

   public static void main(String[] args) {

      Global glob = new Global();
      if (glob.init(args) != 0) { // Get help with -help
         System.out.println(glob.usage());
         System.err.println("Example: java javaclients.graphical.GraphicChat -chatMaster true\n");
         System.exit(1);
      }

      GraphicChat window = new GraphicChat(glob);
      window.open();
   }

}

