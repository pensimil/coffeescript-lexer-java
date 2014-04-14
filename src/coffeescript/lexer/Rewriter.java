package coffeescript.lexer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import static coffeescript.lexer.Helpers.*;


/**
 *
 * @author milos
 */
public class Rewriter {
    private List<CoffeeScriptNativeToken> tokens;
    private static final Set<String> EXPRESSION_START = new TreeSet(Arrays.asList("(","[","{","INDENT","CALL_START","PARAM_START","INDEX_START"));
    private static final Set<String> EXPRESSION_END = new TreeSet(Arrays.asList(")","]","}","OUTDENT","CALL_END","PARAM_END","INDEX_END"));
    private static final Map<String, String> INVERSES = new HashMap<String, String>();
    private static final Set<String> EXPRESSION_CLOSE = new HashSet<String>(Arrays.asList("CATCH", "THEN", "ELSE", "FINALLY"));    
    private static final Set<String> IMPLICIT_FUNC = new HashSet<String>(Arrays.asList("IDENTIFIER", "SUPER", ")", "CALL_END", "]", "INDEX_END", "@", "THIS"));
    private static final Set<String> IMPLICIT_CALL = new HashSet<String>(Arrays.asList("IDENTIFIER", "NUMBER", "STRING", "JS", "REGEX", "NEW", "PARAM_START", 
    "CLASS", "IF", "TRY", "SWITCH", "THIS", "BOOL", "NULL", "UNDEFINED", "UNARY", "UNARY_MATH", "SUPER", "THROW", "@", "->",
    "=>", "[", "(", "{", "--", "++"));
    private static final Set<String> IMPLICIT_UNSPACED_CALL = new HashSet<String>(Arrays.asList("+", "-"));
    private static final Set<String> IMPLICIT_END = new HashSet<String>(Arrays.asList("POST_IF", "FOR", "WHILE", "UNTIL", "WHEN", "BY", "LOOP", "TERMINATOR"));
    private static final Set<String> SINGLE_LINERS = new HashSet<String>(Arrays.asList("ELSE", "->", "=>", "TRY", "FINALLY", "THEN"));
    private static final Set<String> SINGLE_CLOSERS = new HashSet<String>(Arrays.asList("TERMINATOR", "CATCH", "FINALLY", "ELSE", "OUTDENT", "LEADING_WHEN"));
    private static final Set<String> LINEBREAKS = new HashSet<String>(Arrays.asList("TERMINATOR", "INDENT", "OUTDENT"));
    private static final Set<String> CALL_CLOSERS = new HashSet<String>(Arrays.asList(".", "?.", "::", "?::"));

    private String starter;
    private CoffeeScriptNativeToken indent, outdent, original;
    private Boolean insideForDeclaration;
    
    static {
        EXPRESSION_CLOSE.addAll(EXPRESSION_END);
        initInverses();
    }

    private static void initInverses() {
        Iterator<String> iteratorExpressionStart = EXPRESSION_START.iterator();
        Iterator<String> iteratorExpressionEnd = EXPRESSION_END.iterator();
        while (iteratorExpressionStart.hasNext() && iteratorExpressionEnd.hasNext()) {
            String left = iteratorExpressionStart.next();
            String right = iteratorExpressionEnd.next();
            INVERSES.put(left, right);
            INVERSES.put(right, left);
        }
    }
    
    public static Map<String,String> getInverses() {
        return INVERSES;
    }

    public Rewriter(List<CoffeeScriptNativeToken> tokens) {
        this.tokens = tokens;
    }
    
    public List<CoffeeScriptNativeToken> rewrite() {
        
        this.removeLeadingNewLines();
        this.closeOpenCalls();
        this.closeOpenIndexes();
        this.normalizeLines();
        this.tagPostfixConditionals();
        this.addImplicitBracesAndParens();
        this.addLocationDataToGeneratedTokens();
        return this.tokens;
        
    }
    
    private void scanTokens(IBlock block) {
        int i = 0;
        CoffeeScriptNativeToken t;
        while (tokens.size() > i) {
            t = tokens.get(i);
            i += block.call(t, i);
        }       
    }    
    
    private int detectEnd(int i, ICondition condition, IAction action) {
        int levels = 0;
        CoffeeScriptNativeToken t;
        while(tokens.size() > i) {
            t = tokens.get(i);
            if(levels == 0 && condition.call(t, i)) return action.call(t, i);
            if(levels < 0) return action.call(t, i-1);
            if(EXPRESSION_START.contains(t.getTag())) {
                levels++;
            } else if(EXPRESSION_END.contains(t.getTag())) {
                 levels--;
            }
            i++;
        }
        return i-1;
    }
    
