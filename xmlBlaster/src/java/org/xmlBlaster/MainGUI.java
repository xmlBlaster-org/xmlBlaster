/*------------------------------------------------------------------------------
Name:      MainGUI.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main class to invoke the xmlBlaster server
Version:   $Id: MainGUI.java,v 1.6 1999/12/22 12:26:18 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.xmlBlaster.util.*;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.RequestBroker;
import java.awt.*;
import java.awt.event.*;
import jacorb.poa.gui.beans.FillLevelBar;


/**
 * Start xmlBlaster with a stop button.
 * <p />
 * A little AWT button pops up, where you can stop the xmlBlaster<br />
 * The available start parameters are similar to Main
 * @see Main
 */
public class MainGUI extends Frame implements Runnable, org.xmlBlaster.util.LogListener
{
   private Toolkit toolkit = Toolkit.getDefaultToolkit();
   private final String ME = "MainGUI";

   private Button beepButton;

   private TextArea logOutput = null;
   private final long MAX_LOG_LINES = 3000;
   private long numLogLines = 0;

   private long elapsedTime = 0L;

   private FillLevelBar publishedMessagesBar = null;
   private long publishedMessages = 0L;
   private long lastPublishedMessages = 0L;

   private FillLevelBar sentMessagesBar = null;
   private long sentMessages = 0L;
   private long lastSentMessages = 0L;

   private FillLevelBar getMessagesBar = null;
   private long getMessages = 0L;
   private long lastGetMessages = 0L;

   /**
    * Construct the xmlBlaster GUI.
    */
   public MainGUI()
   {
      setTitle("XmlBlaster Control Panel");
      init();

      PollingThread poller = new PollingThread(this);
      poller.start();
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
    * Event fired ecery 2 seconds by the PollingThread. 
    * <p />
    * Update the statistic bars.
    * @param sleepTime Milliseconds how long the PollingThread was sleeping (no zero division check!)
    */
   public void pollEvent(long sleepTime)
   {
      elapsedTime += sleepTime;

      double sleepSeconds = sleepTime / 1000.0;
      double elapsedSeconds = elapsedTime / 1000.0;

      {
         publishedMessages = RequestBroker.publishedMessages;
         int currentPublishedAvg = (int)((publishedMessages - lastPublishedMessages)/sleepSeconds);
         if ((publishedMessages - lastPublishedMessages) == 1) currentPublishedAvg = 1;
         int totalPublishedAvg = (int)(publishedMessages/elapsedSeconds);
         // System.out.println("totally publishedMessages=" + publishedMessages + " current avg=" + currentPublishedAvg + " total avg=" + totalPublishedAvg);
         publishedMessagesBar.setCurrentValue(currentPublishedAvg);
         publishedMessagesBar.setAvgValue(totalPublishedAvg);
         lastPublishedMessages = publishedMessages;
      }
   
      {
         sentMessages = ClientInfo.sentMessages;
         int currentSentAvg = (int)((sentMessages - lastSentMessages)/sleepSeconds);
         if ((sentMessages - lastSentMessages) == 1) currentSentAvg = 1;
         int totalSentAvg = (int)(sentMessages/elapsedSeconds);
         // System.out.println("totally sentMessages=" + sentMessages + " current avg=" + currentSentAvg + " total avg=" + totalSentAvg);
         sentMessagesBar.setCurrentValue(currentSentAvg);
         sentMessagesBar.setAvgValue(totalSentAvg);
         lastSentMessages = sentMessages;
      }

      {
         getMessages = RequestBroker.getMessages;
         int currentGetAvg = (int)((getMessages - lastGetMessages)/sleepSeconds);
         if ((getMessages - lastGetMessages) == 1) currentGetAvg = 1;
         int totalGetAvg = (int)(getMessages/elapsedSeconds);
         // System.out.println("totally getMessages=" + getMessages + " current avg=" + currentGetAvg + " total avg=" + totalGetAvg);
         getMessagesBar.setCurrentValue(currentGetAvg);
         getMessagesBar.setAvgValue(totalGetAvg);
         lastGetMessages = getMessages;
      }
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
            Log.exit(ME, "Good bye!");
         }
      }
      beepButton.addActionListener(new BeepListener());
      gbc.gridx=0; gbc.gridy=0; gbc.gridwidth=1; gbc.gridheight=1;
      gbc.weightx = gbc.weighty = 0.0;
      add(beepButton, gbc);

