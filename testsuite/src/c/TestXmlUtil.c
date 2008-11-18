/*----------------------------------------------------------------------------
 Name:     xmlBlaster/testsuite/src/c/TestXmlUtil.c
 Copyright: "Marcel Ruff" mr@marcelruff.info
 Comment:   Test C client library
 -----------------------------------------------------------------------------*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "util/helper.h"
#include "util/msgUtil.h"
#include "util/base64c.h"
#include "util/XmlUtil.h"
#include "test.h"

static int argc = 0;
static char** argv = 0;
/*
 #define  ERRORSTR_LEN 4096
 static char errorString[ERRORSTR_LEN + 1];
 */

static const char * test_strStartsEnds() {
	mu_assert("startsWith", !startsWith(0, 0));
	mu_assert("startsWith", !startsWith("", "He"));
	mu_assert("startsWith", !startsWith("Hello World", "Bla"));
	mu_assert("startsWith", startsWith("", ""));
	mu_assert("startsWith", startsWith("Hello World", ""));
	mu_assert("startsWith", startsWith("Hello World", "He"));
	mu_assert("startsWith", startsWith("Hello World", "H"));
	mu_assert("startsWith", startsWith("device.joe.nmea", "device."));

	mu_assert("endsWith", !endsWith(0, 0));
	mu_assert("endsWith", !endsWith("", "He"));
	mu_assert("endsWith", !endsWith("Hello World", "Bla"));
	mu_assert("endsWith", endsWith("", ""));
	mu_assert("endsWith", endsWith("Hello World", ""));
	mu_assert("endsWith", endsWith("Hello World", "ld"));
	mu_assert("endsWith", endsWith("Hello World", "d"));
	mu_assert("endsWith", endsWith("device.joe.nmea", ".nmea"));

	printf("[client] Good bye.\n");
	return 0;
}

static const char * test_extractAttributeValue() {
	{
		const char * const xml = 0;
		char *val = xmlBlasterExtractAttributeValue(xml, "p", "k");
		mu_assert("attrval0", val == 0);
	}
	{
		const char * const xml = "<p/>";
		char *val = xmlBlasterExtractAttributeValue(xml, "p", "k");
		mu_assert("attrval0", val == 0);
	}
	{
		const char * const xml = "<p key='bla'></p>";
		char *val = xmlBlasterExtractAttributeValue(xml, "p", "k");
		mu_assert("attrval0", val == 0);
	}
	{
		const char * const xml = "<p k=''/>";
		char *val = xmlBlasterExtractAttributeValue(xml, "p", "k");
		mu_assert("attrval0", val != 0);
		printf("%s\n", val);
		mu_assert("attrval", !strcmp(val, ""));
		xmlBlasterFree(val);
	}
	{
		const char * const xml = "<p k='bla'>blu</p>";
		char *val = xmlBlasterExtractAttributeValue(xml, "p", "k");
		mu_assert("attrval0", val != 0);
		printf("%s\n", val);
		mu_assert("attrval", !strcmp(val, "bla"));
		xmlBlasterFree(val);
	}
	{
		const char * const xml = "<p k='bla'>blu</p><p k='bla2'>blu2</p>";
		char *val = xmlBlasterExtractAttributeValue(xml, "p", "k");
		mu_assert("attrval0", val != 0);
		printf("%s\n", val);
		mu_assert("attrval", !strcmp(val, "bla"));
		xmlBlasterFree(val);
	}

	return 0;
}

static const char * test_extractTagValue() {
	{
		const char * const xml = 0;
		char *val = xmlBlasterExtractTagValue(xml, 0);
		mu_assert("tagval0", val == 0);
	}
	{
		const char * const xml = "<qos></qos>";
		char *val = xmlBlasterExtractTagValue(xml, "sender");
		mu_assert("tagval0", val == 0);
	}
	{
		const char * const xml = "<qos><sender></sender></qos>";
		char *val = xmlBlasterExtractTagValue(xml, "sender");
		mu_assert("sender check", !strcmp(val, ""));
		xmlBlasterFree(val);
	}
	{
		const char * const xml = "<qos><sender>jack</sender></qos>";
		char *val = xmlBlasterExtractTagValue(xml, "sender");
		mu_assert("sender check", !strcmp(val, "jack"));
		xmlBlasterFree(val);
	}
	{
		const char * const xml = "<qos><sender>jack&amp;joe</sender></qos>";
		char *val = xmlBlasterExtractTagValue(xml, "sender");
		mu_assert("sender check", !strcmp(val, "jack&joe"));
		xmlBlasterFree(val);
	}
	return 0;
}

