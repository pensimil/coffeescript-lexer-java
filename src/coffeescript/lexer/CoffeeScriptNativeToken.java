/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package coffeescript.lexer;

/**
 *
 * @author milos
 */
public class CoffeeScriptNativeToken {
    private String tag;
    private String value;
    private CoffeeScriptNativeToken origin;
    private Boolean generated;
    private boolean spaced;
    private boolean stringEnd;
    private boolean newLine;
    private boolean explicit;
    private boolean reserved;
    
    private int firstLine = -1;
    private int firstColumn = -1;
    private int lastLine = -1;
    private int lastColumn = -1;
    private boolean fromThen;

    public void setNewLine(boolean newLine) {
        this.newLine = newLine;
    }

    public void setStringEnd(boolean stringEnd) {
        this.stringEnd = stringEnd;
    }

    public CoffeeScriptNativeToken(String tag, String value, CoffeeScriptNativeToken origin, Boolean generated) {
        this.tag = tag;
        this.value = value;
        this.origin = origin;
        this.generated = generated;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setOrigin(CoffeeScriptNativeToken origin) {
        this.origin = origin;
    }

    public Boolean getGenerated() {
        return generated;
    }

    public void setGenerated(Boolean generated) {
        this.generated = generated;
    }

    public boolean getSpaced() {
        return spaced;
    }

    public void setSpaced(boolean spaced) {
        this.spaced = spaced;
    }

    public boolean getStringEnd() {
        return stringEnd;
    }

    public boolean getNewLine() {
        return newLine;
    }

    public boolean getExplicit() {
        return explicit;
    }

    public void setExplicit(boolean explicit) {
        this.explicit = explicit;
    }

    public boolean getReserved() {
        return this.reserved;
    }

    public void setReserved(boolean resedved) {
        this.reserved = resedved;
    }
    
    public void setLocationData(int[] first, int[] last) {
        this.firstLine = first[0];
        this.firstColumn = first[1];
        this.lastLine = last[0];
        this.lastColumn = last[1];
    }

    public int getFirstLine() {
        return firstLine;
    }

    public void setFirstLine(int firstLine) {
        this.firstLine = firstLine;
    }

    public int getFirstColumn() {
        return firstColumn;
    }

    public void setFirstColumn(int firstColumn) {
        this.firstColumn = firstColumn;
    }

    public int getLastLine() {
        return lastLine;
    }

    public void setLastLine(int lastLine) {
        this.lastLine = lastLine;
    }

    public int getLastColumn() {
        return lastColumn;
    }

    public void setLastColumn(int lastColumn) {
        this.lastColumn = lastColumn;
    }

    public void setFromThen(boolean b) {
        this.fromThen = b;
    }

    public boolean getFromThen() {
        return fromThen;
    }
    
    public boolean hasLocationData() {
        return firstColumn != -1;
    }
    
    
    
}
