#!/usr/bin/env groovy

package git

import java.util.regex.Matcher
import java.util.regex.Pattern


class GitCommands {

  final static String GIT_UPDATE = "git_update";
  final static String GIT_CLONE_SETUP = "git_clone_setup";
  final static String GIT_PRE_COMMIT = "git_pre_commit";
  final static String GIT_COMMIT = "git_commit";
  final static String GIT_UPDATE_ALL = "git_update_all"

  static int lastExitValue = -1;

  static main(args) {

    def commands = [
      GIT_CLONE_SETUP,
      GIT_UPDATE,
      GIT_UPDATE_ALL,
      GIT_PRE_COMMIT,
      GIT_COMMIT
    ];

    if (args.length > 0){
      String methodName = args[0];

      switch (methodName) {
        case GIT_UPDATE:
          gitUpdate(null);
          break;

        case GIT_PRE_COMMIT:
          gitPreCommit(null);
          break;

        case GIT_CLONE_SETUP:
          gitCloneSetup(null);
          break;

        case GIT_COMMIT:
          gitCommit(null);
          break;

        case GIT_UPDATE_ALL:
          gitUpdateAll(null);
          break

        default:
          println("$methodName is an invalid commands. Valid commands are $commands");
          break;
      }
    }
    else {
      println("No argument detected. Valid commands are $commands")
    }
  }

  public static gitUpdateAll(String dir) {
    String dirs = executeShell("ls -la", dir);
    String regex = /d.+?\d\d:\d\d ([A-z0-9-_]+)/;

    // Possibly windows
    if (lastExitValue != 0) {
      dirs = executeShell("dir", dir);
      regex = /<DIR>\s+?([A-z0-9-_]+)/;
    }

    Matcher m = Pattern.compile(regex).matcher(dirs);
    while(m.find()){
      String directory = m.group(1);

      printTitle("Update $directory");
      gitUpdate(directory);
    }
  }


  public static gitPreCommit(String dir) {
    verifyInGitDir(dir);

    // Ensure that repository is set up appropriately
    gitSetup(dir);

    // Update repository
    String updateRes = gitUpdate(dir);

    // Contains merge conflicts from popping stash
    if (updateRes.contains("CONFLICT") || updateRes.contains("MERGE")){
      println("Please resolve your merge conflicts before commiting");
      return;
    }

    String out = read("If there are no conflicts, would you like to continue to commit changes? (y,n)");

    if (!out.equals("y")){
      return;
    }

    gitCommit(dir);
  }

  public static gitCommit(String dir) {
    verifyInGitDir(dir);

    printTitle("Check out dev");
    println(executeShell("git checkout dev", dir));

    String changes = executeShell("git status --short", dir);
    println(changes);

    String ADDITION = " A ";
    String MODIFICATION = " M ";
    String DELETION = " D ";
    String UNTRACKED = "?? ";

    String[] mods = [
      ADDITION,
      MODIFICATION,
      DELETION,
      UNTRACKED
    ];
    for (int i = 0; i < mods.length; i++) {
      String mod = mods[i];
      Matcher m = Pattern.compile(mod+"(.+)").matcher(changes);

      if (!changes.contains(mod)) {
        continue;
      }

      boolean isDelete = false;

      switch (mod) {
        case ADDITION:
          printTitle("Commit Additions");
          break;
        case MODIFICATION:
          printTitle("Commit Modifications");
          break;
        case DELETION:
          isDelete = true;
          printTitle("Commit Deletions");
          break;
        case UNTRACKED:
          printTitle("Commit Untracked Changes");
          break;

        default:
          continue;
          break;
      }

      boolean skip = false;
      boolean all = false;

      while (m.find()) {
        String toCommit = m.group(1);

        // Skip non-file untracked changes
        if (mod.equals(UNTRACKED) && !toCommit.contains(".")){
          continue;
        }

        String out = read("[===== $toCommit ====] Stage for commit? (y, n, all, esc)");

        if (out.equals("all")) {
          all = true;
        } else if (out.equals("esc")){
          skip = true;
        }

        if (skip){
          continue;
        }

        // Stage for addition/deletion
        if (all || out.equals("y")){
          if (isDelete){
            println(executeShell("git rm $toCommit", dir));
          } else {
            println(executeShell("git add $toCommit", dir));
          }
        }
      }

      String out = read("Would you like to commit these changes to your local repository? (y,n)");

      if (!out.equals("y")){
        return;
      }

      String commitMsg = read("Commit message");

      printTitle("commit");
      println(executeShell("git commit -m $commitMsg", dir));

      out = read("Would you like to push your commit(s) to your origin? (y,n)");

      if (!out.equals("y")){
        return;
      }

      printTitle("Push to origin/dev");
      println(executeShell("git push origin dev", dir));
    }
  }

