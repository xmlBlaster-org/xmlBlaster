/** 
  NodeTableSubject holds onto node entries. 
  The NodeTableSubject also allows Observers to add and remove themselves.
 */
package org.xmlBlaster.engine.admin.extern.snmp;


import java.util.ArrayList;
import java.util.Iterator;

public class NodeTableSubject implements Subject {

      public String nodeName;
      public String hostname;
      public long port;
      public long maxClients;
      public long clientThreshold;
      public String errorLogfile;
      public int logLevel;
      public ArrayList observers = new ArrayList();

      public void addEntry(String nodeNameVal, 
                    String hostnameVal,
                    long portVal, 
                    long maxClientsVal, 
                    long clientThresholdVal, 
                    String errorLogfileVal, 
                    int logLevelVal) {

            nodeName = nodeNameVal;
            hostname = hostnameVal;
            port = portVal;
            maxClients = maxClientsVal;
            clientThreshold = clientThresholdVal;
            errorLogfile = errorLogfileVal;
            logLevel = logLevelVal;
            notifyObservers();
      }
 
      public Integer removeEntry( int index ) {
            return null;
      }

      public void addObserver( Observer o ) {
            observers.add( o );
      }

      public void removeObserver( Observer o ) {
            observers.remove( o );
      }

      private void notifyObservers() {
            // loop through and notify each observer
            Iterator i = observers.iterator();
            while( i.hasNext() ) {
                  Observer o = ( Observer ) i.next();
                  o.update( this );
            }
      }
}










