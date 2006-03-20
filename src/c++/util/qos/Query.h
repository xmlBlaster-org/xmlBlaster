/*------------------------------------------------------------------------------
Name:      Query.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding a query std::string and a prepared query object. 
------------------------------------------------------------------------------*/

/**
 * Little container which holds a &lt;filter> query std::string. 
 * For better performance you can preparse the query std::string and
 * store your query object here as well (see example in GnuRegexFilter).
 * @see org.xmlBlaster.engine.mime.regex.GnuRegexFilter
 */

#ifndef _UTIL_QOS_QUERY_H
#define _UTIL_QOS_QUERY_H

#include <util/xmlBlasterDef.h>
#include <string>



namespace org { namespace xmlBlaster { namespace util { namespace qos {

class Dll_Export Query 
{
   const std::string ME; // = "Query";
   org::xmlBlaster::util::Global&      global_;
   std::string       query_;
   void*        preparedQuery_; // = null;

public:
   Query(org::xmlBlaster::util::Global& global, const std::string& query="");
   Query(const Query& query);
   Query& operator =(const Query& query);
   std::string getQuery();
   void setPreparedQuery(void* preparedQuery, size_t size);
   void* getPreparedQuery();
   std::string toString();
};

}}}} // namespace

#endif