    private void removeLeadingNewLines() {
        Iterator<CoffeeScriptNativeToken> iteratorTokens = tokens.iterator();
        while(iteratorTokens.hasNext() && iteratorTokens.next().getTag().equals("TERMINATOR")) {
            iteratorTokens.remove();
        }
    }
    
    private void closeOpenCalls() {
        final ICondition condition = new ICondition() {

            @Override
            public boolean call(CoffeeScriptNativeToken t, int i) {
                String tag = t.getTag();
                return tag.equals(")") || tag.equals("CALL_END") || tag.equals("OUTDENT") && tagAt(tokens, i-1).equals(")");
            }

        };
        
        final IAction action = new IAction() {

            @Override
            public int call(CoffeeScriptNativeToken t, int i) {
                int index = t.getTag().equals("OUTDENT") ? i - 1 : i;
                tokens.get(index).setTag("CALL_END");
                return 1;
            }
            
        };
        
        IBlock block = new IBlock() {

            @Override
            public int call(CoffeeScriptNativeToken t, int i) {
                if(t.getTag().equals("CALL_START")) {
                    detectEnd(i+1, condition, action);
                }
                return 1;
            }
            
        };
        scanTokens(block);
    }
    
    private void closeOpenIndexes() {
        final ICondition condition = new ICondition() {

            @Override
            public boolean call(CoffeeScriptNativeToken t, int i) {
                String tag = t.getTag();
                return tag.equals("]") || tag.equals("INDEX_END");
            }

        };
        
        final IAction action = new IAction() {

            @Override
            public int call(CoffeeScriptNativeToken t, int i) {
                t.setTag("INDEX_END");
                return 1;
            }
            
        };
        
        IBlock block = new IBlock() {

            @Override
            public int call(CoffeeScriptNativeToken t, int i) {
                if(t.getTag().equals("INDEX_START")) {
                    detectEnd(i+1, condition, action);
                }
                return 1;
            }
        };
        scanTokens(block);
    }
    
    private boolean matchTags(Object... args) { 
        assert(args[0] instanceof Integer);
        Object[] tags = null;
        Integer i = (Integer) args[0];
        if(args.length > 1) {
            tags = Arrays.copyOfRange(args, 1, args.length);
        }
        int fuzz = 0, j = 0;
        if(tags != null) {
            for(Object pattern : tags) {
                while(nullSafeCompare(tagAt(tokens, i + j + fuzz), "HERECOMMENT")) {
                    fuzz += 2;
                }                
                if(pattern == null) {
                    j++;
                    continue;                    
                }
                String tag = tagAt(tokens, i + j + fuzz);
                if(pattern instanceof String && !nullSafeCompare(tag, pattern)) return false;
                j++;
            }
        }
        return true;
    }
    
    private boolean looksObjectish(int j) {
        return matchTags(j, "@", null, ":") || matchTags(j, null, ":");
    }
    
    private boolean findTagsBackwards(int i, Set<String> tags) {
        Stack<String> backStack = new Stack<String>();
        String tag = tagAt(tokens, i);
        CoffeeScriptNativeToken token = tokens.get(i);
        while (i>= 0 && (!backStack.isEmpty() || 
                (!containsNullSafe(tag, tags) && 
                (!containsNullSafe(tag, EXPRESSION_START) || token.getGenerated()) && 
                !containsNullSafe(tag, LINEBREAKS)))) {
            if(containsNullSafe(tag, EXPRESSION_END)) {
                backStack.push(tag);
            }
            if(containsNullSafe(tag, EXPRESSION_START) && !backStack.isEmpty()) {
                backStack.pop();
            }
            i-=1;
            tag = tagAt(tokens, i);
            token = tokens.get(i);
        }
        return containsNullSafe(tag, tags);
    }
    
