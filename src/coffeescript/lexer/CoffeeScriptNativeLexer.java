package coffeescript.lexer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static coffeescript.lexer.Helpers.*;

public class CoffeeScriptNativeLexer {
    
    private static final String UTF8_BOM = "\uFEFF";
    private static final Pattern TRAILING_SPACES = Pattern.compile("\\s+$");
    private static final Pattern WHITESPACE = Pattern.compile("^[^\\n\\S]+");
    private static final Pattern IDENTIFIER = Pattern.compile("^([$A-Za-z_\\x7f-\\uffff][$\\w\\x7f-\\uffff]*)([^\\n\\S]*:(?!:))?");
    private static final Pattern NUMBER = Pattern.compile("^0b[01]+|^0o[0-7]+|^0x[\\da-f]+|^\\d*\\.?\\d+(?:e[+-]?\\d+)?",Pattern.CASE_INSENSITIVE);
    private static final Pattern HEREDOC = Pattern.compile("^(\"\"\"|''')((?:\\\\[\\s\\S]|[^\\\\])*?)(?:\\n[^\\n\\S]*)?\\1");
    private static final Pattern OPERATOR = Pattern.compile("^(?:[-=]>|[-+*\\/%<>&|^!?=]=|>>>=?|([-+:])\\1|([&|<>*\\/%])\\2=?|\\?(\\.|::)|\\.{2,3})");
    private static final Pattern COMMENT = Pattern.compile("^###([^#][\\s\\S]*?)(?:###[^\\n\\S]*|###$)|^(?:\\s*#(?!##[^#]).*)+");
    private static final Pattern PRE_COMMENT = Pattern.compile("^\\s*#");
    private static final Pattern CODE = Pattern.compile("^[-=]>");
    private static final Pattern MULTI_DENT = Pattern.compile("^(?:\\n[^\\n\\S]*)+");
    private static final Pattern SIMPLESTR = Pattern.compile("^'[^\\\\']*(?:\\\\[\\s\\S][^\\\\']*)*'");
    private static final Pattern QUOTED_STR = Pattern.compile("^\"[^\\\\\"]*(?:\\\\[\\s\\S][^\\\\\"]*)*\"");    
    private static final Pattern JSTOKEN = Pattern.compile("^`[^\\\\`]*(?:\\\\.[^\\\\`]*)*`");
    private static final Pattern REGEX = Pattern.compile("^(\\/(?![\\s=])[^"+ Pattern .quote("[") +"\\/\\n\\\\]*(?:(?:\\\\[\\s\\S]|\\[[^\\]\\n\\\\]*(?:\\\\[\\s\\S][^\\]\\n\\\\]*)*])[^"+ Pattern.quote("[") +"\\/\\n\\\\]*)*\\/)([imgy]{0,4})(?!\\w)");
    private static final Pattern HEREGEX = Pattern.compile("^\\/{3}((?:\\\\?[\\s\\S])+?)\\/{3}([imgy]{0,4})(?!\\w)");
    private static final Pattern HEREDOC_ILLEGAL = Pattern.compile("\\*\\/");
    private static final Pattern LINE_CONTINUER = Pattern.compile("^\\s*(?:,|\\??\\.(?![.\\d])|::)");
    private static final Pattern RADIX_PREFIX = Pattern.compile("^0[BOX]");
    private static final Pattern EXP_NOTATION_1 = Pattern.compile("E");
    private static final Pattern EXP_NOTATION_2 = Pattern.compile("^0x");
    private static final Pattern DECIMAL_PREFIX = Pattern.compile("^0\\d*[89]");
    private static final Pattern OCTAL_PREFIX = Pattern.compile("^0\\d+");
    private static final Pattern HEREGEX_START_TEST = Pattern.compile("^\\s*\\*");

