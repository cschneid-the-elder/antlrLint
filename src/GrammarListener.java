
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
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

/**
Collect Lexer rules, Parser rules, and Lexer Tokens of interest.
*/

public class GrammarListener extends ANTLRv4ParserBaseListener {
	private ArrayList<String> lexerTokens = new ArrayList<>();
	private ArrayList<String> lexerChannels = new ArrayList<>();
	private ArrayList<String> lexerTokensSpecTokens = new ArrayList<>();
	private ArrayList<String> delegateGrammars = new ArrayList<>();
	private ArrayList<ANTLRv4Parser.LexerRuleSpecContext> lexerRules = new ArrayList<>();
	private ArrayList<ANTLRv4Parser.ParserRuleSpecContext> parserRules = new ArrayList<>();

	public GrammarListener(
		) {
		super();
	}

	/**
	Collect Lexer rules, and Lexer Tokens of interest.
	
	Tokens of interest do not have any action blocks or lexer commands
	associated with them, except for the "type" command for which we
	collect the type being emitted.
	*/
	@Override public void enterLexerRuleSpec(ANTLRv4Parser.LexerRuleSpecContext ctx) { 
		Boolean skipIt = false;
		String typeName = null;
		
		lexerRules.add(ctx);
		ANTLRv4Parser.LexerRuleBlockContext lrbCtx = ctx.lexerRuleBlock();
		ANTLRv4Parser.LexerAltListContext lalCtx = lrbCtx.lexerAltList();

		loop:
		for (ANTLRv4Parser.LexerAltContext laCtx: lalCtx.lexerAlt()) {
			ANTLRv4Parser.LexerCommandsContext lcCtx = laCtx.lexerCommands();
			if (lcCtx != null) {
				for (ANTLRv4Parser.LexerCommandContext lexerCmdCtx: lcCtx.lexerCommand()) {
					ANTLRv4Parser.LexerCommandNameContext lcnCtx = lexerCmdCtx.lexerCommandName();
					//System.out.println(ctx.TOKEN_REF().getSymbol().getText() + " " + lcnCtx.getText());
					if (lcnCtx.getText().equals("type") && lexerCmdCtx.lexerCommandExpr() != null) {
						/*
						If the token has a "type" command associated with it, collect that
						type instead of the token name.
						*/
						typeName = lexerCmdCtx.lexerCommandExpr().getText();
					}
					if (lcnCtx.getText().equals("skip")) {
						skipIt = true;
						break loop;
					}
					if (lcnCtx.getText().equals("channel")) {
						skipIt = true;
						break loop;
					}
					if (lcnCtx.getText().equals("pushMode")) {
						skipIt = true;
						break loop;
					}
					if (lcnCtx.getText().equals("popMode")) {
						skipIt = true;
						break loop;
					}
				}
			}
		}
		
		if (!skipIt) {
			skipIt = this.lexerAltListContainsActionBlock(lalCtx);
		}
		
		if (!skipIt) {
			String s = ctx.TOKEN_REF().getSymbol().getText();
			if (typeName != null) {
				s = typeName;
			}
			if (!this.lexerTokens.contains(s)) {
				this.lexerTokens.add(s);
			}
		}

	}
	
	/**
	Collect Parser rules.
	*/
	@Override public void enterParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
		this.parserRules.add(ctx);
	}

	/**
	Collect channel names.
	*/
	@Override public void enterChannelsSpec(ANTLRv4Parser.ChannelsSpecContext ctx) {
		if (ctx.idList() != null) {
			if (ctx.idList().identifier() != null) {
				for (ANTLRv4Parser.IdentifierContext idCtx: ctx.idList().identifier()) {
					if (!this.lexerChannels.contains(idCtx.getText())) {
						this.lexerChannels.add(idCtx.getText());
					}
				}
			}
		}
	}

	/**
	Collect names of tokens specified in a <code>tokens{}</code> tokensSpec.
	*/
	@Override public void enterTokensSpec(ANTLRv4Parser.TokensSpecContext ctx) {
		if (ctx.idList() != null) {
			if (ctx.idList().identifier() != null) {
				for (ANTLRv4Parser.IdentifierContext idCtx: ctx.idList().identifier()) {
					this.lexerTokensSpecTokens.add(idCtx.getText());
				}
			}
		}
	}

	/**
	Collect names of imported grammars.
	*/
	@Override public void enterDelegateGrammar(ANTLRv4Parser.DelegateGrammarContext ctx) {
		if (ctx.identifier() != null) {
			for (ANTLRv4Parser.IdentifierContext idCtx: ctx.identifier()) {
				delegateGrammars.add(idCtx.getText());
			}
		}
	}

	/**
	Recursive search of <code>lexerAltList</code> looking for an <code>actionBlock</code>.  
	Return true if found.
	*/
	private Boolean lexerAltListContainsActionBlock(ANTLRv4Parser.LexerAltListContext altListCtx) {
		Boolean foundIt = false;

		loop:
		for (ANTLRv4Parser.LexerAltContext laCtx: altListCtx.lexerAlt()) {
			ANTLRv4Parser.LexerElementsContext lesCtx = laCtx.lexerElements();
			for(ANTLRv4Parser.LexerElementContext leCtx: lesCtx.lexerElement()) {
				if (leCtx.actionBlock() != null) {
					foundIt = true;
					break loop;
				} else if (leCtx.lexerBlock() != null) {
					if (leCtx.lexerBlock().lexerAltList() != null) {
						if (this.lexerAltListContainsActionBlock(leCtx.lexerBlock().lexerAltList())) {
							foundIt = true;
							break loop;
						}
					}
				}
			}
		}
		
		return foundIt;
	}
	
	public ArrayList<String> getLexerTokens() {
		return this.lexerTokens;
	}
	
	public ArrayList<String> getLexerChannels() {
		return this.lexerChannels;
	}
	
	public ArrayList<String> getLexerTokensSpecTokens() {
		return this.lexerTokensSpecTokens;
	}
	
	public ArrayList<String> getDelegateGrammars() {
		return this.delegateGrammars;
	}
	
	public ArrayList<ANTLRv4Parser.LexerRuleSpecContext> getLexerRules() {
		return this.lexerRules;
	}

	public ArrayList<ANTLRv4Parser.ParserRuleSpecContext> getParserRules() {
		return this.parserRules;
	}

}

