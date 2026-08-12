/*
 * SPDX-FileCopyrightText: Copyright (c) 2024-2026 Objectionary.com
 * SPDX-License-Identifier: MIT
 */
package org.eolang.hone;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * A random Java class with one stream pipeline in it.
 *
 * <p>The pipeline is a walk through a typed grammar of the Stream API: a
 * source, a few intermediate operations, a terminal. Every step is picked from
 * the productions that the current element domain allows, so whatever comes out
 * compiles. The walk is seeded, therefore the same seed yields the same class on
 * every machine and in every run, and a failure found once can be looked at
 * again.</p>
 *
 * <p>{@link Grammar} holds the productions the walk picks from, and says which
 * element domains and which corners of the terminal API they reach.</p>
 *
 * <p>One pipeline per class is deliberate. The JVM verifies a class as a whole,
 * so a rewrite that corrupts one pipeline takes down every method around it; in
 * its own class it takes down only itself, and the other pipelines still
 * report.</p>
 *
 * <p>The class prints two lines: the value its pipeline produced and the number
 * its {@code peek} accumulated. The second line is the traversal contract — a
 * pipeline may be fused into anything at all, but it must still walk the same
 * elements, and it must still stop where it stopped. A {@code peek} may
 * therefore sit in front of anything except {@code count()}, the one terminal
 * the API allows to skip the traversal altogether.</p>
 *
 * <p>The grammar does not step around the operations that the rewrite has been
 * known to break — every shape behind #787, #788, #790, #794, #798, #799, #804,
 * #805 and #811 is still reachable, since a generator that avoids the defects we
 * already know about would only prove that we know about them, and nothing
 * about a regression. Nothing is quarantined at the moment: {@code
 * quarantined()} names the shapes that break the rewrite today, so that a suite
 * failing on them every night does not report what is already filed, and both
 * of the clauses it held have been deleted along with the issue that owned
 * them.</p>
 *
 * <p>A pipeline is more than the operators in it, so the walk also picks the
 * frame the pipeline sits in, and each frame opens productions the others
 * cannot reach. It picks who holds the method: a {@code static} one, or an
 * instance one, where a lambda may read {@code this} and become an instance
 * method itself, which is the whole population that {@code 121} and {@code 131}
 * exist for and that #689 found empty. It picks whether the body catches what
 * the pipeline throws, and only a body that catches may draw a {@code boom}
 * operator, which throws from inside the walk and names the element it threw on:
 * the message is the oracle for how far a fused body got before it unwound. The
 * four arguments the method takes — a {@code long}, an {@code int}, a
 * {@code double} and a {@code String} — are captured by lambdas all over the
 * grammar, so the capturing metafactory shapes that {@code 112}–{@code 126} lift
 * are reachable from any domain, in any count and any mix of types.</p>
 *
 * <p>Two properties of the pipeline as a whole are picked last, once the walk
 * knows where it ended. A pipeline may go {@code parallel()}, which no rule may
 * fuse a {@code skip} or a {@code distinct} through — {@code 222}–{@code 225}
 * revert exactly that — and which is offered only when nothing counts the
 * traversal and the terminal reduces no floating point, so that what the class
 * prints stays the same on every run. And a terminal may return nothing at all:
 * a {@code forEach} that accumulates, whose only oracle is the number the peek
 * line carries.</p>
 *
 * <p>Reaching a shape is not the same as reaching it soon, and widening the
 * grammar re-deals every seed. #811 — two operators in a primitive-stream run
 * whose trailing {@code boxed()} keeps it in the boxed domain — was found at seed
 * 334 of the walk as it stood before this grammar grew, already well outside the
 * 120 seeds walked by default, and the re-dealt walk does not reach it inside 360
 * at all. That is the argument for the deterministic pack: a shape the walk finds
 * earns one of its own under {@code optimize/streams/} the day it is understood,
 * because the walk is the net that catches a defect, not the test that keeps it
 * caught.</p>
 *
 * @since 0.30.0
 */
final class RandomPipeline {

    /**
     * The line separator of this platform, in the source code the walk prints.
     */
    private static final String EOL = String.format("%n");

