/*
 * XmlUtil.c
 *
 *  Created on: Jul 28, 2008
 *      Author: mr@marcelruff.info
 */
#include <stdio.h> /* fseek etc */
#include <string.h>
#include <stdlib.h>
#include <util/helper.h>
#include <util/msgUtil.h>
#include "XmlUtil.h"

static const char * const AMP = "&amp;";
static const char * const LT = "&lt;";
static const char * const GT = "&gt;";
static const char * const QUOT = "&quot;";
static const char * const APOS = "&apos;";

static const char * const SLASH_R = "&#x0D;";
static const char * const NULL_ = "&#x0;";

static const char * const COMMA = "&comma;";


/**
 * <p k="Hello">
 * @param attributeValue if not 0 it must match as well
 * @param start out parameter for pos of 'H', -1 if not found
 * @param end out parameter for pos of '"' after 'o'
 *
 */
static void xmlBlasterExtractAttributePos(const char * const xml,
		const char * const tag, const char * const attributeName,
		const char * const attributeValue, int *start, int *end) {
	*start = -1;
	*end = -1;
	if (xml == 0 || tag == 0 || attributeName == 0)
		return;
	{
		bool insideTag = false;
		int i;
		int len = (int)strlen(xml);
		char *startTag = strcpyAlloc("<");
		char *attrToken = strcpyAlloc(attributeName);
		startTag = strcatAlloc(&startTag, tag);
		attrToken = strcatAlloc(&attrToken, "=");
		for (i = 0; i < len; i++) {
			if (xml[i] == '>') {
				insideTag = false;
				continue;
			}
			if (insideTag) {
				if (startsWith(xml + i, attrToken)) {
					int pos = i + (int)strlen(attrToken);
					char apos = xml[pos];
					int curr = 0;
					*start = pos + 1;
					i = pos + 1;
					for (; i < len; i++, curr++) {
						if (xml[i] == apos) {
							*end = i;
							xmlBlasterFree(startTag);
							xmlBlasterFree(attrToken);
							return;
						}
						if (attributeValue != 0) {
							if (xml[i] != attributeValue[curr]) {
								insideTag = false;
								break;
							}
						}
					}
				}
				continue;
			}
			if (startsWith(xml + i, startTag)) {
				insideTag = true;
				continue;
			}
		}
		xmlBlasterFree(startTag);
		xmlBlasterFree(attrToken);
		*start = -1;
	}
}

Dll_Export char *xmlBlasterExtractAttributeValue(const char * const xml,
		const char * const tag, const char * const attributeName) {
	if (xml == 0 || tag == 0 || attributeName == 0)
		return 0;
	{
		int start, end;
		xmlBlasterExtractAttributePos(xml, tag, attributeName, 0, &start, &end);
		if (start >= 0) {
			int count = end - start;
			char *ret = (char *) malloc((count + 1) * sizeof(char));
			int j;
			ret[count] = 0;
			for (j = 0; j < count; j++) {
				ret[j] = xml[start + j];
			}
			return ret;
		}
	}
	return 0;
}

Dll_Export long xmlBlasterExtractAttributeLong(const char * const xml,
		const char * const tag, const char * const attributeName, long defaultValue) {
	long val = defaultValue;
	char *valP = xmlBlasterExtractAttributeValue(xml, tag, attributeName);
	if (valP == 0)
		return defaultValue;
	if (sscanf(valP, "%ld", &val) == 1)
		return val;
	return defaultValue;
}

Dll_Export char *xmlBlasterExtractTagValueWithAttribute(const char * const xml,
		const char * const tag, const char * const attributeName,
		const char * const attributeValue) {
	if (xml == 0 || tag == 0 || attributeName == 0)
		return 0;
	{
		int startAttr, endAttr;
		xmlBlasterExtractAttributePos(xml, tag, attributeName, attributeValue,
				&startAttr, &endAttr);
		if (startAttr >= 0) {
			int i;
			int len = (int)strlen(xml);
			for (i = endAttr; i < len; i++) {
				if (xml[i] == '>') {
					if (i == len - 1)
						return strcpyAlloc("");
					{
						const char *p = strstr(xml + i, "<");
						int startVal = i + 1;
						int endTag = (p == 0) ? len : len - (int)strlen(p);
						int count = endTag - startVal;
						char *ret = (char *) malloc((count + 1) * sizeof(char));
						int j;
						ret[count] = '\0';
						for (j = 0; j < count; j++) {
							ret[j] = xml[startVal + j];
						}
						return ret;
					}
				}
			}
		}
		return 0;
	}
}

Dll_Export char *xmlBlasterExtractTagValue(const char * const xml,
		const char * const tag) {
	if (xml == 0 || tag == 0)
		return 0;
	{
		const char *startP = 0;
		char *tagP = strcpyAlloc("<");
		strcatAlloc(&tagP, tag);
		startP = strstr(xml, tagP);
		if (startP != 0) {
			int i;
			int start = -1, end = -1;
			int len = (int)strlen(startP);
			for (i = 1; i < len; i++) {
				if (startP[i] == '>') {
					start = i+1;
				}
				else if (startP[i] == '<') {
					end = i;
					break;
				}
			}
			if (start >= 0 && end >= start) {
				int j = 0;
				int newLen = 0;
				char *ret = (char *)malloc((end-start+1)*sizeof(char));
				for (i = start; i < end; i++, j++) {
					ret[j] = startP[i];
				}
				ret[end-start] = 0;
				xmlBlasterFree(tagP);
				return xmlBlasterUnEscapeXml(ret, &newLen);
			}
		}
		xmlBlasterFree(tagP);
		return 0;
	}
}

