/*------------------------------------------------------------------------------
Name:      XmlUtility.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a svg client using batik (the demo appl. itself)
Version:   $Id$
------------------------------------------------------------------------------*/
package javaclients.svg.batik;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;

// import org.apache.batik.swing.JSVGCanvas;
import org.apache.batik.swing.gvt.GVTTreeRendererAdapter;
import org.apache.batik.swing.gvt.GVTTreeRendererEvent;
import org.apache.batik.swing.svg.SVGDocumentLoaderAdapter;
import org.apache.batik.swing.svg.SVGDocumentLoaderEvent;
import org.apache.batik.swing.svg.GVTTreeBuilderAdapter;
import org.apache.batik.swing.svg.GVTTreeBuilderEvent;

import java.util.logging.Logger;
import org.xmlBlaster.util.Global;


/**
 *
 * @author $Author$ (laghi@swissinfo.org)
 */
public class ExtendedApplication
{

   private static final String ME = "ExtendedApplication";
   private final Global glob;
   private static Logger log = Logger.getLogger(ExtendedApplication.class.getName());

   protected class SimpleLoaderAdapter extends SVGDocumentLoaderAdapter
   {
   // remember to invoke this in the parent class ...

      public SimpleLoaderAdapter (Global glob, Interactor specialInteractor)
      {
         super();
         log.fine(" constructor");
      }

      public void documentLoadingStarted(SVGDocumentLoaderEvent e) {
         log.fine(".documentLoadingStarted");
      }

      public void documentLoadingCompleted(SVGDocumentLoaderEvent e) {
         log.info(".documentLoadingCompleted");
      }

   }



   protected class SimpleGVTTreeRendererAdapter extends GVTTreeRendererAdapter
   {
   // remember to invoke this in the parent class ...

      private JSVGCanvasExtended canvas = null;

      public SimpleGVTTreeRendererAdapter (Global glob, JSVGCanvasExtended canvas)
      {
         super();

         this.canvas = canvas;
         log.fine(" constructor");
      }


      public void gvtRenderingPrepare (GVTTreeRendererEvent e)
      {
         label.setText("Rendering Started...");
         log.fine("Rendering Started...");
      }


      public void gvtRenderingCompleted (GVTTreeRendererEvent e)
      {
         label.setText("");
         // it must be done here because the graphicsNode will be set when
         // all the loading & rendering process has been completed.
         this.canvas.updateDocument();
         log.info("Rendering ended...");
      }
   }


   public static void main (String[] args)
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.out.println(ME + " Init failed");
         System.exit(1);
      }

      JFrame f = new JFrame("Batik");
      ExtendedApplication app = new ExtendedApplication(glob, f);
      f.getContentPane().add(app.createComponents());

      f.addWindowListener(new WindowAdapter()
         {
            public void windowClosing(WindowEvent e) {
              System.exit(0);
            }
         });
       f.setSize(400, 400);
       f.setVisible(true);
   }


   JFrame frame;
   JButton button = new JButton("Load...");
   JLabel label = new JLabel();
   JSVGCanvasExtended svgCanvasExtended = null;

   public ExtendedApplication (Global glob, JFrame f)
   {
      this.glob = glob;

      frame = f;
      svgCanvasExtended = new JSVGCanvasExtended(glob);
   }


   public JComponent createComponents ()
   {
      final JPanel panel = new JPanel(new BorderLayout());

      JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT));
      p.add(button);
      p.add(label);

      panel.add("North", p);
      panel.add("Center", svgCanvasExtended);

      // Set the button action.
      button.addActionListener(new ActionListener()
         {
            public void actionPerformed (ActionEvent ae)
            {
               JFileChooser fc = new JFileChooser(".");
               int choice = fc.showOpenDialog(panel);
               if (choice == JFileChooser.APPROVE_OPTION) {
                  File f = fc.getSelectedFile();
                  try {
                     svgCanvasExtended.setURI(f.toURL().toString());
                  }
                  catch (IOException ex) {
                     ex.printStackTrace();
                  }
               }
            }
         });


      // Set the JSVGCanvas listeners.
      svgCanvasExtended.addSVGDocumentLoaderListener(new SimpleLoaderAdapter(glob, svgCanvasExtended.getSpecificInteractor()));

      svgCanvasExtended.addGVTTreeBuilderListener(new GVTTreeBuilderAdapter()
         {

            public void gvtBuildStarted (GVTTreeBuilderEvent e)
            {
               label.setText("Build Started...");
               log.fine("Build Started...");
            }

            public void gvtBuildCompleted(GVTTreeBuilderEvent e) {
               label.setText("Build Done.");
               log.info("Build Done.");
               frame.pack();
            }
         });

      svgCanvasExtended.addGVTTreeRendererListener(new SimpleGVTTreeRendererAdapter(glob, svgCanvasExtended));
      return panel;
   }
}
