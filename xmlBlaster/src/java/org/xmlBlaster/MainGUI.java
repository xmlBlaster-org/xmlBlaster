/*------------------------------------------------------------------------------
Name:      MainGUI.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main class to invoke the xmlBlaster server
Version:   $Id: MainGUI.java,v 1.5 1999/12/22 09:40:26 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.xmlBlaster.util.*;
import java.awt.*;
import java.awt.event.*;


/**
 * Start xmlBlaster with a stop button.
 * <p />
 * A little AWT button pops up, where you can stop the xmlBlaster<br />
 * The available start parameters are similar to Main
 * @see Main
 */
public class MainGUI extends Frame implements Runnable, org.xmlBlaster.util.LogListener
{
   Toolkit toolkit = Toolkit.getDefaultToolkit();
   private Button beepButton;
   private TextArea logOutput = null;
   private long MAX_LOG_LINES = 3000;
   private long numLogLines = 0;
   private String myName = "MainGUI";


   /**
    * Construct the xmlBlaster GUI. 
    */
   public MainGUI()
   {
      setTitle("xmlBlaster is serving");
      init();
   }


   /**
    * Start the GUI thread. 
    */
   public void run()
   {
      show();
   }

   
   /**
    * Event fired by Log.java through interface LogListener. 
    * <p />
    * Log output into TextArea<br />
    * If the number of lines displayed is too big, cut half of them
    */
   public void log(String str)
   {
      if (numLogLines > MAX_LOG_LINES) {
         String text = logOutput.getText();
         text = text.substring(text.length()/2, text.length());
         logOutput.setText(text);
      }
      numLogLines++;
      logOutput.append(str + "\n");
   }


   /**
    * Build the GUI layout. 
    */
   public void init()
   {
      Log.setDefaultLogLevel();
      Log.addLogListener(this);

      setLayout(new GridBagLayout());
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.fill = GridBagConstraints.BOTH;
      gbc.insets = new Insets(5,5,5,5);
      
      
      beepButton = new Button("Stop xmlBlaster");
      class BeepListener implements ActionListener {
         public void actionPerformed(ActionEvent e) {
            toolkit.beep();
            Log.addLogListener(null);
            Log.exit(myName, "Good bye!");
         }
      }
      beepButton.addActionListener(new BeepListener());
      gbc.gridx=0; gbc.gridy=0; gbc.gridwidth=1; gbc.gridheight=1;
      gbc.weightx = gbc.weighty = 0.0;
      add(beepButton, gbc);

      gbc.gridx=2; gbc.gridy=0; gbc.gridwidth=1; gbc.gridheight=1;
      gbc.weightx = gbc.weighty = 0.0;
      add(new Label("Choose Logging Level: "), gbc);

      Container container = new Container();
      container.setLayout(new GridLayout(1, 7));
      { 
         Checkbox error = new Checkbox("ERROR", null, true);
         error.addItemListener(new LogLevelListener());
         container.add(error);

         Checkbox warning = new Checkbox("WARNING", null, true);
         warning.addItemListener(new LogLevelListener());
         container.add(warning);

         Checkbox info = new Checkbox("INFO", null, true);
         info.addItemListener(new LogLevelListener());
         container.add(info);

         if (Log.CALLS) { // Log.CALLS=true/false: check for dead code elimination
            Checkbox calls = new Checkbox("CALLS", null, false);
            calls.addItemListener(new LogLevelListener());
            container.add(calls);
         }

         if (Log.TIME) {
            Checkbox time = new Checkbox("TIME", null, false);
            time.addItemListener(new LogLevelListener());
            container.add(time);
         }

         if (Log.TRACE) {
            Checkbox trace = new Checkbox("TRACE", null, false);
            trace.addItemListener(new LogLevelListener());
            container.add(trace);
         }

         if (Log.DUMP) {
            Checkbox dump = new Checkbox("DUMP", null, false);
            dump.addItemListener(new LogLevelListener());
            container.add(dump);
         }
      }
      gbc.gridx=3; gbc.gridy=0; gbc.gridwidth=1; gbc.gridheight=1;
      gbc.weightx = gbc.weighty = 0.0;
      add(container, gbc);


      gbc.gridx=0; gbc.gridy=2; gbc.gridwidth=4; // gbc.gridheight=4;
      gbc.weightx = gbc.weighty = 1.0;
      logOutput = new TextArea("", 30, 140);
      logOutput.setEditable(false);
      add(logOutput, gbc);

      pack();
   }


   /**
    * Get the events when a Checkbox is selected with the mouse. 
    */
   class LogLevelListener implements ItemListener
   {
      public void itemStateChanged(ItemEvent e)
      {
         boolean switchOn = e.getStateChange() == ItemEvent.SELECTED;
         String logLevel = (String)e.getItem(); // e.g. "WARNING"
         if (switchOn)
            Log.addLogLevel(logLevel);
         else
            Log.removeLogLevel(logLevel);
         System.out.println("New log level is: " + Log.bitToLogLevel(Log.getLogLevel()));
      }
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
