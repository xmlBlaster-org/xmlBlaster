/*------------------------------------------------------------------------------
Name:      Query.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding a query string and a prepared query object. 
------------------------------------------------------------------------------*/

/**
 * Little container which holds a &lt;filter> query string. 
 * For better performance you can preparse the query string and
 * store your query object here as well (see example in GnuRegexFilter).
 * @see org.xmlBlaster.engine.mime.regex.GnuRegexFilter
 */

#include <util/xmlBlasterDef.h>
#include <string>

namespace org { namespace xmlBlaster { namespace util { namespace mime {

class Dll_Export Query 
{
   const string ME; // = "Query";
   Global&      global_;
   string       query_;
   void*        preparedQuery_; // = null;

public:
   Query(Global& global, const string& query="");
   Query(const Query& query);
   Query& operator =(const Query& query);
   string getQuery();
   void setPreparedQuery(void* preparedQuery, size_t size);
   void* getPreparedQuery();
   string toString();
};

}}}} // namespace


