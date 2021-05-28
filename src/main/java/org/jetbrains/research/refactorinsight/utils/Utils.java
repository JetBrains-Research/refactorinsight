package org.jetbrains.research.refactorinsight.utils;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.vcs.log.impl.VcsLogManager;
import com.intellij.vcs.log.impl.VcsProjectLog;
import git4idea.GitContentRevision;
import git4idea.GitRevisionNumber;
import git4idea.repo.GitRepository;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.research.refactorinsight.adapters.RefactoringType;
import org.jetbrains.research.refactorinsight.data.RefactoringEntry;
import org.jetbrains.research.refactorinsight.data.RefactoringInfo;
import org.jetbrains.research.refactorinsight.data.RefactoringLine;
import org.jetbrains.research.refactorinsight.RefactorInsightBundle;

import static org.jetbrains.research.refactorinsight.adapters.RefactoringType.CHANGE_ATTRIBUTE_TYPE;
import static org.jetbrains.research.refactorinsight.adapters.RefactoringType.CHANGE_PARAMETER_TYPE;
import static org.jetbrains.research.refactorinsight.adapters.RefactoringType.CHANGE_VARIABLE_TYPE;
import static org.jetbrains.research.refactorinsight.adapters.RefactoringType.RENAME_ATTRIBUTE;
import static org.jetbrains.research.refactorinsight.adapters.RefactoringType.RENAME_PARAMETER;

public class Utils {

  public static ToolWindowManager manager;
  /**
   * Used for storing and disposing the MainVcsLogs used for method history action.
   */
  private static final ArrayList<Disposable> logs = new ArrayList<>();

  /**
   * Method used for disposing the logs that were created and shown for the method history action.
   * Called when the project is closing.
   * Avoids memory leaks.
   */
  public static void dispose() {
    for (Disposable log : logs) {
      Disposer.dispose(log);
    }
  }

  /**
   * Adds a Disposable object to the list.
   *
   * @param log to add.
   */
  public static void add(Disposable log) {
    logs.add(log);
  }

  /**
   * Sorts the info list for a better displaying.
   *
   * @param infos to be sorted.
   */
  public static void chronologicalOrder(List<RefactoringInfo> infos) {
    infos.sort(Comparator.comparingLong(o -> o.getEntry().getTimeStamp()));
  }

  /**
   * Finds the start and ending column of a word in a text.
   *
   * @param text Java Code
   * @param word Word to look for.
   * @param line In what line the word can be found.
   * @return Start and ending column in an int[]
   */
  public static int[] findColumns(String text, String word, int line) {
    String[] lines = text.split("\r\n|\r|\n");
    int startColumn = lines[line].indexOf(word) + 1;
    int endColumn = startColumn + word.length();
    return new int[]{startColumn, endColumn};
  }

  /**
   * Similar to find columns but starts from the back of the line.
   *
   * @param text Java code
   * @param word Word to look for
   * @param line Line to look in
   * @return Start and ending column in int[]
   */
  public static int[] findColumnsBackwards(String text, String word, int line) {
    String[] lines = text.split("\r\n|\r|\n");
    int startColumn = lines[line].lastIndexOf(word) + 1;
    int endColumn = startColumn + word.length();
    return new int[]{startColumn, endColumn};
  }

  /**
   * Skips javadoc for a method or class.
   *
   * @param text to search in.
   * @param line current line.
   * @return the actual line.
   */
  public static int skipJavadoc(String text, int line, boolean skipAnnotations) {
    String[] lines = text.split("\r\n|\r|\n");
    if (line >= lines.length) {
      System.out.println(text);

      System.out.println(line);
    }
    if (lines[line].contains("/**")) {
      for (int i = line + 1; i < lines.length; i++) {
        if (lines[i].contains("*/")) {
          return skipAnnotations ? skipAnnotations(lines, i + 1) : i + 1;
        }
      }
    }
    return skipAnnotations ? skipAnnotations(lines, line) : line;
  }

  private static int skipAnnotations(String[] lines, int line) {
    for (int i = line; i < lines.length; i++) {
      if (lines[i].matches("((\\s|\\t)*@(\\w)*([(](.)*[)])*(\\s|\\t)*)+")
          || lines[i].matches("(\\s|\\t)*")) {
        continue;
      } else {
        return i;
      }
    }
    return line;
  }

  /**
   * Calculates offset.
   *
   * @param text   to search in
   * @param line   line
   * @param column column
   * @return offset
   */
  public static int getOffset(String text, int line, int column) {
    int offset = 0;
    String[] lines = text.split("\r\n|\r|\n");
    if (lines.length <= line - 2) {
      line = lines.length;
    }
    for (int i = 0; i < line - 1; i++) {
      offset += lines[i].length() + 1;
    }
    return offset + column - 1;
  }

  /**
   * Returns last line count.
   *
   * @param text to search in
   * @return length of the text
   */
  public static int getMaxLine(String text) {
    return text.split("\r\n|\r|\n").length;
  }

