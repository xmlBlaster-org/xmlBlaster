package org.xmlBlaster.contrib.dbwriter;

import java.io.IOException;
import java.sql.PreparedStatement;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwriter.info.SqlColumn;
import org.xmlBlaster.util.qos.ClientProperty;

public interface I_StatementInjector {

   void init(I_Info info);
   void shutdown();
   boolean insertIntoStatement(PreparedStatement st, int pos, ClientProperty prop, SqlColumn col, int type, boolean isNull) throws IOException;

}