    /**
     * The template of the class, with the package, the name, the body of the
     * method that holds the pipeline, the modifiers of that method, and the
     * call {@code main} reaches it by.
     *
     * <p>The four arguments the method takes are what a capturing lambda
     * captures, one of every category the metafactory distinguishes. They are
     * arguments and not locals on purpose: javac folds a constant local into the
     * body that reads it, and a folded capture is no capture at all.</p>
     */
    private static final String TEMPLATE = String.join(
        RandomPipeline.EOL,
        "package %1$s;",
        "",
        "import java.util.ArrayList;",
        "import java.util.Arrays;",
        "import java.util.Comparator;",
        "import java.util.List;",
        "import java.util.TreeMap;",
        "import java.util.stream.Collector;",
        "import java.util.stream.Collectors;",
        "import java.util.stream.DoubleStream;",
        "import java.util.stream.IntStream;",
        "import java.util.stream.LongStream;",
        "import java.util.stream.Stream;",
        "",
        "public final class %2$s {",
        "",
        "    private static final long[] NUMBERS = {3L, 7L, 7L, 1L, 9L, 4L, 2L, 9L};",
        "",
        "    private static final int[] INTS = {5, 2, 9, 2, 7, 1, 8};",
        "",
        "    private static final double[] DECIMALS = {1.5, 2.25, 1.5, 8.125, 0.5};",
        "",
        "    private static final List<Long> LIST = List.of(4L, 8L, 8L, 15L, 16L, 23L);",
        "",
        "    private static final List<Integer> INTEGERS = List.of(6, 3, 6, 11, 4);",
        "",
        "    private static final List<Double> DOUBLES = List.of(0.5, 2.5, 0.5, 3.75);",
        "",
        "    private static final List<String> WORDS = List.of(\"phi\", \"rule\", \"phi\");",
        "",
        "    private static final String PROSE = \"the quick brown fox jumps over it\";",
        "",
        "    private static final List<Pair> PAIRS =",
        "        List.of(new Pair(2L, \"two\"), new Pair(5L, \"five\"), new Pair(2L, \"two\"));",
        "",
        "    private static final long[] SUM = new long[1];",
        "",
        "    private final long step;",
        "",
        "    private final String mark;",
        "",
        "    private %2$s(final long start) {",
        "        this.step = start;",
        "        this.mark = \"m\" + start;",
        "    }",
        "",
        "    private static IllegalStateException boom(final Object item) {",
        "        return new IllegalStateException(\"boom at \" + item);",
        "    }",
        "",
        "    private long bump(final long num) {",
        "        return num + this.step;",
        "    }",
        "",
        "    private String decorate(final String text) {",
        "        return this.mark + text;",
        "    }",
        "",
        "    private %4$sString pipe(final long by, final int cut,",
        "        final double frac, final String tag) {",
        "%3$s",
        "    }",
        "",
        "    public static void main(final String... args) {",
        "        System.out.println(\"%2$s=\" + %5$s);",
        "        System.out.println(\"%2$s.peeked=\" + SUM[0]);",
        "    }",
        "",
        "    static final class Pair implements Comparable<Pair> {",
        "",
        "        private final long num;",
        "",
        "        private final String word;",
        "",
        "        Pair(final long key, final String text) {",
        "            this.num = key;",
        "            this.word = text;",
        "        }",
        "",
        "        long key() {",
        "            return this.num;",
        "        }",
        "",
        "        String text() {",
        "            return this.word;",
        "        }",
        "",
        "        @Override",
        "        public String toString() {",
        "            return this.num + \":\" + this.word;",
        "        }",
        "",
        "        @Override",
        "        public boolean equals(final Object other) {",
        "            return other instanceof Pair",
        "                && ((Pair) other).num == this.num",
        "                && ((Pair) other).word.equals(this.word);",
        "        }",
        "",
        "        @Override",
        "        public int hashCode() {",
        "            return (int) this.num * 31 + this.word.hashCode();",
        "        }",
        "",
        "        @Override",
        "        public int compareTo(final Pair other) {",
        "            return Long.compare(this.num, other.num);",
        "        }",
        "    }",
        "}",
        ""
    );