static const char * test_extractTagValueWithAttribute() {
	{
		const char * const xml = 0;
		char *val = xmlBlasterExtractTagValueWithAttribute(xml, "p", "k", 0);
		mu_assert("tagval0", val == 0);
	}
	{
		const char * const xml = "<p/>";
		char *val = xmlBlasterExtractTagValueWithAttribute(xml, "p", "k", "bla");
		mu_assert("tagval0", val == 0);
	}
	{
		const char * const xml = "<p key='bla'></p>";
		char *val = xmlBlasterExtractTagValueWithAttribute(xml, "p", "k", "bla");
		mu_assert("tagval0", val == 0);
	}
	{
		const char * const xml = "<p k='bla'>blu</p>";
		char *val = xmlBlasterExtractTagValueWithAttribute(xml, "p", "k", "bla");
		mu_assert("tagval0", val != 0);
		printf("%s\n", val);
		mu_assert("tagval", !strcmp(val, "blu"));
		xmlBlasterFree(val);
	}
	{
		const char * const xml = "<p k='bla'/>";
		char *val = xmlBlasterExtractTagValueWithAttribute(xml, "p", "k", "bla");
		mu_assert("tagval0", val != 0);
		printf("%s\n", val);
		mu_assert("tagval", !strcmp(val, ""));
		xmlBlasterFree(val);
	}
	{
		const char * const xml = "<p k='bla'></p>";
		char *val = xmlBlasterExtractTagValueWithAttribute(xml, "p", "k", "bla");
		mu_assert("tagval0", val != 0);
		printf("%s\n", val);
		mu_assert("tagval", !strcmp(val, ""));
		xmlBlasterFree(val);
	}
	{
		const char * const xml = "<p k='bla1'>blu1</p><p k='bla2'>blu2</p>";
		char *val = xmlBlasterExtractTagValueWithAttribute(xml, "p", "k", "bla2");
		mu_assert("attrval0", val != 0);
		printf("%s\n", val);
		mu_assert("attrval", !strcmp(val, "blu2"));
		xmlBlasterFree(val);
	}
	{
		const char * const xml =
		"<qos>"
		"  <sender>/node/xmlBlaster/client/iphone/session/-562</sender>"
		"  <subscribe id='__subId:1224443501105000000'/>"
		"  <rcvTimestamp nanos='1224455160443000000'/>"
		"  <queue index='0' size='1'/>"
		"  <isPublish/>"
		"  <clientProperty name='replyTo'>iphone</clientProperty>"
		" </qos>";
		char *replyTo = xmlBlasterExtractTagValueWithAttribute(xml, "clientProperty", "name", "replyTo");
		mu_assert("tagval0", replyTo != 0);
		printf("%s\n", replyTo);
		mu_assert("tagval0", !strcmp(replyTo, "iphone"));
		xmlBlasterFree(replyTo);
	}
	return 0;
}

static const char * test_xmlEscape() {
	{
		char *xml;
		int len = 5, newLen;
		char *bytes = (char *)malloc(len*sizeof(char));
		bytes[0] = '<';
		bytes[1] = '<';
		bytes[2] = '\0';
		bytes[3] = '&';
		bytes[4] = '\'';
		xml = xmlBlasterEscapeXmlBytes(len, bytes);
		printf("%s\n", xml);
		mu_assert("xmlEscape", xml != 0);
		mu_assert("xmlEscape", !strcmp(xml, "&lt;&lt;&#x0;&amp;&apos;"));

		xmlBlasterUnEscapeXml(xml, &newLen);
		printf("%s\n", xml);
		mu_assert("xmlEscape", newLen == 5);
		mu_assert("xmlEscape", xml[0] == '<');
		mu_assert("xmlEscape", xml[1] == '<');
		mu_assert("xmlEscape", xml[2] == 0);
		mu_assert("xmlEscape", xml[3] == '&');
		mu_assert("xmlEscape", xml[4] == '\'');

		xmlBlasterFree(xml);
		free(bytes);
	}
	return 0;
}