    private void addImplicitBracesAndParens() {
        final Stack<BracesAndParensDescriptor> stack = new Stack<BracesAndParensDescriptor>();
        IBlock block = new IBlock() {

            @Override
            public int call(CoffeeScriptNativeToken t, int i) {
                Holder<Integer> implicitCallIndex = new Holder<Integer>(i);
                int startIdx = i;
                String tag = t.getTag();
                CoffeeScriptNativeToken prevToken = tokenAt(tokens, implicitCallIndex.value-1);
                String prevTag = tagAt(tokens, implicitCallIndex.value-1);
                String nextTag = tagAt(tokens, implicitCallIndex.value+1);
                if(inImplicitCall(stack) && (tag.equals("IF") || tag.equals("TRY") || tag.equals("FINALLY") ||
                        tag.equals("CATCH") || tag.equals("CLASS") || tag.equals("SWITCH")) ) {
                    BracesAndParensDescriptor d = new BracesAndParensDescriptor("CONTROL", implicitCallIndex.value);
                    d.addParam("ours", true);
                    stack.push(d);
                    return forward(1, startIdx, implicitCallIndex.value);
                }
                if(tag.equals("INDENT") && inImplicit(stack)) {
                    if(!prevTag.equals("=>") && !prevTag.equals("->") && !prevTag.equals("[") &&
                            !prevTag.equals("(") && !prevTag.equals(",") && !prevTag.equals("{") &&
                            !prevTag.equals("TRY") && !prevTag.equals("ELSE") && !prevTag.equals("=")) {
                        while(inImplicitCall(stack)) {
                            endImplicitCall(stack,implicitCallIndex);
                        }
                    }
                    if(inImplicitControl(stack)) {
                        stackPop(stack);
                    }
                    stack.push(new BracesAndParensDescriptor(tag, implicitCallIndex.value));
                    return forward(1, startIdx, implicitCallIndex.value);
                }
                if(containsNullSafe(tag, EXPRESSION_START)) {
                    stack.push(new BracesAndParensDescriptor(tag, implicitCallIndex.value));
                    return forward(1, startIdx, implicitCallIndex.value);
                }
                if(containsNullSafe(tag, EXPRESSION_END)) {
                    while (inImplicit(stack)) {
                        if(inImplicitCall(stack)) {
                            endImplicitCall(stack, implicitCallIndex);
                        } else if(inImplicitObject(stack)) {
                            endImplicitObject(stack, null,t, implicitCallIndex);
                        } else {
                            stackPop(stack);
                        }
                    }                    
                    stackPop(stack);
                }
                CoffeeScriptNativeToken nextToken;
                if((containsNullSafe(tag, IMPLICIT_FUNC) && t.getSpaced() && !t.getStringEnd() || 
                        tag.equals("?") && !tokenAt(tokens,implicitCallIndex.value - 1).getSpaced()) && (containsNullSafe(nextTag, IMPLICIT_CALL) ||
                        containsNullSafe(nextTag, IMPLICIT_UNSPACED_CALL) && !(((nextToken = tokenAt(tokens,implicitCallIndex.value + 1)) != null) ? nextToken.getSpaced() : false) &&
                        !((nextToken != null) ? nextToken.getNewLine(): false))) {
                    if(tag.equals("?")) {
                        tag = "FUNC_EXIST";
                        t.setTag(tag);
                    }
                    startImplicitCall(stack, implicitCallIndex.value+1, implicitCallIndex);
                    return forward(2, startIdx, implicitCallIndex.value);
                }
                if(containsNullSafe(tag, IMPLICIT_FUNC) && matchTags(implicitCallIndex.value+1, "INDENT",null,":") && 
                        !findTagsBackwards(implicitCallIndex.value, new HashSet<String>(Arrays.asList("CLASS", "EXTENDS", "IF",
                        "CATCH", "SWITCH", "LEADING_WHEN", "FOR", "WHILE", "UNTIL")))) {
                    startImplicitCall(stack, implicitCallIndex.value+1,implicitCallIndex);
                    stack.push(new BracesAndParensDescriptor("INDENT", implicitCallIndex.value+2));
                    return forward(3, startIdx,implicitCallIndex.value);
                }
                int s;
                if(tag.equals(":")) {
                    if(nullSafeCompare(tagAt(tokens, implicitCallIndex.value-2),"@")) {
                        s = implicitCallIndex.value - 2;
                    } else {
                        s = implicitCallIndex.value - 1;
                    }
                    while (nullSafeCompare(tagAt(tokens, s - 2),"HERECOMMENT")) {
                        s-=2;
                    }
                    insideForDeclaration = nullSafeCompare(nextTag ,"FOR");
                    boolean startsLine = (s == 0 || containsNullSafe(tagAt(tokens, s - 1), LINEBREAKS) ||
                            tokenAt(tokens,s-1).getNewLine());
                    BracesAndParensDescriptor stackTop = stackPeek(stack);
                    if(stackTop != null) {
                        if((stackTop.getTag().equals("{") || stackTop.getTag().equals("INDENT") && nullSafeCompare(tagAt(tokens, stackTop.getIndex()-1), "{")) &&
                                (startsLine || nullSafeCompare(tagAt(tokens, s-1),",") || nullSafeCompare(tagAt(tokens, s-1),"{"))) {
                            return forward(1, startIdx, implicitCallIndex.value);
                        }
                    }
                    startImplicitObject(stack, s, startsLine,implicitCallIndex);
                    return forward(2, startIdx, implicitCallIndex.value);
                }
                
                if(inImplicitObject(stack) && containsNullSafe(tag, LINEBREAKS)) {
                    if(stackPeek(stack) != null) stackPeek(stack).addParam("sameLine", false);
                }
                boolean newLine = nullSafeCompare(prevTag, "OUTDENT") || ((prevToken!=null) ? prevToken.getNewLine() : false);
                
                if(containsNullSafe(tag, IMPLICIT_END) || containsNullSafe(tag, CALL_CLOSERS) && newLine) {
                    while(inImplicit(stack)) {
                        BracesAndParensDescriptor d = stackPeek(stack);
                        if(inImplicitCall(stack) && !nullSafeCompare(prevTag,",")) {
                            endImplicitCall(stack, implicitCallIndex);
                        } else if(inImplicitObject(stack) && !insideForDeclaration && d.getParamAsBoolean("sameLine") && !nullSafeCompare(tag, "TERMINATOR") && !nullSafeCompare(prevTag, ":") && endImplicitObject(stack, null, t, implicitCallIndex) != 0) {
                        } else if(inImplicitObject(stack) && nullSafeCompare(tag,"TERMINATOR") && !nullSafeCompare(prevTag,",") &&
                                !(d.getParamAsBoolean("startsLine") && looksObjectish(implicitCallIndex.value + 1))) {
                            endImplicitObject(stack, null, t, implicitCallIndex);
                        } else {
                            break;
                        }
                    }
                }
                if(nullSafeCompare(tag,",") && !looksObjectish(implicitCallIndex.value+1) && inImplicitObject(stack) && !insideForDeclaration &&
                        (!nullSafeCompare(nextTag,"TERMINATOR") || !looksObjectish(implicitCallIndex.value + 2))) {
                    int offset = nullSafeCompare(nextTag,"OUTDENT") ? 1 : 0;
                    while (inImplicitObject(stack)) {
                        endImplicitObject(stack, implicitCallIndex.value + offset, t, implicitCallIndex);
                    }
                }
                return forward(1, startIdx, implicitCallIndex.value);
            }
        };
        
        scanTokens(block);
    }
    
