package org.jetbrains.research.refactorinsight.data.types.methods;

import gr.uom.java.xmi.decomposition.AbstractStatement;
import gr.uom.java.xmi.diff.PushDownOperationRefactoring;
import org.jetbrains.research.refactorinsight.adapters.CodeRange;
import org.jetbrains.research.refactorinsight.data.Group;
import org.jetbrains.research.refactorinsight.data.RefactoringInfo;
import org.jetbrains.research.refactorinsight.data.RefactoringLine;
import org.jetbrains.research.refactorinsight.data.types.Handler;
import org.jetbrains.research.refactorinsight.folding.FoldingBuilder;
import org.jetbrains.research.refactorinsight.utils.StringUtils;
import org.jetbrains.research.refactorinsight.utils.Utils;
import org.refactoringminer.api.Refactoring;

import java.util.List;

public class PushDownOperationHandler extends Handler {

  @Override
  public RefactoringInfo specify(Refactoring refactoring, RefactoringInfo info) {
    PushDownOperationRefactoring ref = (PushDownOperationRefactoring) refactoring;

    info.setFoldingDescriptorBefore(FoldingBuilder.fromMethod(ref.getOriginalOperation()));
    info.setFoldingDescriptorAfter(FoldingBuilder.fromMethod(ref.getMovedOperation()));

    List<AbstractStatement> statementsBefore =
        ref.getOriginalOperation().getBody().getCompositeStatement().getStatements();
    List<AbstractStatement> statementsAfter =
        ref.getMovedOperation().getBody().getCompositeStatement().getStatements();
    info.setChanged(!Utils.isStatementsEqualJava(statementsBefore, statementsAfter));

    String classBefore = ref.getOriginalOperation().getClassName();
    String classAfter = ref.getMovedOperation().getClassName();

    return info.setGroup(Group.METHOD)
        .setDetailsBefore(classBefore)
        .setDetailsAfter(classAfter)
        .addMarking(
            new CodeRange(ref.getOriginalOperation().codeRange()),
            new CodeRange(ref.getMovedOperation().codeRange()),
            refactoringLine -> refactoringLine.setWord(new String[]{
                ref.getOriginalOperation().getName(),
                null,
                ref.getMovedOperation().getName()
            }),
            RefactoringLine.MarkingOption.COLLAPSE,
            true)
        .setNameBefore(StringUtils.calculateSignature(ref.getOriginalOperation()))
        .setNameAfter(StringUtils.calculateSignature(ref.getMovedOperation()));

  }

  @Override
  public RefactoringInfo specify(org.jetbrains.research.kotlinrminer.api.Refactoring refactoring,
                                 RefactoringInfo info) {
    org.jetbrains.research.kotlinrminer.diff.refactoring.PushDownOperationRefactoring ref =
        (org.jetbrains.research.kotlinrminer.diff.refactoring.PushDownOperationRefactoring) refactoring;

    info.setFoldingDescriptorBefore(FoldingBuilder.fromMethod(ref.getOriginalOperation()));
    info.setFoldingDescriptorAfter(FoldingBuilder.fromMethod(ref.getMovedOperation()));

    List<org.jetbrains.research.kotlinrminer.decomposition.AbstractStatement> statementsBefore =
        ref.getOriginalOperation().getBody().getCompositeStatement().getStatements();
    List<org.jetbrains.research.kotlinrminer.decomposition.AbstractStatement> statementsAfter =
        ref.getMovedOperation().getBody().getCompositeStatement().getStatements();
    info.setChanged(!Utils.isStatementsEqualKotlin(statementsBefore, statementsAfter));

    String classBefore = ref.getOriginalOperation().getClassName();
    String classAfter = ref.getMovedOperation().getClassName();

    return info.setGroup(Group.METHOD)
        .setDetailsBefore(classBefore)
        .setDetailsAfter(classAfter)
        .addMarking(
            new CodeRange(ref.getOriginalOperation().codeRange()),
            new CodeRange(ref.getMovedOperation().codeRange()),
            refactoringLine -> refactoringLine.setWord(new String[]{
                ref.getOriginalOperation().getName(),
                null,
                ref.getMovedOperation().getName()
            }),
            RefactoringLine.MarkingOption.COLLAPSE,
            true)
        .setNameBefore(StringUtils.calculateSignature(ref.getOriginalOperation()))
        .setNameAfter(StringUtils.calculateSignature(ref.getMovedOperation()));
  }
}
