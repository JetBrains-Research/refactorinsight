package org.jetbrains.research.refactorinsight.processors;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import com.intellij.vcs.log.TimedVcsCommit;
import git4idea.repo.GitRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.lib.Repository;
import org.jetbrains.research.kotlinrminer.api.GitHistoryKotlinRMiner;
import org.jetbrains.research.refactorinsight.data.RefactoringEntry;
import org.jetbrains.research.refactorinsight.RefactorInsightBundle;
import org.jetbrains.research.refactorinsight.services.MiningService;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;

/**
 * The CommitMiner is a Consumer of GitCommit.
 * It mines a commit and updates the refactoring map with the data retrieved for that commit.
 * Consumes a git commit, calls RefactoringMiner and detects the refactorings for a commit.
 */
public class CommitMiner implements Consumer<TimedVcsCommit> {
  private static final String progress = RefactorInsightBundle.message("progress");
  private final ExecutorService pool;
  private final Map<String, RefactoringEntry> map;
  private final Project myProject;
  private final Repository myRepository;
  private final AtomicInteger commitsDone;
  private final ProgressIndicator progressIndicator;
  private final int limit;

  /**
   * misc.CommitMiner for mining a single commit.
   *
   * @param pool       ThreadPool to submit to.
   * @param map        Map to add mined commit data to.
   * @param repository GitRepository.
   */
  public CommitMiner(ExecutorService pool, Map<String, RefactoringEntry> map,
                     GitRepository repository,
                     AtomicInteger commitsDone, ProgressIndicator progressIndicator, int limit) {
    this.pool = pool;
    this.map = map;
    myProject = repository.getProject();
    //NB: nullable, check if initialized correctly
    myRepository = ServiceManager.getService(myProject, MiningService.class).getRepository();
    this.commitsDone = commitsDone;
    this.progressIndicator = progressIndicator;
    this.limit = limit;
  }

  /**
   * Returns a runnable that processes only one commit by consistently running RefactoringMiner and kotlinRMiner.
   *
   * @param commit     commit metadata.
   * @param map        the inner map that should be updated.
   * @param project    the current project.
   * @param repository Git Repository.
   */
  public static Runnable mineAtCommit(TimedVcsCommit commit, Map<String, RefactoringEntry> map,
                                      Project project, Repository repository) {
    return getRunnableToDetectRefactorings(map, commit, repository, project);
  }

  /**
   * Creates a runnable to detect refactorings in Kotlin and Java code.
   *
   * @param commit     commit metadata.
   * @param map        the inner map that should be updated.
   * @param project    the current project.
   * @param repository Git Repository.
   * @return a runnable.
   */
  private static Runnable getRunnableToDetectRefactorings(Map<String, RefactoringEntry> map, TimedVcsCommit commit,
                                                          Repository repository, Project project) {
    String commitId = commit.getId().asString();

    GitHistoryKotlinRMiner kminer = new GitHistoryKotlinRMiner();
    GitHistoryRefactoringMiner jminer = new GitHistoryRefactoringMinerImpl();

    return () -> {
      try {
        jminer.detectAtCommit(repository, commitId, new RefactoringHandler() {
          @Override
          public void handle(String commitId, List<Refactoring> refactorings) {
            map.put(commitId, RefactoringEntry.convertJavaRefactorings(refactorings, commit, project));
          }
        });

        kminer.detectAtCommit(repository, commitId,
            new org.jetbrains.research.kotlinrminer.api.RefactoringHandler() {
              @Override
              public void handle(String commitId,
                                 List<org.jetbrains.research.kotlinrminer.api.Refactoring> refactorings) {
                final RefactoringEntry convertedKtRefactorings =
                    RefactoringEntry.convertKotlinRefactorings(refactorings, commit, project);
                Optional.ofNullable(map.get(commitId)).ifPresentOrElse(
                    re -> re.addRefactorings(convertedKtRefactorings.getRefactorings()),
                    () -> map.put(commitId, convertedKtRefactorings)
                );
              }
            });
      } catch (Exception e) {
        e.printStackTrace();
      }
    };
  }

  /**
   * Mines a gitCommit.
   * Method that calls RefactoringMiner and updates the refactoring map.
   *
   * @param gitCommit to be mined
   */
  public void consume(TimedVcsCommit gitCommit) throws ProcessCanceledException {
    String commitId = gitCommit.getId().asString();

    if (!map.containsKey(commitId)) {
      pool.execute(() -> {
        if (progressIndicator.isCanceled()) {
          cancelProgress();
          return;
        }
        detectRefactorings(getRunnableToDetectRefactorings(map, gitCommit, myRepository, myProject), gitCommit);
        incrementProgress();
      });
    } else {
      incrementProgress();
      progressIndicator.checkCanceled();
    }
  }

  private void detectRefactorings(Runnable runnable, TimedVcsCommit commit) {
    String commitId = commit.getId().asString();
    ExecutorService service = Executors.newSingleThreadExecutor();
    Future<?> f = null;
    try {
      f = service.submit(runnable);
      f.get(120, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      if (f.cancel(true)) {
        RefactoringEntry refactoringEntry = RefactoringEntry.createEmptyEntry(commit);
        refactoringEntry.setTimeout(true);
        map.put(commitId, refactoringEntry);
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      service.shutdown();
    }
  }

  /**
   * Increments the progress bar with each mined commit.
   */
  private void incrementProgress() {
    final int nCommits = commitsDone.incrementAndGet();
    progressIndicator.setText(String.format(progress,
        nCommits, limit));
    progressIndicator.setFraction((float) nCommits / limit);
  }

  private void cancelProgress() {
    final int nCommits = commitsDone.incrementAndGet();
    progressIndicator.setFraction((float) nCommits / limit);
    progressIndicator.setText("Cancelling");
  }
}