/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package coffeescript.lexer;

/**
 *
 * @author milos
 */
public class CoffeeScriptNativeLexerException extends Exception {
    private int line;
    private int column;
    public CoffeeScriptNativeLexerException(String message, int column, int line) {
        super(message);
        this.line = line;
        this.column = column;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    @Override
    public String toString() {
        return "[" + line + "," + column + "] : " + getMessage();
    }
    
    
    
    
}
