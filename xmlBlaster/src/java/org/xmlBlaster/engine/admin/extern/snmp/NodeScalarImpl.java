/*
 * This Java file has been generated by smidump 0.3.1. It
 * is intended to be edited by the application programmer and
 * to be used within a Java AgentX sub-agent environment.
 *
 */
package org.xmlBlaster.engine.admin.extern.snmp;


import java.util.Vector;
import java.util.Enumeration;
import jax.AgentXOID;
import jax.AgentXSetPhase;
import jax.AgentXResponsePDU;

/**
 *  This class extends the Java AgentX (JAX) implementation of
 *  the scalar group nodeScalar defined in XMLBLASTER-MIB.
 *  NodeScalarImpl is the interface side of a bridge pattern.
 *  Contains a reference to the implementation side of the bridge pattern (= NodeScalarImplPeer).
 *  Implements its methods by forwarding its calls to NodeScalarImplPeer.
 *  
 *  @version @VERSION@
 *  @author Udo Thalmann
 */
public class NodeScalarImpl extends NodeScalar
{

    private NodeScalarImplPeer nodeScalarImplPeer;
 
    /**
     * Builds a reference to NodeScalarImplPeer, which implements NodeScalarImpl methods.
     */
    public NodeScalarImpl()
    {
        super();
        nodeScalarImplPeer = new NodeScalarImplPeer();
    }

    /**
     * Forwards the call to nodeScalarImplPeer.get_numNodes().
     * @return NumNodes actual number of nodes in nodeTable.
     */
    public long get_numNodes()
    {
        numNodes = nodeScalarImplPeer.get_numNodes();
        return numNodes;
    }

}
















