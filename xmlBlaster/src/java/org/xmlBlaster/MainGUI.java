/*------------------------------------------------------------------------------
Name:      MainGUI.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main class to invoke the xmlBlaster server
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.StopWatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.log.XbNotifyHandler;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.key.GetKey;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Color;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.Toolkit;
import java.awt.event.*;
import java.util.Vector;

import org.jacorb.poa.gui.beans.FillLevelBar;


/**
 * Start xmlBlaster with a GUI based control panel.
 * <p />
 * A control panel pops up, where you can<br />
 * <ul>
 *   <li>Stop xmlBlaster</li>
 *   <li>View the performance monitor</li>
 *   <li>See and adjust logging output</li>
 *   <li>Invoke XPath queries on messages in xmlBlaster</li>
 * </ul>
 * The available start parameters are similar to Main
 * <p>
 * The login name "__sys__GuiQuery" is reserved!<br />
 * </p>
 * @see org.xmlBlaster.Main
 */
public class MainGUI extends Frame implements Runnable, org.xmlBlaster.util.log.I_LogListener
{
   private static final long serialVersionUID = 1L;
   private ServerScope glob;
   private static Logger log = Logger.getLogger(MainGUI.class.getName());

   private Toolkit toolkit = Toolkit.getDefaultToolkit();
   /** The xmlBlaster server, is set from Main() constructor */
   org.xmlBlaster.Main xmlBlasterMain = null;

   private Button exitButton;
   private Button hideButton;
   private Button clearLogButton;
   private Button dumpButton;

   /** TextArea with scroll bars for logging output. */
   private TextArea logOutput = null;
   /** To save memory consumption, limit number of logging lines to this value. */
   private final long MAX_LOG_LINES = 3000;
   /** The actual number of logged lines in the TextArea. */
   private long numLogLines = 0;

   /** Approximate elapsed time since startup of this server. */
   private long elapsedTime = 0L;
   /** Time when xmlBlaster was started */
   private long startupTime = 0L;
   /** Last time the performance was evaluated */
   private long lastPollingTime = 0L;

   /** Performance monitor for number of published messages. */
   private FillLevelBar publishedMessagesBar = new FillLevelBar();
   private Label publishedLabel = new Label(); // display total count
   private int peakPublishedMessages = 0;
   private long publishedMessages = 0L;
   private long lastPublishedMessages = 0L;

   /** Performance monitor for number of update messages (callbacks to clients). */
   private FillLevelBar sentMessagesBar = new FillLevelBar();
   private Label sentLabel = new Label();
   private int peakSentMessages = 0;
   private long sentMessages = 0L;
   private long lastSentMessages = 0L;

   /** Performance monitor for number of synchronous accessed messages. */
   private FillLevelBar getMessagesBar = new FillLevelBar();
   private Label getLabel = new Label();
   private int peakGetMessages = 0;
   private long getMessages = 0L;
   private long lastGetMessages = 0L;

   /** XPath query input field. */
   private TextField  inputTextField = new TextField();
   /** Display XPath query results. */
   private TextArea queryOutput = null;
   /** A client accessing xmlBlaster to do some XPath query. */
   private GuiQuery clientQuery = null;
   /** Remember previous query strings. */
   private QueryHistory queryHistory;


   /**
    * Construct the xmlBlaster GUI.
    */
   public MainGUI(ServerScope glob, org.xmlBlaster.Main main)
   {
      this.xmlBlasterMain = main;
      this.glob = glob;


      // set the application icon
      java.net.URL oUrl;
      oUrl = this.getClass().getResource("AppIcon.gif");
      Image img = null;
      if (oUrl != null)
         img = java.awt.Toolkit.getDefaultToolkit().getImage(oUrl);
      if(img != null)
      {
        this.setIconImage(img);
        System.out.println(img.toString());
      }
      else
      {
        System.out.println("AppIcon.gif not found");
      }


      setTitle("XmlBlaster Control Panel");
      init();

      // Poll xmlBlaster internal states
      PollingThread poller = new PollingThread(this);
      poller.start();

   }