  /**
   * Gets the main refactoring in the list for combining purposes.
   *
   * @param infos list of refactorings
   * @return RefactoringInfo
   */
  public static RefactoringInfo getMainRefactoringInfo(List<RefactoringInfo> infos) {
    RefactoringInfo info = null;
    if (infos.stream().anyMatch(ofType(RENAME_ATTRIBUTE))
        && infos.stream().anyMatch(ofType(CHANGE_ATTRIBUTE_TYPE))) {
      info = infos.stream().filter(ofType(RENAME_ATTRIBUTE)).findFirst().get();
      info.setType(RefactoringType.RENAME_AND_CHANGE_ATTRIBUTE_TYPE);
    } else if (infos.stream().anyMatch(ofType(RENAME_ATTRIBUTE))) {
      info = infos.stream().filter(ofType(RENAME_ATTRIBUTE)).findFirst().get();
    } else if (infos.stream().anyMatch(ofType(CHANGE_ATTRIBUTE_TYPE))) {
      info = infos.stream().filter(ofType(CHANGE_ATTRIBUTE_TYPE)).findFirst().get();
    } else if (infos.stream().anyMatch(ofType(CHANGE_VARIABLE_TYPE))) {
      info = infos.stream().filter(ofType(CHANGE_VARIABLE_TYPE)).findFirst().get();
      info.setType(RefactoringType.RENAME_AND_CHANGE_VARIABLE_TYPE);
    } else if (infos.stream().anyMatch(ofType(RENAME_PARAMETER))
        && infos.stream().anyMatch(ofType(CHANGE_PARAMETER_TYPE))) {
      info = infos.stream().filter(ofType(RENAME_PARAMETER)).findFirst().get();
      info.setType(RefactoringType.RENAME_AND_CHANGE_PARAMETER_TYPE);
    }
    return info;
  }

  private static Predicate<RefactoringInfo> ofType(RefactoringType type) {
    return (r) -> r.getType().equals(type);
  }

  /**
   * Removes the extra part of the file path.
   *
   * @param path path to the file.
   * @return fixed path.
   */
  public static String fixPath(String path) {
    if (path != null && path.contains(".gradle/caches/")) {
      return path.substring(path.lastIndexOf("/bin/") + 5);
    } else {
      return path;
    }
  }

  /**
   * Checks and corrects the ranges returned by RefactoringMiner.
   *
   * @param info    refactoring info
   * @param project the open project
   * @return the corrected RefactoringInfo
   */
  public static RefactoringInfo check(RefactoringInfo info, Project project) {
    //check for refactorings without line markings
    // such as move source folder or rename package
    if (info.getLeftPath() == null || info.getRightPath() == null) {
      return info;
    }

    FilePath beforePath = new LocalFilePath(
        project.getBasePath() + "/"
            + info.getLeftPath(), false);
    FilePath midPath = !info.isThreeSided() ? null : new LocalFilePath(
        project.getBasePath() + "/"
            + info.getMidPath(), false);
    FilePath afterPath = new LocalFilePath(
        project.getBasePath() + "/"
            + info.getRightPath(), false);
    GitRevisionNumber afterNumber = new GitRevisionNumber(info.getCommitId());
    GitRevisionNumber beforeNumber = new GitRevisionNumber(info.getParent());


    try {
      String after = GitContentRevision
          .createRevision(afterPath, afterNumber, project).getContent();

      if (!info.isMoreSided()) {
        String before = GitContentRevision
            .createRevision(beforePath, beforeNumber, project).getContent();
        String mid = !info.isThreeSided() ? null : GitContentRevision
            .createRevision(midPath, afterNumber, project).getContent();

        info.correctLines(before, mid, after);
      } else {
        List<String> befores = new ArrayList<>();
        for (Pair<String, Boolean> pathPair : info.getMoreSidedLeftPaths()) {
          GitRevisionNumber number = pathPair.second ? afterNumber : beforeNumber;
          FilePath filePath =
              new LocalFilePath(project.getBasePath() + "/" + fixPath(pathPair.first), false);
          befores.add(
              GitContentRevision.createRevision(filePath, number, project).getContent());
        }
        info.correctMoreSidedLines(befores, after);
      }
    } catch (VcsException e) {
      e.printStackTrace();
    }

    return info;
  }

  /**
   * Calculates the line of the package.
   *
   * @param text to search in.
   * @return line of the package.
   */
  public static int findPackageLine(String text) {
    String[] lines = text.split("\r\n|\r|\n");
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].contains("package ")) {
        return i;
      } else if (lines[i].matches("^[a-zA-Z0-9]*$")) {
        return -1;
      }
    }
    return 0;
  }

  /**
   * Get the total amount of commits in a repository.
   *
   * @param repository GitRepository
   * @return the amount of commits
   * @throws IOException in case of a problem
   */
  public static int getCommitCount(GitRepository repository) throws IOException {
    Process process = Runtime.getRuntime().exec("git rev-list --all --count", null,
        new File(repository.getRoot().getCanonicalPath()));
    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    String output = reader.readLine();
    return Integer.parseInt(output);
  }

  /**
   * Calculates the version of the project by computing the hash code of the existing classes.
   *
   * @return the current version.
   */
  public static String version() {
    return RefactorInsightBundle.message("version") + String.valueOf(Stream.of(
        //all classes that can change
        RefactoringEntry.class,
        RefactoringInfo.class,
        RefactoringLine.class,
        RefactoringLine.RefactoringOffset.class
    ).flatMap(c -> Arrays.stream(c.getDeclaredFields())
        .map(Field::getGenericType)
        .map(Type::getTypeName)
    ).collect(Collectors.toList()))
        .hashCode();
  }

  /**
   * Disposes the Vcs Log panel.
   */
  public static void disposeWithVcsLogManager(@NotNull Project project, @NotNull Disposable disposable) {
    Disposable connectionDisposable = Disposer.newDisposable();
    project.getMessageBus().connect(connectionDisposable)
        .subscribe(VcsProjectLog.VCS_PROJECT_LOG_CHANGED, new VcsProjectLog.ProjectLogListener() {
          @Override
          public void logCreated(@NotNull VcsLogManager manager) {
          }

          @Override
          public void logDisposed(@NotNull VcsLogManager manager) {
            Disposer.dispose(connectionDisposable);
            Disposer.dispose(disposable);
          }
        });
  }
}
