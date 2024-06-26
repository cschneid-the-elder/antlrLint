
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

/**
AntlrLint is an attempt to find things in your ANTLR grammar
that may not be what you intended.  Okay, that's too vague.

Currently this code attempts to locate ANTLR Lexer tokens
that are not referred to in any other Lexer rules or in any
Parser rules.

Lexer tokens that have action blocks or lexer commands are
not evaluated, as they likely (IMHO) have a use, and the
point is to find potential cruft.
*/
public class AntlrLint {

	public static String lexerFileName = null;
	public static String parserFileName = null;
	public static String combinedFileName = null;
	public static String pathToFile = null;
	public static Boolean verbose = false;
	
	public static void main(String[] args) throws Exception {

		cli(args);

		GrammarListener listener = new GrammarListener();
		if (lexerFileName != null && parserFileName != null) {
			lexAndParseGrammar(lexerFileName, listener);
			lexAndParseGrammar(parserFileName, listener);
		} else {
			lexAndParseGrammar(combinedFileName, listener);
		}

		/*
		Each grammar file may contain an <code>import</code> statement, which
		gives us another grammar file to process.
		*/
		ListIterator<String> iterator = listener.getDelegateGrammars().listIterator();
		while (iterator.hasNext()) {
			StringBuffer grammarFile = new StringBuffer(pathToFile).append(iterator.next()).append(".g4");
			lexAndParseGrammar(grammarFile.toString(), listener);
		}
	
		System.out.println(
			"searching for " 
			+ listener.getLexerTokens().size()
			+ " tokens in " 
			+ listener.getLexerRules().size()
			+ " lexer rules and "
			+ listener.getParserRules().size()
			+ " parser rules"
			);

		ArrayList<String> fallenLeafTokens = 
			searchForFallenLeafTokens(
				listener.getLexerRules()
				, listener.getParserRules()
				, listener.getLexerTokens());

		if (fallenLeafTokens.size() > 0) {
			System.out.println("Lexer Tokens with no rule referencing them...");
			for (String s: fallenLeafTokens) {
				System.out.println(s);
			}
		} else {
			System.out.println("All Lexer Tokens are referenced in at least one rule");
		}
		
		if (listener.getLexerChannels().size() > 0) {
			System.out.println(
				"searching for " 
				+ listener.getLexerChannels().size()
				+ " channels in " 
				+ listener.getLexerRules().size()
				+ " lexer rules"
				);
			ArrayList<String> fallenLeafChannels = 
				searchForFallenLeafChannels(
					listener.getLexerRules()
					, listener.getLexerChannels());
			if (fallenLeafChannels.size() > 0) {
				System.out.println("Lexer Channels with no rule referencing them...");
				for (String s: fallenLeafChannels) {
					System.out.println(s);
				}
			} else {
				System.out.println("All Lexer Channels are referenced in at least one rule");
			}
		}

		if (listener.getLexerTokensSpecTokens().size() > 0) {
			System.out.println(
				"searching for " 
				+ listener.getLexerTokensSpecTokens().size()
				+ " tokensSpec tokens in " 
				+ listener.getLexerRules().size()
				+ " lexer rules"
				);
			ArrayList<String> fallenLeafTokensSpecTokens = 
				searchForFallenLeafTokensSpecTokens(
					listener.getLexerRules()
					, listener.getLexerTokensSpecTokens());
			if (fallenLeafTokensSpecTokens.size() > 0) {
				System.out.println("Lexer tokensSpec Tokens with no ->type() command referencing them...");
				for (String s: fallenLeafTokensSpecTokens) {
					System.out.println(s);
				}
			} else {
				System.out.println("All Lexer tokensSpec Tokens are referenced in at least one ->type() command");
			}
		}

		if (listener.getModeSpecs().size() > 0) {
			System.out.println(
				"searching for " 
				+ listener.getModeSpecs().size()
				+ " modeSpecs in " 
				+ listener.getLexerRules().size()
				+ " lexer rules"
				);
			ArrayList<String> fallenLeafModeSpecs = 
				searchForFallenLeafModeSpecs(
					listener.getLexerRules()
					, listener.getModeSpecs());
			if (fallenLeafModeSpecs.size() > 0) {
				System.out.println("Lexer modeSpecs with no ->[push|pop]mode() command referencing them...");
				for (String s: fallenLeafModeSpecs) {
					System.out.println(s);
				}
			} else {
				System.out.println("All modeSpecs are referenced in at least one ->[push|pop]mode() command");
			}
		}

		return;
	}