      {
         Panel panel = new Panel();
         panel.setName("PublishedMessagePanel");
         panel.setLayout(null);
         panel.setBackground(java.awt.SystemColor.control);
         panel.setSize(100, 200);

         Label label1 = new Label();
         label1.setName("Label1");
         label1.setLocation(10, 10);
         label1.setText("Published");
         label1.setBackground(java.awt.SystemColor.control);
         label1.setSize(90, 12);
         label1.setForeground(java.awt.Color.black);
         label1.setFont(new java.awt.Font("dialog", 2, 10));
         label1.setAlignment(1);
         panel.add(label1, label1.getName());

         Label label2 = new Label();
         label2.setName("Label2");
         label2.setLocation(10, 22);
         label2.setText("[messages/sec]");
         label2.setBackground(java.awt.SystemColor.control);
         label2.setSize(90, 12);
         label2.setForeground(java.awt.Color.black);
         label2.setFont(new java.awt.Font("dialog", 2, 10));
         label2.setAlignment(1);
         panel.add(label2, label2.getName());

         publishedMessagesBar = new FillLevelBar();
         publishedMessagesBar.setName("PublishedMessages");
         publishedMessagesBar.setLocation(32, 35);
         publishedMessagesBar.setBackground(java.awt.SystemColor.control);
         publishedMessagesBar.setSize(50, 130);
         publishedMessagesBar.init(0, 5, 100, Color.yellow, Color.green, true, true);
         panel.add(publishedMessagesBar, publishedMessagesBar.getName());

         gbc.gridx=0; gbc.gridy=1; gbc.gridwidth=1; gbc.gridheight=1;
         gbc.weightx = gbc.weighty = 0.0;
         add(panel, gbc);
      }

      {
         Panel panel = new Panel();
         panel.setName("SentMessagePanel");
         panel.setLayout(null);
         panel.setBackground(java.awt.SystemColor.control);
         panel.setSize(100, 200);

         Label label1 = new Label();
         label1.setName("Label1");
         label1.setLocation(10, 10);
         label1.setText("Update");
         label1.setBackground(java.awt.SystemColor.control);
         label1.setSize(90, 12);
         label1.setForeground(java.awt.Color.black);
         label1.setFont(new java.awt.Font("dialog", 2, 10));
         label1.setAlignment(1);
         panel.add(label1, label1.getName());

         Label label2 = new Label();
         label2.setName("Label2");
         label2.setLocation(10, 22);
         label2.setText("[messages/sec]");
         label2.setBackground(java.awt.SystemColor.control);
         label2.setSize(90, 12);
         label2.setForeground(java.awt.Color.black);
         label2.setFont(new java.awt.Font("dialog", 2, 10));
         label2.setAlignment(1);
         panel.add(label2, label2.getName());

         sentMessagesBar = new FillLevelBar();
         sentMessagesBar.setName("SentMessages");
         sentMessagesBar.setLocation(32, 35);
         sentMessagesBar.setBackground(java.awt.SystemColor.control);
         sentMessagesBar.setSize(50, 130);
         // yellow avg value, green current value
         sentMessagesBar.init(0, 5, 100, Color.yellow, Color.green, true, true);
         //sentMessagesBar.setMaxValue(100);  // is set variable
         //sentMessagesBar.setMinValue(0);    // zero is default
         //sentMessagesBar.setCurrentValue(50);
         //sentMessagesBar.setAvgValue(25);
         panel.add(sentMessagesBar, sentMessagesBar.getName());

         gbc.gridx=1; gbc.gridy=1; gbc.gridwidth=1; gbc.gridheight=1;
         gbc.weightx = gbc.weighty = 0.0;
         add(panel, gbc);
      }

