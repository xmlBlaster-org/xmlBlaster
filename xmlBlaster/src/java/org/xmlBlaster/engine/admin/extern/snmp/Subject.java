/**
 The Subject interface defines the methods that a Subject must implement 
 in order for Observers to add and remove themselves from the Subject. 
 */
package org.xmlBlaster.engine.admin.extern.snmp;

public interface Subject {
      public void addObserver( Observer o );
      public void removeObserver( Observer o );
}

