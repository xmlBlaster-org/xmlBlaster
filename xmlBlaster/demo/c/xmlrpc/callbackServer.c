/* A simple standalone XML-RPC server written in C. */

#include <stdio.h>

#include <xmlrpc.h>
#include <xmlrpc_abyss.h>
#include <curses.h> /* beep() */

/*
 * Update message arrives here.
 * This is the callback invoked from xmlBlaster
 */
xmlrpc_value *update (xmlrpc_env *env, xmlrpc_value *param_array, void *user_data)
{
   char *loginName=NULL, *key=NULL, *qos=NULL;
   unsigned char *content=NULL; // binary data, the message content
   size_t len;
   xmlrpc_value *retVal = NULL;
   char *retData = NULL;

   if (env->fault_occurred)
      printf("callbackServer: Entering update(), ERROR message arrives ...\n");
   else
      printf("callbackServer: Entering update(), message arrives ...\n");

   xmlrpc_parse_value(env, param_array, "(ss6s*)", &loginName, &key, &content, &len, &qos);
   if (env->fault_occurred) {
      fprintf(stderr, "callbackServer: Error when parsing message ... %d, %d, %s\n",
              env->fault_occurred, env->fault_code, env->fault_string);
      xmlrpc_env_clean(env);
      return xmlrpc_build_value(env, "s", "<qos><state>ERROR</state></qos>");
   }

   printf("callbackServer: Got message\nloginName=%s\nkey=%s\ncontent=%s\nqos=%s",
                    loginName, key, content, qos);

   /* Return our result. */
   retData = "<qos><state>OK</state></qos>";
   retVal = xmlrpc_build_value(env, "s#", retData, strlen(retData));
   printf("callbackServer: Return created\n");
   return retVal;
}

int main (int argc, char **argv)
{
    if (argc != 2) {
        fprintf(stderr, "Usage: servertest abyss.conf\n");
        exit(1);
    }

    xmlrpc_server_abyss_init(XMLRPC_SERVER_ABYSS_NO_FLAGS, argv[1]);
    xmlrpc_server_abyss_add_method("update", &update, NULL);

    printf("server: switching to background.\n");
    xmlrpc_server_abyss_run();

    /* We never reach this point. */
    return 0;
}
