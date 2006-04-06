/*------------------------------------------------------------------------------
Name:      SimpleReaderGui.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Author:    Wolfgang Kleinertz, Thomas Bodemer
------------------------------------------------------------------------------*/
package javaclients.simplereader;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;

import java.awt.event.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.event.*;

/**
 * Usage:
 * <pre>
 *  java javaclients.simplereader.SimpleReaderGui 
 *  java javaclients.simplereader.SimpleReaderGui -xpath "//key[starts-with(@oid,'com.')]" 
 * </pre>
 */
public class SimpleReaderGui extends JFrame implements I_Callback {
   /**
    * 
    */
   private static final long serialVersionUID = 8368002446068669824L;

   private static final String ME = "SimpleReaderGui";

   private static final String USR_LOGIN  = ME;
   private static final String USR_PASSWD = "secret";

   private ImageIcon image;

   private I_XmlBlasterAccess xmlBlaster;
   private SubscribeReturnQos subscribeReturnQos;

   private DefaultListModel listModel = new DefaultListModel();
   private JList jList1 = new JList();
   private JScrollPane jScrollPane1 = new JScrollPane();
   private JSplitPane jSplitPane1 = new JSplitPane();
   private JPanel jPanel1 = new JPanel();
   private JPanel jPanel2 = new JPanel();
   private BorderLayout borderLayout1 = new BorderLayout();
   private BorderLayout borderLayout2 = new BorderLayout();
   private JScrollPane jScrollPane2 = new JScrollPane();
   private JTextArea jTextArea1 = new JTextArea();
   private JPanel jPanel4 = new JPanel();
   private JTextField jTextField1 = new JTextField();
   private JPanel jPanel3 = new JPanel();
   private JButton jButton1 = new JButton();
   private BorderLayout borderLayout3 = new BorderLayout();
   private JButton jButton2 = new JButton();
   private BorderLayout borderLayout4 = new BorderLayout();


   public SimpleReaderGui(I_XmlBlasterAccess _xmlBlaster) throws Exception{
      this.xmlBlaster = _xmlBlaster;
      try {
        jbInit();
      }
      catch(Exception e) {
        e.printStackTrace();
      }

      // set the application icon
      java.net.URL oUrl;
      oUrl = this.getClass().getResource("AppIcon.gif");
      Image img = null;
      if (oUrl != null)
         img = java.awt.Toolkit.getDefaultToolkit().getImage(oUrl);
      if(img != null) {
        this.setIconImage(img);
        // System.out.println(img.toString());
      } else {
        System.out.println("AppIcon.gif not found");
      } // -- if img != null
      this.setTitle(ME);
   }

   public static void main(String[] args) {
      SimpleReaderGui srGui = null;
      try {
         Global glob = new Global(args);
         I_XmlBlasterAccess xmlBlaster = glob.getXmlBlasterAccess();
         srGui = new SimpleReaderGui(xmlBlaster);
         srGui.loadImage();
         ConnectQos qos = new ConnectQos(glob, USR_LOGIN, USR_PASSWD);
         xmlBlaster.connect(qos, srGui);
         srGui.setTitle(ME + "  " + xmlBlaster.getSessionName().getNodeIdStr() + "  <no subscription>");
      }
      catch(Exception ex) {
         log_error( ME, ex.toString(), "");
         ex.printStackTrace();
      }
      if( srGui != null ) {
         srGui.setSize(640,480);
         srGui.show();
      }
   }