    private static final Set<String> COMPOUND_ASSIGN = new HashSet(Arrays.asList("-=", "+=", "/=", "*=", "%=", "||=", "&&=", "?=", "<<=", ">>=", ">>>=", "&=", "^=", "|=", "**=", "//=", "%%="));
    private static final Set<String> UNARY = new HashSet(Arrays.asList("NEW", "TYPEOF", "DELETE", "DO"));
    private static final Set<String> UNARY_MATH = new HashSet(Arrays.asList("!", "~"));
    private static final Set<String> LOGIC = new HashSet(Arrays.asList("&&", "||", "&", "|", "^"));
    private static final Set<String> SHIFT = new HashSet(Arrays.asList("<<", ">>", ">>>"));
    private static final Set<String> COMPARE = new HashSet(Arrays.asList("==", "!=", "<", ">", "<=", ">="));
    private static final Set<String> MATH = new HashSet(Arrays.asList("*", "/", "%", "//", "%%"));
    private static final Set<String> RELATION = new HashSet(Arrays.asList("IN", "OF", "INSTANCEOF"));
    private static final Set<String> BOOL = new HashSet(Arrays.asList("TRUE", "FALSE"));
    private static final Set<String> NOT_REGEX = new HashSet(Arrays.asList("NUMBER", "REGEX", "BOOL", "NULL", "UNDEFINED", "++", "--"));
    private static final Set<String> CALLABLE = new HashSet(Arrays.asList("IDENTIFIER", "STRING", "REGEX", ")", "]", "}", "?", "::", "@", "THIS", "SUPER"));
    private static final Set<String> LINE_BREAK = new HashSet(Arrays.asList("INDENT", "OUTDENT", "TERMINATOR"));
    private static final Set<String> INDENTABLE_CLOSERS = new HashSet(Arrays.asList(")", "}", "]"));    
    private static final Set<String> JS_KEYWORDS = new HashSet(Arrays.asList("true", "false", "null", "this", "new", "delete", "typeof", "in", "instanceof", "return", "throw", "break", "continue", "debugger", "if", "else", "switch", "for", "while", "do", "try", "catch", "finally", "class", "extends", "super"));
    private static final Set<String> COFFEE_KEYWORDS = new HashSet(Arrays.asList("undefined", "then", "unless", "until", "loop", "of", "by", "when"));    
    private static final Set<String> COFFEE_ALIASES = new HashSet(Arrays.asList("and", "or", "is", "isnt", "not", "yes", "no", "on", "off"));    
    private static final Set<String> RESERVED = new HashSet(Arrays.asList("case", "default", "function", "var", "void", "with", "const", "let", "enum", "export", "import", "native", "__hasProp", "__extends", "__slice", "__bind", "__indexOf", "implements", "interface", "package", "private", "protected", "public", "static", "yield"));
    private static final Set<String> STRICT_PROSCRIBED = new HashSet(Arrays.asList("arguments", "eval"));
    private static final Set<String> UNFINISHED = new HashSet(Arrays.asList("\\", ".", "?." ,"?::" ,"UNARY" ,"MATH" ,"UNARY_MATH" ,"+" ,"-" ,"**" ,"SHIFT" ,"RELATION" ,"COMPARE" ,"LOGIC" ,"THROW" ,"EXTENDS"));;
    private static final Set<String> INDEXABLE = new HashSet(Arrays.asList("NUMBER", "BOOL", "NULL", "UNDEFINED"));
    private static final Set<String> NOT_SPACED_REGEX = new HashSet(Arrays.asList(")", "}", "THIS", "IDENTIFIER", "STRING", "]"));
    private static final Set<String> JS_FORBIDDEN = new HashSet(JS_KEYWORDS);
    private static final Map<String,String> COFFEE_ALIAS_MAP = new HashMap();    

    
    static {
        init();
        //concat INDEXABLE with CALLABLE
        INDEXABLE.addAll(CALLABLE);
        
        //concat NOT_SPACED_REGEX with NOT_REGEX
        NOT_SPACED_REGEX.addAll(NOT_REGEX);
        
        //concat COFFEE_KEYWORDS with COFFEE_ALIAS_MAP
        COFFEE_KEYWORDS.addAll(COFFEE_ALIAS_MAP.keySet());
        
        JS_FORBIDDEN.addAll(RESERVED);
        JS_FORBIDDEN.addAll(STRICT_PROSCRIBED);
    }
    