    /**
     * The body that returns what the pipeline produced.
     */
    private static final String VALUE = String.join(
        RandomPipeline.EOL,
        "        return String.valueOf(",
        "            %s",
        "        );"
    );

    /**
     * The body of a pipeline whose terminal returns nothing, where the peek
     * line is the whole oracle.
     */
    private static final String WALK = String.join(
        RandomPipeline.EOL,
        "        %s;",
        "        return \"walked\";"
    );

    /**
     * What a guarded frame wraps a body in, so that a {@code boom} operator
     * names the element it threw on instead of killing the JVM.
     */
    private static final String GUARD = String.join(
        RandomPipeline.EOL,
        "        try {",
        "%s",
        "        } catch (final RuntimeException ex) {",
        "            return \"threw:\" + ex.getMessage();",
        "        }"
    );

    /**
     * The domains whose terminals reduce floating point, where the order the
     * additions happen in is the order they are printed in, and a parallel
     * pipeline would therefore stop being an oracle.
     */
    private static final List<String> FLOATS = Arrays.asList("dbox", "reals");

    /**
     * Who holds the method the pipeline sits in.
     */
    private static final List<String> HOLDERS = Arrays.asList("static", "instance");

    /**
     * Whether the body catches what the pipeline throws.
     */
    private static final List<String> GUARDS = Arrays.asList("plain", "guarded");

    /**
     * What an intermediate step may be, an operation being three times as
     * likely as a conversion to another element domain or a peek. A frame adds
     * its own roles to this list, one each, so an instance method reads
     * {@code this} and a guarded body throws about as often as it peeks.
     */
    private static final List<String> ROLES = Arrays.asList(
        "stage", "stage", "stage", "stage", "stage", "stage",
        "turn", "turn", "peek", "peek"
    );

    /**
     * The largest number of intermediate operations in one pipeline.
     */
    private static final int STAGES = 7;

    /**
     * One walk in this many goes parallel, when it is allowed to.
     */
    private static final int RACES = 5;

    /**
     * How many times a seed may re-roll away from a quarantined shape before
     * the generator gives up and says so.
     */
    private static final int ATTEMPTS = 32;

    /**
     * The seed of the walk.
     */
    private final long seed;

    /**
     * Ctor.
     * @param sed The seed, which fully determines the class produced
     */
    RandomPipeline(final long sed) {
        this.seed = sed;
    }

    /**
     * Print the Java source of the class.
     * @param pkg The package to put the class in
     * @param name The name of the class, also the label of its printed value
     * @return Java source code, ready for {@code javac}
     */
    String java(final String pkg, final String name) {
        final List<String> walk = this.rolled(new Grammar());
        final String[] frame = walk.get(0).split("\\|", 3);
        final StringBuilder pipe = new StringBuilder(Grammar.code(walk.get(1)));
        for (final String step : walk.subList(2, walk.size())) {
            pipe.append(RandomPipeline.EOL).append('.').append(Grammar.code(step));
        }
        return String.format(
            RandomPipeline.TEMPLATE,
            pkg,
            name,
            RandomPipeline.body(
                RandomPipeline.shape(
                    frame[2], walk.get(walk.size() - 1).split("\\|", 3)[1]
                ),
                pipe.toString()
            ),
            RandomPipeline.modifier(frame[1]),
            RandomPipeline.call(frame[1], name)
        );
    }

    /**
     * The walk this seed settles on, re-rolled away from every quarantined
     * shape.
     * @param grammar The grammar to walk
     * @return The grammar lines the class is printed from, the frame first
     */
    private List<String> rolled(final Grammar grammar) {
        List<String> found = new ArrayList<>(0);
        for (int attempt = 0; attempt < RandomPipeline.ATTEMPTS; ++attempt) {
            final List<String> walk = RandomPipeline.walk(
                grammar, new Random(RandomPipeline.scrambled(this.seed * 31L + attempt))
            );
            if (RandomPipeline.quarantined(walk).isEmpty()) {
                found = walk;
                break;
            }
        }
        if (found.isEmpty()) {
            throw new IllegalStateException(
                String.format(
                    "seed %d walks into a quarantined shape %d times in a row",
                    this.seed, RandomPipeline.ATTEMPTS
                )
            );
        }
        return found;
    }

