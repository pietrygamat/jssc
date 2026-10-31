package jssc;

import jssc.junit.rules.DisplayMethodNameRule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static jssc.NativeIdent.isBlank;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Are the precompiled natives built for this version, from these sources, by CI? A release ships the natives on the
 * classpath (<code>src/main/resources-precompiled</code>) as they are, so this reads the <code>JSSC-IDENT</code>
 * ({@link NativeIdent}) of each and checks:
 * <ol>
 *     <li>Every target that <code>cross-compile.yml</code> builds has exactly one library with exactly one ident,
 *     and no other directory is shipped.</li>
 *     <li>version is the native version of this build (the pom version, without its qualifier with
 *     <code>-Djssc.native.release=true</code>). A native that was not rebuilt still says the old version.</li>
 *     <li>target is the directory the library sits in.</li>
 *     <li>All natives come from one CI run: one rev (<code>-Djssc.build.rev</code>, if given) and one origin
 *     (<code>-Djssc.build.origin</code>, else <code>GITHUB_REPOSITORY</code> when run by GitHub Actions); no
 *     "-dirty" or "unknown" rev and no "origin=local".</li>
 *     <li>They are fresh: rev is an ancestor of HEAD, and since rev nothing changed in the native sources. Needs the
 *     git history back to rev (<code>fetch-depth: 0</code>).</li>
 * </ol>
 * Committed natives are usually stale between releases, so this test runs only on request:
 * <code>-Djssc.verify.natives.skip=false</code> runs it, and only it (pom.xml). The release does so in
 * <code>release:prepare</code>, and <code>cross-compile.yml</code> before it pushes the natives it built.
 */
public class PrecompiledNativesTest extends DisplayMethodNameRule {

    private static final Logger LOG = LogManager.getLogger(PrecompiledNativesTest.class);

    // The targets in the matrix of cross-compile.yml, each a directory under natives/
    private static final List<String> TARGETS = Arrays.asList(
            "linux_32", "linux_64", "linux_arm", "linux_arm64", "linux_ppc", "linux_riscv32", "linux_riscv64",
            "osx_64", "osx_arm64",
            "windows_32", "windows_64", "windows_arm64");

    // src/main/cpp includes jssc_SerialNativeInterface.h, which the build keeps equal to the header generated from
    // SerialNativeInterface.java (cmake-build in ant/build.xml), so a JNI change counts too
    private static final String[] NATIVE_SOURCES = {"src/main/cpp", "toolchain", "CMakeLists.txt"};

    private static File nativesDir;
    // The idents of the targets whose library could be read; the others are in unreadable
    private static final Map<String, Map<String, String>> idents = new TreeMap<String, Map<String, String>>();
    private static final List<String> unreadable = new ArrayList<String>();

    @BeforeClass
    public static void readIdents() throws Exception {
        final URL natives = PrecompiledNativesTest.class.getClassLoader().getResource("natives");
        assertNotNull("No natives on the classpath", natives);
        assertEquals("Natives on the classpath are not a directory: " + natives, "file", natives.getProtocol());
        nativesDir = new File(natives.toURI());
        for (String target : TARGETS) {
            final File[] files = new File(nativesDir, target).listFiles();
            if (files == null || files.length == 0) {
                unreadable.add(target + ": no native library in " + new File(nativesDir, target));
            } else if (files.length != 1) {
                unreadable.add(target + ": expected one library, found " + Arrays.toString(files));
            } else if (!NativeIdent.LIBRARY_NAMES.contains(files[0].getName())) {
                unreadable.add(target + ": " + files[0] + " is not one of " + NativeIdent.LIBRARY_NAMES);
            } else {
                try {
                    idents.put(target, NativeIdent.parse(files[0]));
                } catch (AssertionError e) {
                    unreadable.add(target + ": " + e.getMessage() + "; it is stale or was not built by this project");
                }
            }
        }
        for (Map.Entry<String, Map<String, String>> entry : idents.entrySet()) {
            LOG.info("{}: {}", entry.getKey(), entry.getValue());
        }
    }

    @Test
    public void everyTargetHasOneLibraryWithOneIdent() {
        assertNoProblems(unreadable);
    }

    @Test
    public void onlyTheTargetsAreShipped() {
        final List<String> problems = new ArrayList<String>();
        for (File dir : nativesDir.listFiles()) {
            if (!TARGETS.contains(dir.getName())) {
                problems.add(dir + " is shipped but not built by cross-compile.yml");
            }
        }
        assertNoProblems(problems);
    }

    @Test
    public void versionIsTheNativeVersion() {
        // Set by surefire in pom.xml; absent when run outside maven, e.g. from an IDE
        final String expected = System.getProperty("jssc.expected.native.version");
        assumeTrue("Native version not passed by maven", !isBlank(expected));
        final List<String> problems = new ArrayList<String>();
        for (Map.Entry<String, Map<String, String>> entry : idents.entrySet()) {
            final String version = entry.getValue().get("version");
            if (!expected.equals(version)) {
                problems.add(entry.getKey() + ": version is " + version + ", expected " + expected
                        + ". Run cross-compile.yml (with 'release' checked ahead of a release) and merge its"
                        + " -precompiled-natives branch");
            }
        }
        assertNoProblems(problems);
    }