    private static void init() {
        COFFEE_ALIAS_MAP.put("and", "&&");
        COFFEE_ALIAS_MAP.put("or", "||");
        COFFEE_ALIAS_MAP.put("is", "==");
        COFFEE_ALIAS_MAP.put("isnt", "!=");
        COFFEE_ALIAS_MAP.put("not", "!");
        COFFEE_ALIAS_MAP.put("yes", "true");
        COFFEE_ALIAS_MAP.put("no", "false");
        COFFEE_ALIAS_MAP.put("on", "true");
        COFFEE_ALIAS_MAP.put("off", "false");
    }

    private final Stack<String> ends;
    private Map<String, Matcher> matchers;
    private List<CoffeeScriptNativeToken> tokens;
    private String code;
    private String chunk;
    private int chunkLine;
    private int chunkColumn;
    private boolean seenFor;
    private int indent;
    private int indebt;
    private int baseIndent;
    private int outdebt;
    private Stack<Integer> indents;
    
    public CoffeeScriptNativeLexer(String code) {
        this.code = code;
        this.ends = new Stack<String>();
        this.tokens = new ArrayList<CoffeeScriptNativeToken>();
        this.indents = new Stack<Integer>();  
        this.matchers = new HashMap<String, Matcher>();
    }
    
    public List<CoffeeScriptNativeToken> tokenize(boolean rewrite) throws CoffeeScriptNativeLexerException {
        String tag;
        this.chunkLine = 0;
        this.chunkColumn = 0;
        this.code = clean(code);
        int consumed, i = 0;
        this.chunk = slice(this.code,i);
        while (!this.chunk.isEmpty()) {            
            consumed = consume();
            int[] coordinates = getLineAndColumnFromChunk(consumed);
            this.chunkLine = coordinates[0];
            this.chunkColumn = coordinates[1];
            i += consumed;
            this.chunk = slice(this.code,i);
            
        }
        closeIndentation();
        if((tag = stackPop(ends)) != null) {
            error("missing "+ tag);
        }
        if(rewrite) {
            return new Rewriter(this.tokens).rewrite();
        }
        return this.tokens;
    }
    
    private int consume() throws CoffeeScriptNativeLexerException {
        int consumed;
        if((consumed = identifierToken()) != 0) return consumed;
        if((consumed = commentToken()) != 0) return consumed;
        if((consumed = whitespaceToken()) != 0) return consumed;
        if((consumed = lineToken()) != 0) return consumed;
        if((consumed = heredocToken()) != 0) return consumed;
        if((consumed = stringToken()) != 0) return consumed;
        if((consumed = numberToken()) != 0) return consumed;
        if((consumed = regexToken()) != 0) return consumed;
        if((consumed = jsToken()) != 0) return consumed;
        if((consumed = literalToken()) != 0) return consumed;
        return consumed;
    }
    
    private String clean(String code) {
        if(code.startsWith(UTF8_BOM)) {
            code = slice(code, 1);
        }
        code = code.replaceAll("\\r", "").replaceAll(TRAILING_SPACES.pattern(), "");
        if(getMatcher(WHITESPACE, code).find()) {
            code = "\n" + code;
            this.chunkLine--;
        }
        return code;
    }