   public String update(String secretCallbackSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException
   {
      MessageWrapper messageWrapper = new MessageWrapper(secretCallbackSessionId, updateKey, content, updateQos);
      listModel.addElement(messageWrapper);
      System.out.println("Key: "+updateKey.toXml()+" >>> Content: "+new String(content)+" >>> ---");
      return "";
   }


  private void jbInit() throws Exception {
     jList1.setFixedCellHeight(15);
     jList1.setModel(listModel);
     jList1.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
     jList1.setMaximumSize(new Dimension(1000, 1000));
     jList1.setMinimumSize(new Dimension(100, 10));
     jList1.setCellRenderer(new MyCellRenderer());

     /*
     this.setDefaultCloseOperation(EXIT_ON_CLOSE);
     EXIT_ON_CLOSE is not working with JDK 1.2.2.
     EXIT_ON_CLOSE should be defined in the interface javax.swing.WindowConstants
     but it isn't.
     The value is set to 3, therefore we set it hard here.
     */
     this.setDefaultCloseOperation(3);
     jList1.addListSelectionListener(new ListSelectionListener() {
         public void valueChanged(ListSelectionEvent evt) {
            JList source = (JList) evt.getSource();
            MessageWrapper selection = (MessageWrapper) source.getSelectedValue();
            if (selection != null) {
               String secretCallbackSessionId = selection.getSecretCallbackSessionId();
               UpdateKey updateKey = selection.getUpdateKey();
               byte[] content = selection.getContent();
               UpdateQos updateQos = selection.getUpdateQos();

               String text = (
                  new StringBuffer()
                     .append(" - - - secretCallbackSessionId: - - -\n")
                     .append(secretCallbackSessionId)
                     .append("\n - - - updateKey: - - -")
                     .append(updateKey.toXml())
                     .append("\n - - - content: - - -\n")
                     .append(new String(content))
                     .append("\n - - - updateQos: - - -")
                     .append(updateQos.toXml()))
                     .append("\n - - - end - - -\n")
                     .toString();
               jTextArea1.setText(text);
            } else {
               jTextArea1.setText("");
            }
         }
      });
      jPanel1.setLayout(borderLayout1);
      jPanel2.setLayout(borderLayout2);
      jTextArea1.setMinimumSize(new Dimension(20, 23));
      jTextArea1.setEditable(false);
      jPanel1.setMaximumSize(new Dimension(400, 300));
      jPanel1.setMinimumSize(new Dimension(200, 300));
      jPanel1.setPreferredSize(new Dimension(200, 300));
      jScrollPane2.setAutoscrolls(true);
      jScrollPane2.setPreferredSize(new Dimension(300, 26));
      jSplitPane1.setMinimumSize(new Dimension(234, 400));
      jSplitPane1.setPreferredSize(new Dimension(512, 400));
      jTextField1.setText(this.xmlBlaster.getGlobal().getProperty().get("xpath", "//key"));
      jPanel3.setLayout(borderLayout4);
      jPanel3.setMaximumSize(new Dimension(120, 40));
      jPanel3.setMinimumSize(new Dimension(120, 40));
      jPanel3.setPreferredSize(new Dimension(120, 40));
      jButton1.setSelected(true);
      jButton1.setText("subscribe");
      jButton1.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(ActionEvent e) {
           jButton1_actionPerformed(e);
        }
      });
      jPanel4.setLayout(borderLayout3);
      jPanel4.setMaximumSize(new Dimension(32767, 70));
      jPanel4.setMinimumSize(new Dimension(120, 70));
      jPanel4.setPreferredSize(new Dimension(120, 70));
      jScrollPane1.setAutoscrolls(true);
      jButton2.setText("clear");
      jButton2.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(ActionEvent e) {
            jButton2_actionPerformed(e);
         }
      });
      jPanel1.add(jScrollPane1,  BorderLayout.CENTER);
      jScrollPane1.getViewport().add(jList1);
      jPanel1.add(jPanel4, BorderLayout.NORTH);
      jPanel3.add(jButton1,  BorderLayout.WEST);
      jPanel3.add(jButton2, BorderLayout.CENTER);
      jPanel4.add(jPanel3, BorderLayout.CENTER);
      jPanel4.add(jTextField1,  BorderLayout.NORTH);
      jSplitPane1.add(jPanel2, JSplitPane.RIGHT);
      jPanel2.add(jScrollPane2,  BorderLayout.CENTER);
      jSplitPane1.add(jPanel1, JSplitPane.LEFT);
      jScrollPane2.getViewport().add(jTextArea1, null);
      this.getContentPane().add(jSplitPane1, BorderLayout.CENTER);
  }

   void jButton1_actionPerformed(ActionEvent e) {
      String text = jTextField1.getText();
      this.setTitle(ME + "  " + xmlBlaster.getSessionName().getNodeIdStr() + "  " + text);
      
      if (this.subscribeReturnQos != null) {
         try {
            UnSubscribeKey key = new UnSubscribeKey(xmlBlaster.getGlobal(), this.subscribeReturnQos.getSubscriptionId());
            UnSubscribeQos qos = new UnSubscribeQos(xmlBlaster.getGlobal() );
            xmlBlaster.unSubscribe(key.toXml(), qos.toXml());
            System.out.println(ME + " unSubscribe from " + this.subscribeReturnQos.getSubscriptionId());
            this.subscribeReturnQos = null;
         }
         catch( Throwable ex ) {
            System.err.println("error-error-error-error >>>"+ex.toString());
            System.out.println(ME + " " + ex.getMessage());
            ex.printStackTrace();
         }
      }

      try {
         SubscribeKey key = new SubscribeKey(xmlBlaster.getGlobal(), text, "XPATH");
         SubscribeQos qos = new SubscribeQos(xmlBlaster.getGlobal() );
         this.subscribeReturnQos = xmlBlaster.subscribe(key.toXml(), qos.toXml());
         System.out.println(ME + " subscribe on " + text + "  ->  " + this.subscribeReturnQos.getSubscriptionId());
      }
      catch( Exception ex ) {
         System.err.println("error-error-error-error >>>"+ex.toString());
         System.out.println(ME + " " + ex.getMessage());
         ex.printStackTrace();
      }
   }

   void jButton2_actionPerformed(ActionEvent ae) {
      try {
         listModel.clear();
      } catch (Exception e) {
         System.err.println("error-error-error-error >>>"+e.toString());
         System.out.println(ME + " " + e.getMessage());
         e.printStackTrace();
      }
   }

   public static void log_error(String ME, String text1, String text2) {
      System.err.println("ME:" + ME + "text:" + text1 +  text2);
   }


   public void loadImage() {
      try {
         String filename = "red.gif";
         Image img = Toolkit.getDefaultToolkit().createImage(getClass().getResource(filename));
         image = new ImageIcon( img );
      }
      catch (Exception ex) {
         System.err.println("error-error-error-error >>>"+ex.toString());
         System.out.println(ME + " " + ex.getMessage());
         ex.printStackTrace();
      }
   }

   class MyCellRenderer extends DefaultListCellRenderer {
      /**
       * 
       */
      private static final long serialVersionUID = 5678672570993331767L;

      public Component getListCellRendererComponent(
          JList list,
          Object value,
          int index,
          boolean isSelected,
          boolean cellHasFocus)
      {
          this.setComponentOrientation(list.getComponentOrientation());
     if (isSelected) {
         this.setBackground(list.getSelectionBackground());
         this.setForeground(list.getSelectionForeground());
     }
     else {
         this.setBackground(list.getBackground());
         this.setForeground(list.getForeground());
     }

     if (value instanceof Icon) {
         setIcon((Icon)value);
         setText("");
     }
     else {
         setIcon(image);
         setText((value == null) ? "" : ( ((MessageWrapper) value).getUpdateKey().getOid()));
     }

     this.setEnabled(list.isEnabled());
     this.setFont(list.getFont());
     setBorder((cellHasFocus) ? UIManager.getBorder("List.focusCellHighlightBorder") : noFocusBorder);

     return this;
      }
   }


} // -- class

// -- file
