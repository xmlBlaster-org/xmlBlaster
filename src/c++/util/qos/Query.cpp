/*------------------------------------------------------------------------------
Name:      Query.cpp
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

# include <util/qos/Query.h>
#include <util/Global.h>

using namespace std;

namespace org { namespace xmlBlaster { namespace util { namespace qos {

Query::Query(Global& global, const string& query)
   : ME("Query"), global_(global)
{
   query_ = query;
   preparedQuery_ = NULL;
}

Query::Query(const Query& query)
   : ME(query.ME), global_(query.global_)
{
   preparedQuery_ = query.preparedQuery_;
   query_         = query.query_;
}

Query& Query::operator =(const Query& query)
{
   preparedQuery_ = query.preparedQuery_;
   query_         = query.query_;
   return *this;
}
 
string Query::getQuery()
{
   return query_;
}

void Query::setPreparedQuery(void* preparedQuery, size_t /*size*/)
{
   // here a copy should be done ...
   preparedQuery_ = preparedQuery;
}

void* Query::getPreparedQuery()
{
   return preparedQuery_;
}

string Query::toString()
{
   return query_;
}

}}}} // namespace



