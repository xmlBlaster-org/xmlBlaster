/*
 * This Java file has been generated by smidump 0.3.1. Do not edit!
 * It is intended to be used within a Java AgentX sub-agent environment.
 *
 * $Id$
 */
package org.xmlBlaster.engine.admin.extern.snmp;

import jax.AgentXOID;
import jax.AgentXVarBind;
import jax.AgentXNotification;
import java.util.Vector;

public class SessionTableThresholdOverflow extends AgentXNotification
{

    private final static long[] sessionTableThresholdOverflow_OID = {1, 3, 6, 1, 4, 1, 11662, 2, 3};
    private static AgentXVarBind snmpTrapOID_VarBind =
        new AgentXVarBind(snmpTrapOID_OID,
                          AgentXVarBind.OBJECTIDENTIFIER,
                          new AgentXOID(sessionTableThresholdOverflow_OID));

    private final static long[] OID1 = {1, 3, 6, 1, 4, 1, 11662, 1, 4, 1, 2};
    private final AgentXOID clientName_OID = new AgentXOID(OID1);
    private final static long[] OID2 = {1, 3, 6, 1, 4, 1, 11662, 1, 4, 1, 9};
    private final AgentXOID numSessions_OID = new AgentXOID(OID2);
    private final static long[] OID3 = {1, 3, 6, 1, 4, 1, 11662, 1, 4, 1, 10};
    private final AgentXOID maxSessions_OID = new AgentXOID(OID3);
    private final static long[] OID4 = {1, 3, 6, 1, 4, 1, 11662, 1, 4, 1, 11};
    private final AgentXOID sessionThreshold_OID = new AgentXOID(OID4);


    public SessionTableThresholdOverflow(ClientEntry clientEntry_1, ClientEntry clientEntry_2, ClientEntry clientEntry_3, ClientEntry clientEntry_4) {
        AgentXOID oid;
        AgentXVarBind varBind;

        // add the snmpTrapOID object
        varBindList.addElement(snmpTrapOID_VarBind);

        // add the clientName columnar object of clientEntry_1
        oid = clientName_OID;
        oid.appendImplied(clientEntry_1.getInstance());
        varBind = new AgentXVarBind(oid,
                                    AgentXVarBind.OCTETSTRING,
                                    clientEntry_1.get_clientName());
        varBindList.addElement(varBind);

        // add the numSessions columnar object of clientEntry_2
        oid = numSessions_OID;
        oid.appendImplied(clientEntry_2.getInstance());
        varBind = new AgentXVarBind(oid,
                                    AgentXVarBind.GAUGE32,
                                    clientEntry_2.get_numSessions());
        varBindList.addElement(varBind);

        // add the maxSessions columnar object of clientEntry_3
        oid = maxSessions_OID;
        oid.appendImplied(clientEntry_3.getInstance());
        varBind = new AgentXVarBind(oid,
                                    AgentXVarBind.GAUGE32,
                                    clientEntry_3.get_maxSessions());
        varBindList.addElement(varBind);

        // add the sessionThreshold columnar object of clientEntry_4
        oid = sessionThreshold_OID;
        oid.appendImplied(clientEntry_4.getInstance());
        varBind = new AgentXVarBind(oid,
                                    AgentXVarBind.GAUGE32,
                                    clientEntry_4.get_sessionThreshold());
        varBindList.addElement(varBind);
    }

    public Vector getVarBindList() {
        return varBindList;
    }

}

