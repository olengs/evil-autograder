// package workspace;

// import autograderexception.WorkspacePreparationException;
// import model.QuestionAttempt;
// import model.QuestionDefinition;
// // import model.TesterDefinition;

// import java.io.IOException;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.nio.file.SimpleFileVisitor;
// import java.nio.file.FileVisitResult;
// import java.nio.file.StandardCopyOption;
// import java.nio.file.attribute.BasicFileAttributes;
// import java.util.Comparator;
// import java.util.List;
// import java.util.concurrent.atomic.AtomicLong;
// import java.util.stream.Stream;

// /**
//  * Builds an isolated, temporary workspace for a single grading attempt and
//  * assembles the three inputs needed to compile/run the attempt:
//  *
//  * <ol>
//  *   <li>the student's answer file(s)</li>
//  *   <li>the question's resource folder (recursively)</li>
//  *   <li>the tester source</li>
//  * </ol>
//  *
//  * All source material is copied, sources are never mutated.
//  * On any failure during {@link #prepare}, the partially-created workspace is
//  * self-cleaned before the {@link WorkspacePreparationException} is thrown, so no
//  * orphaned directories are left behind.
//  *
//  * Implements {@link AutoCloseable} so callers can scope a workspace with
//  * try-with-resources; {@link #close()} deletes the prepared workspace.
//  */
// public class WorkspacePreparer implements AutoCloseable {

//     /** Root of the temp workspace created by {@link #prepare}; {@code null} until prepared. */
//     private Path workspaceroot;

//     /**
//      * Process-wide, monotonic counter used to generate a unique id component
//      * when an attempt carries no explicit id. Being static and atomic, it
//      * guarantees that two concurrent attempts with a {@code null}/blank id can
//      * never produce the same workspace prefix and therefore cannot overwrite
//      * each other.
//      */
//     private static final AtomicLong AUTO_ID = new AtomicLong();

//     /**
//      * Creates a fresh temporary workspace and populates it with the attempt's
//      * answer files, the question's resource folder, and the tester source.
//      *
//      * @param question the question definition (resource folder + tester)
//      * @param attempt  the student's attempt (located answer files)
//      * @return the populated workspace root
//      * @throws WorkspacePreparationException with code:
//      *         wsp-001 if the temp workspace cannot be created,
//      *         wsp-002 if answer file copy fails,
//      *         wsp-003 if the question resource folder is missing,
//      *         wsp-004 if the tester source copy fails
//      */
//     public Path prepare(QuestionDefinition question, QuestionAttempt attempt)
//             throws WorkspacePreparationException {
//         Path root;
//         try {
//             // TODO: no attempt id yet
//             root = Files.createTempDirectory("autograder-ws-" + attemptId(attempt) + "-");
//         } catch (Exception e) {
//             throw new WorkspacePreparationException("wsp-001", "Temporary workspace cannot be created", e);
//         }

//         this.workspaceroot = root;
//         try {
//             copyFile(attempt, root);
//             copyFile(question, root);
//             copyFile(resolveTester(question), root);
//         } catch (WorkspacePreparationException e) {
//             // Self-clean the partially-created workspace, then rethrow the original (coded) failure.
//             selfCleanQuietly(root);
//             this.workspaceroot = null;
//             throw e;
//         }
//         return this.workspaceroot;
//     }

//     /**
//      * Copies the attempt's located answer file(s) into {@code target}.
//      *
//      * @throws WorkspacePreparationException wsp-002 if the copy fails
//      */
//     public void copyFile(QuestionAttempt attempt, Path target)
//             throws WorkspacePreparationException {
//         try {
//             // TODO: only one question file or multiple?
//             List<Path> answers = resolveAnswerPaths(attempt);
//             for (Path answer : answers) {
//                 if (answer == null) {
//                     continue;
//                 }
//                 Path dest = target.resolve(answer.getFileName().toString());
//                 copyRecursive(answer, dest);
//             }
//         } catch (IOException | RuntimeException e) {
//             throw new WorkspacePreparationException(
//                     "wsp-002", "Answer file copy failed", e);
//         }
//     }

//     /**
//      * Recursively copies the question's resource folder into {@code target}.
//      *
//      * @throws WorkspacePreparationException wsp-003 if the resource folder is missing
//      */
//     public void copyFile(QuestionDefinition question, Path target)
//             throws WorkspacePreparationException {
//         Path folder = resolveQuestionFolder(question);
//         if (folder == null || !Files.exists(folder) || !Files.isDirectory(folder)) {
//             throw new WorkspacePreparationException(
//                     "wsp-003", "Question resource folder missing: " + folder);
//         }
//         try {
//             // Copy the folder's contents directly into the workspace root.
//             copyDirectoryContents(folder, target);
//         } catch (IOException | RuntimeException e) {
//             throw new WorkspacePreparationException(
//                     "wsp-003", "Question resource folder copy failed: " + folder, e);
//         }
//     }

//     // /**
//     //  * Copies the tester source into {@code target}.
//     //  *
//     //  * @throws WorkspacePreparationException wsp-004 if the copy tester file fails
//     //  */
//     // public void copyFile(TesterDefinition tester, Path target)
//     //         throws WorkspacePreparationException {
//     //     try {
//     //         // TODO: tester.getfilepath()?
//     //         Path source = resolveTesterSource(tester);
//     //         if (source == null || !Files.exists(source)) {
//     //             throw new IOException("Tester source does not exist: " + source);
//     //         }
//     //         Path dest = target.resolve(source.getFileName().toString());
//     //         copyRecursive(source, dest);
//     //     } catch (IOException | RuntimeException e) {
//     //         throw new WorkspacePreparationException(
//     //                 "wsp-004", "Tester source copy failed", e);
//     //     }
//     // }

