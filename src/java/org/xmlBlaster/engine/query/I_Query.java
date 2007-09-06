/*------------------------------------------------------------------------------
Name:      I_Query.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine.query;

import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * I_Query the interface to implement for all plugins which are doing a query.
 * 
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public interface I_Query {
   
   /**
    * Makes a query to the specified source object.
    * @param source the object on which to submit the query.
    * @param query the qos of the query, i.e. what has to be queried. Can be null (implementation can restrict that)
    * @return a MsgUnit[] containing all entries which match the query
    * @throws XmlBlasterException if something is wrong in the query (for example
    *         if the type of the object specified as the source is wrong)
    */
   public MsgUnit[] query(Object source, String query) throws XmlBlasterException;
   
}
