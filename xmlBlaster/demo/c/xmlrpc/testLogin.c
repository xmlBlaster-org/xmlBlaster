/*
   Example how to access xmlBlaster wit C and XmlRpc

   http://xmlrpc-c.sourceforge.net/

   CLIENT_CFLAGS=`xmlrpc-c-config libwww-client --cflags`
   CLIENT_LIBS=`xmlrpc-c-config libwww-client --libs`
   gcc $CLIENT_CFLAGS -o testLogin testLogin.c $CLIENT_LIBS

   Read doc/overview.txt
*/
#include <stdio.h>
#include <xmlrpc.h>
#include <xmlrpc_client.h>
#define NAME       "XMLRPC xmlBlaster.org C Client"
#define VERSION    "0.1"
#define SERVER_URL "http://localhost:8080/"

void usage()
{
   fprintf(stderr,
    "--------------------------------------------------\n"
    "Usage:\n"
    "   testLogin -xmlrpc.serverUrl <serverURL> -xmlrpc.cbUrl <myCallbackServerURL> -loginName <loginName> -passwd <Password> -dest <loginName> -msgFile <xmlFile>\n"
    "Example:\n"
    "   testLogin -xmlrpc.serverUrl http://anotherHost:8080/ -xmlrpc.cbUrl http://myHost:8080/ -loginName ben -passwd secret -dest gesa -msgFile xy.xml\n"
    "--------------------------------------------------\n");
}

void die_if_fault_occurred (xmlrpc_env *env)
{
    /* Check our error-handling environment for an XMLRPC fault. */
    if (env->fault_occurred) {
        fprintf(stderr, "XMLRPC Fault: %s (%d)\n",
                env->fault_string, env->fault_code);
        usage();
        exit(1);
    }
}

int main (int argc, char** argv)
{
   xmlrpc_env env;
   xmlrpc_value *result;
   char *sessionId;
   char key[256], *content, qos[256], *pubReturn, *loginName, *passwd, loginQos[256], *cbUrl;
   char *keyOid = "PIB_REQUEST";
   char *serverUrl = NULL; //SERVER_URL;
   char *msgFile = NULL;
   char *destination = NULL;
   char serverHostName[126];
   char tmp[256];
   int iarg;

   for (iarg=0; iarg < argc-1; iarg++) {
     if (strcmp(argv[iarg], "-xmlrpc.serverUrl") == 0)
        serverUrl = argv[++iarg];
     else if (strcmp(argv[iarg], "-xmlrpc.cbUrl") == 0) // URL of our here instantiated callback server
        cbUrl = argv[++iarg];
     else if (strcmp(argv[iarg], "-dest") == 0)
        destination = argv[++iarg];
     else if (strcmp(argv[iarg], "-msgFile") == 0)
        msgFile = argv[++iarg];
     else if (strcmp(argv[iarg], "-loginName") == 0)
        loginName = argv[++iarg];
     else if (strcmp(argv[iarg], "-passwd") == 0)
        passwd = argv[++iarg];
     else if (strcmp(argv[iarg], "--help") == 0 || strcmp(argv[iarg], "-help") == 0 || strcmp(argv[iarg], "-?") == 0) {
        usage();
        exit(0);
     }
   }

   if (destination == NULL || msgFile == NULL) {
      printf("Missing arguments\n");
      usage();
      exit(0);
   }

   /*
    !!! Missing !!!
    Startup the XmlRpc server for callbacks
   */

   if (serverUrl == NULL) {
      gethostname(serverHostName, 125);
      sprintf(tmp, "http://%s:8080/", serverHostName);
      serverUrl = tmp;
   }
   
   /* Start up our XMLRPC client library. */
   xmlrpc_client_init(XMLRPC_CLIENT_NO_FLAGS, NAME, VERSION);
   xmlrpc_env_init(&env);

   /* Call our XMLRPC server. */
   sprintf(loginQos, "<qos><callback type='XMLRPC'>%.100s</callback></qos>", cbUrl);
   printf("Login to %s as user %s ...\n", serverUrl, loginName);
   result = xmlrpc_client_call(&env, serverUrl,
                               "authenticate.login", "(ssss)",
                               loginName, passwd, loginQos, "");
   die_if_fault_occurred(&env);
   
   /* Parse our result value. */
   xmlrpc_parse_value(&env, result, "s", &sessionId);
   die_if_fault_occurred(&env);
   printf("Login success, got sessionId=%s\n", sessionId);
   
   /* Send message to destination. */
   printf("publish %s to %s ...\n", keyOid, destination);
   sprintf(key, "<key oid='%.100s' contentMime='text/xml'/>", keyOid);
   sprintf(qos, "<qos><destination queryType='EXACT'>%.100s</destination></qos>", destination);
   content = "Hello world";
   printf("Key=%s Qos=%s\n", key, qos);
   result = xmlrpc_client_call(&env, serverUrl,
                               "xmlBlaster.publish", "(ssss)",
                               sessionId, key, content, qos);
   die_if_fault_occurred(&env);

   /* Parse our result value. */
   xmlrpc_parse_value(&env, result, "s", &pubReturn);
   die_if_fault_occurred(&env);
   printf("Publish success, return value is %s\n", pubReturn);


   /* Dispose of our result value. */
   xmlrpc_DECREF(result);

   /* Shutdown our XMLRPC client library. */
   xmlrpc_env_clean(&env);
   xmlrpc_client_cleanup();

   return 0;
}