   /**
    * Start the GUI thread.
    */
   public void run()
   {
      show();
      if (this.xmlBlasterMain == null)
         this.xmlBlasterMain = new org.xmlBlaster.Main(glob, this);
   }

   /**
    * Event fired by Logger.java through interface I_LogListener. 
    * <p />
    * log.addLogDevice(this);
    * <p />
    * Log output into TextArea<br />
    * If the number of lines displayed is too big, cut half of them
    */
   public void log(LogRecord record)
   {
      String str = record.getLevel().toString() + " [" + record.getLoggerName() + "] " + record.getMessage();
      if (logOutput == null) {
         System.err.println(str + "\n");
         return;
      }
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
      long current = System.currentTimeMillis();
      if (lastPollingTime > 0L) {
         sleepTime = current - lastPollingTime; // correct sleepTime with the real sleeping time
      }
      lastPollingTime = current;
      elapsedTime += current - startupTime;

      double sleepSeconds = sleepTime / 1000.0;
      //double elapsedSeconds = elapsedTime / 1000.0;

      {
         publishedMessages = this.glob.getRequestBroker().getNumPublish();
         int currentPublishedAvg = (int)((publishedMessages - lastPublishedMessages)/sleepSeconds);
         if ((publishedMessages - lastPublishedMessages) == 1) currentPublishedAvg = 1;
         //int totalPublishedAvg = (int)(numPublish/elapsedSeconds);
         publishedMessagesBar.setCurrentValue(currentPublishedAvg);
         if (currentPublishedAvg > peakPublishedMessages) {
            peakPublishedMessages = currentPublishedAvg;
            publishedMessagesBar.setAvgValue(peakPublishedMessages);
         }
         //publishedMessagesBar.setAvgValue(totalPublishedAvg);
         publishedLabel.setText("Total:  " + publishedMessages);
         lastPublishedMessages = publishedMessages;
      }

      {
         sentMessages = 0;// TODO SessionInfo.sentMessages;
         int currentSentAvg = (int)((sentMessages - lastSentMessages)/sleepSeconds);
         if ((sentMessages - lastSentMessages) == 1) currentSentAvg = 1;
         //int totalSentAvg = (int)(sentMessages/elapsedSeconds);
         sentMessagesBar.setCurrentValue(currentSentAvg);
         if (currentSentAvg > peakSentMessages) {
            peakSentMessages = currentSentAvg;
            sentMessagesBar.setAvgValue(peakSentMessages);
         }
         // sentMessagesBar.setAvgValue(totalSentAvg);
         sentLabel.setText("Total:  " + sentMessages);
         lastSentMessages = sentMessages;
      }

      {
         getMessages = this.glob.getRequestBroker().getNumGet();
         int currentGetAvg = (int)((getMessages - lastGetMessages)/sleepSeconds);
         if ((getMessages - lastGetMessages) == 1) currentGetAvg = 1;
         //int totalGetAvg = (int)(numGet/elapsedSeconds);
         // System.out.println("totally numGet=" + numGet + " current avg=" + currentGetAvg + " total avg=" + totalGetAvg);
         getMessagesBar.setCurrentValue(currentGetAvg);
         if (currentGetAvg > peakGetMessages) {
            peakGetMessages = currentGetAvg;
            getMessagesBar.setAvgValue(peakGetMessages);
         }
         // getMessagesBar.setAvgValue(totalGetAvg);
         getLabel.setText("Total:  " + getMessages);
         lastGetMessages = getMessages;
      }
   }

   private void registerLogEvents() {
      XbNotifyHandler.instance().register(Level.ALL.intValue(), this);
   }

   private void unregisterLogEvents() {
      XbNotifyHandler.instance().unregister(Level.ALL.intValue(), this);
   }
   