	/**
	Search all <code>lexerRuleSpec</code>s and <code>parserRuleSpec</code>s for tokens
	and return a list of those not found.  These are potential cruft.
	*/
	private static ArrayList<String> searchForFallenLeafTokens(
		ArrayList<ANTLRv4Parser.LexerRuleSpecContext> lexerRules
		, ArrayList<ANTLRv4Parser.ParserRuleSpecContext> parserRules
		, ArrayList<String> lexerTokens) {
		
		ArrayList<String> fallenLeafTokens = new ArrayList<>();
		for (String s: lexerTokens) {
			Boolean foundItInLexerRule = false;
			Boolean foundItInParserRule = false;
			if (verbose) System.out.println("searching for " + s);
			lexerLoop:
			for (ANTLRv4Parser.LexerRuleSpecContext lrsCtx: lexerRules) {
				ANTLRv4Parser.LexerRuleBlockContext lrbCtx = lrsCtx.lexerRuleBlock();
				ANTLRv4Parser.LexerAltListContext lalCtx = lrbCtx.lexerAltList();
				foundItInLexerRule = searchLexerAltList(lalCtx, s, lrsCtx.TOKEN_REF().getSymbol().getText());
				if (foundItInLexerRule) {
					break lexerLoop;
				}
			}
			if (!foundItInLexerRule) {
				parserLoop:
				for (ANTLRv4Parser.ParserRuleSpecContext prsCtx: parserRules) {
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
					fallenLeafTokens.add(s);
				}
			}
		}
		
		return fallenLeafTokens;
	}

	/**
	Search for channels which were specified in a <code>prequelConstruct</code> but are
	never used in a <code>lexerCommand</code>.  Recursively checking for <code>lexerCommand</code>s 
	in <code>lexerCommands</code> in <code>lexerAlt</code>s in <code>lexerAltList</code>s is
	not necessary as a <code>lexerCommand</code> can only be specified once per <code>lexerRuleSpec</code>
	as per the "Lexer Commands" section on page 283 of _The Definitive ANTLR 4 Reference_
	ISBN 978-1-93435-699-9.
	*/
	private static ArrayList<String> searchForFallenLeafChannels(
		ArrayList<ANTLRv4Parser.LexerRuleSpecContext> lexerRules
		, ArrayList<String> lexerChannels) {
		
		ArrayList<String> fallenLeafChannels = new ArrayList<>();
		for (String s: lexerChannels) {
			Boolean foundChannel = false;
			if (verbose) System.out.println("searching for " + s );
			lexerLoop:
			for (ANTLRv4Parser.LexerRuleSpecContext lrsCtx: lexerRules) {
				ANTLRv4Parser.LexerRuleBlockContext lrbCtx = lrsCtx.lexerRuleBlock();
				ANTLRv4Parser.LexerAltListContext lalCtx = lrbCtx.lexerAltList();
				for (ANTLRv4Parser.LexerAltContext laCtx: lalCtx.lexerAlt()) {
					ANTLRv4Parser.LexerCommandsContext lcCtx = laCtx.lexerCommands();
					if (lcCtx != null) {
						for (ANTLRv4Parser.LexerCommandContext lexerCmdCtx: lcCtx.lexerCommand()) {
							ANTLRv4Parser.LexerCommandNameContext lcnCtx = lexerCmdCtx.lexerCommandName();
							if (lcnCtx.getText().equals("channel") && lexerCmdCtx.lexerCommandExpr() != null) {
								if (lexerCmdCtx.lexerCommandExpr().getText().equals(s)) {
									foundChannel = true;
									if (verbose) System.out.println("\tfound in lexer rule " + lrsCtx.TOKEN_REF().getSymbol().getText());
									break lexerLoop;
								}
							}
						}
					}
				}
			}
			if (!foundChannel) {
				fallenLeafChannels.add(s);
			}
		}
		
		return fallenLeafChannels;
	}

