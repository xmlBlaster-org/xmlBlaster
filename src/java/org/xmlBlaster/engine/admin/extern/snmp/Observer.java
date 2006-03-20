
package org.xmlBlaster.engine.admin.extern.snmp;

/** 
 * The Observer interface lists the methods that an Observer must implement 
 * so that a Subject can send an update notification to the Observer.
 * @version @VERSION@
 * @author Udo Thalmann
 */
public interface Observer {

    /**
     * update must be implemented by a concrete observer.
     * @param Subject o: sends an update notification to the observer. 
     */
    public void update( Subject o );
}