    /**
     * The seed, mixed.
     *
     * <p>{@link Random} scrambles a seed by exclusive-or alone, which moves the
     * state of two neighbouring seeds by so little that the first few bits they
     * draw are the same bits — the first {@code nextInt(2)} of seeds 0 to 200
     * answers the same way almost every time. The walk draws the frame it sits
     * in first of all, so without a mix of its own three quarters of the seeds
     * would land in one frame and the axis would be an axis in name only. This
     * is SplitMix64's finalizer, which spreads one increment across all sixty
     * four bits.</p>
     *
     * @param seed The seed, as the caller counted it
     * @return A seed whose neighbours are nowhere near it
     */
    private static long scrambled(final long seed) {
        long mixed = seed + 0x9E37_79B9_7F4A_7C15L;
        mixed = (mixed ^ mixed >>> 30) * 0xBF58_476D_1CE4_E5B9L;
        mixed = (mixed ^ mixed >>> 27) * 0x94D0_49BB_1331_11EBL;
        return mixed ^ mixed >>> 31;
    }

    /**
     * One walk through the grammar, as the lines of it that were picked.
     *
     * <p>The first line is not a step but the frame the pipeline sits in, as
     * {@code frame|holder|guard}. The frame is picked before anything else
     * because it decides which roles the walk may draw from.</p>
     *
     * @param grammar The grammar to walk
     * @param rnd The source of randomness
     * @return The grammar lines, a frame first, a source second, a terminal last
     */
    private static List<String> walk(final Grammar grammar, final Random rnd) {
        final String holder =
            RandomPipeline.HOLDERS.get(rnd.nextInt(RandomPipeline.HOLDERS.size()));
        final String guard =
            RandomPipeline.GUARDS.get(rnd.nextInt(RandomPipeline.GUARDS.size()));
        final List<String> roles = new ArrayList<>(RandomPipeline.ROLES);
        if ("instance".equals(holder)) {
            roles.add("self");
        }
        if ("guarded".equals(guard)) {
            roles.add("boom");
        }
        String domain = Grammar.domain(rnd);
        final List<String> steps = new ArrayList<>(0);
        steps.add(String.join("|", "frame", holder, guard));
        steps.add(
            String.join("|", domain, "source", grammar.pick(domain, "source", rnd))
        );
        final int total = 1 + rnd.nextInt(RandomPipeline.STAGES);
        boolean observed = false;
        for (int stage = 0; stage < total; ++stage) {
            final String role = roles.get(rnd.nextInt(roles.size()));
            final String fragment = grammar.pick(domain, role, rnd);
            steps.add(String.join("|", domain, role, fragment));
            observed = observed || "peek".equals(role) || "boom".equals(role);
            if ("turn".equals(role)) {
                domain = fragment.substring(fragment.lastIndexOf('|') + 1);
            }
        }
        final String end = grammar.terminal(domain, observed, rnd);
        if (RandomPipeline.raceable(end, observed, domain)
            && rnd.nextInt(RandomPipeline.RACES) == 0) {
            steps.add(String.join("|", domain, "race", "parallel()"));
        }
        steps.add(end);
        return steps;
    }

    /**
     * Whether this pipeline may go parallel.
     *
     * <p>Two things forbid it. A traversal that is counted, by a {@code peek}
     * or by a {@code forEach} that accumulates, is counted by several ForkJoin
     * workers at once into one unsynchronized slot, and what they lose is not
     * the same on every run. And a terminal that reduces floating point adds in
     * whatever order the splits happened to combine in. Everything else the
     * grammar can build is ordered, so a parallel run prints what a sequential
     * one prints — which is the very contract {@code 222}-{@code 225} keep by
     * refusing to fuse a {@code skip} or a {@code distinct} into a parallel
     * pipeline.</p>
     *
     * @param end The terminal line the walk picked
     * @param observed TRUE if something counts the traversal
     * @param domain The element domain the pipeline ended in
     * @return TRUE if {@code parallel()} may be appended
     */
    private static boolean raceable(final String end, final boolean observed,
        final String domain) {
        return !observed
            && !"void".equals(end.split("\\|", 3)[1])
            && !RandomPipeline.FLOATS.contains(domain);
    }