  public static gitCloneSetup(String dir) {

    String out = read("Are you currently in the directory where you would like to clone your repository? (y,n)");

    if (!out.equals("y")){
      return;
    }

    String url = "";
    while (!verifyGitUrl(url)) {
      url = read("URL to clone");
    }

    printTitle("Clone repository");
    println("Please be patient as this process may take a while to complete");

    // Clone the url
    String res = executeShell("git clone $url", dir);
    println("Clone complete");

    Matcher m = Pattern.compile("'(.+)'").matcher(res);
    m.find();

    String directory = m.group(1);

    // Setup just cloned repo
    gitSetup(directory);
  }

  private static gitSetup(String dir) {
    verifyInGitDir(dir);

    // Create remote upstream
    String remotes = executeShell("git remote show", dir);
    if (!remotes.contains("upstream")) {
      printTitle("Create remote upstream");

      String url = "";
      while (!verifyGitUrl(url)){
        url = read("Please provide remote upstream url");
      }
      executeShell("git remote add upstream $url", dir);
      println(executeShell("git remote show", dir));
    }

    // Create dev branch
    String branches = executeShell("git branch", dir);
    if (!branches.contains("dev")) {
      printTitle("Create dev branch");
      executeShell("git checkout -b dev", dir);
      println(executeShell("git branch", dir));
    }
  }

  public static gitUpdate(String dir) {
    verifyInGitDir(dir);

    gitSetup(dir);

    printTitle("Stash Changes");
    println(executeShell("git stash", dir));

    printTitle("Checkout Master Branch");
    println(executeShell("git checkout master", dir));

    printTitle("Pull Upstream Master");
    println(executeShell("git pull upstream master", dir));

    printTitle("Checkout and Rebase Dev Branch");
    executeShell("git checkout dev", dir);
    println(executeShell("git rebase master", dir));

    printTitle("Pop Stashed Changes");
    String popStash = executeShell("git stash pop", dir);
    println(popStash);

    return popStash;
  }

  private static verifyGitUrl(String url){
    boolean isHttps = Pattern.compile("https://github.com/").matcher(url).find();
    boolean isSSH = Pattern.compile("github@github.com/").matcher(url).find();
    boolean isReadOnly = Pattern.compile("git://github.com/").matcher(url).find();

    return isHttps || isReadOnly || isSSH;
  }

  /**
   * Determines if the user is currently navigated into a git directory
   * @return boolean
   */
  private static boolean verifyInGitDir(String dir, boolean throwException) {
    executeShell("git status", dir);

    boolean inDir = lastExitValue == 0;

    if (!inDir && throwException){
      throw new RuntimeException("Attempting to execute not from ");
    }

    return inDir;
  }

  private static boolean verifyInGitDir(String dir) {
    return verifyInGitDir(dir, true);
  }

  /**
   * Executes shell commands
   * 
   * @param cmd
   *        {@link String} command
   * @param output
   *        boolean - whether to print the output of the execution
   * @return {@link String} output of execution
   */
  private static String executeShell(String cmd, String workingDir) {

    lastExitValue = -1;

    def sout = new StringBuffer();
    def serr = new StringBuffer();

    def exec = cmd.execute(null, new File("./" + (workingDir == null ? "" : workingDir)));
    exec.waitForProcessOutput(sout, serr);

    lastExitValue = exec.exitValue();

    return lastExitValue == 0 ? sout.toString() : "ERROR: $serr.toString()";
  }

  private static   String read(String msg) {
    print(msg + ": ");
    BufferedReader br = new BufferedReader(new InputStreamReader(System.in))
    return br.readLine();
  }

  private static printTitle(String title) {
    println("\n=============================================== ${title.toUpperCase()} ===============================================");
  }
}
