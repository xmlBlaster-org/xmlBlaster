/*------------------------------------------------------------------------------
Name:      IconCellRenderer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jmxgui;

import javax.swing.*;
import java.awt.*;
import javax.swing.tree.*;

/**
 * IconCellRenderer that is responsible for the appearance of the Tree-View
 */
public class IconCellRenderer extends JLabel implements TreeCellRenderer {

  protected Color m_textSelectionColor;
   protected Color m_textNonSelectionColor;
   protected Color m_bkSelectionColor;
   protected Color m_bkNonSelectionColor;
   protected Color m_borderSelectionColor;

   protected boolean m_selected;

   public IconCellRenderer() {

     super();
     m_textSelectionColor = UIManager.getColor(
       "Tree.selectionForeground");
     m_textNonSelectionColor = UIManager.getColor(
       "Tree.textForeground");
     m_bkSelectionColor = UIManager.getColor(
       "Tree.selectionBackground");
     m_bkNonSelectionColor = UIManager.getColor(
       "Tree.textBackground");
     m_borderSelectionColor = UIManager.getColor(
       "Tree.selectionBorderColor");
     setOpaque(false);
   }

   public Component getTreeCellRendererComponent(JTree tree,
    Object value, boolean sel, boolean expanded, boolean leaf,
    int row, boolean hasFocus)
   {
     DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
     Object obj = node.getUserObject();
     setText(obj.toString());
     if (obj instanceof Boolean)
       setText("Retrieving data...");

     if (obj instanceof BasicNode) {
       BasicNode idata = (BasicNode)obj;
       if (expanded) {
         setIcon(idata.getExpandedIcon());
       }
       else {
         setIcon(idata.getNormalIcon());
       }
     }
     else {
//       setIcon(null);
     }
     setFont(tree.getFont());
     setForeground(sel ? m_textSelectionColor :
       m_textNonSelectionColor);
     setBackground(sel ? m_bkSelectionColor :
       m_bkNonSelectionColor);
     m_selected = sel;
     return this;
   }

   public void paintComponent(Graphics g) {
     Color bColor = getBackground();
     Icon icon = getIcon();

     g.setColor(bColor);
     int offset = 0;
     if(icon != null && getText() != null)
       offset = (icon.getIconWidth() + getIconTextGap());
     g.fillRect(offset, 0, getWidth() - 1 - offset,
       getHeight() - 1);

     if (m_selected) {
       g.setColor(m_borderSelectionColor);
       g.drawRect(offset, 0, getWidth()-1-offset, getHeight()-1);
     }
     super.paintComponent(g);
   }

}