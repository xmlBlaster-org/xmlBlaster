/*------------------------------------------------------------------------------
Name:      Interactor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   The class which takes care of the user events (mouse events)
Version:   $Id$
------------------------------------------------------------------------------*/
package javaclients.svg.batik;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

import org.apache.batik.swing.JSVGCanvas;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.swing.gvt.InteractorAdapter;

import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.Point;

import java.util.List;


/**
 * We need to create an extention of JSVGCanvas in order to be able to
 * retrieve the protected member bridgeContext. Lets call this class
 * com.eclettic.svg.JSVGCanvasExtended
 * @author $Author$ (laghi@swissinfo.org)
 */
public class Interactor extends InteractorAdapter
{
   /**
    * The JSVGCanvas on which to work on.
    */
   private final String ME                   = "Interactor";
   private final LogChannel log;
   private JSVGCanvas   canvas               = null;
   private GraphicsNode graphicsNode         = null;
   // the mouse position where the last mousePressed event occured
   private Point        lastMousePosition    = null;
   // the graphicsNode on which the mouse hit when mousePressed occured
   private GraphicsNode selectedGraphicsNode = null;
   private BridgeContext bridgeContext       = null;
   private Transceiver   transceiver         = null;

   public Interactor ()
   {
      super();
      log = Global.instance().getLog("batik");
      log.trace(ME,"constructor called");
   }


   public void setTransceiver (Transceiver transceiver)
   {
      this.transceiver = transceiver;
   }


   public void setBridgeContext (BridgeContext bridgeContext)
   {
      this.bridgeContext = bridgeContext;
   }


   public BridgeContext getBridgeContext ()
   {
      return this.bridgeContext;
   }


   /**
    * setter method for the canvas. At the same time it adds itself to the
    * interactor list of the related canvas object (if it has not been set
    * previously). When doing this, it also creates (and retrieves) a new
    * BridgeContext for that canvas. This means that you should call this
    * method BEFORE you load any document
    */
   public void setCanvas (JSVGCanvas canvas)
   {
      log.trace(ME,".setCanvas start");
      boolean setInteractorList = false;
      System.out.println(ME + ".setCanvas " + canvas + " start");
      // if the same canvas is set for the second time, the interactor list
      // is unchanged.
      if ((canvas != this.canvas) && (canvas != null)) {
         log.info(ME,".setCanvas interactor list set");
         List interactorList = canvas.getInteractors();
         interactorList.add(this);
      }
      this.canvas   = canvas;
      log.trace(ME,".setCanvas end");
   }


   /**
    * The reason why this method is not called togheter when setting the canvas
    * is that the graphics node is different for each new DOM loaded, while the
    * canvas is always the same even if a second DOM has been loaded. If you
    * invoke this method before a DOM has been completely loaded, it is set to
    * null.
    */
   public void setGraphicsNode () throws XmlBlasterException
   {
      log.trace(ME,".setGraphicsNode start");
      if (this.canvas == null) {
         log.error(ME,".setGraphicsNode canvas is null");
         throw new XmlBlasterException(ME, ".setGraphicsNode canvas is null");
      }
      this.graphicsNode = this.canvas.getGraphicsNode();
      if (this.graphicsNode == null)
         log.warn(ME, ".setGraphicsNode: the graphics node is null");
      log.trace(ME,".setGraphicsNode end");
   }


   public boolean startInteraction (InputEvent ie)
   {
      log.trace(ME,".startInteraction called");
      // don't really know what to return here ...
      return true;
   }


   public JSVGCanvas getCanvas ()
   {
      return canvas;
   }


   public void mousePressed (MouseEvent evt)
   {
      log.trace(ME,".mousePressed");
      // this.graphicsNode.getGlobalTransform().deltaTransform(p0, p1);
      this.lastMousePosition = evt.getPoint();
      Rectangle2D rect = graphicsNode.getBounds();
//      double width  = rect.getX() * point.getX() / canvas.getWidth();
//      double height = rect.getY() * point.getY() / canvas.getHeight();
      Point2D point2D = new Point2D.Double();
//      point2D.setLocation(width, height);
      point2D.setLocation(this.lastMousePosition.getX(),
                          this.lastMousePosition.getY());
      this.selectedGraphicsNode = this.graphicsNode.nodeHitAt(point2D);
      if (this.selectedGraphicsNode == null) {
         log.warn(ME,".mousePressed hit a null object");
      }
      else {
         log.info(ME,".mousePressed hit " + this.selectedGraphicsNode.toString());
      }
   }


   public void mouseDragged (MouseEvent evt)
   {
//      System.out.println(ME + ".mouseDragged");
   }


   public void mouseEntered (MouseEvent evt)
   {
//      System.out.println(ME + ".mouseEntered");
   }


   public void mouseExited (MouseEvent evt)
   {
//      System.out.println(ME + ".mouseExited");
   }


   public void mouseMoved (MouseEvent evt)
   {
//      System.out.println(ME + ".mouseMoved");
   }


   public void mouseClicked (MouseEvent evt)
   {
//      System.out.println(ME + ".mouseClicked");
   }


   public void mouseReleased (MouseEvent evt)
   {
      log.info(ME,".mouseReleased");

      // here you can perform the tasks which are specific to this implementation
      /* In this case translate the current object */

      // calculate the translation coordinates ..
      if (this.lastMousePosition == null) {
         log.error(ME, "the last mouse position was null");
      }
      else {
         if (this.selectedGraphicsNode != null) {
            Point p = new Point();
            p.x = evt.getX() - this.lastMousePosition.x;
            p.y = evt.getY() - this.lastMousePosition.y;
            this.transceiver.move(this.selectedGraphicsNode, p);
         }
         else log.warn(ME, ".mouseReleased: selectedGraphicsNode was null");
      }
      // reset the members
      this.selectedGraphicsNode = null;
      this.lastMousePosition    = null;
   }

}
