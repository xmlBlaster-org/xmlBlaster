/*------------------------------------------------------------------------------
Name:      BasicNode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jmxgui;

import javax.swing.Icon;
import javax.swing.JPanel;

/**
 * Wraps Information about a Node in the TreeView
 * Used to create a TreeView that contains the Nodes with their functionality
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author unascribed
 * @version 1.0
 */
public class BasicNode {

  protected JPanel panel = null;
  protected String m_name;
  protected String className;

  protected Icon expandedIcon = null;
  protected Icon normalIcon = null;

  public BasicNode(JPanel panel, String m_name) {
    this.m_name = m_name;
    this.panel = panel;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public String getClassName() {
    return this.className;
  }

  public void setExpandedIcon(Icon myIcon) {
    this.expandedIcon = myIcon;
  }

  public void setNormalIcon(Icon myIcon) {
    this.normalIcon = myIcon;
  }

  public Icon getExpandedIcon() {
    return this.expandedIcon;
  }

  public Icon getNormalIcon() {
    return this.normalIcon;
  }

  public void setName(String name) {
    this.m_name = name;
  }

  public String getName() {
    return this.m_name;
  }

  public void setPanel(JPanel panel) {
    this.panel = panel;
  }

  public JPanel getPanel() {
    return this.panel;
  }

  public boolean hasPanel() {
    if (this.panel == null)
      return false;
      else
        return true;
  }

  public String toString() {
    return this.m_name;
  }

}