	/**
	Search all <code>lexerRuleSpec</code>s for <code>->type()</code> commands referencing tokens
	specified in a tokensSpec and return a list of those not found.  These are potential cruft.
	*/
	private static ArrayList<String> searchForFallenLeafTokensSpecTokens(
		ArrayList<ANTLRv4Parser.LexerRuleSpecContext> lexerRules
		, ArrayList<String> lexerTokensSpecTokens) {
		
		ArrayList<String> fallenLeafTokensSpecTokens = new ArrayList<>();
		for (String s: lexerTokensSpecTokens) {
			Boolean foundItInLexerRule = false;
			if (verbose) System.out.println("searching for " + s);
			lexerLoop:
			for (ANTLRv4Parser.LexerRuleSpecContext lrsCtx: lexerRules) {
				ANTLRv4Parser.LexerRuleBlockContext lrbCtx = lrsCtx.lexerRuleBlock();
				ANTLRv4Parser.LexerAltListContext lalCtx = lrbCtx.lexerAltList();
				for (ANTLRv4Parser.LexerAltContext laCtx: lalCtx.lexerAlt()) {
					ANTLRv4Parser.LexerCommandsContext lcCtx = laCtx.lexerCommands();
					if (lcCtx != null) {
						for (ANTLRv4Parser.LexerCommandContext lexerCmdCtx: lcCtx.lexerCommand()) {
							ANTLRv4Parser.LexerCommandNameContext lcnCtx = lexerCmdCtx.lexerCommandName();
							if (lcnCtx.getText().equals("type") && lexerCmdCtx.lexerCommandExpr() != null) {
								if (lexerCmdCtx.lexerCommandExpr().getText().equals(s)) {
									foundItInLexerRule = true;
									break lexerLoop;
								}
							}
						}
					}
				}
			}
			if (!foundItInLexerRule) {
				fallenLeafTokensSpecTokens.add(s);
			}
		}
		
		return fallenLeafTokensSpecTokens;
	}

	/**
	Search for Lexer modes which were specified in a <code>modeSpec</code> but are
	never used in a <code>lexerCommand</code>.  Recursively checking for <code>lexerCommand</code>s 
	in <code>lexerCommands</code> in <code>lexerAlt</code>s in <code>lexerAltList</code>s is
	not necessary as a <code>lexerCommand</code> can only be specified once per <code>lexerRuleSpec</code>
	as per the "Lexer Commands" section on page 283 of _The Definitive ANTLR 4 Reference_
	ISBN 978-1-93435-699-9.
	*/
	private static ArrayList<String> searchForFallenLeafModeSpecs(
		ArrayList<ANTLRv4Parser.LexerRuleSpecContext> lexerRules
		, ArrayList<String> modeSpecs) {
		
		ArrayList<String> modeCommands = new ArrayList<>();
		modeCommands.add("mode");
		modeCommands.add("pushMode");
		modeCommands.add("popMode");
		
		ArrayList<String> fallenLeafModeSpecs = new ArrayList<>();
		for (String s: modeSpecs) {
			Boolean foundModeSpec = false;
			if (verbose) System.out.println("searching for " + s );
			lexerLoop:
			for (ANTLRv4Parser.LexerRuleSpecContext lrsCtx: lexerRules) {
				ANTLRv4Parser.LexerRuleBlockContext lrbCtx = lrsCtx.lexerRuleBlock();
				ANTLRv4Parser.LexerAltListContext lalCtx = lrbCtx.lexerAltList();
				for (ANTLRv4Parser.LexerAltContext laCtx: lalCtx.lexerAlt()) {
					ANTLRv4Parser.LexerCommandsContext lcCtx = laCtx.lexerCommands();
					if (lcCtx != null) {
						for (ANTLRv4Parser.LexerCommandContext lexerCmdCtx: lcCtx.lexerCommand()) {
							ANTLRv4Parser.LexerCommandNameContext lcnCtx = lexerCmdCtx.lexerCommandName();
							if (modeCommands.contains(lcnCtx.getText()) && lexerCmdCtx.lexerCommandExpr() != null) {
								if (lexerCmdCtx.lexerCommandExpr().getText().equals(s)) {
									foundModeSpec = true;
									if (verbose) System.out.println("\tfound in lexer rule " + lrsCtx.TOKEN_REF().getSymbol().getText());
									break lexerLoop;
								}
							}
						}
					}
				}
			}
			if (!foundModeSpec) {
				fallenLeafModeSpecs.add(s);
			}
		}
		
		return fallenLeafModeSpecs;
	}

