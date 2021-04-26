package org.jetbrains.research.refactorinsight.folding.handlers;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.research.refactorinsight.data.RefactoringInfo;
import org.jetbrains.research.refactorinsight.utils.PsiUtils;

import java.util.Collections;
import java.util.List;

public class MoveOperationFoldingHandler implements FoldingHandler {
  @NotNull
  @Override
  public List<Folding> getFolds(@NotNull RefactoringInfo info, @NotNull PsiFile file, boolean isBefore) {
    PsiMethod method = PsiUtils.findMethod(file, isBefore ? info.getNameBefore() : info.getNameAfter());
    if (method == null) {
      return Collections.emptyList();
    }
    String details = isBefore ? info.getDetailsAfter() : info.getDetailsBefore();
    String hintText = specificOperation(info)
        + (info.isChanged() ? "with changes " : "without changes ")
        + (isBefore ? "to " : "from ")
        + details.substring(details.lastIndexOf('.') + 1);
    PsiCodeBlock body = method.getBody();
    return Collections.singletonList(
        new Folding(
            hintText,
            method.getTextRange().getStartOffset(),
            body == null ? -1 : body.getTextRange().getStartOffset(),
            body == null ? -1 : body.getTextRange().getEndOffset()
        )
    );
  }

  private String specificOperation(RefactoringInfo info) {
    switch (info.getType()) {
      case MOVE_OPERATION:
        return "Moved ";
      case PULL_UP_OPERATION:
        return "Pulled up ";
      case PUSH_DOWN_OPERATION:
        return "Pushed down ";
      default:
        throw new AssertionError("Illegal refactoring type");
    }
  }
}