    private int identifierToken() throws CoffeeScriptNativeLexerException {
        String tag;
        char first = this.chunk.charAt(0);
        if(!((first >= 'A' && first <= 'Z') || (first >= 'a' && first <= 'z') || (first >= '\u007f' && first <= '\uffff') || first == '$' || first == '_')) return 0;
        Matcher m = getMatcher(IDENTIFIER, this.chunk);
        if(!m.find()) {
            return 0;
        }
        String input = m.group(0), id = m.group(1), colon = m.group(2);
        int idLength = id.length();
        
        if(nullSafeCompare(id, "own") && nullSafeCompare(lastTag(tokens), "FOR")) {
            token("OWN", id, 0, -1);
            return id.length();        
        }
        CoffeeScriptNativeToken prev = last(tokens);
        boolean forcedIdentifier = colon != null || (prev != null) && 
                (nullSafeCompare(prev.getTag(), ".") || nullSafeCompare(prev.getTag(), "?.") || nullSafeCompare(prev.getTag(), "::") || nullSafeCompare(prev.getTag(), "?::") ||
                !prev.getSpaced() && nullSafeCompare(prev.getTag(),"@"));
        tag = "IDENTIFIER";
        CoffeeScriptNativeToken poppedToken = null;
        if(!forcedIdentifier && (containsNullSafe(id, JS_KEYWORDS) || containsNullSafe(id, COFFEE_KEYWORDS))) {
            tag = id.toUpperCase();
            
            if(nullSafeCompare(tag, "WHEN") && containsNullSafe(lastTag(tokens), LINE_BREAK)) {
                tag = "LEADING_WHEN";
            } else if(nullSafeCompare(tag, "FOR")) {
                this.seenFor = true;
            } else if(nullSafeCompare(tag, "UNLESS")) {
                tag = "IF";
            } else if(containsNullSafe(tag, UNARY)) {
                tag = "UNARY";
            } else if(containsNullSafe(tag, RELATION)) {
                if(!nullSafeCompare(tag, "INSTANCEOF") && this.seenFor) {
                    tag = "FOR" + tag;
                    this.seenFor = false;
                } else {
                    tag = "RELATION";
                    if(nullSafeCompare(lastValue(tokens), "!")) {
                        poppedToken = tokensPop();
                        id = "!" + id;
                    }
                }                
            }
        }
        boolean reserved = false;
        if(containsNullSafe(id, JS_FORBIDDEN)) {
            if(forcedIdentifier) {
                tag = "IDENTIFIER";
                reserved = true;
            } else if(containsNullSafe(id, RESERVED)) {
                error("reserved word " + id);
            }
        }
        if(!forcedIdentifier) {
            if(containsNullSafe(id, COFFEE_ALIASES)) {
                id = COFFEE_ALIAS_MAP.get(id);
            }
            switch(id) {
                case "!":
                    tag = "UNARY";
                    break;
                case "==":
                case "!=":
                    tag = "COMPARE";
                    break;
                case "&&":
                case "||":
                    tag = "LOGIC";
                    break;
                case "true":
                case "false":
                    tag = "BOOL";
                    break;
                case "break":
                case "continue":
                    tag = "STATEMENT";
            }
        }
        CoffeeScriptNativeToken tagToken = token(tag, id, 0, idLength);
        tagToken.setReserved(reserved);
        if(poppedToken != null) {
            tagToken.setFirstColumn(poppedToken.getFirstColumn());
            tagToken.setFirstLine(poppedToken.getFirstLine());
        }
        if(colon != null) {
            int colonOffset = input.lastIndexOf(":");
            token(":", ":", colonOffset, colon.length());
        }
        return input.length();
    }

    private int commentToken() throws CoffeeScriptNativeLexerException {
        Matcher pre = getMatcher(PRE_COMMENT, this.chunk);
        if(!pre.find()) return 0;
        Matcher m = getMatcher(COMMENT, this.chunk);
        if(!m.find()) {
            return 0;
        }
        String comment = m.group(0), here = m.group(1);        
        if(here != null) {
            Map<String, Object> options = new HashMap<String,Object>();
            options.put("herecomment", true);
            options.put("indent", repeat(" ", this.indent));
            token("HERECOMMENT", sanitizeHeredoc(here, options), 0, comment.length());
        }
        return comment.length();
    }