    private void addLocationDataToGeneratedTokens() {
        IBlock block = new IBlock() {

            @Override
            public int call(CoffeeScriptNativeToken t, int i) {
                CoffeeScriptNativeToken nextLocation, prevLocation;
                int line, column;
                if(t.hasLocationData()) return 1;
                if(!(t.getGenerated() || t.getExplicit())) return 1;
                if(t.getTag().equals("{") && ((nextLocation = tokenAt(tokens, i+1)) != null ? nextLocation.hasLocationData() : false)) {
                    line = nextLocation.getFirstLine();
                    column = nextLocation.getFirstColumn();
                } else if (((prevLocation = tokenAt(tokens, i-1)) != null ? prevLocation.hasLocationData() : false)) {
                    line = prevLocation.getFirstLine();
                    column = prevLocation.getFirstColumn();
                } else {
                    line = 0;
                    column = 0;
                }
                t.setFirstColumn(column);
                t.setLastColumn(column);
                t.setFirstLine(line);
                t.setLastLine(line);
                return 1;
            };
        };
        scanTokens(block);
    }
    
    private void normalizeLines() {
        starter = null;
        indent = outdent = null;
        final ICondition condition = new ICondition() {

            @Override
            public boolean call(CoffeeScriptNativeToken t, int i) {
                return !nullSafeCompare(t.getValue(), ";") && containsNullSafe(t.getTag(), SINGLE_CLOSERS) &&
                        !(nullSafeCompare(t.getTag(),"TERMINATOR") && containsNullSafe(tagAt(tokens, i + 1), EXPRESSION_CLOSE)) &&
                        !(nullSafeCompare(t.getTag(), "ELSE") && !nullSafeCompare(starter, "THEN")) && 
                        !((nullSafeCompare(t.getTag(), "CATCH") || nullSafeCompare(t.getTag(), "FINALLY")) && (nullSafeCompare(starter, "->") || nullSafeCompare(starter, "=>"))) ||
                        containsNullSafe(t.getTag(), CALL_CLOSERS) && tokenAt(tokens, i-1).getNewLine();
            }
        };
        
        final IAction action = new IAction() {

            @Override
            public int call(CoffeeScriptNativeToken t, int i) {
                int index = nullSafeCompare(tagAt(tokens, i-1), ",") ? i-1 : i;
                tokens.add(index, outdent);
                return 0;
            }
        };
        
        IBlock block = new IBlock() {

            @Override
            public int call(CoffeeScriptNativeToken t, int i) {
                String tag = t.getTag();
                if(nullSafeCompare(tag, "TERMINATOR")) {
                    if(nullSafeCompare(tagAt(tokens, i+1), "ELSE") && !nullSafeCompare(tagAt(tokens, i-1), "OUTDENT")) {
                        tokens.remove(i);
                        CoffeeScriptNativeToken[] indentation = indentation(null);
                        tokens.add(i,indentation[0]);
                        tokens.add(i+1,indentation[1]);
                        
                        return 1;
                    }
                    if(containsNullSafe(tagAt(tokens, i+1), EXPRESSION_CLOSE)) {
                        tokens.remove(i);
                        return 0;
                    }
                }
                if(nullSafeCompare(tag, "CATCH")) {
                    for(int k = 1; k < 3; k ++) {
                        String tmpTag = tagAt(tokens, i+k);
                        if(!(nullSafeCompare(tmpTag, "OUTDENT") || nullSafeCompare(tmpTag, "TERMINATOR") || nullSafeCompare(tmpTag, "FINALLY"))) {
                            continue;
                        }
                        tokens.remove(i+k);
                        CoffeeScriptNativeToken[] indentation = indentation(null);
                        tokens.add(i+k,indentation[1]);
                        tokens.add(i+k,indentation[0]);
                        return 2 + k;                        
                    }
                }
                if(containsNullSafe(tag, SINGLE_LINERS) && !nullSafeCompare(tagAt(tokens, i + 1),"INDENT") && 
                        !(nullSafeCompare(tag, "ELSE") && nullSafeCompare(tagAt(tokens, i + 1), "IF"))) {
                    starter = tag;
                    CoffeeScriptNativeToken[] inOut = indentation(tokenAt(tokens, i));
                    indent = inOut[0]; outdent = inOut[1];
                    if(nullSafeCompare(starter, "THEN")) {
                        indent.setFromThen(true);
                    }
                    tokens.add(i+1,indent);
                    detectEnd(i+2, condition, action);
                    if(nullSafeCompare(tag, "THEN")) {
                        tokens.remove(i);
                    }
                    return 1;
                }
                return 1;
            }
        };
        scanTokens(block);
    }
    
