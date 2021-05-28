package org.jetbrains.research.refactorinsight.data;

import static org.jetbrains.research.refactorinsight.utils.StringUtils.ENTRY;
import static org.jetbrains.research.refactorinsight.adapters.RefactoringType.EXTRACT_CLASS;
import static org.jetbrains.research.refactorinsight.adapters.RefactoringType.EXTRACT_SUPERCLASS;
import static org.jetbrains.research.refactorinsight.adapters.RefactoringType.MOVE_ATTRIBUTE;
import static org.jetbrains.research.refactorinsight.adapters.RefactoringType.MOVE_OPERATION;
import static org.jetbrains.research.refactorinsight.adapters.RefactoringType.PULL_UP_ATTRIBUTE;
import static org.jetbrains.research.refactorinsight.adapters.RefactoringType.PULL_UP_OPERATION;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.research.refactorinsight.utils.StringUtils;
import org.jetbrains.research.refactorinsight.utils.Utils;
import org.refactoringminer.api.Refactoring;

/**
 * Collects data for a single commit: commit id, timestamp, parent, and a list of refactorings.
 */
public class RefactoringEntry implements Serializable {

  private static final transient InfoFactory factory = new InfoFactory();

  private final transient String commitId;
  private final String parent;
  private final long time;
  private List<RefactoringInfo> refactorings;
  public boolean timeout = false;

  /**
   * Constructor for refactoring entry.
   *
   * @param parent the commit id of the parent.
   * @param time   timestamp of the commit.
   */
  public RefactoringEntry(String commitId, String parent, long time) {
    this.commitId = commitId;
    this.parent = parent;
    this.time = time;
  }

  /**
   * Deserializes an {@link RefactoringEntry} instance from string.
   *
   * @param value String.
   * @return the RefactoringEntry.
   */
  public static RefactoringEntry fromString(String value, String commitId) {
    String regex = StringUtils.delimiter(ENTRY, true);
    String[] tokens = value.split(regex, 4);
    String[] refs = tokens[3].split(regex);
    if (refs[0].isEmpty()) {
      refs = new String[0];
    }
    RefactoringEntry entry = new RefactoringEntry(
        commitId, tokens[0], Long.parseLong(tokens[1]))
        .setRefactorings(Arrays.stream(refs)
                             .map(RefactoringInfo::fromString).collect(Collectors.toList()));
    entry.timeout = Boolean.parseBoolean(tokens[2]);
    entry.getRefactorings().forEach(r -> r.setEntry(entry));
    return entry;
  }

  public void setTimeout(boolean timeout) {
    this.timeout = timeout;
  }

  /**
   * Converter to RefactoringEntry given a list of refactorings, commit metadata and project.
   *
   * @param refactorings     to be processed.
   * @param commitHash       current commit.
   * @param commitParentHash parent commit hash.
   * @param commitTimestamp  commit timestamp.
   * @return new refactoring entry.
   */
  public static RefactoringEntry convertJavaRefactorings(List<Refactoring> refactorings,
                                                         String commitHash,
                                                         String commitParentHash,
                                                         long commitTimestamp,
                                                         Project project) {
    RefactoringEntry entry =
        new RefactoringEntry(commitHash, commitParentHash, commitTimestamp);

    List<RefactoringInfo> infos =
        refactorings.stream().map(ref -> factory.create(ref, entry)).collect(
            Collectors.toList());

    entry.setRefactorings(infos).combineRelated();

    entry.refactorings.forEach(info -> Utils.check(info, project));
    return entry;
  }

  /**
   * Converter to RefactoringEntry given a list of refactorings, commit metadata and project.
   *
   * @param refactorings     to be processed.
   * @param commitHash       current commit.
   * @param commitParentHash parent commit hash.
   * @param commitTimestamp  commit timestamp.
   * @return new refactoring entry.
   */
  public static RefactoringEntry convertKotlinRefactorings(
      List<org.jetbrains.research.kotlinrminer.api.Refactoring> refactorings,
      String commitHash,
      String commitParentHash,
      long commitTimestamp,
      Project project) {
    RefactoringEntry entry =
        new RefactoringEntry(commitHash, commitParentHash, commitTimestamp);

    List<RefactoringInfo> infos =
        refactorings.stream().map(ref -> factory.create(ref, entry)).collect(
            Collectors.toList());

    entry.setRefactorings(infos).combineRelated();

    entry.refactorings.forEach(info -> Utils.check(info, project));
    return entry;
  }

  /**
   * Creates an empty entry for a commit that doesn't contain any refactoring.
   *
   * @param commitHash       current commit.
   * @param commitParentHash parent commit hash.
   * @param commitTimestamp  commit timestamp.
   * @return a new empty refactoring empty.
   */
  public static RefactoringEntry createEmptyEntry(String commitHash,
                                                  String commitParentHash,
                                                  long commitTimestamp) {
    RefactoringEntry refactoringEntry = new RefactoringEntry(commitHash,
                                                             commitParentHash, commitTimestamp);
    refactoringEntry.setRefactorings(new ArrayList<>());
    return refactoringEntry;
  }

