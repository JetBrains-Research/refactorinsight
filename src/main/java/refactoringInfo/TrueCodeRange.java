package refactoringInfo;

import gr.uom.java.xmi.diff.CodeRange;

public class TrueCodeRange extends CodeRange {

    private int trueStartLine;
    private int trueEndLine;

    public TrueCodeRange(CodeRange codeRange) {
        super(codeRange.getFilePath(), codeRange.getStartLine(), codeRange.getEndLine(),
                codeRange.getStartColumn(), codeRange.getEndColumn(), codeRange.getCodeElementType());
        trueStartLine = this.getStartLine();
        trueEndLine = this.getEndLine();
    }

    public int getTrueStartLine() {
        return trueStartLine;
    }

    public void setTrueStartLine(int trueStartLine) {
        this.trueStartLine = trueStartLine;
    }

    public int getTrueEndLine() {
        return trueEndLine;
    }

    public void setTrueEndLine(int trueEndLine) {
        this.trueEndLine = trueEndLine;
    }
}