   /**
    * Build the GUI layout.
    */
   private void init()
   {
      registerLogEvents();
      setLayout(new GridBagLayout());
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.fill = GridBagConstraints.BOTH;
      gbc.insets = new Insets(5,5,5,5);

      // Exit Button
      exitButton = new Button("Exit");
      class BeepListener implements ActionListener {
         public void actionPerformed(ActionEvent e) {
            toolkit.beep();
            if (clientQuery != null)
               clientQuery.logout();
            //unregisterLogEvents();
            log.info("Good bye!");
            System.exit(0);
         }
      }
      exitButton.addActionListener(new BeepListener());
      gbc.gridx=0; gbc.gridy=0; gbc.gridwidth=1; gbc.gridheight=1;
      gbc.weightx = gbc.weighty = 0.0;
      add(exitButton, gbc);

      // Hide Button
      hideButton = new Button("Hide Window");
      class HideListener implements ActionListener {
         public void actionPerformed(ActionEvent e) {
            hideWindow();
         }
      }
      hideButton.addActionListener(new HideListener());
      gbc.gridx=1; gbc.gridy=0; gbc.gridwidth=1; gbc.gridheight=1;
      gbc.weightx = gbc.weighty = 0.0;
      add(hideButton, gbc);

      // Statistic display with fill level bars
      int offset = 0;
      gbc.gridx=offset; gbc.gridy=1; gbc.gridwidth=1; gbc.gridheight=1;
      gbc.weightx = gbc.weighty = 0.0;
      createBarPanel(publishedMessagesBar, publishedLabel, "Published", gbc, offset++);
      createBarPanel(sentMessagesBar,      sentLabel,      "Update",    gbc, offset++);
      createBarPanel(getMessagesBar,       getLabel,       "Get",       gbc, offset++);


      {  // XPath query GUI
         Panel panel = new Panel();
         panel.setName("QueryPanel");
         panel.setLayout(new BorderLayout());
         panel.setBackground(java.awt.SystemColor.control);

         {  // Field to enter XPath text
            Panel inputPanel = new Panel();
            inputPanel.setLayout(new BorderLayout());

            Label inputLabel = new Label("XPath query: ");
            inputPanel.add("West", inputLabel);

            inputTextField.setText("//key");
            inputPanel.add("Center", inputTextField);
            inputTextField.addKeyListener(new XPathKeyListener());

            panel.add("North", inputPanel);
         }

         {  // TextArea to show query results
            queryOutput = new TextArea();
            queryOutput.setEditable(false);

            panel.add("South", queryOutput);
         }

         gbc.gridx=offset; gbc.gridy=1; gbc.gridwidth=3; gbc.gridheight=1;
         add(panel, gbc);
      }


      // Checkboxes for log levels
      gbc.gridx=0; gbc.gridy=2; gbc.gridwidth=1; gbc.gridheight=1;
      add(new Label("Choose Logging Level: "), gbc);
      gbc.gridx=1; gbc.gridwidth=3; gbc.gridheight=1;
      add(createLogLevelBoxes(), gbc);


      // Clear logging output - Button
      clearLogButton = new Button("Clear Log Window");
      class ClearListener implements ActionListener {
         public void actionPerformed(ActionEvent e) {
            logOutput.setText("");
         }
      }
      clearLogButton.addActionListener(new ClearListener());
      gbc.gridx=4; gbc.gridy=2; gbc.gridwidth=1; gbc.gridheight=1;
      gbc.weightx = gbc.weighty = 0.0;
      add(clearLogButton, gbc);


      // Dump internal state - Button
      dumpButton = new Button("Dump State");
      class DumpListener implements ActionListener {
         public void actionPerformed(ActionEvent e) {
            // logOutput.setText("");  // clear log window
            try {
               log.info("Dump start");
               I_Authenticate auth = xmlBlasterMain.getAuthenticate();
               StringBuffer buf = new StringBuffer(auth.toXml());
               buf.append(xmlBlasterMain.getXmlBlaster().toXml());
               LogRecord record = new LogRecord(Level.INFO, buf.toString());
               log(record);
               log.info("Dump end");
            }
            catch(XmlBlasterException ee) {
               log.severe("Sorry, dump failed: " + ee.getMessage());
            }
         }
      }
      dumpButton.addActionListener(new DumpListener());
      gbc.gridx=5; gbc.gridy=2; gbc.gridwidth=1; gbc.gridheight=1;
      gbc.weightx = gbc.weighty = 0.0;
      add(dumpButton, gbc);


      // TextArea for log outputs
      gbc.gridx=0; gbc.gridy=3; gbc.gridwidth=6; gbc.gridheight=6;
      gbc.weightx = gbc.weighty = 1.0;
      logOutput = new TextArea("", 30, 100); // set rows here (width 100 is ignored)
      logOutput.setEditable(false);
      add(logOutput, gbc);

      pack();

      startupTime = System.currentTimeMillis();
   }