      {
         Panel panel = new Panel();
         panel.setName("GetMessagePanel");
         panel.setLayout(null);
         panel.setBackground(java.awt.SystemColor.control);
         panel.setSize(100, 200);

         Label label1 = new Label();
         label1.setName("Label1");
         label1.setLocation(10, 10);
         label1.setText("Get Synchronous");
         label1.setBackground(java.awt.SystemColor.control);
         label1.setSize(90, 12);
         label1.setForeground(java.awt.Color.black);
         label1.setFont(new java.awt.Font("dialog", 2, 10));
         label1.setAlignment(1);
         panel.add(label1, label1.getName());

         Label label2 = new Label();
         label2.setName("Label2");
         label2.setLocation(10, 22);
         label2.setText("[messages/sec]");
         label2.setBackground(java.awt.SystemColor.control);
         label2.setSize(90, 12);
         label2.setForeground(java.awt.Color.black);
         label2.setFont(new java.awt.Font("dialog", 2, 10));
         label2.setAlignment(1);
         panel.add(label2, label2.getName());

         getMessagesBar = new FillLevelBar();
         getMessagesBar.setName("GetMessages");
         getMessagesBar.setLocation(32, 35);
         getMessagesBar.setBackground(java.awt.SystemColor.control);
         getMessagesBar.setSize(50, 130);
         getMessagesBar.init(0, 5, 100, Color.yellow, Color.green, true, true);
         panel.add(getMessagesBar, getMessagesBar.getName());

         gbc.gridx=2; gbc.gridy=1; gbc.gridwidth=1; gbc.gridheight=1;
         gbc.weightx = gbc.weighty = 0.0;
         add(panel, gbc);
      }

      {
         gbc.gridx=0; gbc.gridy=2; gbc.gridwidth=1; gbc.gridheight=1;
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

            if (true/*Log.CALLS*/) { // Log.CALLS=true/false: check for dead code elimination
               Checkbox calls = new Checkbox("CALLS", null, false);
               calls.addItemListener(new LogLevelListener());
               container.add(calls);
            }

            if (true/*Log.TIME*/) {
               Checkbox time = new Checkbox("TIME", null, false);
               time.addItemListener(new LogLevelListener());
               container.add(time);
            }

            if (true/*Log.TRACE*/) {
               Checkbox trace = new Checkbox("TRACE", null, false);
               trace.addItemListener(new LogLevelListener());
               container.add(trace);
            }

            if (true/*Log.DUMP*/) {
               Checkbox dump = new Checkbox("DUMP", null, false);
               dump.addItemListener(new LogLevelListener());
               container.add(dump);
            }
         }
         gbc.gridx=1; gbc.gridwidth=3; gbc.gridheight=1;
         gbc.weightx = gbc.weighty = 0.0;
         add(container, gbc);
      }

      gbc.gridx=0; gbc.gridy=3; gbc.gridwidth=4; // gbc.gridheight=4;
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


/**
 * Polls the state of xmlBlaster
 */
class PollingThread extends Thread 
{
   private final int POLLING_FREQUENCY = 2000;  // sleep 2 sec
   private final MainGUI mainGUI;


   /**
    **/
   public PollingThread(MainGUI mainGUI)
   {
      this.mainGUI = mainGUI;
      setPriority(Thread.MIN_PRIORITY);
   }


   /**
    * Start the timeout thread. 
    **/
   public void run()
   {
      try {
         System.out.println("Starting poller");
         while (true) {
            sleep(POLLING_FREQUENCY);
            mainGUI.pollEvent(POLLING_FREQUENCY);  // Todo: calculate the exact sleeping period
         }
      }
      catch (Exception e) {
         System.err.println("PollingThread problem: "+ e.toString());
      }
   }
}
