/*------------------------------------------------------------------------------
Name:      callbackServer.c

Project:   xmlBlaster.org

Comment:   Example how to receive asynchronous callback messages from xmlBlaster
           with C and XmlRpc (see README)
           See http://xmlrpc-c.sourceforge.net/

Author:    xmlBlaster@marcelruff.info

Compile:   Read xmlrpc-c/doc/overview.txt
           SERVER_CFLAGS=`xmlrpc-c-config abyss-server --cflags`
           SERVER_LIBS=`xmlrpc-c-config abyss-server --libs`
           gcc $SERVER_CFLAGS -o callbackServer callbackServer.c $SERVER_LIBS -Wall

Invoke:    callbackServer <pathToXmlrpcConf>abyss.conf
           Please edit abyss.conf before starting,
           e.g. set Port 8081
------------------------------------------------------------------------------*/
#include <stdio.h>
#include <string.h>
#include <xmlrpc.h>
#include <xmlrpc_abyss.h>

xmlrpc_value *update (xmlrpc_env *env, xmlrpc_value *param_array, void *user_data);
xmlrpc_value *ping (xmlrpc_env *env, xmlrpc_value *param_array, void *user_data);


/**
 * Starts XmlRpc Webserver as configured in abyss.conf
 */
int main (int argc, char **argv)
{
    if (argc != 2) {
        fprintf(stderr, "Usage: servertest abyss.conf\n");
        exit(1);
    }

    xmlrpc_server_abyss_init(XMLRPC_SERVER_ABYSS_NO_FLAGS, argv[1]);
    xmlrpc_server_abyss_add_method("update", &update, NULL);
    xmlrpc_server_abyss_add_method("ping", &ping, NULL);

    printf("server: switching to background.\n");
    xmlrpc_server_abyss_run();

    /* We never reach this point. */
    return 0;
}


/**
 * Update message arrives here.
 * This is the callback invoked from xmlBlaster
 */
xmlrpc_value *update (xmlrpc_env *env, xmlrpc_value *param_array, void *user_data)
{
   char *cbSessionId=NULL;      // A unique ID, as specified by the subscriber (we can check it to trust xmlBlaster)
   char *key=NULL;              // The message meta information
   char *qos=NULL;              // Some Quality of Service information
   unsigned char *content=NULL; // binary data, the message content
   size_t len;
   xmlrpc_value *retVal = NULL;
   char *retData = NULL;

   {  // print info ...
      printf("\n\n-------------------------------------------\n");
      if (env->fault_occurred)
         printf("callbackServer: Entering update(), ERROR message arrives ...\n");
      else
         printf("callbackServer: Entering update(), message arrives ...\n\n");
   }

   // Parse the received message ...
   xmlrpc_parse_value(env, param_array, "(ss6s*)", &cbSessionId, &key, &content, &len, &qos);
   if (env->fault_occurred) {
      fprintf(stderr, "callbackServer: Error when parsing message ... %d, %d, %s\n",
              env->fault_occurred, env->fault_code, env->fault_string);
      xmlrpc_env_clean(env);
      return xmlrpc_build_value(env, "s", "<qos><state>ERROR</state></qos>");
   }

   {  // dump what we have got ...
      unsigned char buf[len+2];
      strncpy(buf, content, len);
      *(buf + len) = 0;
      printf("cbSessionId=%s\nkey=%s\ncontent=%s\nqos=%s\n",
                       cbSessionId, key, buf, qos);
      printf("\n-------------------------------------------\n");
   }

   // Return our result
   retData = "<qos><state>OK</state></qos>";
   retVal = xmlrpc_build_value(env, "s#", retData, strlen(retData));
   return retVal;
}


/**
 * Update message arrives here.
 * This is the callback invoked from xmlBlaster
 */
xmlrpc_value *ping (xmlrpc_env *env, xmlrpc_value *param_array, void *user_data)
{
   xmlrpc_value *retVal = NULL;
   char *retData = NULL;

   {  // print info ...
      printf("\n\n-------------------------------------------\n");
      if (env->fault_occurred)
         printf("callbackServer: Entering ping(), ERROR message arrives ...\n");
      else
         printf("callbackServer: Entering ping(), message arrives ...\n\n");
   }

   // Return our result
   retData = "<qos><state>OK</state></qos>";
   retVal = xmlrpc_build_value(env, "s#", retData, strlen(retData));
   return retVal;
}