    private void tagPostfixConditionals() {
        final ICondition condition = new ICondition() {
            

            @Override
            public boolean call(CoffeeScriptNativeToken t, int i) {
                String prevTag = tagAt(tokens, i-1);
                return nullSafeCompare(t.getTag(), "TERMINATOR") || (nullSafeCompare(t.getTag(), "INDENT") && !containsNullSafe(prevTag, SINGLE_LINERS));
            }
        };
        final IAction action = new IAction() {

            @Override
            public int call(CoffeeScriptNativeToken t, int i) {
                if(!t.getTag().equals("INDENT") || (t.getGenerated() && !t.getFromThen())) {
                    original.setTag("POST_" + original.getTag());
                    return 0;
                }
                return 0;
            }
        };
        IBlock block = new IBlock() {

            @Override
            public int call(CoffeeScriptNativeToken t, int i) {
                if(!nullSafeCompare(t.getTag(), "IF")) {
                    return 1;
                }
                original = t;
                detectEnd(i+1, condition, action);
                return 1;
            }
        };
        scanTokens(block);
    }
    
    private int forward(int n, int startIdx, int implicitCallIndex) {
        return implicitCallIndex - startIdx + n;
    }
    
    private boolean inImplicit(Stack<BracesAndParensDescriptor> s) {
        BracesAndParensDescriptor desc = stackPeek(s);
        return (desc != null) ? desc.getParamAsBoolean("ours") : false;
    }
    
    private boolean inImplicitCall(Stack<BracesAndParensDescriptor> s) {
        BracesAndParensDescriptor desc;
        return inImplicit(s) && (((desc = stackPeek(s)) != null) ? desc.getTag().equals("(") : false);
    }
    
