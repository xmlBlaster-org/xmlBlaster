/** 
 * The Observer interface lists the methods that an Observer must implement 
 * so that a Subject can send an update notification to the Observer.
 *  @version @VERSION@
 *  @author Udo Thalmann
 */
package org.xmlBlaster.engine.admin.extern.snmp;

public interface Observer {
    public void update( Subject o );
}

