/*------------------------------------------------------------------------------
Name:      MainGUI.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main class to invoke the xmlBlaster server
Version:   $Id: MainGUI.java,v 1.8 1999/12/22 20:39:04 ruff Exp $
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

   private FillLevelBar publishedMessagesBar = new FillLevelBar();
   private Label publishedLabel = new Label(); // display total count
   private long publishedMessages = 0L;
   private long lastPublishedMessages = 0L;

   private FillLevelBar sentMessagesBar = new FillLevelBar();
   private Label sentLabel = new Label();
   private long sentMessages = 0L;
   private long lastSentMessages = 0L;

   private FillLevelBar getMessagesBar = new FillLevelBar();
   private Label getLabel = new Label();
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
    * Event fired every 1 seconds by the PollingThread.
    * <p />
    * Update the statistic bars.
    * @param sleepTime Milliseconds how long the PollingThread was sleeping (no zero division check!)
    */
   void pollEvent(long sleepTime)
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
         publishedLabel.setText("Total:  " + publishedMessages);
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
         sentLabel.setText("Total:  " + sentMessages);
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
         getLabel.setText("Total:  " + getMessages);
         lastGetMessages = getMessages;
      }
   }


   /**
    * Build the GUI layout.
    */
   private void init()
   {
      Log.setDefaultLogLevel();
      Log.addLogListener(this);

      setLayout(new GridBagLayout());
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.fill = GridBagConstraints.BOTH;
      final int GRID_WIDTH = 4;
      final int GRID_HEIGHT = 4;
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

      int offset = 0;
      gbc.gridx=offset; gbc.gridy=1; gbc.gridwidth=1; gbc.gridheight=1;
      gbc.weightx = gbc.weighty = 0.0;
      createBarPanel(publishedMessagesBar, publishedLabel, "Published", gbc, offset++);
      createBarPanel(sentMessagesBar,      sentLabel,      "Update",    gbc, offset++);
      createBarPanel(getMessagesBar,       getLabel,       "Get",       gbc, offset++);

      gbc.gridx=offset; gbc.gridy=1; gbc.gridwidth=3; gbc.gridheight=1;


      gbc.gridx=0; gbc.gridy=2; gbc.gridwidth=1; gbc.gridheight=1;
      add(new Label("Choose Logging Level: "), gbc);
      gbc.gridx=1; gbc.gridwidth=3; gbc.gridheight=1;
      add(createLogLevelBoxes(), gbc);


      gbc.gridx=0; gbc.gridy=3; gbc.gridwidth=GRID_WIDTH; // gbc.gridheight=4;
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
    * Create a Panel with a FillLevelBar and some labels. 
    * @param messageBar The instance of FillLevelBar to use
    * @param totalCountLabel The instance of total count Label to use
    * @param token Describing text e.g. "Published"
    * @param gbc The layout manager
    * @param offset The position of the panel (grid layout)
    */
   private void createBarPanel(FillLevelBar messageBar, Label totalCountLabel, String token, GridBagConstraints gbc, int offset)
   {
      final int LABEL_LOCATION_X = 2;
      final int LABEL_LOCATION_Y = 10;
      final int LABEL_WIDTH = 90;
      final int LABEL_HEIGHT = 12;
      final int BAR_X = 32;
      final int BAR_Y = LABEL_LOCATION_Y + 2 * LABEL_HEIGHT;
      final int BAR_WIDTH = 50;
      final int BAR_HEIGHT = 130;
      final int TOTAL_LABEL_Y = BAR_Y + BAR_HEIGHT;
      final int PANEL_WIDTH = LABEL_LOCATION_X + LABEL_WIDTH + 2; // 94
      final int PANEL_HEIGHT = TOTAL_LABEL_Y + LABEL_HEIGHT + 4;  // 180
      Font barFont = new java.awt.Font("dialog", 2, 10);

      Panel panel = new Panel();
      panel.setName(token + "MessagePanel");
      panel.setLayout(null);
      panel.setBackground(java.awt.SystemColor.control);
      panel.setSize(PANEL_WIDTH, PANEL_HEIGHT);

      Label label1 = new Label();
      label1.setName("Label1");
      label1.setLocation(LABEL_LOCATION_X, LABEL_LOCATION_Y);
      label1.setText(token);
      label1.setBackground(java.awt.SystemColor.control);
      label1.setSize(LABEL_WIDTH, LABEL_HEIGHT);
      label1.setForeground(java.awt.Color.black);
      label1.setFont(barFont);
      label1.setAlignment(1);
      panel.add(label1, label1.getName());

      Label label2 = new Label();
      label2.setName("Label2");
      label2.setLocation(LABEL_LOCATION_X, LABEL_LOCATION_Y + LABEL_HEIGHT);
      label2.setText("[messages/sec]");
      label2.setBackground(java.awt.SystemColor.control);
      label2.setSize(LABEL_WIDTH, LABEL_HEIGHT);
      label2.setForeground(java.awt.Color.black);
      label2.setFont(barFont);
      label2.setAlignment(1);
      panel.add(label2, label2.getName());

      messageBar.setName(token + "Messages");
      messageBar.setLocation(BAR_X, BAR_Y);
      messageBar.setBackground(java.awt.SystemColor.control);
      messageBar.setSize(BAR_WIDTH, BAR_HEIGHT);
      messageBar.init(0, 5, 100, Color.yellow, Color.green, true, true);
      panel.add(messageBar, messageBar.getName());

      totalCountLabel.setName(token + "Label");
      totalCountLabel.setLocation(LABEL_LOCATION_X, TOTAL_LABEL_Y);
      totalCountLabel.setText("Total:  0");
      totalCountLabel.setBackground(java.awt.SystemColor.control);
      totalCountLabel.setSize(LABEL_WIDTH, LABEL_HEIGHT);
      totalCountLabel.setForeground(java.awt.Color.black);
      totalCountLabel.setFont(barFont);
      totalCountLabel.setAlignment(1);
      panel.add(totalCountLabel, totalCountLabel.getName());

      gbc.gridx=offset;
      add(panel, gbc);
   }


   /**
    * Create Checkboxes to adjust the logging levels
    * @return container with checkboxes
    */
   private Container createLogLevelBoxes()
   {
      Container container = new Container();
      container.setLayout(new GridLayout(1, 7));

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
      return container;
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
   private final int POLLING_FREQUENCY = 1000;  // sleep 1 sec
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