    private int whitespaceToken() {
        Matcher m = getMatcher(WHITESPACE, this.chunk);
        boolean found;
        if(!((found = m.find()) || (this.chunk.charAt(0)) == '\n')) {
            return 0;
        }
        CoffeeScriptNativeToken prev = last(tokens);
        if(prev != null) {
            if(found) {
                prev.setSpaced(true);
            } else {
                prev.setNewLine(true);
            }
        }
        if(found) {
            return m.group(0).length();
        } else {
            return 0;
        }
    }

    private int lineToken() throws CoffeeScriptNativeLexerException {
        
        String indent;
        int size, diff;
        boolean noNewLines;
        if(this.chunk.charAt(0) != '\n') return 0;
        Matcher m = getMatcher(MULTI_DENT, this.chunk);
        if(!m.find()) {
            return 0;
        }
        indent = m.group(0);
        this.seenFor = false;
        size = indent.length() - 1 - indent.lastIndexOf("\n");
        noNewLines = unfinished();
        if(size - this.indebt == this.indent) {
            if(noNewLines) {
                suppressNewlines();
            } else {
                newlineToken(0);
            }
            return indent.length();
        }
        if(size > this.indent) {
            if(noNewLines) {
                this.indebt = size - this.indent;
                suppressNewlines();
                return indent.length();
            }
            if(this.tokens.isEmpty()) {
                this.baseIndent = this.indent = size;
                return indent.length();
            }
            diff = size - this.indent + this.outdebt;
            token("INDENT",String.valueOf(diff), indent.length()-size,size);
            this.indents.push(diff);
            this.ends.push("OUTDENT");
            this.outdebt = this.indebt = 0;
            this.indent = size;
        } else if(size < this.baseIndent) {
            error("missing indentation", indent.length());
        } else {
            this.indebt = 0;
            this.outdentToken(this.indent - size, noNewLines, indent.length());
        }
        return indent.length();
    }

    private int heredocToken() throws CoffeeScriptNativeLexerException {
        if(!(this.chunk.startsWith("\"\"\"") || this.chunk.startsWith("'''"))) return 0;
        Matcher m = getMatcher(HEREDOC, this.chunk);
        if(!m.find()) {
            return 0;
        }
        String heredoc = m.group(0);
        String quote = String.valueOf(heredoc.charAt(0));
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("quote", quote);
        options.put("indent", null);
        String doc = sanitizeHeredoc(code, options);
        token("STRING", heredoc, 0, heredoc.length());
        return heredoc.length();
    }

    private int stringToken() {
        String quote = String.valueOf(this.chunk.charAt(0));
        String string = null;
        switch(quote) {
            case "'" :
                Matcher m = getMatcher(SIMPLESTR, this.chunk);
                if(!m.find()) return 0;
                string = m.group(0);
                break;
            case "\"": 
                Matcher m1 = getMatcher(QUOTED_STR, this.chunk);
                if(!m1.find()) return 0;
                string = m1.group(0);
                break;
        }
        if(string == null) {
            return 0;
        }
        token("STRING", string, 0, string.length());
        return string.length();
    }

    private int numberToken() throws CoffeeScriptNativeLexerException {
        if(!(chunk.charAt(0) >= '0' && chunk.charAt(0) <= '9')) return 0;
        Matcher m = getMatcher(NUMBER, this.chunk);
        if(!m.find()) {
            return 0;
        }
        String number = m.group(0);
        if(testRegexp(RADIX_PREFIX, number)) {
            error("radix prefix '" + number + "' must be lowercase"); 
        } else if(testRegexp(EXP_NOTATION_1, number) && !testRegexp(EXP_NOTATION_2, number)) {
            error("exponential notation '" + number + "' must be indicated with a lowercase 'e'");
        } else if(testRegexp(DECIMAL_PREFIX, number)) {
            error("decimal literal '" + number + "' must not be prefixed with '0'");
        } else if(testRegexp(OCTAL_PREFIX, number)) {
            error("octal literal '" + number + "' must be prefixed with '0o'");
        }
        int lexedLength = number.length();
        token("NUMBER", number, 0, lexedLength);
        return lexedLength;
        
    }

