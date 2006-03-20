/*------------------------------------------------------------------------------
Name:      I_StatusChangeListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.jms;

/**
 * I_StatusChangeListener Listens to changes of status of a particular object. 
 * Statuses are int values defined as static constant members here.
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
interface I_StatusChangeListener {
   
   static final int CLOSED = 0;
   static final int RUNNING = 0;

   void statusPreChanged(String id, int oldStatus, int newStatus);
   void statusPostChanged(String id, int oldStatus, int newStatus);
   
}
