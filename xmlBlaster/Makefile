#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster.org
#Comment:   Makefile for sub directories
#------------------------------------------------------------------------------

include $(XMLBLASTER_HOME)/Makefile.Include

SUB_DIRS = src demo testsuite

all: all_SUB_DIRS

clean: clean_SUB_DIRS

distclean: clean
	$(RM_RECURSIVE) $(JAVADOC_DIR)
	$(RM) $(JAR_DIR)/xmlBlaster.jar

PACKGS = org.xmlBlaster \
			org.xmlBlaster.engine \
			org.xmlBlaster.authenticateIdl \
			org.xmlBlaster.authentication \
			org.xmlBlaster.client \
			org.xmlBlaster.clientIdl \
			org.xmlBlaster.serverIdl \
			org.xmlBlaster.util

TEST_PACKGS = testsuite.org.xmlBlaster

javadoc:
	${MKDIR_RECURSIVE} ${JAVADOC_DIR} 
	${JAVADOC} -d ${JAVADOC_DIR} -sourcepath ./src/java $(PACKGS) 
 

javadoc_testsuite:
	${MKDIR_RECURSIVE} ${JAVADOC_DIR} 
	${JAVADOC} -d ${JAVADOC_DIR} -sourcepath . $(TEST_PACKGS) 
 

jar: all
	( cd ${CLASS_TOP_DIR}; jar cvf ${JAR_DIR}/xmlBlaster.jar ./org )


test: all
	( cd ${XMLBLASTER_HOME}/testsuite/org/xmlBlaster; make test )