    private int regexToken() throws CoffeeScriptNativeLexerException {

        int length;
        if(this.chunk.charAt(0) != '/') {
            return 0;
        }
        if((length = heregexToken()) != 0) {
            return length;
        }
        CoffeeScriptNativeToken prev = last(tokens);
        if(prev != null && containsNullSafe(prev.getTag(), prev.getSpaced() ? NOT_REGEX : NOT_SPACED_REGEX)) {
            return 0;
        }
        
        Matcher m = getMatcher(REGEX, this.chunk);
        if(!m.find()) {
            return 0;
        }
        String match = m.group(0);
        String regex = m.group(1);
        String flags = m.group(2);
        if(nullSafeCompare(regex,"//")) {
            return 0;
        }
        
        if(slice(regex, 0, 2).equals("/*")) {
            error("regular expressions cannot begin with `*`");
        }

        token("REGEX", regex + flags, 0, match.length());
        
        return match.length();
        
    }

    private int jsToken() {
        if(this.chunk.charAt(0) != '`') return 0;
        Matcher m = getMatcher(JSTOKEN, this.chunk);
        if(!(this.chunk.charAt(0) == '`' && m.find())) {
            return 0;
        }
        String script  = m.group(0);
        token("JS", slice(script, 1, -1), 0, script.length());
        return script.length();
    }

    private int literalToken() throws CoffeeScriptNativeLexerException {

        Matcher m = getMatcher(OPERATOR, this.chunk);
        String value;
        if(m.find()) {
            value = m.group(0);
            if(testRegexp(CODE, value)) {
                tagParameters();
            }
        } else {
            value = String.valueOf(this.chunk.charAt(0));
        }
        String tag = value;
        CoffeeScriptNativeToken prev = last(tokens);
        if(nullSafeCompare(value, "=") && prev != null) {
            if(!prev.getReserved() && containsNullSafe(prev.getValue(), JS_FORBIDDEN)) {
                error("reserved word \"" + (lastValue(tokens)) + "\" can't be assigned");
            }
            if(nullSafeCompare(prev.getValue(), "||") || nullSafeCompare(prev.getValue(), "&&")) {
                prev.setTag("COMPOUND_ASSIGN");
                prev.setValue(prev.getValue() + "=");
                return value.length();
            }
        }
        if(nullSafeCompare(value, ";")) {
            this.seenFor = false;
            tag = "TERMINATOR";
        } else if (containsNullSafe(value, MATH)) {
            tag = "MATH";
        } else if (containsNullSafe(value, COMPARE)) {
            tag = "COMPARE";
        } else if (containsNullSafe(value, COMPOUND_ASSIGN)) {
            tag = "COMPOUND_ASSIGN";
        } else if (containsNullSafe(value, UNARY)) {
            tag = "UNARY";
        } else if (containsNullSafe(value, UNARY_MATH)) {
            tag = "UNARY_MATH";
        } else if (containsNullSafe(value, SHIFT)) {
            tag = "SHIFT";
        } else if (containsNullSafe(value, LOGIC) || nullSafeCompare(value, "?") && (prev != null ? prev.getSpaced() : false)) {
            tag = "LOGIC";
        } else if (prev!=null && !prev.getSpaced()) {
            if(nullSafeCompare(value, "(") && containsNullSafe(prev.getTag(), CALLABLE)) {
                if(nullSafeCompare(prev.getTag(), "?")) {
                    prev.setTag("FUNC_EXIST");
                }
                tag = "CALL_START";
            } else if(nullSafeCompare(value, "[") && containsNullSafe(prev.getTag(), INDEXABLE)) {
                tag = "INDEX_START";
                if(nullSafeCompare(prev.getTag(), "?")) {
                    prev.setTag("INDEX_SOAK");
                }
            }
        }
        switch (value) {
            case "(":
            case "{":
            case "[":                
                this.ends.push(Rewriter.getInverses().get(value));
                break;
            case ")":
            case "}":
            case "]":
                pair(value);
        }
        token(tag, value,0, -1);
        return value.length();
    }