    private boolean inImplicitObject(Stack<BracesAndParensDescriptor> s) {
        BracesAndParensDescriptor desc;
        return inImplicit(s) && (((desc = stackPeek(s)) != null) ? desc.getTag().equals("{") : false);
    }
    
    private boolean inImplicitControl(Stack<BracesAndParensDescriptor> s) {
        BracesAndParensDescriptor desc;
        return inImplicit(s) && (((desc = stackPeek(s)) != null) ? desc.getTag().equals("CONTROL") : false);
    }
    
    private int startImplicitCall(Stack<BracesAndParensDescriptor> s, Integer j, Holder<Integer> implicitCallIndex) {
        int idx = (j!= null) ? j : implicitCallIndex.value;
        BracesAndParensDescriptor d = new BracesAndParensDescriptor("(", idx);
        d.addParam("ours", true);
        s.push(d);
        tokens.add(idx, generate("CALL_START", "("));
        if(j == null) implicitCallIndex.value++;
        return implicitCallIndex.value;
    }
    
    private int endImplicitCall(Stack<BracesAndParensDescriptor> s, Holder<Integer> implicitCallIndex) {
        s.pop();
        tokens.add(implicitCallIndex.value, generate("CALL_END", "(", null));
        implicitCallIndex.value++;
        return implicitCallIndex.value;
    }
    
    private int startImplicitObject(Stack<BracesAndParensDescriptor> s, Integer j, Boolean startsLine, Holder<Integer> implicitCallIndex) {
        int idx = (j!= null) ? j : implicitCallIndex.value;
        BracesAndParensDescriptor d = new BracesAndParensDescriptor("{", idx);
        d.addParam("ours", true);
        d.addParam("sameLine", true);
        d.addParam("startsLine", Boolean.TRUE.equals(startsLine));
        s.push(d);
        tokens.add(idx, generate("{", "{"));
        if(j == null) implicitCallIndex.value++;
        return implicitCallIndex.value;
    }
    
    private int endImplicitObject(Stack<BracesAndParensDescriptor> s, Integer j, CoffeeScriptNativeToken t, Holder<Integer> implicitCallIndex) {
        j = (j != null) ? j : implicitCallIndex.value;
        stackPop(s);
        tokens.add(j, generate("}", "}", t));
        implicitCallIndex.value ++;
        return implicitCallIndex.value;
    }
    
    private CoffeeScriptNativeToken[] indentation(CoffeeScriptNativeToken origin) {
        
        CoffeeScriptNativeToken in = new CoffeeScriptNativeToken("INDENT", "2", null, false);
        CoffeeScriptNativeToken out = new CoffeeScriptNativeToken("OUTDENT", "2", null, false);
        
        if(origin != null) {
            in.setGenerated(true);
            out.setGenerated(true);
            in.setOrigin(origin);
            out.setOrigin(origin);
        } else {
            in.setExplicit(true);
            out.setExplicit(true);
        }
        return new CoffeeScriptNativeToken[] {in, out};
    }
                
    private interface IBlock {
        int call(CoffeeScriptNativeToken t, int i);
    }
    
    private interface ICondition {
        boolean call(CoffeeScriptNativeToken t, int i);
    }
    
    private interface IAction {
        int call(CoffeeScriptNativeToken t, int i);
    }
    
    private class Indentation {
        Map<String,Object> params = new HashMap<String, Object>();

        public Indentation(String tag, int number) {
            params.put("tag", tag);
            params.put("number", number);
        }
                
        public void addParam(String key, Object o) {
            params.put(key, o);
        }
        
        public String getAsString(String s) {
            return (String) params.get(s);
        }
        public Boolean getAsBoolean(String s) {
            return params.containsKey(s) && (Boolean) params.get(s);
        }
    }
    
    private class BracesAndParensDescriptor {
        private String tag;
        private int index;
        private Map<String, Object> params = new HashMap<String, Object>();

        public BracesAndParensDescriptor(String tag, int index) {
            this.tag = tag;
            this.index = index;
        }       
        
        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }
        
        public Boolean getParamAsBoolean(String key) {
            return params.containsKey(key) && (Boolean) params.get(key);
        }
        
        public void addParam(String key, Object o) {
            params.put(key, o);
        }
    }
    
    private class Holder<T> {
        private T value;
        public Holder(T value) {
            this.value = value;
        }        
    }
    
}
