
/*Copyright (C) 2023 Craig Schneiderwent.  All rights reserved.*/

/*
 *  THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 *  OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 *  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 *  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 *  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 *  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import java.util.*;
import java.io.*;
import java.nio.file.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.apache.commons.cli.*;

public class AntlrLint {

	public static String lexerFileName = null;
	public static String parserFileName = null;
	public static String combinedFileName = null;
	public static String pathToFile = null;
	
	public static void main(String[] args) throws Exception {

		cli(args);

		GrammarListener listener = new GrammarListener();
		if (lexerFileName != null && parserFileName != null) {
			lexAndParseGrammar(lexerFileName, listener);
			lexAndParseGrammar(parserFileName, listener);
		} else {
			lexAndParseGrammar(combinedFileName, listener);
		}
	
		ArrayList<String> orphanTokens = new ArrayList<>();
		for (String s: listener.getLexerTokens()) {
			Boolean foundItInLexerRule = false;
			Boolean foundItInParserRule = false;
			System.out.println("searching for " +s);
			lexerLoop:
			for (ANTLRv4Parser.LexerRuleSpecContext lrsCtx: listener.getLexerRules()) {
				ANTLRv4Parser.LexerRuleBlockContext lrbCtx = lrsCtx.lexerRuleBlock();
				ANTLRv4Parser.LexerAltListContext lalCtx = lrbCtx.lexerAltList();
				foundItInLexerRule = searchLexerAltList(lalCtx, s, lrsCtx.TOKEN_REF().getSymbol().getText());
				if (foundItInLexerRule) {
					break lexerLoop;
				}
			}
			if (!foundItInLexerRule) {
				parserLoop:
				for (ANTLRv4Parser.ParserRuleSpecContext prsCtx: listener.getParserRules()) {
					ANTLRv4Parser.RuleBlockContext rbCtx = prsCtx.ruleBlock();
					ANTLRv4Parser.RuleAltListContext ralCtx = rbCtx.ruleAltList();
					for (ANTLRv4Parser.LabeledAltContext laCtx: ralCtx.labeledAlt()) {
						ANTLRv4Parser.AlternativeContext altCtx = laCtx.alternative();
						foundItInParserRule = searchParserAlternative(altCtx, s, prsCtx.RULE_REF().getSymbol().getText());
						if (foundItInParserRule) {
							break parserLoop;
						}
					}
				}
				if (!foundItInLexerRule && !foundItInParserRule) {
					orphanTokens.add(s);
				}
			}
		}

		System.out.println("Tokens with no rule referencing them...");
		for (String s: orphanTokens) {
			System.out.println(s);
		}
		
		return;
	}

	private static Boolean searchLexerAltList(ANTLRv4Parser.LexerAltListContext altListCtx, String aString, String lexerRuleName) {
		Boolean foundIt = false;

		loop:
		for (ANTLRv4Parser.LexerAltContext laCtx: altListCtx.lexerAlt()) {
			ANTLRv4Parser.LexerElementsContext lesCtx = laCtx.lexerElements();
			for(ANTLRv4Parser.LexerElementContext leCtx: lesCtx.lexerElement()) {
				if (leCtx.lexerAtom() != null) {
					ANTLRv4Parser.TerminalContext tc = leCtx.lexerAtom().terminal();
					if (tc != null) {
						if (tc.getText().equals(aString)) {
							System.out.println("\tfound in lexer rule " + lexerRuleName);
							foundIt = true;
							break loop;
						}
					}
				} else if (leCtx.lexerBlock() != null) {
					if (leCtx.lexerBlock().lexerAltList() != null) {
						if (searchLexerAltList(leCtx.lexerBlock().lexerAltList(), aString, lexerRuleName)) {
							foundIt = true;
							break loop;
						}
					}
				}
			}
		}
		
		return foundIt;
	}
	
	private static Boolean searchParserAlternative(ANTLRv4Parser.AlternativeContext altCtx, String aString, String parserRuleName) {
		//System.out.println("searchParserAlternative(" + altCtx.getText() + ", " + aString + ", " + parserRuleName + ")");
		Boolean foundIt = false;
		
		loop:
		for (ANTLRv4Parser.ElementContext elmntCtx: altCtx.element()) {
			if (elmntCtx.atom() != null) {
				if (elmntCtx.atom().terminal() != null) {
					if (elmntCtx.atom().terminal().TOKEN_REF() != null) {
						if (elmntCtx.atom().terminal().TOKEN_REF().getText().equals(aString)) {
							System.out.println("\tfound in parser rule " + parserRuleName);
							foundIt = true;
							break loop;
						}
					}
				}
			} else if (elmntCtx.ebnf() != null) {
				if (elmntCtx.ebnf().block() != null) {
					if (elmntCtx.ebnf().block().altList() != null) {
						for (ANTLRv4Parser.AlternativeContext ac: elmntCtx.ebnf().block().altList().alternative()) {
							if (searchParserAlternative(ac, aString, parserRuleName)) {
								foundIt = true;
								break loop;
							}
						}
					}
				}
			}
		}
		//System.out.println("searchParserAlternative() returning " + foundIt);
		return foundIt;
	}

	private static void lexAndParseGrammar(String fileName, GrammarListener listener) {
	
		CharStream cs = null;
		try {
			cs = CharStreams.fromFileName(fileName);  //load the file
		} catch(Exception e) {
			System.err.println("Error instantiating Charstreams.fromFileName(" + fileName + ") " + e);
			System.exit(12);
		}

		System.out.println("lexing " + fileName);
		ANTLRv4Lexer lexer = new ANTLRv4Lexer(cs);  //instantiate a lexer
		CommonTokenStream tokens = new CommonTokenStream(lexer); //scan stream for tokens

		System.out.println("parsing " + fileName);
		ANTLRv4Parser parser = new ANTLRv4Parser(tokens);  //parse the tokens	

		ParseTree tree = null;
		try {
			tree = parser.grammarSpec(); // parse the content and get the tree
		} catch(Exception e) {
			System.err.println("Parser error " + e);
			System.exit(12);
		}
		
		ParseTreeWalker walker = new ParseTreeWalker();
	
		System.out.println("walking parse tree with " + listener.getClass().getName());
	
		try {
			walker.walk(listener, tree);
		} catch(Exception e) {
			System.err.println(listener.getClass().getName() + " error " + e);
			e.printStackTrace();
			System.exit(12);
		}

	}
	
	public static void cli(String[] args) {
		Options options = new Options();
		CommandLineParser parser = new DefaultParser();
		CommandLine line = null;
		HelpFormatter formatter = new HelpFormatter();
		
		Option lexerGrammar = new Option("lexerGrammar", true
			, "path and file name of a single lexer grammar to preprocess, ignored if combinedGrammar is provided");
		Option parserGrammar = new Option("parserGrammar", true
			, "path and file name of a single parser grammar to preprocess, ignored if combinedGrammar is provided");
		Option combinedGrammar = new Option("combinedGrammar", true
			, "path and file name of a single combined grammar to preprocess");
		Option path = new Option("path", true
			, "path where imported files are located including trailing file system separator");
		Option help = new Option("help", false, "print this message");

		options.addOption(lexerGrammar);
		options.addOption(parserGrammar);
		options.addOption(combinedGrammar);
		options.addOption(path);
		options.addOption(help);

		try {
			line = parser.parse( options, args );
		} catch( ParseException exp ) {
			System.err.println( "Command line parsing failed.  Reason: " + exp.getMessage() );
			System.exit(16);
		}

		if (line.hasOption("help")) {
			formatter.printHelp( "AntlrLint", options, true );
			System.exit(0);
		}

		if (line.hasOption("lexerGrammar") && line.hasOption("parserGrammar")) {
			lexerFileName = line.getOptionValue("lexerGrammar");
			parserFileName = line.getOptionValue("parserGrammar");
		} else if (line.hasOption("combinedGrammar")) {
			combinedFileName = line.getOptionValue("combinedGrammar");
		} else {
			System.err.println("please specify either a combinedGrammar or both a lexerGrammar and parserGrammar");
			formatter.printHelp( "AntlrLint", options, true );
			System.exit(4);
		}

		if (line.hasOption("path")) {
			pathToFile = line.getOptionValue("path");
		}

	}	
}