    private int[] getLineAndColumnFromChunk(int offset) {
        String string;
        if(offset == 0) {
            return new int[]{this.chunkLine, this.chunkColumn};
        }
        if (offset >= this.chunk.length()) {
            string = this.chunk;
        } else {
            string = (offset == 0) ? this.chunk : slice(this.chunk, 0, (offset - 1) + 1);
        }
        int lineCount = count(string,"\n");
        int column = this.chunkColumn;
        
        if(lineCount > 0) {
            String[] lines = string.replaceAll("\n", "#\n").split("\n");
            column = lines[lines.length-1].length()-1;
        } else {
            column += string.length();
        }
        return new int[] {this.chunkLine + lineCount, column};
    }

    private void closeIndentation() throws CoffeeScriptNativeLexerException {
        outdentToken(this.indent, false, -1);
    }
    
    private String sanitizeHeredoc(String doc, Map<String, Object> options) throws CoffeeScriptNativeLexerException {
        Boolean herecomment = Boolean.TRUE.equals(options.get("herecomment"));
        
        if(herecomment) {
            if(testRegexp(HEREDOC_ILLEGAL, doc)) {
                error("block comment cannot contain \"*/\", starting");
            }
        }
        return doc;
    }
    
    private boolean testRegexp(Pattern pattern, String s) {        
        return getMatcher(pattern, s).find();
    }
    
    // length = -1 -> null value, 
    private CoffeeScriptNativeToken makeToken(String tag, String value, int offsetInChunk, int length) {
        
        if(length == -1) {
            length = value.length();
        }
        
        int[] first = getLineAndColumnFromChunk(offsetInChunk);
        int lastCharacter = Math.max(0, length-1);
        int[] last = getLineAndColumnFromChunk(offsetInChunk + lastCharacter);
        CoffeeScriptNativeToken t = new CoffeeScriptNativeToken(tag, value, null, false);
        t.setLocationData(first, last);
        return t;
        
    }
    private CoffeeScriptNativeToken token(String tag, String value, int offsetInChunk, int length) {
        CoffeeScriptNativeToken t = makeToken(tag, value, offsetInChunk, length);
        this.tokens.add(t);
        return t;
    }
    
    private CoffeeScriptNativeToken tokensPop() {
        int index = tokens.size()-1;
        CoffeeScriptNativeToken poppedToken = null;
        if(index>-1) {
            poppedToken = tokens.get(index);
            tokens.remove(index);
        } 
        return poppedToken;
    }

    private boolean unfinished() {
        String tag = lastTag(tokens);
        return testRegexp(LINE_CONTINUER, this.chunk) || containsNullSafe(tag, UNFINISHED);
    }

    private void suppressNewlines() {
        if(nullSafeCompare(lastValue(tokens), "\\")) {
            tokensPop();
        }
    }

    private void newlineToken(int offset) {
        while(nullSafeCompare(lastValue(tokens), ";")) {
            tokensPop();
        }
        if(!nullSafeCompare(lastTag(tokens),"TERMINATOR")) {
            token("TERMINATOR", "\\n", offset, 0);
        }
    }