   /**
    * Hide the window.
    * Note that all the resources are still busy, only logging is directed to console
    */
   private void hideWindow()
   {
      unregisterLogEvents();
      if (isShowing()) {
         log.info("Press <g> and <Enter> to popup the GUI again (press ? for other options).");
         setVisible(false); // dispose(); would clean up all resources
      }
   }


   /**
    * Hide the window.
    * Note that all the resources are still busy, only logging is directed to console
    */
   void showWindow()
   {
      if (!isShowing()) {
         registerLogEvents();
         if (log.isLoggable(Level.FINE)) log.fine("Show window again ...");
         show();
      }
   }


   /**
    * Get the events when a Checkbox is selected with the mouse.
    */
   class LogLevelListener implements ItemListener
   {
      public void itemStateChanged(ItemEvent e)
      {  
         log.warning("is not implemented");
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
      boolean useAvg = true;
      boolean isVariable = true;     // !!!
      int MAX_SCALE = 10;
      messageBar.init(0, 5, MAX_SCALE, Color.green, Color.green, useAvg, isVariable);
      messageBar.setAvgValue(0);
      // messageBar.init(0, 5, 100, Color.yellow, Color.green, true, true);
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
   private Panel createLogLevelBoxes()
   {
      Panel container = new Panel();
      container.setLayout(new GridLayout(1, 7));

      Checkbox error = new Checkbox("ERROR", null, true);
      error.addItemListener(new LogLevelListener());
      container.add(error);

      Checkbox warning = new Checkbox("WARN", null, true);
      warning.addItemListener(new LogLevelListener());
      container.add(warning);

      Checkbox info = new Checkbox("INFO", null, true);
      info.addItemListener(new LogLevelListener());
      container.add(info);

      if (true/*log.CALL*/) { // log.CALL=true/false: check for dead code elimination
         Checkbox calls = new Checkbox("CALL", null, false);
         calls.addItemListener(new LogLevelListener());
         container.add(calls);
      }

      if (true/*log.TIME*/) {
         Checkbox time = new Checkbox("TIME", null, false);
         time.addItemListener(new LogLevelListener());
         container.add(time);
      }

      if (true/*log.TRACE*/) {
         Checkbox trace = new Checkbox("TRACE", null, false);
         trace.addItemListener(new LogLevelListener());
         container.add(trace);
      }

      if (true/*log.DUMP*/) {
         Checkbox dump = new Checkbox("DUMP", null, false);
         dump.addItemListener(new LogLevelListener());
         container.add(dump);
      }
      return container;
   }


   /**
    * Access the query history.
    */
   private QueryHistory getQueryHistory()
   {
      if (queryHistory == null)
        queryHistory = new QueryHistory();
      return queryHistory;
   }


   /**
    *  Invoke: <pre>jaco org.xmlBlaster.MainGUI</pre><br />
    * to start xmlBlaster with a control panel
    */
   static public void main(String[] args)
   {
      ServerScope glob = new ServerScope(args);
      Main.controlPanel = new MainGUI(glob, null);
      Main.controlPanel.run();
   }




   /**
    * Polls the state of xmlBlaster.
    */
   private class PollingThread extends Thread
   {
      private final int POLLING_FREQUENCY = 1000;  // sleep 1 sec
      private final MainGUI mainGUI;


      /**
       **/
      public PollingThread(MainGUI mainGUI)
      {
         this.mainGUI = mainGUI;
         // !!! setPriority(Thread.MIN_PRIORITY);
      }


      /**
       * Start the timeout thread.
       **/
      public void run()
      {
         Thread.currentThread().setName("XmlBlaster GUIPollingThread");
         try {
            if (log.isLoggable(Level.FINE)) log.fine("Starting poller");
            while (true) {
               sleep(POLLING_FREQUENCY);
               mainGUI.pollEvent(POLLING_FREQUENCY);  // Todo: calculate the exact sleeping period
            }
         }
         catch (Exception e) {
            log.fine("PollingThread problem: "+ e.toString());
            //e.printStackTrace();
         }
      }
   }



   /**
    * Handles return key when a XPath query is entered into the TextField.
    */
   private class XPathKeyListener implements KeyListener
   {
      /**
       * Access XPath query string (event from KeyListener).
       */
      public final void keyPressed(KeyEvent ev)
      {
         switch (ev.getKeyCode())
         {
            case KeyEvent.VK_ENTER:
               try {
                  if (clientQuery == null) {
                     clientQuery = new GuiQuery(xmlBlasterMain.getAuthenticate(), xmlBlasterMain.getXmlBlaster());
                  }
                  queryOutput.setText("");
                  getQueryHistory().changedHistory(inputTextField.getText());
                  MsgUnitRaw[] msgArr = clientQuery.get(inputTextField.getText());
                  for (int ii=0; ii<msgArr.length; ii++) {
                     /*
                     UpdateKey updateKey = new UpdateKey();
                     updateKey.init(msgArr[ii].getKey());
                     queryOutput.append("### UpdateKey:\n" + updateKey.printOn().toString());
                     */
                     queryOutput.append("### XmlKey:\n" + msgArr[ii].getKey());
                     queryOutput.append("\n");
                     queryOutput.append("### Content:\n" + new String(msgArr[ii].getContent()) + "\n");
                     queryOutput.append("======================================================\n");
                  }
                  if (msgArr.length == 0) {
                     if (publishedMessages == 0L) {
                        queryOutput.setText("\n");
                        queryOutput.append ("   Sorry, no data in xmlBlaster.\n");
                        queryOutput.append ("   Use a demo client to publish some.\n");
                        queryOutput.append ("\n");
                     }
                     else
                        queryOutput.setText("****** Sorry, no match ******");
                  }
               } catch(XmlBlasterException e) {
                  log.severe("XmlBlasterException: " + e.getMessage());
               }
               break;
            case KeyEvent.VK_DOWN:
               displayHistory(getQueryHistory().getNext());
               break;
            case KeyEvent.VK_UP:
               displayHistory(getQueryHistory().getPrev());
               break;
         }
      }
      public final void keyReleased(KeyEvent ev)
      {
      }
      public final void keyTyped(KeyEvent ev)
      {
      }
   }


   /**
    * Scrolling with key arrow up/down your last XPath queries.
    * @param stmt The XPath stmt to display
    */
   private void displayHistory(String stmt)
   {
      if (stmt.length() < 1) return;
      inputTextField.setText(stmt);
   }


   /**
    * A client accessing xmlBlaster to do some XPath query.
    */
   private class GuiQuery
   {
      private String queryType = Constants.XPATH;
      private StopWatch stop = new StopWatch();
      /** Handle to login */
      I_Authenticate authenticate;
      /** The handle to access xmlBlaster */
      private I_XmlBlaster xmlBlasterImpl;
      /** The session id on successful authentication */
      private String secretSessionId;
      private AddressServer addressServer;

      /**
       * Login to xmlBlaster and get a sessionId. 
       */
      public GuiQuery(I_Authenticate authenticate, I_XmlBlaster xmlBlasterImpl) throws XmlBlasterException
      {
         this.xmlBlasterImpl = xmlBlasterImpl;
         this.authenticate = authenticate;
         String loginName = glob.getProperty().get("__sys__GuiQuery.loginName", "__sys__GuiQuery");
         String passwd = glob.getProperty().get("__sys__GuiQuery.password", "secret");
         ConnectQos connectQos = new ConnectQos(authenticate.getGlobal());
         connectQos.loadClientPlugin("htpasswd", "1.0", loginName, passwd);
         connectQos.getSessionQos().setSessionTimeout(0L);
         // TODO: Port to use "LOCAL" protocol to connect
         this.addressServer = new AddressServer(authenticate.getGlobal(), "NATIVE", authenticate.getGlobal().getId(), (java.util.Properties)null);
         String ret = authenticate.connect(addressServer, connectQos.toXml(), null); // synchronous access only, no callback.
         ConnectReturnQos retQos = new ConnectReturnQos(authenticate.getGlobal(), ret);
         this.secretSessionId = retQos.getSecretSessionId();
         log.info("login for '" + loginName + "' successful.");
      }

      /**
       * Query xmlBlaster.
       */
      MsgUnitRaw[] get(String queryString)
      {
         try {
            GetKey getKey = new GetKey(this.authenticate.getGlobal(), queryString, queryType);
            stop.restart();
            MsgUnitRaw[] msgArr = this.xmlBlasterImpl.get(this.addressServer, this.secretSessionId, getKey.toXml(), "<qos/>");
            log.info("Got " + msgArr.length + " messages for query '" + queryString + "'" + stop.nice());
            return msgArr;
         } catch(XmlBlasterException e) {
            log.severe("XmlBlasterException: " + e.getMessage());
            return new MsgUnitRaw[0];
         }
      }

      /** Logout. */
      void logout() {
         try { this.authenticate.disconnect(this.addressServer, this.secretSessionId, null); } catch (XmlBlasterException e) { }
      }
   }


   /**
    * Implements a stack to hold the previous XPath queries.
    */
   private class QueryHistory
   {
     //private String ME = "QueryHistory";
     private Vector stack = new Vector();
     private int currentIndex = 0;


     /**
      * Constructs a history stack.
      */
     public QueryHistory()
     {
     }

     /**
      * Add new statement into history.
      */
     public void changedHistory(String stmt)
     {
       if (stack.size() > 1) {
         String last = (String)stack.elementAt(stack.size()-1);
         if (last.equals(stmt)) return;
       }
       currentIndex = stack.size();
       stack.addElement(stmt);
     }


     /**
      * Access previous XPath query.
      */
     String getPrev()
     {
       if (stack.size() < 1) return "";
       if (currentIndex > 0) currentIndex--;
       return (String)stack.elementAt(currentIndex);
     }


     /**
      * Access next XPath query.
      */
     String getNext()
     {
       if (stack.size() < 1) return "";
       if (currentIndex < stack.size()-1) currentIndex++;
       return (String)stack.elementAt(currentIndex);
     }


     /**
      * Access last (the newest), sets current to last.
      */
     String getLast()
     {
       if (stack.size() < 1) return "";
       currentIndex = stack.size() - 1;
       return (String)stack.elementAt(currentIndex);
     }


     /**
      * Access first (the oldest), sets current to first.
      */
     String getFirst()
     {
       if (stack.size() < 1) return "";
       currentIndex = 0;
       return (String)stack.elementAt(currentIndex);
     }

   }
}