	/**
	A <code>lexerAltList<code> contains a collection of <code>lexerAlt</code>, each of which contains a 
	<code>lexerElements</code>, which contains a collection of <code>lexerElement</code>, which contains 
	either a <code>lexerAtom</code> or a <code>lexerBlock</code>.  A <code>lexerAtom</code> might refer to the token 
	we're looking for.  A <code>lexerBlock</code> might contain a <code>lexerAltList</code>, in which
	case we recurse.
	
	A <code>lexerElement</code> might also contain other things, but we don't care about those.
	*/
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
							if (verbose) System.out.println("\tfound in lexer rule " + lexerRuleName);
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
	
	/**
	A Parser rule contains an <code>alternative</code>, which contains a collection of
	<code>element</code>, which contains an <code>atom</code>, which contains a <code>terminal</code> which may
	refer to the token we're searching for.
	
	An <code>element</code> may also contain an <code>ebnf</code>, which contains a <code>block</code>, which contains 
	an <code>altList</code>, which contains a collection of <code>alternative</code>, in which case we
	recurse.
	*/
	private static Boolean searchParserAlternative(ANTLRv4Parser.AlternativeContext altCtx, String aString, String parserRuleName) {
		//System.out.println("searchParserAlternative(" + altCtx.getText() + ", " + aString + ", " + parserRuleName + ")");
		Boolean foundIt = false;
		
		loop:
		for (ANTLRv4Parser.ElementContext elmntCtx: altCtx.element()) {
			if (elmntCtx.atom() != null) {
				if (elmntCtx.atom().terminal() != null) {
					if (elmntCtx.atom().terminal().TOKEN_REF() != null) {
						if (elmntCtx.atom().terminal().TOKEN_REF().getText().equals(aString)) {
							if (verbose) System.out.println("\tfound in parser rule " + parserRuleName);
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
	
	/**
	Lex and parse the indicated grammar file, then walk the parse tree with
	the GrammarListener which will collect tokens of possible interest, lexer 
	rules, and parser rules.
	*/
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
	
	/**
	Process command line options.
	*/
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
		Option verbose_ = new Option("verbose", false, "print more detailed progress messages");
		Option help = new Option("help", false, "print this message");

		options.addOption(lexerGrammar);
		options.addOption(parserGrammar);
		options.addOption(combinedGrammar);
		options.addOption(path);
		options.addOption(verbose_);
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

		if (line.hasOption("verbose_")) {
			verbose = true;
		}

		if (line.hasOption("combinedGrammar")) {
			combinedFileName = line.getOptionValue("combinedGrammar");
		} else if (line.hasOption("lexerGrammar") && line.hasOption("parserGrammar")) {
			lexerFileName = line.getOptionValue("lexerGrammar");
			parserFileName = line.getOptionValue("parserGrammar");
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

