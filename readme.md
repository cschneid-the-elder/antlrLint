Attempt at a Lint-like function for ANTLR4 grammars.

Grammars with thousands of rules pose a challenge as maintenance 
deprecates tokens.  This is an attempt to automate, or at least
semi-automate, "weeding" such large-ish grammars.

A list of tokens to which no rule refers is written to stdout.  This
list does not include tokens with action blocks or lexer commands,
as the intent is to locate cruft and such tokens likely have a use.

A list of channels in a `channels{}` channelsSpec to which no rule 
refers is written to stdout.

A list of tokens in a `tokens {}` tokensSpec which do not appear in 
any `->type()` commands is written to stdout.

Grammars specified in `import` statements are processed.

A list of Lexer `mode`s not referenced in any `->pushMode()`, 
`->popMode()`, or `mode()` command is written to stdout.

Please keep in mind that these are only leading indicators that something
_may_ be ahoo in your grammar.  If you have overridden the superclass of
the Lexer or have `actionBlock`s that use the ANTLR runtime this application 
is not going to find those references.

    usage: AntlrLint [-combinedGrammar <arg>] [-help] [-lexerGrammar <arg>]
           [-parserGrammar <arg>] [-path <arg>] [-verbose]
     -combinedGrammar <arg>   path and file name of a single combined grammar
                              to preprocess
     -help                    print this message
     -lexerGrammar <arg>      path and file name of a single lexer grammar to
                              preprocess, ignored if combinedGrammar is
                              provided
     -parserGrammar <arg>     path and file name of a single parser grammar to
                              preprocess, ignored if combinedGrammar is
                              provided
     -path <arg>              path where imported files are located including
                              trailing file system separator
     -verbose                 print more detailed progress messages


