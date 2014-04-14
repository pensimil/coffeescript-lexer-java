package coffeescript.lexer;

import java.util.List;
import java.util.Set;
import java.util.Stack;

/**
 *
 * @author milos
 */
public class Helpers {
    public static CoffeeScriptNativeToken last(List<CoffeeScriptNativeToken> tokens) {
        int index = tokens.size() - 1;
        if(index<0) return null;
        return tokens.get(index);
    }
    
    public static CoffeeScriptNativeToken tokenAt(List<CoffeeScriptNativeToken> tokens, int index) {
        if(index<0 || tokens.size()-1 < index) return null;
        return tokens.get(index);
    }
        
    public static String tagAt(List<CoffeeScriptNativeToken> tokens, int index) {
        CoffeeScriptNativeToken t = tokenAt(tokens, index);
        if(t == null) return null;
        return t.getTag();
    }
    
    public static String lastTag(List<CoffeeScriptNativeToken> tokens) {
        CoffeeScriptNativeToken last = last(tokens);
        if(last == null) return null;
        return last.getTag();
    }
    
    public static String lastValue(List<CoffeeScriptNativeToken> tokens) {
        CoffeeScriptNativeToken last = last(tokens);
        if(last == null) return null;
        return last.getValue();
    }
    
    public static String repeat(String str, int n) {
        String res="";
        while (n>0) {
            if((n & 1) != 0) {
                res += str;
            }
            n >>>= 1;
            str += str;
        }
        return res;
    }
    
    public static String slice(String s, int begin, int end) {
        boolean beginEndCondition = (end < 0) ? begin < end : begin > end;
        if(begin < 0 || beginEndCondition || begin >= s.length()) {
            return s.substring(s.length());
        }
        int stop = (end < 0) ? Math.max(s.length() + end,0) : Math.min(end, s.length());
                 
        return s.substring(begin, stop);
    }

    
    public static String slice(String s, int begin) {
        int start = (begin < 0 ) ? Math.max(s.length() + begin, 0) : Math.min(s.length(), begin);         
        return s.substring(start);
    }
    
    public static int count(String string, String substr) {
        int num = 0,pos = 0;
        
        if(substr.length()==0) {
            return 0;
        }
        
        while ((pos = 1 + string.indexOf(substr, pos)) != 0) {
            num ++;
        }
        return num;
    }
    
    public static <T> T stackPop(Stack<T> stack) {
        if(stack.empty()) return null;
        return stack.pop();
    }
    
    public static <T> T stackPeek(Stack<T> stack) {
        if(stack.empty()) return null;
        return stack.peek();
    }
    
    public static int intStackPeek(Stack<Integer> stack) {
        if(stack.empty()) return -1;
        return stack.peek();
    }
    
    public static int intStackPop(Stack<Integer> stack) {
        if(stack.empty()) return -1;
        return stack.pop();
    }
    
    public static CoffeeScriptNativeToken generate(String tag, String value, CoffeeScriptNativeToken origin) {
        return new CoffeeScriptNativeToken(tag, value, origin, true);
    }
    
    public static CoffeeScriptNativeToken generate(String tag, String value) {
        return generate(tag, value,null);
    }
    
    public static boolean containsNullSafe(String tag, Set<String> tagSet) {
        if (tag == null) return false;
        return tagSet.contains(tag);
    }
    
    public static boolean nullSafeCompare(Object s1, Object s2) {
        if(s1 != null) return s1.equals(s2);
        if(s1 == null && s2 == null) {
            return true;
        }         
        return false;
    }
}
