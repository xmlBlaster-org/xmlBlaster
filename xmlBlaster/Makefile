#------------------------------------------------------------------------------
#Name:      Makefile
#Project:   xmlBlaster.org
#Comment:   Makefile for sub directories
#------------------------------------------------------------------------------

include $(XMLBLASTER_HOME)/Makefile.Include

SUB_DIRS = src demo testsuite

all: all_SUB_DIRS jar

clean: clean_SUB_DIRS

distclean: clean
	$(RM_RECURSIVE) $(JAVADOC_DIR)
	$(RM) $(JAR_DIR)/xmlBlaster.jar

PACKGS = org.xmlBlaster \
			org.xmlBlaster.engine \
			org.xmlBlaster.engine.callback \
			org.xmlBlaster.engine.xml2java \
			org.xmlBlaster.engine.helper \
			org.xmlBlaster.engine.queue \
			org.xmlBlaster.engine.mime \
			org.xmlBlaster.engine.mime.demo \
			org.xmlBlaster.engine.persistence \
			org.xmlBlaster.engine.persistence.xmldb \
			org.xmlBlaster.engine.persistence.xmldb.xindice \
			org.xmlBlaster.engine.persistence.filestore \
			org.xmlBlaster.j2ee.k2 \
			org.xmlBlaster.j2ee.k2.client \
			org.xmlBlaster.authentication \
			org.xmlBlaster.authentication.plugins \
			org.xmlBlaster.authentication.plugins.simple \
			org.xmlBlaster.authentication.plugins.a2Blaster \
			org.xmlBlaster.authentication.plugins.demo \
			org.xmlBlaster.authentication.plugins.ldap \
			org.xmlBlaster.authentication.plugins.htpasswd \
			org.xmlBlaster.client \
			org.xmlBlaster.client.protocol \
			org.xmlBlaster.client.protocol.socket \
			org.xmlBlaster.client.protocol.corba \
			org.xmlBlaster.client.protocol.rmi \
			org.xmlBlaster.client.protocol.xmlrpc \
			org.xmlBlaster.client.feeder \
			org.xmlBlaster.client.reader \
			org.xmlBlaster.protocol \
			org.xmlBlaster.protocol.corba.clientIdl \
			org.xmlBlaster.protocol.corba.authenticateIdl \
			org.xmlBlaster.protocol.corba.serverIdl \
			org.xmlBlaster.protocol.email \
			org.xmlBlaster.protocol.http \
			org.xmlBlaster.protocol.jdbc \
			org.xmlBlaster.protocol.rmi \
			org.xmlBlaster.protocol.xmlrpc \
			org.xmlBlaster.protocol.socket \
			org.xmlBlaster.protocol.nativ \
			org.xmlBlaster.util

JUTILS_PACKGS = 

#JUTILS_PACKGS = org.jutils \
#			org.jutils.collection \
#			org.jutils.init \
#			org.jutils.io \
#			org.jutils.log \
#			org.jutils.pool \
#			org.jutils.runtime \
#			org.jutils.text \
#			org.jutils.time



TEST_PACKGS = testsuite.org.xmlBlaster

DEMO_PACKGS = javaclients javaclients.corba javaclients.rmi javaclients.jdbc javaclients.chat javaclients.HelloWorldApplet http.dhtml.systemInfo

DOC_PACKGS = doc.requirements

javadoc_server:
	${MKDIR_RECURSIVE} ${JAVADOC_DIR} 
	${JAVADOC} -d ${JAVADOC_DIR} -sourcepath ./src/java $(PACKGS) 
 

javadoc_testsuite:
	${MKDIR_RECURSIVE} ${JAVADOC_DIR} 
	${JAVADOC} -d ${JAVADOC_DIR} -sourcepath . $(TEST_PACKGS) 
 

javadoc_demo:
	${MKDIR_RECURSIVE} ${JAVADOC_DIR} 
	${JAVADOC} -d ${JAVADOC_DIR} -sourcepath demo $(DEMO_PACKGS) 

doc: javadoc 

javadoc:
	${MKDIR_RECURSIVE} ${JAVADOC_DIR} 
	${JAVADOC} -d ${JAVADOC_DIR} -sourcepath ./src/java:./:./demo $(PACKGS) $(JUTILS_PACKGS) $(TEST_PACKGS) $(DEMO_PACKGS) $(DOC_PACKGS)
	# making requirements.html from xml requirements:
	( cd ${SRC_DOCS_DIR}; make doc)
	( cd ${SRC_TESTSUITE_TOP_DIR}/org/xmlBlaster; make html)

# Indexing of jar file only since JDK 1.3:  jar -i  ${JAR_DIR}/xmlBlaster.jar
jar:
	(	\
		cd ${CLASS_TOP_DIR}; \
		$(CP) ${XMLBLASTER_HOME}/config/jacorb.properties.template ${CLASS_TOP_DIR}/jacorb.properties; \
		jar cmf manifest.xmlBlaster ${JAR_DIR}/xmlBlaster.jar ./org ./testsuite ./javaclients xmlBlaster.policy jacorb.properties; \
	)


test: all
	( cd ${XMLBLASTER_HOME}/testsuite/org/xmlBlaster; make test )

test-console: all
	( cd ${XMLBLASTER_HOME}/testsuite/org/xmlBlaster; make test-console )

install: all
	( tar cvzf xmlBlasterInstall.tar.gz bin config lib )