static const char * test_xmlUnEscape() {
	int newLen;
	{
		char *xml;
		newLen = 77;
		xml = xmlBlasterUnEscapeXml(0, &newLen);
		mu_assert("xmlEscape", xml == 0);
		mu_assert("xmlEscape", newLen == 0);
	}
	{
		char *xml = strcpyAlloc("");
		xmlBlasterUnEscapeXml(xml, &newLen);
		printf("%s\n", xml);
		mu_assert("xmlEscape", !strcmp(xml, ""));
		mu_assert("xmlEscape", newLen == 0);
		xmlBlasterFree(xml);
	}
	{
		char *xml = strcpyAlloc("&lt;X/&gt;");
		xmlBlasterUnEscapeXml(xml, &newLen);
		printf("%s\n", xml);
		mu_assert("xmlEscape", !strcmp(xml, "<X/>"));
		mu_assert("xmlEscape", newLen == strlen("<X/>"));
		xmlBlasterFree(xml);
	}
	{
		const char
				* const expected =
						"<bc ln=\"n6230i\" t=\"all\"><b><ln>dirk</ln><al>dirk</al><st>0</st><pe n=\"gps\" d=\"Show my position\"/><pe n=\"xsms\" d=\"Send mails to me\"/></b></bc>";
		char *xml = strcpyAlloc(
				"&lt;bc ln=&quot;n6230i&quot; t=&quot;all&quot;&gt;"
					"&lt;b&gt;"
					"&lt;ln&gt;dirk&lt;/ln&gt;"
					"&lt;al&gt;dirk&lt;/al&gt;"
					"&lt;st&gt;0&lt;/st&gt;"
					"&lt;pe n=&quot;gps&quot; d=&quot;Show my position&quot;/&gt;"
					"&lt;pe n=&quot;xsms&quot; d=&quot;Send mails to me&quot;/&gt;"
					"&lt;/b&gt;"
					"&lt;/bc&gt;");
		xmlBlasterUnEscapeXml(xml, &newLen);
		printf("%s\n", xml);
		mu_assert("xmlEscape", !strcmp(xml, expected));
		mu_assert("xmlEscape", newLen == strlen(expected));
		xmlBlasterFree(xml);
	}
	return 0;
}


#define count 6
static const char * test_base64() {
	int run;
	const char * binary[count] = { "HelloWorld",       "1",    "22",   "333",  "4444",
			"Hello World, Hello World, Hello World, Hello World, Hello World, Hello World, Hello World, Hello World, Hello World, Hello World, " };
	const char * base64[count] = { "SGVsbG9Xb3JsZA==", "MQ==", "MjI=", "MzMz", "NDQ0NA==",
			"SGVsbG8gV29ybGQsIEhlbGxvIFdvcmxkLCBIZWxsbyBXb3JsZCwgSGVsbG8g\r\n"
            "V29ybGQsIEhlbGxvIFdvcmxkLCBIZWxsbyBXb3JsZCwgSGVsbG8gV29ybGQs\r\n"
            "IEhlbGxvIFdvcmxkLCBIZWxsbyBXb3JsZCwgSGVsbG8gV29ybGQsIA=="};
	for (run=0; run<count; run++) {
		const char *inP = binary[run];
		const char *expectedBase64 = base64[run];
		char *encoded = 0;
		int lineSize = 60; /* -1; */
		char *origP = 0;
		int origLen;

		encoded = Base64EncodeLen(strlen(inP), inP, lineSize);
		mu_assert("base64", encoded != 0);
		printf("inP='%s', encoded='%s'\n", inP, encoded);
		mu_assert("base64", !strcmp(encoded, expectedBase64));


		origP = Base64Decode(encoded, &origLen);
		mu_assert("base64", origP != 0);
		printf("len=%d, decodedAgain='%s'\n", origLen, origP);

		xmlBlasterFree(encoded);
		xmlBlasterFree(origP);
	}
	return 0;
}

static const char *all_tests() {
	mu_run_test(test_base64);
	mu_run_test(test_strStartsEnds);
	mu_run_test(test_extractAttributeValue);
	mu_run_test(test_extractTagValue);
	mu_run_test(test_extractTagValueWithAttribute);
	mu_run_test(test_xmlEscape);
	mu_run_test(test_xmlUnEscape);
	return 0;
}

int main(int argc_, char **argv_) {
	const char *result;
	argc = argc_;
	argv = argv_;

	result = all_tests();

	if (result != 0) {
		printf("%s\n", result);
	} else {
		printf("ALL TESTS PASSED\n");
	}
	printf("Tests run: %d\n", tests_run);

	return result != 0;
}

