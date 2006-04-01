package org.xmlBlaster.authentication.plugins.demo;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import org.xmlBlaster.util.def.MethodName;


/*****************************************************************************
 * Demonstation of xmlBlaster security basics.
 * @author <a href="email:wolfgang.kleinertz@doubleSlash.de">Wolfgang Kleinertz</a>
 *****************************************************************************/
public class PluginGUI extends JFrame {
   private static final long serialVersionUID = 8063864833824851457L;
   JPanel           contentPane;
   GridLayout       gridLayout1 = new GridLayout();
   JScrollPane     jScrollPane1 = new JScrollPane();
   Box                     box1;
   JScrollPane     jScrollPane2 = new JScrollPane();
   JTextArea           inOutput = new JTextArea();
   JTextArea          outOutput = new JTextArea();
   JLabel           serverImage = new JLabel();
   JScrollPane     jScrollPane3 = new JScrollPane();
   JPanel               jPanel1 = new JPanel();
   JMenuBar           jMenuBar1 = new JMenuBar();
   BorderLayout   borderLayout1 = new BorderLayout();
   JPanel               jPanel2 = new JPanel();
   JLabel             actionOut = new JLabel();
   JLabel           actionLabel = new JLabel();
   FlowLayout       flowLayout1 = new FlowLayout();
   JTabbedPane     jTabbedPane1 = new JTabbedPane();
   JPanel                keyTab = new JPanel();
   JPanel                qosTab = new JPanel();
   JPanel            contentTab = new JPanel();
   JScrollPane outKeyScrollPane = new JScrollPane();
   JTextArea             outKey = new JTextArea();
   BorderLayout   borderLayout2 = new BorderLayout();
   JScrollPane     jScrollPane4 = new JScrollPane();
   JTextArea             outQoS = new JTextArea();
   BorderLayout   borderLayout3 = new BorderLayout();
   BorderLayout   borderLayout4 = new BorderLayout();
   JScrollPane     jScrollPane5 = new JScrollPane();
   JTextArea         outContent = new JTextArea();
   JPanel               jPanel3 = new JPanel();
   JButton          allowButton = new JButton();
   JButton           denyButton = new JButton();
   JLabel             nameLabel = new JLabel("UserId: ");
   JLabel               outName = new JLabel();

   private boolean               allowed = false;
   private boolean       threadSuspended = false;
   private Object  accessDecisionMonitor = new Object();
   private Thread                sleeper;
      

   /**************************************************************************
    * Construct the frame
    **************************************************************************/
   public PluginGUI() {
      enableEvents(AWTEvent.WINDOW_EVENT_MASK);
      try {
         jbInit();
      }
      catch(Exception e) {
         e.printStackTrace();
      }
   }
   

