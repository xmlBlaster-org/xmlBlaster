/*------------------------------------------------------------------------------
Name:      XmlUtility.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a svg client using batik (the demo appl. itself)
Version:   $Id: ExtendedApplication.java,v 1.1 2002/01/04 01:05:38 laghi Exp $
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

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;

import org.jutils.log.LogChannel;

/**
 *
 * @author $Author: laghi $ (laghi@swissinfo.org)
 */
public class ExtendedApplication
{

   private static final String ME = "ExtendedApplication";

   protected class SimpleLoaderAdapter extends SVGDocumentLoaderAdapter
   {
   // remember to invoke this in the parent class ...

      private Interactor specialInteractor = null;

      private final String ME = "SimpleLoaderAdapter";

      public SimpleLoaderAdapter (Interactor specialInteractor)
      {
         super();
         this.specialInteractor = specialInteractor;
         Log.trace(ME, " constructor");
      }

      public void documentLoadingStarted(SVGDocumentLoaderEvent e) {
         Log.trace(ME, ".documentLoadingStarted");
      }

      public void documentLoadingCompleted(SVGDocumentLoaderEvent e) {
         Log.info(ME, ".documentLoadingCompleted");
      }

   }



   protected class SimpleGVTTreeRendererAdapter extends GVTTreeRendererAdapter
   {
   // remember to invoke this in the parent class ...

      private JSVGCanvasExtended canvas = null;

      private final String ME = "SimpleGVTTreeRendererAdapter";

      public SimpleGVTTreeRendererAdapter (JSVGCanvasExtended canvas)
      {
         super();
         this.canvas = canvas;
         Log.trace(ME, " constructor");
      }


      public void gvtRenderingPrepare (GVTTreeRendererEvent e)
      {
         label.setText("Rendering Started...");
         Log.trace(ME, "Rendering Started...");
      }


      public void gvtRenderingCompleted (GVTTreeRendererEvent e)
      {
         label.setText("");
         // it must be done here because the graphicsNode will be set when
         // all the loading & rendering process has been completed.
         this.canvas.updateDocument();
         Log.info(ME, "Rendering ended...");
      }
   }


   public static void main (String[] args)
   {

      Log.setLogLevel(LogChannel.LOG_ALL);
      Log.trace(ME, " this is a trace");
      JFrame f = new JFrame("Batik");
      ExtendedApplication app = new ExtendedApplication(f, args);
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

   public ExtendedApplication (JFrame f, String[] args)
   {
      frame = f;
      svgCanvasExtended = new JSVGCanvasExtended(args);
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
      svgCanvasExtended.addSVGDocumentLoaderListener(new SimpleLoaderAdapter(svgCanvasExtended.getSpecificInteractor()));

      svgCanvasExtended.addGVTTreeBuilderListener(new GVTTreeBuilderAdapter()
         {

            public void gvtBuildStarted (GVTTreeBuilderEvent e)
            {
               label.setText("Build Started...");
               Log.trace(ME, "Build Started...");
            }

            public void gvtBuildCompleted(GVTTreeBuilderEvent e) {
               label.setText("Build Done.");
               Log.info(ME, "Build Done.");
               frame.pack();
            }
         });

      svgCanvasExtended.addGVTTreeRendererListener(new SimpleGVTTreeRendererAdapter(svgCanvasExtended));
      return panel;
   }
}