  @Override
  public String toString() {
    String del = StringUtils.delimiter(ENTRY);
    return parent + del + time + del + timeout + del + refactorings.stream()
        .map(RefactoringInfo::toString).collect(Collectors.joining(del));
  }

  /**
   * Combines related refactorings.
   * Firstly, it combines Extract Superclass and Extract Class with its specific move attribute's
   * and move method's refactorings.
   * Secondly, it combines the refactorings that have the same group identifiers.
   */
  private void combineRelated() {
    combineRelatedExtractSuperClass();
    combineRelatedExtractClass();

    HashMap<String, List<RefactoringInfo>> groups = new HashMap<>();
    refactorings.forEach(r -> {
      if (r.getGroupId() != null) {
        List<RefactoringInfo> list = groups.getOrDefault(r.getGroupId(), new ArrayList<>());
        list.add(r);
        groups.put(r.getGroupId(), list);
      }
      r.setEntry(this);
    });

    groups.forEach((k, v) -> {
      if (v.size() > 1) {
        RefactoringInfo info = Utils.getMainRefactoringInfo(v);

        if (info == null) {
          return;
        }

        v.remove(info);
        v.forEach(r -> {
          info.addIncludedRefactoring(r.getName());
          info.addAllMarkings(r);
          r.setHidden(true);
        });
      }
    });
  }

  private void combineRelatedExtractSuperClass() {
    //Find all extract Super Class Refactorings
    List<RefactoringInfo> superClassRefs = refactorings.stream()
        .filter(info -> info != null && info.getType().equals(EXTRACT_SUPERCLASS))
        .collect(Collectors.toList());

    //Find all pull up refactorings
    List<RefactoringInfo> pullUpRefs = refactorings.stream()
        .filter(info -> info != null && info.getType().equals(PULL_UP_ATTRIBUTE)
            || info != null && info.getType().equals(PULL_UP_OPERATION))
        .collect(Collectors.toList());

    superClassRefs.forEach(info -> {
      String superPath = info.getRightPath();
      //Relate
      List<RefactoringInfo> related = pullUpRefs.stream()
          .filter(pullUp -> pullUp != null && pullUp.getRightPath().equals(superPath))
          .collect(Collectors.toList());

      //Combine ranges
      related.forEach(relInfo -> {
        relInfo.setHidden(true);
        relInfo.getLineMarkings().forEach(line -> {
          line.setMoreSided(true);
          info.getLineMarkings().add(0, line);
          info.getMoreSidedLeftPaths().add(0, new Pair<>(relInfo.getLeftPath(), false));
        });
      });
    });
  }

  private void combineRelatedExtractClass() {
    //find all extract class refactorings
    List<RefactoringInfo> extractClassRefactorings = refactorings
        .stream().filter(x -> x != null && x.getType().equals(EXTRACT_CLASS))
        .collect(Collectors.toList());

    List<RefactoringInfo> moves = refactorings.stream()
        .filter(info -> info.getType().equals(MOVE_OPERATION)
            || info.getType().equals(MOVE_ATTRIBUTE))
        .collect(Collectors.toList());

    extractClassRefactorings.forEach(
        extractClass -> {
          String extracted = extractClass.getMidPath() == null ? extractClass.getRightPath() :
              extractClass.getMidPath();
          String leftPath = extractClass.getLeftPath();
          //Relate
          List<RefactoringInfo> related = moves.stream()
              .filter(move -> move.getRightPath().equals(extracted)
                  && move.getLeftPath().equals(leftPath))
              .collect(Collectors.toList());

          //Combine ranges
          related.forEach(r -> {
            extractClass.addIncludedRefactoring(r.getName());
            r.setHidden(true);
          });
        }
    );
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RefactoringEntry entry = (RefactoringEntry) o;
    return time == entry.time
        && Objects.equals(commitId, entry.commitId)
        && Objects.equals(parent, entry.parent)
        && Objects.equals(refactorings, entry.refactorings);
  }


  public List<RefactoringInfo> getRefactorings() {
    return refactorings;
  }

  public void addRefactorings(List<RefactoringInfo> refactorings) {
    this.refactorings.addAll(refactorings);
  }

  public RefactoringEntry setRefactorings(@NotNull List<RefactoringInfo> refactorings) {
    this.refactorings = refactorings;
    return this;
  }

  public String getParent() {
    return parent;
  }

  public long getTimeStamp() {
    return time;
  }

  public String getCommitId() {
    return commitId;
  }
}

