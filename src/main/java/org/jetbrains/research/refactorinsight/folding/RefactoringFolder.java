package org.jetbrains.research.refactorinsight.folding;

import com.intellij.application.options.colors.highlighting.RendererWrapper;
import com.intellij.codeInsight.daemon.impl.HintRenderer;
import com.intellij.diff.DiffVcsDataKeys;
import com.intellij.diff.FrameDiffTool;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.tools.util.base.DiffViewerBase;
import com.intellij.diff.tools.util.side.OnesideTextDiffViewer;
import com.intellij.diff.tools.util.side.ThreesideTextDiffViewer;
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.research.refactorinsight.adapters.RefactoringType;
import org.jetbrains.research.refactorinsight.data.RefactoringEntry;
import org.jetbrains.research.refactorinsight.data.RefactoringInfo;
import org.jetbrains.research.refactorinsight.folding.handlers.ExtractOperationFoldingHandler;
import org.jetbrains.research.refactorinsight.folding.handlers.FoldingHandler;
import org.jetbrains.research.refactorinsight.folding.handlers.InlineOperationFoldingHandler;
import org.jetbrains.research.refactorinsight.folding.handlers.MoveOperationFoldingHandler;
import org.jetbrains.research.refactorinsight.services.MiningService;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RefactoringFolder {
  static Map<RefactoringType, FoldingHandler> foldingHandlers;

  static {
    foldingHandlers = new EnumMap<>(RefactoringType.class);
    FoldingHandler moveOperationHandler = new MoveOperationFoldingHandler();
    foldingHandlers.put(RefactoringType.MOVE_OPERATION, moveOperationHandler);
    foldingHandlers.put(RefactoringType.PULL_UP_OPERATION, moveOperationHandler);
    foldingHandlers.put(RefactoringType.PUSH_DOWN_OPERATION, moveOperationHandler);
    foldingHandlers.put(RefactoringType.MOVE_AND_RENAME_OPERATION, moveOperationHandler);
    FoldingHandler inlineOperationHandler = new InlineOperationFoldingHandler();
    foldingHandlers.put(RefactoringType.INLINE_OPERATION, inlineOperationHandler);
    foldingHandlers.put(RefactoringType.MOVE_AND_INLINE_OPERATION, inlineOperationHandler);
    FoldingHandler extractOperationHandler = new ExtractOperationFoldingHandler();
    foldingHandlers.put(RefactoringType.EXTRACT_OPERATION, extractOperationHandler);
    foldingHandlers.put(RefactoringType.EXTRACT_AND_MOVE_OPERATION, extractOperationHandler);
  }

  private RefactoringFolder() {}

  /**
   * Fold refactorings in the viewer if supported.
   *
   * @param viewer  Viewer of diff request.
   * @param request Associated diff request.
   */
  public static void foldRefactorings(@NotNull FrameDiffTool.DiffViewer viewer, @NotNull DiffRequest request) {
    if (!(request instanceof SimpleDiffRequest && viewer instanceof DiffViewerBase)) {
      return;
    }
    SimpleDiffRequest diffRequest = (SimpleDiffRequest) request;
    DiffViewerBase viewerBase = (DiffViewerBase) viewer;

    Project project = viewerBase.getProject();
    if (project == null) {
      return;
    }

    String commitId = getRevisionAfter(diffRequest);
    if (commitId == null) {
      return;
    }

    RefactoringEntry entry = MiningService.getInstance(project).get(commitId);
    if (entry == null) {
      return;
    }

    List<RefactoringInfo> foldableRefactorings =
        entry.getRefactorings().stream()
            .filter(info -> foldingHandlers.containsKey(info.getType()))
            .collect(Collectors.toList());

    if (viewerBase instanceof OnesideTextDiffViewer) {
      RefactoringFolder.foldRefactorings(foldableRefactorings, (OnesideTextDiffViewer) viewerBase, project);
    } else if (viewerBase instanceof TwosideTextDiffViewer) {
      RefactoringFolder.foldRefactorings(foldableRefactorings, (TwosideTextDiffViewer) viewerBase, project);
    } else if (viewerBase instanceof ThreesideTextDiffViewer) {
      RefactoringFolder.foldRefactorings(foldableRefactorings, (ThreesideTextDiffViewer) viewerBase, project);
    }
  }

  /**
   * Fold only in added files.
   */
  private static void foldRefactorings(@NotNull List<RefactoringInfo> foldableRefactorings,
                                       @NotNull OnesideTextDiffViewer viewer,
                                       @NotNull Project project) {
    modifyEditor(viewer.getEditor(), foldableRefactorings, project, false);
  }

  private static void foldRefactorings(@NotNull List<RefactoringInfo> foldableRefactorings,
                                       @NotNull TwosideTextDiffViewer viewer,
                                       @NotNull Project project) {
    modifyEditor(viewer.getEditor1(), foldableRefactorings, project, true);
    modifyEditor(viewer.getEditor2(), foldableRefactorings, project, false);
  }

  private static void foldRefactorings(@NotNull List<RefactoringInfo> foldableRefactorings,
                                       @NotNull ThreesideTextDiffViewer viewer,
                                       @NotNull Project project) {
    List<? extends EditorEx> editors = viewer.getEditors();

    modifyEditor(editors.get(0), foldableRefactorings, project, true);
    modifyEditor(editors.get(1), foldableRefactorings, project, false);
    modifyEditor(editors.get(2), foldableRefactorings, project, true);
  }

  private static void modifyEditor(@NotNull Editor editor,
                                   @NotNull List<RefactoringInfo> foldableRefactorings,
                                   @NotNull Project project,
                                   boolean before) {
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (psiFile == null) {
      return;
    }

    List<Folding> folds = foldableRefactorings.stream()
        .flatMap(info ->
            foldingHandlers.get(info.getType()).getFolds(info, psiFile, before).stream()
                .map(folding -> new Pair<>(info.getType(), folding))
        ).collect(
            Collectors.groupingBy(pair -> pair.second.positions.hintOffset,
                Collectors.groupingBy(pair -> pair.first,
                    Collectors.mapping(pair -> pair.second,
                        Collectors.toList()))))
        .values().stream()
        .flatMap(map -> map.entrySet().stream())
        .map(group -> group.getValue().size() > 1
            ? foldingHandlers.get(group.getKey()).uniteFolds(group.getValue())
            : group.getValue().get(0))
        .collect(Collectors.toList());

    editor.getFoldingModel().runBatchFoldingOperation(() -> {
      for (Folding folding : folds) {
        FoldRegion value = editor.getFoldingModel()
            .addFoldRegion(folding.positions.foldingStartOffset, folding.positions.foldingEndOffset, "");
        if (value != null) {
          value.setExpanded(false);
          value.setInnerHighlightersMuted(true);
        }

        RendererWrapper renderer = new RendererWrapper(new HintRenderer(folding.hintText), false);
        editor.getInlayModel().addBlockElement(
            folding.positions.hintOffset,
            true, true, 1,
            renderer);
      }
    });
  }

  @Nullable
  private static String getRevisionAfter(@NotNull SimpleDiffRequest request) {
    List<DiffContent> contents = request.getContents();
    if (contents.size() < 2) {
      return null;
    }
    Pair<FilePath, VcsRevisionNumber> userDataAfter = contents.get(1).getUserData(DiffVcsDataKeys.REVISION_INFO);
    if (userDataAfter == null) {
      return null;
    }
    return userDataAfter.second.asString();
  }
}
