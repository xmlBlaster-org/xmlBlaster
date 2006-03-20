/*------------------------------------------------------------------------------
Name:      I_ObjectStream.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.client.protocol.http.common;

/**
 * I_ObjectStream
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public interface I_ObjectStream {
   final static int STRING = 0;
   final static int HASHTABLE = 1;
   final static int HASHTABLE_ARR = 2;
   final static int VECTOR = 3;
}