    private void outdentToken(int moveOut, boolean noNewLines, int outdentLength) throws CoffeeScriptNativeLexerException {
        int lastIndent, dent = -1;
        if(moveOut == -1) {
            moveOut = 0;
        }
        int decreasedIndent = this.indent - moveOut;
        while (moveOut > 0) {
            lastIndent = (int) intStackPeek(this.indents);
            if(lastIndent == -1 || lastIndent == 0) {
                moveOut = 0;
            } else if(lastIndent == this.outdebt) {
                moveOut -= this.outdebt;
                this.outdebt = 0;
            } else if(lastIndent < this.outdebt) {
                this.outdebt -= lastIndent;
                moveOut -= lastIndent;
            } else {
                dent = intStackPop(this.indents) + this.outdebt;
                String part = (outdentLength != -1 && outdentLength != 0) ? String.valueOf(this.chunk.charAt(outdentLength)) : null;
                if((outdentLength != -1 && outdentLength != 0) && containsNullSafe(part, INDENTABLE_CLOSERS)) {
                    decreasedIndent -= dent - moveOut;
                    moveOut = dent;
                }
                this.outdebt = 0;
                pair("OUTDENT");
                token("OUTDENT", String.valueOf(moveOut), 0 , outdentLength);
                moveOut -= dent;
            }
        }
        
        if(dent != -1 && dent != 0) {
            this.outdebt -= moveOut;
        }
        while (nullSafeCompare(lastValue(tokens),";")) {
            tokensPop();
        }
        if(!(nullSafeCompare(lastTag(tokens), "TERMINATOR") || noNewLines)) {
            token("TERMINATOR", "\\n", outdentLength, 0);
        }
        this.indent = decreasedIndent;
    }

    private int heregexToken() throws CoffeeScriptNativeLexerException {
        Matcher m = getMatcher(HEREGEX,this.chunk);
        if(!m.find()) {
            return 0;
        }
        String heregex = m.group(0);
        String body = m.group(1);
        String flags = m.group(2);
        if(getMatcher(HEREGEX_START_TEST,body).find()) {
            error("regular expressions cannot begin with `*`");
        }
        token("REGEX", "/" + body + "/" + flags, 0, heregex.length());
        return heregex.length();
    }

    private void tagParameters() {
        Stack<CoffeeScriptNativeToken> stack = new Stack<CoffeeScriptNativeToken>();
        if(!nullSafeCompare(lastTag(tokens), ")")) {
            return;
        }
        int i = tokens.size();
        CoffeeScriptNativeToken t = tokens.get(--i);
        t.setTag("PARAM_END");
        
        while(i > 0) {
            t = tokens.get(--i);
            switch(t.getTag()) {
                case ")" : 
                    stack.push(t);
                    break;
                case "(":
                case "CALL_START":
                    if(!stack.isEmpty()) {
                        stack.pop();
                    } else if(nullSafeCompare(t.getTag(),"(")) {
                        t.setTag("PARAM_START");
                        return;
                    } else {
                        return;
                    }
            }
        }
    }

    private void pair(String tag) throws CoffeeScriptNativeLexerException {
        String wanted;
        if(!nullSafeCompare(tag, wanted = stackPeek(this.ends))) {
            if(!nullSafeCompare(wanted, "OUTDENT")) {
                error("unmatched "+ tag);
            }
            outdentToken(intStackPeek(this.indents), true, -1);
            pair(tag);
            return;
        }
        stackPop(this.ends);
    }
    
    private Matcher getMatcher(Pattern p, String textToMatch) {
        Matcher m;
        if(matchers.containsKey(p.pattern())) {
            m = matchers.get(p.pattern());
            m.reset(textToMatch);
        } else {
            m = p.matcher(textToMatch);
            matchers.put(p.pattern(), m);
        }
        return m;        
    }
    
    private void error(String message, Integer offset) throws CoffeeScriptNativeLexerException {
        if(offset == null) {
            offset = 0;
        }        
        int[] location = getLineAndColumnFromChunk(offset);
        throw new CoffeeScriptNativeLexerException(message, location[1], location[0]);
    }    
    
    private void error(String message) throws CoffeeScriptNativeLexerException {
        error(message, null);
    }
}
