/*------------------------------------------------------------------------------
Name:      MyExpansionListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jmxgui;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;


/**
 * SplashWindow displayed at the Startup of the GUI
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author unascribed
 * @version 1.0
 */
public class SplashWindow extends JWindow {
  private static String ME = "SplashWindow";

  public SplashWindow(String iconName, Frame frame, int waitTime) {
    super(frame);
    try {
      JLabel l = new JLabel(loadIcon(iconName));
      this.getContentPane().add(l, BorderLayout.CENTER);
      pack();
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      Dimension labelSize = l.getPreferredSize();
      this.setLocation(screenSize.width / 2 - (labelSize.width /2 ), screenSize.height /2 - (labelSize.height /2) ) ;
      addMouseListener(new MouseAdapter(){
        public void mousePressed(MouseEvent e) {
          setVisible(false);
          dispose();
        }
      });
      final int pause = waitTime;
      final Runnable closeRunner = new Runnable() {
        public void run() {
          setVisible(false);
          dispose();
        }
      };

      final Runnable waitRunner = new Runnable() {
        public void run() {
          try {
            Thread.sleep(pause);
            SwingUtilities.invokeAndWait(closeRunner);
          }
          catch (Exception ex) {
            System.out.println("Error: " + ex.toString());
          }
        }
      };
      setVisible(true);
      Thread splashThread = new Thread();
      splashThread.start();
    }
    catch (Exception e) {

    }
  }
  public ImageIcon loadIcon(String filename){
  ImageIcon icon = null;
  java.net.URL oUrl;
  oUrl = this.getClass().getResource(filename);
  Image img;
  img = java.awt.Toolkit.getDefaultToolkit().getImage(oUrl);
  if(img != null)
  {
    icon = new ImageIcon(img);
  }
  else
  {
  }
  return icon;
}

}