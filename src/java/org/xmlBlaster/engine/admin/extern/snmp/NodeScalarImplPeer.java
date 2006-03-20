package org.xmlBlaster.engine.admin.extern.snmp;
 
import java.util.*;

/** 
 *  NodeScalarImplPeer is the implementation side of a bridge pattern.
 *  Implements the methods, which are called by NodeScalarImpl.
 *  @version @VERSION@
 *  @author Udo Thalmann
 */
public class NodeScalarImplPeer {

    public NodeScalarImplPeer() {
    }

    /**
     * Provides the actual number of nodes in the nodeTable.
     * @return long number of nodes in the node Table.
     */
    public long get_numNodes() {
        System.out.println("NodeScalarImplPeer: get_numNodes() HEEEELLLLLO");
        Random r = new Random();
        return 1 + Math.abs(r.nextLong()) % 100;
    }
}







