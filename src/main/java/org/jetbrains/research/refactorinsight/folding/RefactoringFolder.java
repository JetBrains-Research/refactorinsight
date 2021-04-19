package org.jetbrains.research.refactorinsight.folding;

import com.intellij.application.options.colors.highlighting.RendererWrapper;
import com.intellij.codeInsight.daemon.impl.HintRenderer;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer;
import com.intellij.diff.tools.util.side.ThreesideTextDiffViewer;
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.research.refactorinsight.adapters.RefactoringType;
import org.jetbrains.research.refactorinsight.data.RefactoringInfo;
import org.jetbrains.research.refactorinsight.folding.handlers.FoldingHandler;
import org.jetbrains.research.refactorinsight.folding.handlers.MoveOperationHandler;
import org.jetbrains.research.refactorinsight.services.MiningService;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class RefactoringFolder {
  static Map<RefactoringType, FoldingHandler> foldingHandlers;

  static {
    foldingHandlers = new EnumMap<>(RefactoringType.class);
    FoldingHandler moveOperationHandler = new MoveOperationHandler();
    foldingHandlers.put(RefactoringType.MOVE_OPERATION, moveOperationHandler);
    foldingHandlers.put(RefactoringType.PULL_UP_OPERATION, moveOperationHandler);
    foldingHandlers.put(RefactoringType.PUSH_DOWN_OPERATION, moveOperationHandler);
  }

  // Suppresses default constructor, ensuring non-instantiability.
  private RefactoringFolder() {
  }

  /**
   * TODO: Javadoc.
   */
  public static void foldRefactorings(@NotNull FrameDiffTool.DiffViewer viewer, @NotNull DiffRequest request) {
    if (request instanceof SimpleDiffRequest) {
      SimpleDiffRequest diffRequest = (SimpleDiffRequest) request;
      String commitId = diffRequest.getContentTitles().get(1);
      if (viewer instanceof OnesideTextDiffViewer) {
        RefactoringFolder.foldRefactorings((OnesideTextDiffViewer) viewer, commitId);
      } else if (viewer instanceof TwosideTextDiffViewer) {
        RefactoringFolder.foldRefactorings((TwosideTextDiffViewer) viewer, commitId);
      } else if (viewer instanceof ThreesideTextDiffViewer) {
        RefactoringFolder.foldRefactorings((ThreesideTextDiffViewer) viewer, commitId);
      }
    }
  }

  private static void foldRefactorings(@NotNull OnesideTextDiffViewer viewer, String commitId) {
    List<RefactoringInfo> foldableRefactorings =
        MiningService.getInstance(viewer.getProject()).get(commitId).getRefactorings().stream()
            .filter(info -> foldingHandlers.containsKey(info.getType()))
            .collect(Collectors.toList());

    Editor editor = viewer.getEditor();

    modifyEditor(editor, foldableRefactorings, true);
    modifyEditor(editor, foldableRefactorings, false);
  }

  private static void foldRefactorings(@NotNull TwosideTextDiffViewer viewer, String commitId) {
    List<RefactoringInfo> foldableRefactorings =
        MiningService.getInstance(viewer.getProject()).get(commitId).getRefactorings().stream()
            .filter(info -> foldingHandlers.containsKey(info.getType()))
            .collect(Collectors.toList());

    modifyEditor(viewer.getEditor1(), foldableRefactorings, true);
    modifyEditor(viewer.getEditor2(), foldableRefactorings, false);
  }

  private static void foldRefactorings(@NotNull ThreesideTextDiffViewer viewer, String commitId) {
    List<RefactoringInfo> foldableRefactorings =
        MiningService.getInstance(viewer.getProject()).get(commitId).getRefactorings().stream()
            .filter(info -> foldingHandlers.containsKey(info.getType()))
            .collect(Collectors.toList());

    List<? extends EditorEx> editor = viewer.getEditors();

    modifyEditor(editor.get(0), foldableRefactorings, true);
    modifyEditor(editor.get(1), foldableRefactorings, false);
    modifyEditor(editor.get(2), foldableRefactorings, true);
  }

  private static void modifyEditor(@NotNull Editor editor,
                                   @NotNull List<RefactoringInfo> foldableRefactorings,
                                   boolean before) {
    PsiFile psiFile = PsiDocumentManager
        .getInstance(editor.getProject())
        .getPsiFile(editor.getDocument());

    foldableRefactorings.forEach(info -> {
          List<FoldingHandler.Folding> folds = foldingHandlers.get(info.getType()).getFolds(info, psiFile, before);
          if (!folds.isEmpty()) {
            editor.getFoldingModel().runBatchFoldingOperation(
                () -> {
                  for (FoldingHandler.Folding folding : folds) {
                    FoldRegion value = editor.getFoldingModel()
                        .addFoldRegion(folding.foldingStartOffset, folding.foldingEndOffset, "");
                    if (value != null) {
                      value.setExpanded(false);
                      value.setInnerHighlightersMuted(true);
                    }

                    RendererWrapper renderer = new RendererWrapper(new HintRenderer(folding.hintText), false);
                    editor.getInlayModel().addBlockElement(
                        folding.hintOffset,
                        true, true, 1,
                        renderer);
                  }
                });
          }
        }
    );
  }
}
