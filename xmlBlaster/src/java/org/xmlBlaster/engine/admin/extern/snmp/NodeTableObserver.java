/**
  NodeTableObserver adds itself to the integer bag as observer. 
  When NodeTableObserver receives an update, it .... 
 */
package org.xmlBlaster.engine.admin.extern.snmp;

import java.util.*;
import jax.*;

public class NodeTableObserver implements Observer {

      private NodeTableSubject nodeTableSubject;
      private AgentXSession session;
      private NodeEntry nodeEntry;
      private NodeTable nodeTable;
      private Hashtable nodeHashtable;
      private BitSet indexSet;
      private final static int MAXINDX = 100;

      public Integer getIndex(String key) {
          return (Integer)nodeHashtable.get(key);
      }

      public NodeTableObserver( NodeTableSubject nodeTableSubject,
                                AgentXSession session ) {
            this.nodeTableSubject = nodeTableSubject;               
            this.session = session;
            nodeTableSubject.addObserver( this );
            nodeTable = new NodeTable();
            session.addGroup(nodeTable);
            nodeHashtable = new Hashtable();
            indexSet = new BitSet();
            for (int i = 1; i <= MAXINDX; i++) {
                indexSet.set(i);
            }
      }

      public void update( Subject o ) {
            if( o == nodeTableSubject ) {
                  if (nodeHashtable.containsKey(nodeTableSubject.nodeName)) {
                     System.out.println("A node with nodename = " + nodeTableSubject.nodeName + " already exists.");
                  }
                  else {
                      System.out.println("Insert node = " + nodeTableSubject.nodeName);
                      // new nodeIndex
                      int nodeIndex = 1;
                      while (nodeIndex <= MAXINDX && !indexSet.get(nodeIndex)) {
                          nodeIndex++;
                      }

                      if (nodeIndex > MAXINDX) {
                         System.out.println("Error: nodeIndex > " + MAXINDX);
                      }
                      else {
                          // remove nodeIndex from indexSet
                          indexSet.clear(nodeIndex);

                          // insert nodeName and nodeIndex in nodeHashtable
                          nodeHashtable.put(nodeTableSubject.nodeName, new Integer(nodeIndex));
  
                          // insert new nodeEntry in nodeTable
                          nodeEntry = new NodeEntryImpl(nodeIndex, 
                              nodeTableSubject.nodeName,
                              nodeTableSubject.hostname,
                              nodeTableSubject.port, 
                              nodeTableSubject.maxClients, 
                              nodeTableSubject.clientThreshold,
                              nodeTableSubject.errorLogfile,
                              nodeTableSubject.logLevel);
                          nodeTable.addEntry(nodeEntry);
                      }
                  }
            }
      }
}