Dll_Export char* xmlBlasterEscapeXml(const char *xml) {
	if (xml == 0)
		return strcpyAlloc("");
	return xmlBlasterEscapeXmlBytes((int)strlen(xml), xml);
}

Dll_Export char* xmlBlasterEscapeXmlBytes(int len, const char *bytes) {
	int i, newLen, pos;
	char *res;
	if (bytes == 0 || *bytes == 0 || len < 1)
		return strcpyAlloc("");
	newLen = len + 100;
	res = (char *) malloc(newLen * sizeof(char));
	memset(res, 0, newLen * sizeof(char));
	pos = 0; /* new index */
	for (i = 0; i < len; i++, pos++) {
		/* Guarantee more space of at least strlen(&comma;) */
		if (pos >= (newLen-10)) {
			newLen += (len < 1000) ? 100 : len/10;
			res = (char *)realloc(res, newLen * sizeof(char));
			memset(res+pos, '\0', (size_t)(newLen-pos));
		}
		if (bytes[i] == '&') {
			strcat(res + pos, AMP);
			pos += (int)strlen(AMP)-1;
		} else if (bytes[i] == '<') {
			strcat(res + pos, LT);
			pos += (int)strlen(LT)-1;
		} else if (bytes[i] == '>') {
			strcat(res + pos, GT);
			pos += (int)strlen(GT)-1;
		} else if (bytes[i] == '"') {
			strcat(res + pos, QUOT);
			pos += (int)strlen(QUOT)-1;
		} else if (bytes[i] == '\'') {
			strcat(res + pos, APOS);
			pos += (int)strlen(APOS)-1;
		} else if (bytes[i] == '\r') {
			strcat(res + pos, SLASH_R);
			pos += (int)strlen(SLASH_R)-1;
		} else if (bytes[i] == '\0') {
			strcat(res + pos, NULL_);
			pos += (int)strlen(NULL_)-1;
		} else {
			res[pos] = bytes[i];
		}
	}
	*(res + pos) = 0;
	return res;
}

Dll_Export char *xmlBlasterUnEscapeXml(char * const xml, int *newLen) {
	int i, len, pos;
	*newLen = 0;
	if (xml == 0)
		return xml;
	len = (int)strlen(xml);
	pos = 0; /* new index */
	for (i = 0; i < len; i++, pos++) {
		if (xml[i] != '&') {
			xml[pos] = xml[i];
			continue;
		}
		if (startsWith(xml + i, AMP)) {
			xml[pos] = '&';
			i += (int)strlen(AMP) - 1;
		} else if (startsWith(xml + i, LT)) {
			xml[pos] = '<';
			i += (int)strlen(LT) - 1;
		} else if (startsWith(xml + i, GT)) {
			xml[pos] = '>';
			i += (int)strlen(GT) - 1;
		} else if (startsWith(xml + i, QUOT)) {
			xml[pos] = '"';
			i += (int)strlen(QUOT) - 1;
		} else if (startsWith(xml + i, APOS)) {
			xml[pos] = '\'';
			i += (int)strlen(APOS) - 1;
		} else if (startsWith(xml + i, SLASH_R)) {
			xml[pos] = '\r';
			i += (int)strlen(SLASH_R) - 1;
		} else if (startsWith(xml + i, NULL_)) {
			xml[pos] = '\0';
			i += (int)strlen(NULL_) - 1;
		}
	}
	*(xml + pos) = 0;
	*newLen = pos;
	return xml;
}

Dll_Export char* xmlBlasterEscapeCSV(const char *csv) {
	int i, len, pos;
	char *res;
	if (csv == 0 || *csv == 0)
		return strcpyAlloc("");
	len = (int)strlen(csv);
	res = (char *) malloc(5 * len * sizeof(char));
	memset(res, 0, 5 * len * sizeof(char));
	pos = 0; /* new index */
	for (i = 0; i < len; i++, pos++) {
		if (csv[i] == ',') {
			strcat(res + pos, COMMA);
			pos += (int)strlen(COMMA)-1;
		} else {
			res[pos] = csv[i];
		}
	}
	*(res + pos) = 0;
	return res;
}

Dll_Export char *xmlBlasterUnEscapeCSV(char * const csv, int *newLen) {
	int i, len, pos;
	*newLen = 0;
	if (csv == 0)
		return csv;
	len = (int)strlen(csv);
	pos = 0; /* new index */
	for (i = 0; i < len; i++, pos++) {
		if (csv[i] != '&') {
			csv[pos] = csv[i];
			continue;
		}
		if (startsWith(csv + i, COMMA)) {
			csv[pos] = ',';
			i += (int)strlen(COMMA) - 1;
		}
	}
	*(csv + pos) = 0;
	*newLen = pos;
	return csv;
}

Dll_Export char *xmlBlasterReadBinaryFile(const char *name, int *len)
{
	FILE *file;
	char *buffer = 0;
	unsigned long fileLen;

	*len = 0;

	file = fopen(name, "rb");
	if (!file)
	{
		fprintf(stderr, "xmlBlasterReadBinaryFile: Unable to open file %s\n", name);
		return 0;
	}

	/*Get file length*/
	fseek(file, 0, SEEK_END);
	fileLen=ftell(file);
	fseek(file, 0, SEEK_SET);

	buffer=(char *)malloc(fileLen+1);
	if (!buffer)
	{
		fprintf(stderr, "xmlBlasterReadBinaryFile: Memory error!\n");
                                fclose(file);
		return 0;
	}

	*len = (int)fread(buffer, 1, fileLen, file);
	fclose(file);

	return buffer;
}
