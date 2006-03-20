package org.xmlBlaster.jmxgui;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.tree.*;
import javax.swing.event.*;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;

public class MainFrame extends JFrame {

  private final String strInfo = "XmlBlaster Management GUI";
  protected DataModel myModel = null;
  protected JTree m_tree = null;
  protected DefaultTreeModel m_model = null;
  protected JTextField m_display = null;

  private JScrollPane s = new JScrollPane();
  protected JPanel PanelContent = new JPanel();
  private JPanel PanelLeft = new JPanel();
  private GridLayout gridLayout1 = new GridLayout();
  private JTree jTree1 = new JTree();
  private JPanel jPanel1 = new JPanel();

   private static Logger log = Logger.getLogger(MainFrame.class.getName());
  private Global glob = null;
  private final String ME = "MainFrame";
//  private ConnectorClient connectorClient = null;
  private JMenuBar menuBar = new JMenuBar();
  private JMenu fileMenu = new JMenu();
  private JMenu helpMenu = new JMenu();
  private JMenuItem exitItem = new JMenuItem();
  private JMenuItem aboutMenuItem = new JMenuItem();

  private GridBagLayout gbl = new GridBagLayout();

  public MainFrame(Global glob) {
    this.glob = glob;

    try {
      jbInit();
    }
    catch(Exception e) {
      e.printStackTrace();
    }
  }


  public void removePanel() {
    this.PanelContent.removeAll();
  }

  private void jbInit() throws Exception {

    try {
      this.setTitle("xmlBlaster");
      this.exitItem.setText("Exit");
      this.exitItem.setMnemonic('e');
      this.fileMenu.setText("File");
      this.fileMenu.setMnemonic('f');

      this.exitItem.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(ActionEvent e) {
            exit();
        }
      }
      );
      this.helpMenu.setText("?");
      this.aboutMenuItem.setText("About");

      this.helpMenu.add(aboutMenuItem);
      this.setJMenuBar(menuBar);
      this.fileMenu.add(exitItem);
      this.menuBar.add(fileMenu);
      this.menuBar.add(helpMenu);

      // Associate Action to a new item
       aboutMenuItem.addActionListener(
          new java.awt.event.ActionListener() {
              public void actionPerformed(ActionEvent e) {
                          aboutMenuItem_actionPerformed(e);
              }
          });

      log.info("jbinit() called");
      //placing the window on screen
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      Dimension labelSize = new Dimension(screenSize.height/3, screenSize.width/3);
      this.setSize(new Dimension(labelSize));
      this.setLocation(screenSize.width / 2 - (labelSize.width /2 ), screenSize.height /2 - (labelSize.height /2) ) ;

      java.net.URL oUrl;
      oUrl = this.getClass().getResource("AppIcon.gif");
      Image img;
      img = java.awt.Toolkit.getDefaultToolkit().getImage(oUrl);
      if(img != null)
      {
        this.setIconImage(img);
        log.info("Image found " + img.toString());
      }
      else
      {
        log.warning("AppIcon.gif not found");
      }

      log.info("Trying to build model");
      try {
        myModel = new DataModel(glob);
      }
      catch (Exception ex) {
        JOptionPane.showMessageDialog(this, "Error when connecting to server!", "Error", JOptionPane.WARNING_MESSAGE);
        ex.printStackTrace();
      }
      m_model = new DefaultTreeModel(myModel.getTree());
      m_tree = new JTree(m_model);


      DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
      TreeCellRenderer myRenderer = new IconCellRenderer();
      m_tree.setCellRenderer(myRenderer);


      m_tree.addTreeSelectionListener(new MyExpansionListener(this, glob));


      m_tree.setShowsRootHandles(true);
      m_tree.setEditable(false);

      m_tree.addTreeSelectionListener(new IdSelectionListener(this));

      m_display = new JTextField();
      m_display.setEditable(false);

      getContentPane().add(m_display, BorderLayout.SOUTH);


    /**
     * Closing event
     */
      WindowListener wndCloser = new WindowAdapter() {
        public void windowClosing(WindowEvent e) {
          exit();
        }
      };

      addWindowListener(wndCloser);
      m_tree.setAutoscrolls(true);
      m_tree.setScrollsOnExpand(true);
      s.getViewport().add(m_tree);
      
      PanelLeft.add(s, BorderLayout.CENTER);
      this.setResizable(true);

      JSplitPane sp = new JSplitPane();

      PanelContent.setLayout(gbl);
      PanelContent.add(jPanel1, new GridBagConstraints(0,0,1,1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0,0,0,0),0,0));

      sp.setLeftComponent(new JScrollPane(PanelLeft));
      sp.setRightComponent(PanelContent);
      this.getContentPane().add(sp);

      setVisible(true);
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public void exit() {
    Object[] options = {"Yes","No"};
    int n = JOptionPane.showOptionDialog(null,
        "Really exit Programm?",
        "Question",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.QUESTION_MESSAGE,
        null,
        options,
        options[0]);
    if (n == JOptionPane.YES_OPTION) {
      log.info("Exit handeled by WindowManager...");
      log.info("Logging out from XmlBlaster..");
//              connectorClient.logout();
       System.exit(0);
    }
  }

  /** Action handler for About menu */
  void aboutMenuItem_actionPerformed(ActionEvent e) {
      try {
          System.out.println("Aboutmenu..");
          JOptionPane.showMessageDialog(this,strInfo, "Info", JOptionPane.INFORMATION_MESSAGE);
      }
      catch (Throwable te) {
          System.out.println("Exception by about >>> " + te);
      }
      return;
  }



}
