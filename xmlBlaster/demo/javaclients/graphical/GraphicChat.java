/*------------------------------------------------------------------------------
Name:      GraphicChat.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package javaclients.graphical;

import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JToolBar;
import CH.ifa.draw.framework.*;
import CH.ifa.draw.standard.*;
import CH.ifa.draw.figures.*;
// import CH.ifa.draw.application.DrawApplication;
import CH.ifa.draw.samples.net.*;
import CH.ifa.draw.samples.javadraw.*;

import org.jutils.log.LogChannel;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;

/**
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class GraphicChat extends /*NetApp*/ JavaDrawApp implements I_Callback {

   private Global global;
   private LogChannel log;
   private String ME = "GraphicChat";
   private I_XmlBlasterAccess accessor;

   public GraphicChat(Global global) {
      super("GraphicChat");
      this.global = global;
      this.log = this.global.getLog("main");
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      this.log.info(ME, "update for '" + cbSessionId + "', '" + updateKey.getOid() + "' length of msg is '" + content.length + "'");
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

      Tool tool = new TextTool(this, new NodeFigure());
      palette.add(createToolButton(IMAGES + "TEXT", "Text Tool", tool));

      tool = new CreationTool(this, new NodeFigure());
      palette.add(createToolButton(IMAGES + "RECT", "Create Org Unit", tool));

      tool = new ConnectionTool(this, new LineConnection());
      palette.add(createToolButton(IMAGES + "CONN", "Connection Tool", tool));
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

