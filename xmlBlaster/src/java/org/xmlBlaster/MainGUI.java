/*------------------------------------------------------------------------------
Name:      MainGUI.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main class to invoke the xmlBlaster server
Version:   $Id: MainGUI.java,v 1.2 1999/11/16 18:44:49 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.xmlBlaster.util.*;
import java.awt.*;
import java.awt.event.*;


/**
 * Start xmlBlaster with a stop button
 */
public class MainGUI extends Frame implements Runnable
{
   Toolkit toolkit = Toolkit.getDefaultToolkit();
   Button beepButton;
   java.awt.TextArea logOutput = null; // PENDING !!!
   private String myName = "MainGUI";

   public MainGUI()
   {
      setTitle("xmlBlaster is serving");
      init();
      setSize(150, 70);
   }


   public void run()
   {
      show();
   }


   public void init()
   {
      beepButton = new Button("Stop xmlBlaster");
      class BeepListener implements ActionListener {
         public void actionPerformed(ActionEvent e) {
           toolkit.beep();
            Log.exit(myName, "Good bye!");
         }
      }
      beepButton.addActionListener(new BeepListener());
            setLayout(new FlowLayout());
            add(beepButton);
   }


   /**
    *  Invoke: jaco org.xmlBlaster.MainGUI
    */
   static public void main(String[] args)
   {
      MainGUI mg = new MainGUI();
      mg.run();

      new org.xmlBlaster.Main(args);
   }
}