    /**
     * The template of the body of the method that holds the pipeline.
     * @param guard Either {@code plain} or {@code guarded}
     * @param role The role of the terminal, either {@code end} or {@code void}
     * @return A template with one {@code %s} where the pipeline goes
     */
    private static String shape(final String guard, final String role) {
        final String body;
        if ("void".equals(role)) {
            body = RandomPipeline.WALK;
        } else {
            body = RandomPipeline.VALUE;
        }
        final String shape;
        if ("guarded".equals(guard)) {
            shape = String.format(RandomPipeline.GUARD, RandomPipeline.deeper(body));
        } else {
            shape = body;
        }
        return shape;
    }

    /**
     * The body of the method, with the pipeline in it.
     *
     * <p>The pipeline arrives with one step per line and no indentation at all,
     * and every line but the first is pushed out to where the {@code %s} of the
     * shape stands, so that the shapes carry their own layout and nothing has
     * to be told twice how deep a body is nested.</p>
     *
     * @param shape The template of the body
     * @param pipe The pipeline, one step per line
     * @return The Java statements of the body
     */
    private static String body(final String shape, final String pipe) {
        final int mark = shape.indexOf("%s");
        return String.format(
            shape,
            pipe.replace(
                RandomPipeline.EOL,
                RandomPipeline.EOL.concat(
                    shape.substring(shape.lastIndexOf('\n', mark) + 1, mark)
                )
            )
        );
    }

    /**
     * The same block, one level deeper.
     * @param block The Java statements
     * @return The very same statements, indented by four more spaces
     */
    private static String deeper(final String block) {
        return "    ".concat(
            block.replace(RandomPipeline.EOL, RandomPipeline.EOL.concat("    "))
        );
    }

    /**
     * The modifiers of the method that holds the pipeline.
     * @param holder Either {@code static} or {@code instance}
     * @return What stands in front of the return type
     */
    private static String modifier(final String holder) {
        final String modifier;
        if ("instance".equals(holder)) {
            modifier = "";
        } else {
            modifier = "static ";
        }
        return modifier;
    }

    /**
     * The expression {@code main} reaches the pipeline by.
     * @param holder Either {@code static} or {@code instance}
     * @param name The name of the class
     * @return The Java expression that runs the pipeline once
     */
    private static String call(final String holder, final String name) {
        final String call;
        if ("instance".equals(holder)) {
            call = String.format("new %s(4L).pipe(2L, 3, 1.5, \"z\")", name);
        } else {
            call = "pipe(2L, 3, 1.5, \"z\")";
        }
        return call;
    }

    /**
     * The issue that quarantines this walk, if one does.
     *
     * <p>A shape that breaks the rewrite today, emitting a class the verifier
     * rejects, gets a clause here once it is reported upstream: a walk that
     * reaches it is re-rolled from a derived seed instead of being generated, so
     * the suite stays a net for regressions rather than a standing failure. Each
     * clause is as narrow as the shape it owns and is named by its issue, so
     * closing the issue widens the walk again by deleting one.</p>
     *
     * <p>There is nothing to quarantine right now. The two clauses this method
     * held are both gone: #804's, a {@code filter} behind a {@code dropWhile},
     * which lost the copy of the item its predicate eats, and #805's, a
     * conversion into an object domain with a {@code peek}, a {@code filter} or
     * another guard between it and a stateful guard, whose keep-frame was read
     * off whatever opcode happened to sit in front of it. Add the next one the
     * day the walk finds a shape we cannot fix at once.</p>
     *
     * @param walk The lines the walk picked, the frame first
     * @return The issue that owns the shape, or an empty string
     */
    @SuppressWarnings("PMD.UnusedFormalParameter")
    private static String quarantined(final List<String> walk) {
        return "";
    }
}
