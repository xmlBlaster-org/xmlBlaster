package org.xmlBlaster.util.property;

import org.xmlBlaster.util.property.PropertyChangeEvent;

/**
 * Please implement this interface to receive notification if a property changes. 
 * @author Marcel Ruff
 * @see testsuite.TestProperty
 */
public interface I_PropertyChangeListener
{
    void propertyChanged(PropertyChangeEvent ev);
}