    @Test
    public void targetIsTheDirectory() {
        final List<String> problems = new ArrayList<String>();
        for (Map.Entry<String, Map<String, String>> entry : idents.entrySet()) {
            final String target = entry.getValue().get("target");
            if (!entry.getKey().equals(target)) {
                problems.add(entry.getKey() + ": ident says target=" + target);
            }
        }
        assertNoProblems(problems);
    }

    @Test
    public void allComeFromOneCiRun() {
        final String expectedRev = System.getProperty("jssc.expected.build.rev");
        String expectedOrigin = System.getProperty("jssc.expected.build.origin");
        if (isBlank(expectedOrigin)) {
            // Only this repository's CI may build the natives it ships
            expectedOrigin = System.getenv("GITHUB_REPOSITORY");
        }
        final List<String> problems = new ArrayList<String>();
        for (Map.Entry<String, Map<String, String>> entry : idents.entrySet()) {
            final String target = entry.getKey();
            final String rev = entry.getValue().get("rev");
            final String origin = entry.getValue().get("origin");
            if (!isTraceable(rev)) {
                problems.add(target + ": rev=" + rev + " cannot be traced to a commit; only CI-built natives may ship");
            } else if (!isBlank(expectedRev) && !expectedRev.equals(rev)) {
                problems.add(target + ": rev is " + rev + ", expected " + expectedRev);
            }
            if (origin == null || origin.equals("local")) {
                problems.add(target + ": origin=" + origin + ", built outside CI; only CI-built natives may ship");
            } else if (!isBlank(expectedOrigin) && !expectedOrigin.equals(origin)) {
                problems.add(target + ": origin is " + origin + ", expected " + expectedOrigin);
            }
        }
        final Set<String> revs = distinct("rev");
        if (revs.size() > 1) {
            problems.add("natives come from different revisions: " + revs + "; some were not rebuilt");
        }
        final Set<String> origins = distinct("origin");
        if (origins.size() > 1) {
            problems.add("natives come from different origins: " + origins);
        }
        assertNoProblems(problems);
    }

    @Test
    public void nativeSourcesAreUnchangedSinceRev() throws Exception {
        assertNotNull("Cannot read the git history: not a git checkout, or git is missing", git("rev-parse", "HEAD"));
        final List<String> problems = new ArrayList<String>();
        for (String rev : distinct("rev")) {
            if (!isTraceable(rev)) {
                continue; // see allComeFromOneCiRun
            }
            if (git("rev-parse", "-q", "--verify", rev + "^{commit}") == null) {
                problems.add("rev " + rev + " is not in the git history (shallow clone? check out with fetch-depth: 0)");
            } else if (git("merge-base", "--is-ancestor", rev, "HEAD") == null) {
                problems.add("natives were built from " + rev + ", which is not part of this branch");
            } else {
                final List<String> diff = new ArrayList<String>(Arrays.asList("diff", "--name-only", rev, "HEAD", "--"));
                diff.addAll(Arrays.asList(NATIVE_SOURCES));
                final String changed = git(diff.toArray(new String[0]));
                assertNotNull("git diff failed", changed);
                if (!changed.isEmpty()) {
                    problems.add("native sources changed since the natives were built from " + rev + ": "
                            + changed.replace('\n', ' '));
                }
            }
        }
        assertNoProblems(problems);
    }

    private static boolean isTraceable(String rev) {
        return rev != null && rev.matches("[0-9a-f]{4,40}");
    }

    private static Set<String> distinct(String key) {
        final Set<String> values = new TreeSet<String>();
        for (Map<String, String> ident : idents.values()) {
            values.add(String.valueOf(ident.get(key)));
        }
        return values;
    }

    /**
     * Runs git in the working directory, the project base directory under surefire
     *
     * @return its trimmed output, or null if it failed
     */
    private static String git(String... args) throws IOException, InterruptedException {
        final List<String> command = new ArrayList<String>();
        command.add("git");
        command.addAll(Arrays.asList(args));
        final Process process = new ProcessBuilder(command).start();
        process.getOutputStream().close();
        final InputStream in = process.getInputStream();
        final String output;
        try {
            output = new String(NativeIdent.readFully(in), "UTF-8").trim();
        } finally {
            in.close();
        }
        return process.waitFor() == 0 ? output : null;
    }

    private static void assertNoProblems(Collection<String> problems) {
        final StringBuilder message = new StringBuilder(problems.size() + " problem(s) with the precompiled natives:");
        for (String problem : problems) {
            message.append("\n  ").append(problem);
        }
        assertTrue(message.toString(), problems.isEmpty());
    }
}