//     /**
//      * Recursively deletes {@code workspace}. No action if workspace is {@code null} or does not exist.
//      *
//      * @throws WorkspacePreparationException wsp-005 on a delete failure
//      */
//     public void cleanup(Path workspace) throws WorkspacePreparationException {
//         if (workspace == null || !Files.exists(workspace)) {
//             return;
//         }
//         try {
//             deleteRecursive(workspace);
//         } catch (IOException | RuntimeException e) {
//             throw new WorkspacePreparationException(
//                     "wsp-005", "Workspace cleanup failed: " + workspace, e);
//         }
//     }

//     /**
//      * Deletes the prepared workspace. Safe to call when nothing was prepared.
//      *
//      * @throws WorkspacePreparationException wsp-005 on a delete failure
//      */
//     @Override
//     public void close() throws WorkspacePreparationException {
//         cleanup(this.workspaceroot);
//         this.workspaceroot = null;
//     }

//     // ------------------------------------------------------------------
//     // type resolvers (centralized so getter-name mismatches are
//     // one-line fixes). These assume the spec getter names.
//     // TODO: replace with actual getter or remain centralised?
//     // ------------------------------------------------------------------

//     /** @return located answer file paths for the attempt. */
//     private List<Path> resolveAnswerPaths(QuestionAttempt attempt) {
//         return attempt.getAnswerPaths();
//     }

//     /** @return the question's resource folder. */
//     private Path resolveQuestionFolder(QuestionDefinition question) {
//         return question.getQuestionFolder();
//     }

//     /** @return the tester bound to the question. */
//     private TesterDefinition resolveTester(QuestionDefinition question) {
//         return question.getTester();
//     }

//     /** @return the tester's source path. */
//     private Path resolveTesterSource(TesterDefinition tester) {
//         return tester.getSourcePath();
//     }

//     /**
//      * Resolves a filesystem-safe id component for the temp-folder prefix.
//      *
//      * <p>If the attempt carries a usable id, it is sanitized and used. Otherwise
//      * a process-wide, monotonically increasing {@code auto-<n>} id is generated
//      * from {@link #AUTO_ID}, so concurrent attempts with no id each receive a
//      * distinct prefix and cannot overwrite one another.</p>
//      *
//      * @return a filesystem-safe attempt identifier used in the temp prefix
//      */
//     private String attemptId(QuestionAttempt attempt) {
//         // TODO: modify this when attempt id is ready / make id here
//         try {
//             String id = attempt.getAttemptId();
//             if (id != null && !id.isBlank()) {
//                 return id.replaceAll("[^A-Za-z0-9._-]", "_");
//             }
//         } catch (RuntimeException ignored) {
//             // Fall through to a generated id.
//         }
//         // auto increment for concurrent safe operation, prevent creating the same folder and overwrite each other
//         return "autoID-" + AUTO_ID.incrementAndGet();
//     }

//     // ------------------------------------------------------------------
//     // Private recursive I/O helpers (copy-only; never mutate sources).
//     // TODO: might require modification when dependencies code ready
//     // ------------------------------------------------------------------

//     /**
//      * Copies a file or directory from {@code source} to {@code dest}.
//      * Directories are copied recursively.
//      */
//     private void copyRecursive(Path source, Path dest) throws IOException {
//         if (Files.isDirectory(source)) {
//             copyDirectoryTree(source, dest);
//         } else {
//             Path parent = dest.getParent();
//             if (parent != null) {
//                 Files.createDirectories(parent);
//             }
//             Files.copy(source, dest,
//                     StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
//         }
//     }

//     /** Copies the <em>contents</em> of {@code sourceDir} into {@code destDir}. */
//     private void copyDirectoryContents(Path sourceDir, Path destDir) throws IOException {
//         Files.createDirectories(destDir);
//         try (Stream<Path> entries = Files.list(sourceDir)) {
//             // To allow stream object to be temporary iterable.
//             for (Path entry : (Iterable<Path>) entries::iterator) {
//                 copyRecursive(entry, destDir.resolve(entry.getFileName().toString()));
//             }
//         }
//     }

//     /** Copies the whole {@code sourceDir} tree to {@code destDir}. */
//     private void copyDirectoryTree(Path sourceDir, Path destDir) throws IOException {
//         // Walk through all files and folder in the directory
//         Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
//             // Create directory when meet one
//             @Override
//             public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
//                     throws IOException {
//                 Files.createDirectories(destDir.resolve(sourceDir.relativize(dir).toString()));
//                 return FileVisitResult.CONTINUE;
//             }

//             // Copy file when meet one
//             @Override
//             public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
//                     throws IOException {
//                 Files.copy(file, destDir.resolve(sourceDir.relativize(file).toString()),
//                         StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
//                 return FileVisitResult.CONTINUE;
//             }
//         });
//     }

//     /** Recursively deletes {@code path} (deepest entries first). */
//     private void deleteRecursive(Path path) throws IOException {
//         try (Stream<Path> walk = Files.walk(path)) {
//             List<Path> ordered = walk.sorted(Comparator.reverseOrder()).toList();
//             for (Path p : ordered) {
//                 Files.deleteIfExists(p);
//             }
//         }
//     }

//     /** Best-effort self-clean used on the failure path; never throws. */
//     private void selfCleanQuietly(Path path) {
//         if (path == null) {
//             return;
//         }
//         try {
//             deleteRecursive(path);
//         } catch (IOException | RuntimeException ignored) {
//             // Swallow: the original coded failure is the one that matters.
//         }
//     }
// }