   /**************************************************************************
    * Component initialization
    **************************************************************************/
   private void jbInit() throws Exception  {
      box1 = Box.createHorizontalBox();
      gridLayout1.setRows(3);
      //setIconImage(Toolkit.getDefaultToolkit().createImage(Frame1.class.getResource("[Your Icon]")));
      contentPane = (JPanel) this.getContentPane();
      contentPane.setLayout(gridLayout1);
      this.setJMenuBar(jMenuBar1);
      this.setResizable(false);
      this.setSize(new Dimension(600, 800));
      this.setTitle("Demo Security Plugin");
      inOutput.setMargin(new Insets(5, 5, 5, 5));
      inOutput.setBorder(BorderFactory.createLoweredBevelBorder());
      inOutput.setSelectedTextColor(Color.darkGray);
      inOutput.setText("");
      inOutput.setToolTipText("encrypted");
      inOutput.setEditable(false);
      inOutput.setLineWrap(true);
      //inOutput.setAutoscrolls(true);
//      inOutput.setFont(new java.awt.Font("SansSerif", 1, 12));
      outOutput.setMargin(new Insets(5, 5, 5, 5));
      outOutput.setBorder(BorderFactory.createLoweredBevelBorder());
      outOutput.setSelectedTextColor(Color.darkGray);
      outOutput.setToolTipText("decrypted");
      outOutput.setEditable(false);
      //outOutput.setAutoscrolls(true);
      //serverImage.setIcon(new ImageIcon(new java.net.URL("file:////home/kleiner/jbproject/gfx_test/classes/gfx_test/aufmacher_ho_.gif")));
      try {
         serverImage.setIcon(new ImageIcon(org.xmlBlaster.authentication.plugins.demo.PluginGUI.class.getResource("aufmacher_ho_.gif")));
      } catch (java.lang.Exception e) {
         System.err.println("PluginGUI: Can't find image 'aufmacher_ho_.gif'");
      }
      jPanel1.setLayout(borderLayout1);
      actionOut.setText("- - -");
      actionLabel.setText("Action: ");
      jPanel2.setLayout(flowLayout1);
      flowLayout1.setAlignment(FlowLayout.LEFT);
      keyTab.setLayout(borderLayout2);
      qosTab.setLayout(borderLayout3);
      contentTab.setLayout(borderLayout4);
      outContent.setEditable(false);
      outQoS.setEditable(false);
      outKey.setEditable(false);
      allowButton.setActionCommand("allow");
      allowButton.setText("allow");
      allowButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(ActionEvent e) {
            allowButton_actionPerformed(e);
         }
      });
      denyButton.setActionCommand("deny");
      denyButton.setText("deny");
      denyButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(ActionEvent e) {
            denyButton_actionPerformed(e);
         }
      });
      contentPane.add(jScrollPane1, null);
      jScrollPane1.getViewport().add(inOutput, null);
      contentPane.add(box1, null);
      box1.add(serverImage, null);
      box1.add(jScrollPane3, null);
      jScrollPane3.getViewport().add(jPanel1, null);
      jPanel1.add(jPanel2, BorderLayout.NORTH);
      jPanel2.add(actionLabel, null);
      jPanel2.add(actionOut, null);
      jPanel2.add(nameLabel, null);
      jPanel2.add(outName, null);
      jPanel1.add(jTabbedPane1, BorderLayout.CENTER);
      jTabbedPane1.add(keyTab, "Key");
      keyTab.add(outKeyScrollPane, BorderLayout.CENTER);
      outKeyScrollPane.getViewport().add(outKey, null);
      jTabbedPane1.add(qosTab, "Quality of Service");
      qosTab.add(jScrollPane4, BorderLayout.CENTER);
      jScrollPane4.getViewport().add(outQoS, null);
      jTabbedPane1.add(contentTab, "Content");
      contentTab.add(jScrollPane5, BorderLayout.CENTER);
      jPanel1.add(jPanel3, BorderLayout.SOUTH);
      jPanel3.add(denyButton, null);
      jPanel3.add(allowButton, null);
      jScrollPane5.getViewport().add(outContent, null);
      contentPane.add(jScrollPane2, null);
      jScrollPane2.getViewport().add(outOutput, null);
      System.out.println("DONE");
   }


   /**************************************************************************
    * Overridden so we can exit when window is closed
    **************************************************************************/
   protected void processWindowEvent(WindowEvent e) {
      super.processWindowEvent(e);
      if (e.getID() == WindowEvent.WINDOW_CLOSING) {
//         System.exit(0);

      }
   }


   /**************************************************************************
    * Call as result of a pressed deny-button to reject a subjects attempt
    * to log in.
    * <br />
    * @param e An ActionEvent
    **************************************************************************/
   void denyButton_actionPerformed(ActionEvent e) {
      allowed = false;
      //if(sleeper!=null) sleeper.resume();// deprecated -> using the following workaround instead
      if (sleeper!=null) {
         synchronized(accessDecisionMonitor) {
             threadSuspended = false;
         }
      }
   }

   
   /**************************************************************************
    * Call as result of a pressed allow-button to grant a subject to log in.
    * <br />
    * @param e An ActionEvent
    **************************************************************************/
   void allowButton_actionPerformed(ActionEvent e) {
      allowed = true;
      //if(sleeper!=null) sleeper.resume();// deprecated -> using the following workaround instead
      if (sleeper!=null) {
         synchronized(accessDecisionMonitor) {
             threadSuspended = false;
         }
      }
   }

   
   /**************************************************************************
    * Returns the result of decision process. Access granted or rejected.
    * <br />
    * @param true: access granted; otherwise false
    **************************************************************************/
   public synchronized boolean getAccessDecision() {
      try {
         sleeper = Thread.currentThread();
         //sleeper.suspend(); // deprecated -> using the following workaround instead
         synchronized(accessDecisionMonitor) {
            threadSuspended = true;
            while (threadSuspended) {
               wait();
            }
         }
      }
      catch (Exception e) {
      }

      return allowed;
   }


   /**************************************************************************
    *
    * <br />
    *
    **************************************************************************/
   public void printName(String name) {
      outName.setText(name);
   }

   /**************************************************************************
    * Displays actions
    * <br />
    * @param key The action type
    **************************************************************************/
   public void printAction(MethodName key) {
      actionOut.setText(key.toString());
      outKey.disable();
      if(key == MethodName.CONNECT) {
         outKey.setText("");
         outKey.setEditable(false);
         outQoS.setText("");
         outQoS.setEditable(false);
         outContent.setText("");
         outContent.setEditable(false);
      } else if(key == MethodName.GET) {
         outKey.setEditable(true);
         outQoS.setEditable(true);
         outContent.setEditable(false);
      } else if(key == MethodName.ERASE) {
         outKey.setEditable(true);
         outQoS.setEditable(true);
         outContent.setEditable(false);
      } else if(key == MethodName.PUBLISH) {
         outKey.setEditable(true);
         outQoS.setEditable(true);
         outContent.setEditable(true);
      } else if(key == MethodName.SUBSCRIBE) {
         outKey.setEditable(true);
         outQoS.setEditable(true);
         outContent.setEditable(false);
      } else if(key == MethodName.UNSUBSCRIBE) {
         outKey.setEditable(true);
         outQoS.setEditable(true);
         outContent.setEditable(false);
      }
   }

   
   /**************************************************************************
    * Displays the key information on screen
    * <br />
    * @param QoS The key
    **************************************************************************/
   public void printKey(String key) {
      outKey.setText(key);
   }

   
   /**************************************************************************
    * Displays the content information on screen
    * <br />
    * @param QoS The content
    **************************************************************************/
   public void printContent(String content) {
      outContent.setText(content);
   }


   /**************************************************************************
    * Displays the qos information on screen
    * <br />
    * @param QoS The qos
    **************************************************************************/
   public void printQoS(String qos) {
      outQoS.setText(qos);
   }

   
   /**************************************************************************
    *
    * <br />
    *
    **************************************************************************/
   public void printInputStream(String xmlString) {
//      inOutput.append(xmlString+"\n\n");
      inOutput.setText(xmlString);
   }


   /**************************************************************************
    *
    * <br />
    *
    **************************************************************************/
   public void printOutputStream(String xmlString) {
//      outOutput.append(xmlString+"\n\n");
      outOutput.setText(xmlString);
   }


   public static void main(String [] args) {
      PluginGUI frame = new PluginGUI();
      //Validate frames that have preset sizes
      //Pack frames that have useful preferred size info, e.g. from their layout
      frame.validate();
      //Center the window
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      Dimension frameSize = frame.getSize();
      if (frameSize.height > screenSize.height) {
         frameSize.height = screenSize.height;
      }
      if (frameSize.width > screenSize.width) {
         frameSize.width = screenSize.width;
      }
      frame.setLocation((screenSize.width - frameSize.width) / 2, (screenSize.height - frameSize.height) / 2);
      frame.setVisible(true);
   }
}
