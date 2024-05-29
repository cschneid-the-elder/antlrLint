#
# It's a little tricky getting this to make correctly.  LexerAdaptor and
# ANTLRv4Lexer.g4 have a mutual dependency.  I got this to work for me
# by commenting out lines in LexerAdaptor.java that refer to
# ANTLRv4Lexer, compiling that, then generating and compiling
# ANDLRv4Lexer.g4, then uncommenting out the lines in LexerAdaptor.java,
# and finally recompiling that.
#

SEP = :
ifdef OS
    SEP = ;
endif

JC = javac
CP = "./class$(SEP)./commons-cli-1.4.jar$(SEP)./antlr-4.13.1-complete.jar"
JCOPT = -d ./class -g -Xlint -cp $(CP)
#JCOPT = -d ./class -g -cp $(CP)
JCOPT1 = -d ./class -cp $(CP)
AOPT = -visitor -listener
#AOPT = -o ./src -lib ./src -visitor -listener

./class/%.class: ./src/%.java
	echo `date` $< >>build.log
	$(JC) $(JCOPT) $<

./src/%.tokens: ./src/%.g4
	echo `date` $< >>build.log
	java -jar ./antlr-4.13.1-complete.jar $(AOPT) $<
	$(JC) $(JCOPT1) ./src/$**.java
	

all: ./class/LexerAdaptor.class ./src/ANTLRv4Lexer.tokens ./src/ANTLRv4Parser.tokens ./class/GrammarListener.class ./class/AntlrLint.class

testrig:
	echo `date` $@ $(n) >> build.log
	java -cp ./class$(SEP).$(SEP)./antlr-4.13.1-complete.jar org.antlr.v4.gui.TestRig ANTLRv4 grammarSpec -gui -tokens < ./testdata/$(n)

testtree:
	echo `date` $@ $(n) >> build.log
	java -cp ./class$(SEP).$(SEP)./antlr-4.13.1-complete.jar org.antlr.v4.gui.TestRig ANTLRv4 grammarSpec -tree -tokens < ./testdata/$(n)

test:
ifeq ($(strip $(c)),)
	echo `date` $@ $(l) $(p) >> build.log
	java -jar AntlrLint.jar -lexerGrammar testdata/$(l) -parserGrammar testdata/$(p) -path ./testdata/ 
else
	echo `date` $@ $(c) >> build.log
	java -jar AntlrLint.jar -combinedGrammar testdata/$(c) -path ./testdata/ 
endif

jar:
	echo `date` $@ >> build.log
	jar cfm AntlrLint.jar manifest -C class .

init:
	echo `date` $@ $(n) >> build.log
	if [ ! -d class ]; then mkdir class; fi


./src/ANTLRv4Lexer.tokens: 

./src/ANTLRv4Parser.tokens: ./src/ANTLRv4Lexer.tokens

./class/LexerAdaptor.class: 
