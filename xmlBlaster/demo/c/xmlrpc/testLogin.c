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
#define NAME       "XML-RPC xmlBlaster.org C Client"
#define VERSION    "0.1"
#define SERVER_URL "http://idvssrv.avitech.de:8080/"

void die_if_fault_occurred (xmlrpc_env *env)
{
    /* Check our error-handling environment for an XML-RPC fault. */
    if (env->fault_occurred) {
        fprintf(stderr, "XML-RPC Fault: %s (%d)\n",
                env->fault_string, env->fault_code);
        fprintf(stderr, "----------------------------------\n"
	                "Usage:\n"
	                "   testLogin <serverURL>\n"
	                "Example:\n"
			"   testLogin http://myHost:8080/\n"
	                "----------------------------------\n");
        exit(1);
    }
}

int main (int argc, char** argv)
{
    xmlrpc_env env;
    xmlrpc_value *result;
    char *sessionId;
    char *host = SERVER_URL;

    if (argc == 1) {
	// Use default value for host.
    } else if (argc == 2) {
	host = argv[1];
    }
    
    /* Start up our XML-RPC client library. */
    xmlrpc_client_init(XMLRPC_CLIENT_NO_FLAGS, NAME, VERSION);
    xmlrpc_env_init(&env);

    /* Call our XML-RPC server. */
    printf("Login to %s ...\n", host);
    result = xmlrpc_client_call(&env, host,
                                "authenticate.login", "(ssss)",
				"ben", "secret", "<qos></qos>", "");
    die_if_fault_occurred(&env);
    
    /* Parse our result value. */
    xmlrpc_parse_value(&env, result, "s", &sessionId);
    die_if_fault_occurred(&env);
    printf("Login success, got sessionId=%s\n", sessionId);
    
    /* Dispose of our result value. */
    xmlrpc_DECREF(result);

    /* Shutdown our XML-RPC client library. */
    xmlrpc_env_clean(&env);
    xmlrpc_client_cleanup();

    return 0;
}


