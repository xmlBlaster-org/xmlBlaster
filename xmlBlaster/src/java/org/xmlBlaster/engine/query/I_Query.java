/*------------------------------------------------------------------------------
Name:      I_Query.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine.query;

import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.QueryQosData;

/**
 * I_Query the interface to implement for all plugins which are doing a query.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public interface I_Query {
   
   /**
    * Makes a query to the specified source object.
    * @param source the object on which to submit the query.
    * @param keyData the key of the query. Can be null (implementation can restrict that)
    * @param qosData the qos of the query, i.e. what has to be queried. Can be null (implementation can restrict that)
    * @return a MsgUnit[] containing all entries which match the query
    * @throws XmlBlasterException if something is wrong in the query (for example
    *         if the type of the object specified as the source is wrong)
    */
   public MsgUnit[] query(Object source, QueryKeyData keyData, QueryQosData qosData) throws XmlBlasterException;
   
}
