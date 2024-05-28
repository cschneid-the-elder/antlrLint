
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

public class GrammarListener extends ANTLRv4ParserBaseListener {
	private ArrayList<String> lexerTokens = new ArrayList<>();
	private ArrayList<ANTLRv4Parser.LexerRuleSpecContext> lexerRules = new ArrayList<>();
	private ArrayList<ANTLRv4Parser.ParserRuleSpecContext> parserRules = new ArrayList<>();

	public GrammarListener(
		) {
		super();
	}

	@Override public void enterLexerRuleSpec(ANTLRv4Parser.LexerRuleSpecContext ctx) { 
		Boolean foundIt = false;
		Boolean skipIt = false;
		
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
						this.lexerTokens.add(lexerCmdCtx.lexerCommandExpr().getText());
						foundIt = true;
						break loop;
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
		
		if (!foundIt && !skipIt) {
			this.lexerTokens.add(ctx.TOKEN_REF().getSymbol().getText());
		}

	}
	
	@Override public void enterParserRuleSpec(ANTLRv4Parser.ParserRuleSpecContext ctx) {
		this.parserRules.add(ctx);
	}

	public ArrayList<String> getLexerTokens() {
		return this.lexerTokens;
	}
	
	public ArrayList<ANTLRv4Parser.LexerRuleSpecContext> getLexerRules() {
		return this.lexerRules;
	}

	public ArrayList<ANTLRv4Parser.ParserRuleSpecContext> getParserRules() {
		return this.parserRules;
	}

}

