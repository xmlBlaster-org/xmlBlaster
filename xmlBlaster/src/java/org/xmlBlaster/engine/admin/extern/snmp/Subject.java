package org.xmlBlaster.engine.admin.extern.snmp;

/**
 * The Subject interface defines the methods that a Subject must implement 
 * in order for Observers to add and remove themselves to/from the Subject.
 * @version @VERSION@
 * @author Udo Thalmann
 */
public interface Subject {
    /**
     * addObserver must be implemented by a concrete subject,
     * in order for observers to add themselves to the subject.
     * @param Observer o: observer to be added.
     */
    public void addObserver( Observer o );

    /**
     * removeObserver must be implemented by a concrete subject,
     * in order for observers to remove themselves from the subject.
     * @param Observer o: observer to be removed.
     */
    public void removeObserver( Observer o );

}

