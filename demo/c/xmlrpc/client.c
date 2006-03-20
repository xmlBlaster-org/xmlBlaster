/*------------------------------------------------------------------------------
Name:      client.c

Project:   xmlBlaster.org

Comment:   Example how to access xmlBlaster with C and XmlRpc
           See http://xmlrpc-c.sourceforge.net/
           Usually you start first the callbackServer to receive
           asynchronous update from xmlBlaster as well (see README)

Author:    xmlBlaster@marcelruff.info

Compile:   Read xmlrpc-c/doc/overview.txt
           CLIENT_CFLAGS=`xmlrpc-c-config libwww-client --cflags`
           CLIENT_LIBS=`xmlrpc-c-config libwww-client --libs`
           gcc $CLIENT_CFLAGS -o client client.c $CLIENT_LIBS -Wall

Invoke:    See usage text below
------------------------------------------------------------------------------*/
#include <stdio.h>
#include <unistd.h>
#include <string.h>
#include <sys/types.h> // for file-read only
#include <sys/stat.h>  // for file-read only
#include <fcntl.h>     // for file-read only
#include <malloc.h>    // for file-read only
#include <xmlrpc.h>
#include <xmlrpc_client.h>
#define NAME       "XMLRPC xmlBlaster.org C Client"
#define VERSION    "0.79d"

char *readFile(const char *const fileName);
void usage();
void die_if_fault_occurred (xmlrpc_env *env);


int main (int argc, char** argv)
{
   xmlrpc_env env;
   xmlrpc_value *result;
   char sessionId[256], *tmpP;
   char key[256], *content, qos[256], *retVal, *loginName, *passwd, loginQos[256], *cbUrl;
   char *keyOid = "PIB_REQUEST";
   char *serverUrl = NULL; // "http://localhost:8080/"
   char *msgFile = NULL, *data = NULL;
   char *destination = NULL;
   char serverHostName[256];
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

   if (destination == NULL) // as default, we send messages to ourself (-> our callbackServer)
      destination = loginName;

   data = readFile(msgFile); // get the data we want to send

   if (destination == NULL || data == NULL) {
      printf("Missing arguments\n");
      usage();
      exit(0);
   }

   if (serverUrl == NULL) {
      gethostname(serverHostName, 125);
      sprintf(tmp, "http://%.255s:8080/", serverHostName);
      serverUrl = tmp;
   }

   printf("\n\n-------------------------------------------\n"
              "Hello as C XmlRpc client for xmlBlaster ...\n\n");

   /* Start up our XMLRPC client library. */
   xmlrpc_client_init(XMLRPC_CLIENT_NO_FLAGS, NAME, VERSION);
   xmlrpc_env_init(&env);


   /* Login to xmlBlaster XMLRPC server. */
   {
      printf("Login to %s as user %s ...\n", serverUrl, loginName);
      sprintf(loginQos, "<qos><callback type='XMLRPC'>%.100s</callback></qos>", cbUrl);
      result = xmlrpc_client_call(&env, serverUrl,
                                  "authenticate.login", "(ssss)",
                                  loginName, passwd, loginQos, "");
      die_if_fault_occurred(&env);
      
      /* Parse our result value. */
      xmlrpc_parse_value(&env, result, "s", &tmpP);
      die_if_fault_occurred(&env);
      strncpy(sessionId, tmpP, 255); // remember sessionId, tmpP is empty after xmlrpc_DECREF(result)
      printf("Login success, got sessionId=%s\n", sessionId);
      
      /* Dispose of our result value. */
      xmlrpc_DECREF(result);
   }

   /* Send message to destination. */
   {
      printf("publish %s to %s ...\n", keyOid, destination);
      sprintf(key, "<key oid='%.100s' contentMime='text/xml'/>", keyOid);
      sprintf(qos, "<qos><destination queryType='EXACT'>%.100s</destination></qos>", destination);
      content = data;
      printf("Key=%s Qos=%s\n", key, qos);
      result = xmlrpc_client_call(&env, serverUrl,
                                  "xmlBlaster.publish", "(ssss)",
                                  sessionId, key, content, qos);
      die_if_fault_occurred(&env);

      /* Parse our result value. */
      xmlrpc_parse_value(&env, result, "s", &retVal);
      die_if_fault_occurred(&env);
      printf("Publish success, return value is %s\n", retVal);

      /* Dispose of our result value. */
      xmlrpc_DECREF(result);
   }

   printf("\nWait until callbackServer has received the response!\n");
   printf("Than hit a key to logout ...\n");
   getc(stdin);

   /* Logout from xmlBlaster XMLRPC server. */
   {
      printf("Logout from %s as user %s, sessionId=%s ...\n", serverUrl, loginName, sessionId);
      strcpy(loginQos, "<qos></qos>");
      result = xmlrpc_client_call(&env, serverUrl, "authenticate.logout", "(s)", sessionId);
      die_if_fault_occurred(&env);
      
      /* Parse our result value. */
      xmlrpc_parse_value(&env, result, "s", &retVal);
      die_if_fault_occurred(&env);
      printf("Logout success, got '%s'\n", retVal);
      
      /* Dispose of our result value. */
      xmlrpc_DECREF(result);
   }

   /* Shutdown our XMLRPC client library. */
   xmlrpc_env_clean(&env);
   xmlrpc_client_cleanup();

   free(data);

   printf("\nSuccessful communicated with xmlBlaster.\n"
              "-------------------------------------------\n");

   return 0;
}


/*
 * Some default error handling
 */
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


/*
 * @return data from file, you need to call free() on this pointer!
 */
char *readFile(const char *const fileName)
{
   int n, fd;
   const int BUFFSIZE=8192;
   char buf[BUFFSIZE];
   char *data;
   struct stat statbuf;

   if (fileName == NULL)
      return NULL;

   // get size of file ...
   if (stat(fileName, &statbuf)) {
      fprintf(stderr, "File %s not found\n", fileName);
      return NULL;
   }

   // read file ...
   data = malloc((statbuf.st_size+1)*sizeof(char));
   *data = 0;
   fd = open(fileName, O_RDONLY);
   if (fd < 0) {
      fprintf(stderr, "Can't read file %s\n", fileName);
      return NULL;
   }
   while((n=read(fd, buf, BUFFSIZE)) > 0) {
      buf[n] = NULL;
      strcat(data, buf);
   }
   *(data + statbuf.st_size) = 0;
   close(fd);
   return data;
}

void usage()
{
   fprintf(stderr,
    "--------------------------------------------------\n"
    "Usage:\n"
    "   client -xmlrpc.serverUrl <serverURL> -xmlrpc.cbUrl <myCallbackServerURL> -loginName <loginName> -passwd <Password> -dest <loginName> -msgFile <xmlFile>\n"
    "Example:\n"
    "   client -xmlrpc.serverUrl http://anotherHost:8080/ -xmlrpc.cbUrl http://myHost:8080/RPC2 -loginName gesa -passwd secret -dest gesa -msgFile hello.xml\n"
    "   This sends a message to myself, we have to start the callbackServer first\n"
    "--------------------------------------------------\n");
}

